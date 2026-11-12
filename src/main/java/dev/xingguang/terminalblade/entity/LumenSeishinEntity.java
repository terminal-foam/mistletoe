package dev.xingguang.terminalblade.entity;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.combat.MistletoeDamage;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * 流明·星：三阶段人形剑士 Boss。
 *
 * <p>外观拿刀，但伤害完全由本实体结算，不走玩家的 {@code attack_base} 连段公式，
 * 也不使用 SlashBlade 的输入/连段状态机 —— 这样最不容易出 bug。</p>
 *
 * <p>当前实现：M1 实体 + 三阶段，M2 招式（普攻、霜刃 7×2、暮色斩星 25%、朝露回血）。</p>
 */
public class LumenSeishinEntity extends TamableAnimal implements GeoAnimatable {
    public static final float MAX_HEALTH = 360.0F;
    private static final int PHASE_LOCK_TICKS = 20;

    /** 朝露普通三连：每刀 5。 */
    /** 随从形态：最大生命 20，普攻与玩家初雪一致。 */
    public static final float TAME_MAX_HEALTH = 100.0F;
    private static final int TAME_REGEN_INTERVAL = 40;

    private static final float MELEE_DAWN = 5.0F;
    /** 初雪普通四连：每刀 6。 */
    private static final float MELEE_FIRST_SNOW = 6.0F;
    /** 暮色普通攻击：玩家最大生命值 × 10%。 */
    private static final float MELEE_MISTLETOE_RATIO = 0.10F;

    /** 霜刃：7 刀，每刀固定 2；前方 100°、6 格。 */
    private static final int FROST_HITS = 7;
    private static final float FROST_DAMAGE = 2.0F;
    private static final double FROST_RANGE = 6.0D;
    private static final double FROST_CONE_DEGREES = 100.0D;
    private static final long FROST_COOLDOWN = 240L;

    /** 暮色斩星：一次 25% 最大生命值，前摇 1.2 秒，冷却 15 秒。 */
    private static final float SLASH_RATIO = 0.25F;
    private static final long SLASH_WINDUP = 24L;
    private static final long SLASH_COOLDOWN = 300L;

    /** 朝露回血：每次 10，整场最多 30，冷却 10 秒。 */
    private static final float HEAL_AMOUNT = 10.0F;
    private static final float HEAL_CAP = 30.0F;
    private static final long HEAL_COOLDOWN = 200L;

    /** 终局蓄力：P3 首次掉到 36 HP 触发，蓄力 6 秒、球形半径 12 格，只执行一次。 */
    private static final float FINAL_CHARGE_THRESHOLD = 36.0F;
    private static final int FINAL_CHARGE_TICKS = 120;
    private static final double FINAL_CHARGE_RADIUS = 12.0D;
    private static final int FINAL_WARN_1 = 80;
    private static final int FINAL_WARN_2 = 40;
    /** 处决伤害：没有 bypasses_invulnerability，特殊免疫/锁血仍能拦下。 */
    private static final float FINAL_EXECUTE_DAMAGE = 9999.0F;
    /** 终局蓄力期间的减伤：只承受 10% 伤害，但仍可被打死（结局 A）。 */
    private static final float FINAL_CHARGE_DAMAGE_MULTIPLIER = 0.10F;

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(LumenSeishinEntity.class, EntityDataSerializers.INT);
    /** 当前招式动画，客户端据此压入对应 VMD 帧段。 */
    private static final EntityDataAccessor<Byte> DATA_ACTION =
            SynchedEntityData.defineId(LumenSeishinEntity.class, EntityDataSerializers.BYTE);
    /** 每次换招式自增，客户端据此重启动画。 */
    private static final EntityDataAccessor<Integer> DATA_ANIM_SEQ =
            SynchedEntityData.defineId(LumenSeishinEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.mistletoe.lumen_seishin"),
            BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarOverlay.PROGRESS);
    private int lockTicks;
    /** 随从的物品栏：36 格（9×4），用原版箱子界面打开。 */
    private final net.minecraft.world.SimpleContainer inventory = new net.minecraft.world.SimpleContainer(36);
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    /** 客户端记录上次播放的动画序号，避免每帧重启动画。 */
    /** 击败它的玩家（用于死亡时生成随从）。 */
    private Player subduedBy;
    private int lastAnimSeq = -1;
    /** 招式动画剩余 tick，到点自动复位成 NONE。 */
    private int actionTicksLeft;
    /** 客户端记录上次播放的片段名。 */
    private String lastAnimName = "";

    private int frostHitsLeft;
    private int frostHitIndex;
    private long nextFrostTick;
    /** A 连段：段数、当前段、下一刀时间、锁定的目标。 */
    private int meleeHitsLeft;
    private int meleeHitIndex;
    private int meleeCooldownTicks;
    private LivingEntity meleeTarget;
    private long frostCooldownUntil;
    private long slashWindupUntil;
    private long slashCooldownUntil;
    private long healCooldownUntil;
    private float healedTotal;
    private int finalChargeLeft;
    private double chargeAnchorX;
    private double chargeAnchorY;
    private double chargeAnchorZ;
    private float chargeYaw;
    private boolean finalChargeDone;

    public LumenSeishinEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.50D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                // 普攻间隔约 10 tick；伤害由 doHurtTarget 自己按阶段结算。
                .add(Attributes.ATTACK_SPEED, 2.0D)
                // SlashBlade 的 AttackHelper 会无条件读这个属性；缺了会在出刀光时抛异常。
                .add(Attributes.SWEEPING_DAMAGE_RATIO, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, LumenPhase.DAWN.ordinal());
        builder.define(DATA_ACTION, (byte) LumenAction.NONE.ordinal());
        builder.define(DATA_ANIM_SEQ, 0);
    }

    public LumenAction getAction() {
        return LumenAction.byOrdinal(this.entityData.get(DATA_ACTION));
    }

    public int getAnimSeq() {
        return this.entityData.get(DATA_ANIM_SEQ);
    }

    /** 服务端：切换招式动画，序号自增让客户端重放。 */
    private void playAction(LumenAction action) {
        if (this.level().isClientSide() || action == null) {
            return;
        }
        this.entityData.set(DATA_ACTION, (byte) action.ordinal());
        this.entityData.set(DATA_ANIM_SEQ, this.entityData.get(DATA_ANIM_SEQ) + 1);
        this.actionTicksLeft = actionTicks(action);
    }

    /**
     * 招式动画的生命周期（tick）。播完必须把 DATA_ACTION 归零，
     * 否则客户端会一直挑中一次性片段，播完停在最后一帧，腿不再动 → 看起来就是平移。
     */
    private static int actionTicks(LumenAction action) {
        return switch (action) {
            case MELEE -> 14;
            case FROST -> 24;
            case SLASH_ART -> 30;
            case PHASE -> 22;
            case CHARGE -> 130;
            case NONE -> 0;
        };
    }

    /** 招式计时到点后复位成 NONE，交还给 idle / walk / run。 */
    private void tickActionLifetime() {
        if (this.actionTicksLeft <= 0) {
            return;
        }
        if (--this.actionTicksLeft <= 0) {
            this.entityData.set(DATA_ACTION, (byte) LumenAction.NONE.ordinal());
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 终局蓄力时用最高优先级占位，压住移动与普攻。
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.Goal() {
            @Override
            public boolean canUse() {
                return isFinalCharging();
            }

            @Override
            public boolean canContinueToUse() {
                return isFinalCharging();
            }

            @Override
            public void start() {
                getNavigation().stop();
            }
        });
        // 坐下指令：优先级高于近战，坐下时不再移动
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        // 驯服后：跟随主人、替主人作战
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0D, 10.0F, 3.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new OwnerHurtTargetGoal(this));
        // 野生时主动攻击玩家；臣服后不再攻击玩家
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<Player>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return !isTame() && super.canUse();
            }
        });
        // 驯服后：主动搜寻并攻击敌对生物（Enemy），但不碰主人与同主人的同伴。
        // Enemy 是敌对生物的标记接口（僵尸/骷髅/苦力怕等都实现它），
        // 因此不会误打牛、羊这类被动生物。
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<net.minecraft.world.entity.Mob>(
                this, net.minecraft.world.entity.Mob.class, 10, true, false,
                target -> target instanceof net.minecraft.world.entity.monster.Enemy
                        && !this.isAlliedTo(target)) {
            @Override
            public boolean canUse() {
                return isTame() && super.canUse();
            }
        });
    }

    // ── 阶段 ────────────────────────────────────────────────────────────

    public LumenPhase getPhase() {
        // 臣服后固定用暮色一套（招式、伤害、刀外观）
        if (this.isTame()) {
            return LumenPhase.MISTLETOE;
        }
        return LumenPhase.byOrdinal(this.entityData.get(DATA_PHASE));
    }

    /** 纯数据切换：只改阶段、血条与刀，不播特效也不放 SA（测试与初始化用）。 */
    public void setPhase(LumenPhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
        this.bossEvent.setColor(phase.barColor());
        this.bossEvent.setName(getDisplayName());
        // 不再用无敌等待换刀：阶段锁血（伤害截断）已经保证不会跳阶段。
        this.lockTicks = 0;
        this.frostHitsLeft = 0;
        this.slashWindupUntil = 0L;
        this.meleeHitsLeft = 0;
        refreshBlade(phase);
    }

    /** 正式转阶段：切数据 + 特效 + 立刻用新刀放一次该阶段的 SA。 */
    private void enterPhase(LumenPhase phase) {
        setPhase(phase);
        playPhaseChangeEffect(phase);
        releaseTransitionSa(phase);
    }

    /**
     * 转阶段即出招：初雪换刀后立刻甩一次霜刃，暮色换刀后立刻放一次斩星。
     * 朝露是初始阶段，没有转阶段。
     */
    private void releaseTransitionSa(LumenPhase phase) {
        if (this.level().isClientSide() || phase == LumenPhase.DAWN) {
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            target = this.level().getNearestPlayer(this, 24.0D);
        }
        if (phase == LumenPhase.FIRST_SNOW) {
            playAction(LumenAction.FROST);
            frostStrike();
            return;
        }
        if (target != null && target.isAlive() && distanceToSqr(target) <= 16.0D * 16.0D) {
            playAction(LumenAction.SLASH_ART);
            slashStrike(target);
        }
    }

    /** 阶段切换特效：换刀刀光 + 粒子爆发 + 音效。 */
    private void playPhaseChangeEffect(LumenPhase phase) {
        if (this.level().isClientSide()) {
            return;
        }
        playAction(LumenAction.PHASE);
        spawnCosmeticSlash(-45.0F, 3.0F, phaseColor(phase));
        spawnCosmeticSlash(45.0F, 3.0F, phaseColor(phase));
        playSound(SoundEvents.BEACON_ACTIVATE, 1.6F, 0.7F);
        if (this.level() instanceof net.minecraft.server.level.ServerLevel level) {
            MistletoeDamage.burst(level, getX(), getY(0.8D), getZ(), 60, 0.8D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    getX(), getY(1.0D), getZ(), 30, 0.6D, 1.2D, 0.6D, 0.05D);
        }
        TerminalBlade.LOGGER.info("[流明·星] 换阶段 -> {}", phase);
    }

    private static int phaseColor(LumenPhase phase) {
        return switch (phase) {
            case DAWN -> 0x9FD8FF;
            case FIRST_SNOW -> 0xEAF6FF;
            case MISTLETOE -> 0xC9A0FF;
        };
    }

    /**
     * 纯视觉刀光：{@code comboRatio = 0} 让 {@code EntitySlashEffect} 不结算伤害，
     * 真正伤害仍由本实体的 {@code doHurtTarget} / 技能直接结算。
     */
    private void spawnCosmeticSlash(float roll, float size, int color) {
        if (this.level().isClientSide()) {
            return;
        }
        var slash = mods.flammpfeil.slashblade.util.AttackManager.doSlash(this, roll, true, false, 0.0D);
        if (slash != null) {
            slash.setColor(color);
            slash.setBaseSize(size);
            slash.setLifetime(8);
            slash.setDamage(0.0D);
        }
    }

    public boolean isPhaseLocked() {
        return this.lockTicks > 0;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return isPhaseLocked() || super.isInvulnerableTo(source);
    }

    /** 阶段锁血：只允许把生命削到下一阶段阈值，不允许一刀跳过阶段。 */
    public void clampPhaseDamage(LivingIncomingDamageEvent event) {
        if (this.level().isClientSide() || isPhaseLocked()) {
            return;
        }
        // 终局蓄力：90% 减伤。蓄力不是无敌，玩家仍可在 6 秒内把它打死（结局 A）。
        if (isFinalCharging()) {
            event.setAmount(event.getAmount() * FINAL_CHARGE_DAMAGE_MULTIPLIER);
            return;
        }
        float threshold = getPhase().nextThreshold();
        if (threshold <= 0.0F) {
            return;
        }
        float health = getHealth();
        if (health > threshold && health - event.getAmount() < threshold) {
            event.setAmount(health - threshold);
        }
    }

    // ── 主循环 ──────────────────────────────────────────────────────────

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.lockTicks > 0) {
            this.lockTicks--;
        }
        this.bossEvent.setProgress(getHealth() / getMaxHealth());
        if (!isAlive()) {
            return;
        }
        // 随从形态缓慢回血
        if (this.isTame() && getHealth() < getMaxHealth() && this.tickCount % TAME_REGEN_INTERVAL == 0) {
            this.heal(1.0F);
        }
        if (!this.isTame()) {
            LumenPhase expected = LumenPhase.forHealth(getHealth());
            if (expected.ordinal() > getPhase().ordinal()) {
                enterPhase(expected);
            }
        }
        if (isFinalCharging()) {
            tickFinalCharge();
            return;
        }
        if (!this.isTame() && getPhase() == LumenPhase.MISTLETOE && !this.finalChargeDone
                && getHealth() <= FINAL_CHARGE_THRESHOLD) {
            startFinalCharge();
            return;
        }
        tickSkills();
    }

    // ── 终局蓄力 ────────────────────────────────────────────────────────

    public boolean isFinalCharging() {
        return this.finalChargeLeft > 0;
    }

    private void startFinalCharge() {
        this.finalChargeDone = true;
        this.finalChargeLeft = FINAL_CHARGE_TICKS;
        this.frostHitsLeft = 0;
        this.slashWindupUntil = 0L;
        this.meleeHitsLeft = 0;
        this.chargeAnchorX = getX();
        this.chargeAnchorY = getY();
        this.chargeAnchorZ = getZ();
        this.chargeYaw = getYRot();
        playAction(LumenAction.CHARGE);
        getNavigation().stop();
        setTarget(null);
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        this.bossEvent.setName(Component.translatable("entity.mistletoe.lumen_seishin.charging"));
        // 类似凋灵：蓄力期间把天空压暗，给玩家一个“现在必须跑”的强提示。
        this.bossEvent.setDarkenScreen(true);
        playSound(SoundEvents.WITHER_SPAWN, 1.0F, 0.6F);
        TerminalBlade.LOGGER.info("[流明·星] 终局蓄力开始");
    }

    private void tickFinalCharge() {
        // 原地站桩：每 tick 清位移并钉回蓄力起点，防止被 AI/物理/击退带走。
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        this.setPos(this.chargeAnchorX, this.chargeAnchorY, this.chargeAnchorZ);
        this.setYRot(this.chargeYaw);
        this.setYHeadRot(this.chargeYaw);
        this.getNavigation().stop();
        this.setSprinting(false);
        this.finalChargeLeft--;
        // 常规每 4 tick 画一圈；最后 2 秒加密到每 tick，强化“马上要炸”的压迫感。
        if (this.finalChargeLeft % 4 == 0 || this.finalChargeLeft <= FINAL_WARN_2) {
            spawnChargeRing();
        }
        if (this.finalChargeLeft == FINAL_WARN_1 || this.finalChargeLeft == FINAL_WARN_2) {
            playSound(SoundEvents.BEACON_ACTIVATE, 1.2F, 0.5F);
        }
        if (this.finalChargeLeft <= 0) {
            resolveFinalCharge();
        }
    }

    /**
     * 12 格危险区边界。之前只有 36 个 SOUL_FIRE_FLAME 点（间隔 2.1 格），太稀太暗看不出是圈，
     * 现在改成 72 点 END_ROD（亮白、无重力）+ 地面一圈魂火 + 内部薄雾，边界一眼可读。
     */
    private void spawnChargeRing() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        int points = 72;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            double x = getX() + Math.cos(angle) * FINAL_CHARGE_RADIUS;
            double z = getZ() + Math.sin(angle) * FINAL_CHARGE_RADIUS;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    x, getY() + 0.15D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    x, getY() + 0.05D, z, 1, 0.0D, 0.02D, 0.0D, 0.0D);
        }
        // 内部薄雾：提示“整个区域都危险”，而不是只有一圈线
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                getX(), getY() + 0.25D, getZ(), 40, 3.0D, 0.05D, 3.0D, 0.0D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                getX(), getY() + 1.0D, getZ(), 10, 0.4D, 1.2D, 0.4D, 0.02D);
    }

    /**
     * 结局 A：蓄力期间被玩家击杀 → {@link #hurt} 已拦下，本方法不会执行（已臣服）。
     * 结局 B：范围内没有玩家 → 没人受伤。
     * 结局 C：范围内玩家吃处决。
     *
     * <p>蓄力放完就献祭死亡并掉落暮色；只有被玩家直接打死的才臣服。</p>
     */
    public void resolveFinalCharge() {
        if (this.level().isClientSide() || this.isTame()) {
            this.finalChargeLeft = 0;
            return;
        }
        this.finalChargeLeft = 0;
        Vec3 center = position().add(0.0D, getBbHeight() * 0.5D, 0.0D);
        double radiusSq = FINAL_CHARGE_RADIUS * FINAL_CHARGE_RADIUS;
        var box = new net.minecraft.world.phys.AABB(center, center).inflate(FINAL_CHARGE_RADIUS);
        for (Player player : this.level().getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            if (center.distanceToSqr(player.position()) <= radiusSq) {
                player.hurt(MistletoeDamage.source(this, MistletoeDamage.LUMEN_TRUE),
                        Math.max(FINAL_EXECUTE_DAMAGE, player.getMaxHealth() * 20.0F));
            }
        }
        playSound(SoundEvents.WITHER_DEATH, 1.5F, 0.5F);
        this.finalChargeDone = true;
        // 没被玩家打断：献祭死亡，走正常 hurt 以尊重特殊免疫与锁血标签。
        this.hurt(MistletoeDamage.source(this, MistletoeDamage.SACRIFICE),
                getHealth() + getMaxHealth());
        TerminalBlade.LOGGER.info("[流明·星] 终局释放");
    }

    private void tickSkills() {
        long now = this.level().getGameTime();
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            this.frostHitsLeft = 0;
            this.slashWindupUntil = 0L;
            return;
        }

        if (this.frostHitsLeft > 0) {
            if (now >= this.nextFrostTick) {
                frostStrike();
                this.frostHitsLeft--;
                this.nextFrostTick = now + 1L;
            }
            return;
        }
        if (this.slashWindupUntil > 0L) {
            if (now >= this.slashWindupUntil) {
                this.slashWindupUntil = 0L;
                slashStrike(target);
            }
            return;
        }
        if (getPhase() == LumenPhase.DAWN
                && now >= this.healCooldownUntil
                && this.healedTotal < HEAL_CAP
                && getHealth() < getMaxHealth() * 0.75F) {
            heal();
            return;
        }
        if (getPhase() == LumenPhase.FIRST_SNOW
                && now >= this.frostCooldownUntil
                && distanceToSqr(target) <= FROST_RANGE * FROST_RANGE) {
            this.frostHitsLeft = FROST_HITS;
            this.frostHitIndex = 0;
            this.nextFrostTick = now;
            playAction(LumenAction.FROST);
            this.frostCooldownUntil = now + FROST_COOLDOWN;
            playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 1.6F);
            return;
        }
        if (getPhase() == LumenPhase.MISTLETOE
                && now >= this.slashCooldownUntil
                && distanceToSqr(target) <= 16.0D * 16.0D) {
            this.slashWindupUntil = now + SLASH_WINDUP;
            this.slashCooldownUntil = now + SLASH_COOLDOWN;
            playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
        }
    }

    // ── 招式 ────────────────────────────────────────────────────────────

    @Override
    public boolean doHurtTarget(Entity target) {
        // 蓄力期间禁止任何普攻：Goal 优先级可能仍有窗口，这里直接兜底。
        if (this.level().isClientSide() || isFinalCharging()
                || !(target instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        // A 连段：启动连段，第一刀立刻出；后续由 tickMeleeChain 按 meleeInterval() 推进。
        this.meleeTarget = living;
        this.meleeHitIndex = 0;
        this.meleeHitsLeft = meleeChainLength();
        this.meleeCooldownTicks = 0;
        meleeHit();
        return true;
    }

    /** 朝露三连、初雪四连、暮色两连（文档的 A 连段）。 */
    private int meleeChainLength() {
        return switch (getPhase()) {
            case DAWN -> 3;
            case FIRST_SNOW -> 4;
            case MISTLETOE -> 2;
        };
    }

    private long meleeInterval() {
        return switch (getPhase()) {
            case DAWN -> 9L;
            case FIRST_SNOW -> 5L;
            case MISTLETOE -> 10L;
        };
    }

    /** 连段的单刀：目标丢失、超出 3 格或死亡就中断，不积压。 */
    /**
     * 连段的单刀。目标是发起时的锁定目标：只要还活着就继续打，
     * 不再用距离判断中断——Boss 在几 tick 里会自然位移，3 格阈值会把连段误杀。
     */
    private void meleeHit() {
        LivingEntity target = this.meleeTarget;
        if (target == null || !target.isAlive()) {
            this.meleeHitsLeft = 0;
            return;
        }
        long now = this.level().getGameTime();
        float amount = meleeDamage(target);
        playAction(LumenAction.MELEE);
        spawnCosmeticSlash(-25.0F + getRandom().nextFloat() * 50.0F, 1.8F, phaseColor(getPhase()));
        target.hurt(MistletoeDamage.source(this, meleeDamageType()), amount);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.0F);
        this.meleeHitIndex++;
        this.meleeHitsLeft--;
        this.meleeCooldownTicks = (int) meleeInterval();
    }

    private float meleeDamage(LivingEntity target) {

        return switch (getPhase()) {
            case DAWN -> MELEE_DAWN;
            case FIRST_SNOW -> MELEE_FIRST_SNOW;
            case MISTLETOE -> Math.max(1.0F, target.getMaxHealth() * MELEE_MISTLETOE_RATIO);
        };
    }

    private ResourceKey<DamageType> meleeDamageType() {
        return getPhase() == LumenPhase.MISTLETOE ? MistletoeDamage.LUMEN_TRUE : MistletoeDamage.LUMEN_BLADE;
    }

    public void frostStrike() {
        spawnCosmeticSlash(-35.0F + this.frostHitIndex * 12.0F, 2.0F, 0xA9D8FF);
        this.frostHitIndex++;
        for (LivingEntity victim : fanTargets(FROST_RANGE, FROST_CONE_DEGREES)) {
            victim.hurt(MistletoeDamage.source(this, MistletoeDamage.LUMEN_BLADE), FROST_DAMAGE);
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.4F);
    }

    public void slashStrike(LivingEntity target) {
        playAction(LumenAction.SLASH_ART);
        spawnCosmeticSlash(-30.0F, 2.6F, 0xC9A0FF);
        float amount = Math.max(1.0F, target.getMaxHealth() * SLASH_RATIO);
        target.hurt(MistletoeDamage.source(this, MistletoeDamage.LUMEN_TRUE), amount);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
    }

    private void heal() {
        heal(HEAL_AMOUNT);
        this.healedTotal += HEAL_AMOUNT;
        this.healCooldownUntil = this.level().getGameTime() + HEAL_COOLDOWN;
        playSound(SoundEvents.PLAYER_LEVELUP, 0.6F, 1.4F);
    }

    /** 水平扇形内的合法活体目标（排除创造/旁观玩家）。 */
    private List<LivingEntity> fanTargets(double range, double coneDegrees) {
        Vec3 look = getLookAngle();
        double length = Math.hypot(look.x, look.z);
        Vec3 flat = length < 1.0E-4D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : new Vec3(look.x / length, 0.0D, look.z / length);
        double cosHalf = Math.cos(Math.toRadians(Math.max(1.0D, coneDegrees) * 0.5D));
        return this.level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range), entity -> {
            if (entity == this || !entity.isAlive()) {
                return false;
            }
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return false;
            }
            Vec3 to = new Vec3(entity.getX() - getX(), 0.0D, entity.getZ() - getZ());
            double distance = to.length();
            return distance < 1.0E-4D || flat.dot(to.scale(1.0D / distance)) >= cosHalf;
        });
    }

    // ── 血条 / 拿刀 / 存读档 ────────────────────────────────────────────

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        // 已臣服的随从不再显示 Boss 血条
        if (!this.isTame()) {
            this.bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            if (this.subduedBy != null) {
                // 被玩家击败：原地转成随从，不掉落
                this.spawnCompanion(this.subduedBy);
            } else {
                // 其它死法（终局蓄力自爆、摔死等）：掉落槲寄生「暮色」。
                // named blade 的物品栈没法用纯数据战利品表表达，这里直接生成并丢出。
                ItemStack blade = bladeFor(LumenPhase.MISTLETOE);
                if (!blade.isEmpty()) {
                    this.spawnAtLocation(blade);
                }
            }
        }
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("LumenInventory", this.inventory.createTag(this.registryAccess()));
        tag.putInt("LumenPhase", getPhase().ordinal());
        tag.putInt("LumenLock", this.lockTicks);
        tag.putFloat("LumenHealed", this.healedTotal);
        tag.putLong("LumenFrostCooldown", this.frostCooldownUntil);
        tag.putLong("LumenSlashCooldown", this.slashCooldownUntil);
        tag.putLong("LumenHealCooldown", this.healCooldownUntil);
        tag.putInt("LumenFinalCharge", this.finalChargeLeft);
        tag.putBoolean("LumenFinalChargeDone", this.finalChargeDone);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("LumenInventory")) {
            this.inventory.fromTag(tag.getList("LumenInventory", 10), this.registryAccess());
        }
        this.entityData.set(DATA_PHASE, LumenPhase.byOrdinal(tag.getInt("LumenPhase")).ordinal());
        this.lockTicks = tag.getInt("LumenLock");
        this.healedTotal = tag.getFloat("LumenHealed");
        this.frostCooldownUntil = tag.getLong("LumenFrostCooldown");
        this.slashCooldownUntil = tag.getLong("LumenSlashCooldown");
        this.healCooldownUntil = tag.getLong("LumenHealCooldown");
        this.finalChargeLeft = tag.getInt("LumenFinalCharge");
        this.finalChargeDone = tag.getBoolean("LumenFinalChargeDone");
        this.bossEvent.setColor(getPhase().barColor());
        this.bossEvent.setName(getDisplayName());
    }

    /**
     * 手里那把刀的阶段。随从形态固定持「暮色」，但招式仍按初雪结算，
     * 所以刀的外观与招式是分开的。
     */
    private LumenPhase bladePhase() {
        return this.isTame() ? LumenPhase.MISTLETOE : getPhase();
    }

    private void refreshBlade(LumenPhase phase) {
        if (this.level().isClientSide()) {
            return;
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, bladeFor(bladePhase()));
    }

    private ItemStack bladeFor(LumenPhase phase) {
        ResourceKey<SlashBladeDefinition> key = ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, phase.bladeId());
        return this.level().registryAccess()
                .lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .get(key)
                .map(holder -> holder.value().getBlade(this.level().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.tickCount == 1) {
                refreshBlade(getPhase());
            }
            tickActionLifetime();
            tickMeleeChain();
        }
    }

    /** A 连段推进：放在 tick 里用简单计数器，不依赖目标/技能状态。 */
    private void tickMeleeChain() {
        if (this.meleeHitsLeft <= 0) {
            return;
        }
        this.meleeCooldownTicks--;
        if (this.meleeCooldownTicks <= 0) {
            meleeHit();
        }
    }
    // ── 骑乘 / 坐下 ─────────────────────────────────────────────────────

    /** 右键骑乘；潜行 + 右键切换坐下。只有主人能操作。 */
    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player,
            net.minecraft.world.InteractionHand hand) {
        if (!this.isTame() || !this.isOwnedBy(player)) {
            return super.mobInteract(player, hand);
        }
        if (player.isShiftKeyDown()) {
            if (this.isOrderedToSit()) {
                this.setOrderedToSit(false);
                this.setInSittingPose(false);
            } else if (!this.isVehicle()) {
                this.setOrderedToSit(true);
                this.setInSittingPose(true);
                this.getNavigation().stop();
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        // 手上有东西：打开物品栏（可以直接往里放东西）
        if (!player.getItemInHand(hand).isEmpty()) {
            if (!this.level().isClientSide()) {
                this.openInventory(player);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        if (this.isOrderedToSit()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            player.startRiding(this);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    /** 用原版 9×4 箱子界面打开随从的物品栏。 */
    private void openInventory(Player player) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, playerInv, p) -> new net.minecraft.world.inventory.ChestMenu(
                        net.minecraft.world.inventory.MenuType.GENERIC_9x4, id, playerInv, this.inventory, 4),
                this.getDisplayName()));
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player && this.isOwnedBy(player) ? player : null;
    }

    /** 骑手坐的高度（模型 1.8 格，坐在肩背上）。 */
    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return new Vec3(this.getX(), this.getY() + 1.25D, this.getZ());
    }

    /** 有玩家骑乘时按玩家输入驱动，做法参照原版猪。 */
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isAlive() && this.getControllingPassenger() instanceof Player player) {
            this.setYRot(player.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(player.getXRot() * 0.5F);
            this.setRot(this.getYRot(), this.getXRot());
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.yBodyRot;
            float strafe = player.xxa * 0.5F;
            float forward = player.zza;
            if (forward <= 0.0F) {
                forward *= 0.25F;
            }
            this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
            super.travel(new Vec3(strafe, 0.0D, forward));
            return;
        }
        super.travel(travelVector);
    }
    // ── 臣服 / 友方 ─────────────────────────────────────────────────────

    /** 被玩家打出致死一击时不死，而是当场臣服，成为该玩家的随从。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 被玩家打出致死一击：只记录是谁终结的，本体照常死亡，
        // 由 die() 在死亡时生成随从形态。
        if (!this.level().isClientSide() && this.subduedBy == null
                && source.getEntity() instanceof Player player
                && this.getHealth() - amount <= 0.0F) {
            this.subduedBy = player;
        }
        return super.hurt(source, amount);
    }

    /** 玩家击败时，在死亡位置生成一只属于该玩家的随从。 */
    private void spawnCompanion(Player owner) {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        LumenCompanionEntity companion = dev.xingguang.terminalblade.registry.ModEntities.LUMEN_COMPANION
                .get().create(server);
        if (companion == null) {
            return;
        }
        companion.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
        companion.setTame(true, true);
        companion.setOwnerUUID(owner.getUUID());
        companion.setCustomName(this.getCustomName());
        server.addFreshEntity(companion);
        TerminalBlade.LOGGER.info("[流明·星] 被 {} 击败，生成随从", owner.getName().getString());
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        if (super.isAlliedTo(entity)) {
            return true;
        }
        if (this.isTame() && entity instanceof LumenSeishinEntity other) {
            return java.util.Objects.equals(this.getOwnerUUID(), other.getOwnerUUID());
        }
        return false;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isTame() && target instanceof Player player
                && player.getUUID().equals(this.getOwnerUUID())) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public Component getName() {
        return Component.translatable("entity.mistletoe.lumen_seishin");
    }

    // ── GeckoLib 动画 ───────────────────────────────────────────────────

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }

    @Override
    public double getTick(Object animatable) {
        return this.tickCount;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::mainAnim));
    }

    /**
     * 每 tick 重新挑选动画片段：招式优先，否则按实际位移在 idle / walk / run 之间切换。
     * 只有片段真正变化时才 setAnimation，避免每帧重启。
     */
    private PlayState mainAnim(AnimationState<LumenSeishinEntity> state) {
        String desired = this.desiredAnimation();
        if (!desired.equals(this.lastAnimName)) {
            this.lastAnimName = desired;
            this.lastAnimSeq = this.getAnimSeq();
            state.getController().setAnimation(RawAnimation.begin().thenPlay(desired));
        }
        return PlayState.CONTINUE;
    }

    private String desiredAnimation() {
        // 坐下优先：坐姿不参与移动与招式
        if (this.isInSittingPose()) {
            return "sit";
        }
        LumenAction action = getAction();
        if (action != LumenAction.NONE) {
            return this.actionAnimation(action);
        }
        // 用实际位置增量判断移动速度：0.005 ≈ 0.07 格/tick，0.15 ≈ 0.39 格/tick
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        double speedSq = dx * dx + dz * dz;
        if (speedSq > 0.15D) {
            return "run";
        }
        if (speedSq > 0.005D) {
            return "walk";
        }
        return "idle";
    }

    /** 招式 → YSM 动画名（资源里是 Bedrock 的 slashblade:* 动作）。 */
    private String actionAnimation(LumenAction action) {
        return switch (action) {
            case MELEE -> switch (Math.min(this.meleeHitIndex, 4)) {
                case 1 -> "slashblade:combo_a1";
                case 2 -> "slashblade:combo_a2";
                case 3 -> "slashblade:combo_a3";
                default -> "slashblade:combo_a4";
            };
            case FROST -> "slashblade:combo_a5ex";
            case SLASH_ART -> "slashblade:combo_c";
            case PHASE -> "slashblade:combo_a1";
            // 终局蓄力原地站桩 6 秒：用从 sneak 冻结出来的静态蹲姿。
            // 直接播 slashblade:sneak 会变成「蹲着原地踏步」，因为那是潜行移动循环。
            case CHARGE -> "charge";
            case NONE -> "idle";
        };
    }
}
