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
public class LumenSeishinEntity extends Monster implements GeoAnimatable {
    public static final float MAX_HEALTH = 360.0F;

    /** 朝露普通三连：每刀 5。 */
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
    private static final double SLASH_HIT_RANGE = 8.0D;
    private static final double SLASH_CONE_DEGREES = 120.0D;
    private static final double MELEE_HIT_RANGE = 4.25D;

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
    private static final EntityDataAccessor<Integer> DATA_COMBO_INDEX =
            SynchedEntityData.defineId(LumenSeishinEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.mistletoe.lumen_seishin"),
            BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarOverlay.PROGRESS);
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    /** 客户端记录上次播放的动画序号，避免每帧重启动画。 */
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
    /** 霜刃起手时锁定的目标列表：目标被击退也不会丢刀。 */
    private java.util.List<LivingEntity> frostTargets = java.util.List.of();
    private long frostCooldownUntil;
    private long slashWindupUntil;
    /** Direction chosen on entering slash windup: dodging sideways should work. */
    private float slashLockedYaw;
    private long slashCooldownUntil;
    private long healCooldownUntil;
    private float healedTotal;
    private int finalChargeLeft;
    private double chargeAnchorX;
    private double chargeAnchorY;
    private double chargeAnchorZ;
    private float chargeYaw;
    private boolean finalChargeDone;

    public LumenSeishinEntity(EntityType<? extends Monster> type, Level level) {
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
        builder.define(DATA_COMBO_INDEX, 0);
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
        this.goalSelector.addGoal(1, new LumenFootworkGoal(this, () -> isFinalCharging()
                || meleeHitsLeft > 0 || frostHitsLeft > 0 || slashWindupUntil > 0L));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ── 阶段 ────────────────────────────────────────────────────────────

    public LumenPhase getPhase() {
        return LumenPhase.byOrdinal(this.entityData.get(DATA_PHASE));
    }

    /** 纯数据切换：只改阶段、血条与刀，不播特效也不放 SA（测试与初始化用）。 */
    public void setPhase(LumenPhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
        this.bossEvent.setColor(phase.barColor());
        this.bossEvent.setName(getDisplayName());
        // 不再用无敌等待换刀：阶段锁血（伤害截断）已经保证不会跳阶段。
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
        // Switching does NOT add a separate animation or wait. The new blade's SA begins this tick.
        if (phase == LumenPhase.FIRST_SNOW) {
            startFrostCombo(); // 1 immediate hit + 6 follow-up hits; not just frostStrike() once
        } else {
            if (target != null && target.isAlive()) {
                getLookControl().setLookAt(target, 180.0F, 180.0F);
                faceTarget(target);
            }
            slashStrike(target); // instant transition SA, with actual geometry/LOS hit validation
            this.slashCooldownUntil = this.level().getGameTime() + SLASH_COOLDOWN;
        }
    }

    private void startFrostCombo() {
        if (this.frostHitsLeft > 0 || isFinalCharging()) {
            return;
        }
        this.meleeHitsLeft = 0;
        this.frostHitIndex = 0;
        this.frostHitsLeft = FROST_HITS;
        this.nextFrostTick = this.level().getGameTime();
        this.frostCooldownUntil = this.level().getGameTime() + FROST_COOLDOWN;
        // 起手即锁定目标：后续六刀打同一批目标，不因被击退出扇形而丢刀
        this.frostTargets = new java.util.ArrayList<>(fanTargets(FROST_RANGE, FROST_CONE_DEGREES));
        playAction(LumenAction.FROST);
        playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 1.6F);
        tickFrostCombo(this.level().getGameTime()); // first slash immediately
    }

    private void tickFrostCombo(long now) {
        if (this.frostHitsLeft <= 0 || now < this.nextFrostTick) {
            return;
        }
        frostStrike();
        this.frostHitsLeft--;
        this.nextFrostTick = now + 3L; // 7 strikes over 19 ticks, matched to accelerated frost animation
        if (this.frostHitsLeft <= 0) {
            this.frostTargets = java.util.List.of();
        }
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        setYRot(yaw);
        setYHeadRot(yaw);
        this.yBodyRot = yaw;
    }

    /** 阶段切换特效：换刀刀光 + 粒子爆发 + 音效。 */
    private void playPhaseChangeEffect(LumenPhase phase) {
        if (this.level().isClientSide()) {
            return;
        }
        // No extra PHASE animation: transition SA is the only combat animation.
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

    /** Final net damage, *after* armor/effects; keeps exact 240/120 threshold. */
    public float adjustFinalDamage(float damage) {
        if (damage <= 0.0F) {
            return damage;
        }
        // Deliberate mechanic: 90% reduction in the last 6-second charge.
        if (isFinalCharging()) {
            return damage * FINAL_CHARGE_DAMAGE_MULTIPLIER;
        }
        float threshold = getPhase().nextThreshold();
        if (threshold <= 0.0F) {
            return damage;
        }
        return Math.min(damage, Math.max(0.0F, getHealth() - threshold));
    }

    // ── 主循环 ──────────────────────────────────────────────────────────

    /** Immediately switch on successful damage to 240/120 HP; tick path remains fallback for setHealth / commands. */
    public void onDamageResolved() {
        if (this.level().isClientSide() || !isAlive()) {
            return;
        }
        LumenPhase expected = LumenPhase.forHealth(getHealth());
        if (expected.ordinal() > getPhase().ordinal()) {
            enterPhase(expected);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            return;
        }
        this.bossEvent.setProgress(getHealth() / getMaxHealth());
        if (!isAlive()) {
            return;
        }
        onDamageResolved();
        if (isFinalCharging()) {
            tickFinalCharge();
            return;
        }
        if (getPhase() == LumenPhase.MISTLETOE && !this.finalChargeDone
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
        if (this.level().isClientSide()) {
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
        if (this.frostHitsLeft > 0) {
            tickFrostCombo(now);
            return;
        }
        if (this.slashWindupUntil > 0L) {
            if (now >= this.slashWindupUntil) {
                this.slashWindupUntil = 0L;
                releaseWoundUpSlash(getTarget());
            }
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
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
            startFrostCombo();
            return;
        }
        if (getPhase() == LumenPhase.MISTLETOE
                && now >= this.slashCooldownUntil
                && distanceToSqr(target) <= 16.0D * 16.0D) {
            this.meleeHitsLeft = 0;
            faceTarget(target);
            this.slashLockedYaw = getYRot();
            this.slashWindupUntil = now + SLASH_WINDUP;
            this.slashCooldownUntil = now + SLASH_COOLDOWN;
            playAction(LumenAction.SLASH_ART); // show the windup immediately
            playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
        }
    }

    // ── 招式 ────────────────────────────────────────────────────────────

    @Override
    public boolean doHurtTarget(Entity target) {
        // 蓄力期间禁止任何普攻：Goal 优先级可能仍有窗口，这里直接兜底。
        if (this.level().isClientSide() || isFinalCharging() || this.meleeHitsLeft > 0
                || this.frostHitsLeft > 0 || this.slashWindupUntil > 0L
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

    /** Each hit independently checks range, facing and line-of-sight; escapes cancel the chain. */
    private void meleeHit() {
        LivingEntity target = this.meleeTarget;
        if (target == null || !target.isAlive()) {
            this.meleeHitsLeft = 0;
            return;
        }
        float amount = meleeDamage(target);
        this.entityData.set(DATA_COMBO_INDEX, this.meleeHitIndex + 1);
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
        // 直接调用（测试/兼容路径）时按当前朝向惰性取一次快照
        if (this.frostTargets.isEmpty()) {
            this.frostTargets = new java.util.ArrayList<>(fanTargets(FROST_RANGE, FROST_CONE_DEGREES));
        }
        for (LivingEntity victim : this.frostTargets) {
            if (victim != null && victim.isAlive()) {
                victim.hurt(MistletoeDamage.source(this, MistletoeDamage.LUMEN_BLADE), FROST_DAMAGE);
            }
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.4F);
    }

    /** Public entry point also used by transition, including immediate transition cast. */
    public void slashStrike(LivingEntity target) {
        if (target != null && target.isAlive()) {
            faceTarget(target);
        }
        playAction(LumenAction.SLASH_ART);
        strikeWithYaw(target, getYRot());
    }

    /** Ordinary slash: rotation locked at windup start, allowing real dodges. */
    private void releaseWoundUpSlash(LivingEntity target) {
        strikeWithYaw(target, this.slashLockedYaw);
    }

    private void strikeWithYaw(LivingEntity target, float lockedYaw) {
        spawnCosmeticSlash(-30.0F, 2.6F, 0xC9A0FF);
        if (target != null && target.isAlive()) {
            float amount = Math.max(1.0F, target.getMaxHealth() * SLASH_RATIO);
            target.hurt(MistletoeDamage.source(this, MistletoeDamage.LUMEN_TRUE), amount);
        }
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
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            if (source.getEntity() instanceof Player killer) {
                // Only the actual lethal damage source decides the reward; overkill at 240/120 HP must not pre-credit a player.
                this.spawnCompanion(killer);
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

    // ── 防御性状态：不被推动 / 不消失 ────────────────────────────────────

    /** 蓄力时不能被实体挤开（否则会把蓄力锚点推歪）。 */
    @Override
    public boolean isPushable() {
        return !this.isFinalCharging() && super.isPushable();
    }

    /** 蓄力时免疫击退，避免被玩家的刀推离蓄力原点。 */
    @Override
    public void knockback(double strength, double x, double z) {
        if (this.isFinalCharging()) {
            return;
        }
        super.knockback(strength, x, z);
    }

    /** 和平模式不消失（Boss 不该因为切换难度就凭空不见）。 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** 玩家走远时不卸载。 */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LumenPhase", getPhase().ordinal());
        tag.putFloat("LumenHealed", this.healedTotal);
        tag.putLong("LumenFrostCooldown", this.frostCooldownUntil);
        tag.putLong("LumenSlashCooldown", this.slashCooldownUntil);
        tag.putLong("LumenHealCooldown", this.healCooldownUntil);
        tag.putInt("LumenFinalCharge", this.finalChargeLeft);
        tag.putDouble("LumenChargeAnchorX", this.chargeAnchorX);
        tag.putDouble("LumenChargeAnchorY", this.chargeAnchorY);
        tag.putDouble("LumenChargeAnchorZ", this.chargeAnchorZ);
        tag.putFloat("LumenChargeYaw", this.chargeYaw);
        tag.putBoolean("LumenFinalChargeDone", this.finalChargeDone);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_PHASE, LumenPhase.byOrdinal(tag.getInt("LumenPhase")).ordinal());
        this.healedTotal = tag.getFloat("LumenHealed");
        this.frostCooldownUntil = tag.getLong("LumenFrostCooldown");
        this.slashCooldownUntil = tag.getLong("LumenSlashCooldown");
        this.healCooldownUntil = tag.getLong("LumenHealCooldown");
        this.finalChargeLeft = tag.getInt("LumenFinalCharge");
        this.chargeAnchorX = tag.contains("LumenChargeAnchorX") ? tag.getDouble("LumenChargeAnchorX") : getX();
        this.chargeAnchorY = tag.contains("LumenChargeAnchorY") ? tag.getDouble("LumenChargeAnchorY") : getY();
        this.chargeAnchorZ = tag.contains("LumenChargeAnchorZ") ? tag.getDouble("LumenChargeAnchorZ") : getZ();
        this.chargeYaw = tag.contains("LumenChargeYaw") ? tag.getFloat("LumenChargeYaw") : getYRot();
        if (this.finalChargeLeft > 0) {
            playAction(LumenAction.CHARGE);
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
            this.bossEvent.setName(Component.translatable("entity.mistletoe.lumen_seishin.charging"));
            this.bossEvent.setDarkenScreen(true);
        }
        this.finalChargeDone = tag.getBoolean("LumenFinalChargeDone");
        if (!isFinalCharging()) {
            this.bossEvent.setColor(getPhase().barColor());
            this.bossEvent.setName(getDisplayName());
            this.bossEvent.setDarkenScreen(false);
        }
    }

    private void refreshBlade(LumenPhase phase) {
        if (this.level().isClientSide()) {
            return;
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, bladeFor(getPhase()));
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
        if (this.meleeHitsLeft <= 0 || isFinalCharging() || this.frostHitsLeft > 0
                || this.slashWindupUntil > 0L) {
            return;
        }
        this.meleeCooldownTicks--;
        if (this.meleeCooldownTicks <= 0) {
            meleeHit();
        }
    }
    // ── 骑乘 / 坐下 ─────────────────────────────────────────────────────

    // ── 臣服 / 友方 ─────────────────────────────────────────────────────

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
        // Combat clips in the existing Bedrock JSON run much longer than the hit cadence.
        // Speed them up instead of adding transition pauses or changing damage values.
        state.getController().setAnimationSpeed(switch (getAction()) {
            case FROST -> 5.0D;
            case MELEE -> getPhase() == LumenPhase.FIRST_SNOW ? 4.0D : 3.0D;
            case SLASH_ART -> 2.0D;
            default -> 1.0D;
        });
        int sequence = this.getAnimSeq();
        if (!desired.equals(this.lastAnimName) || sequence != this.lastAnimSeq) {
            this.lastAnimName = desired;
            this.lastAnimSeq = sequence;
            state.getController().forceAnimationReset();
            if (getAction() == LumenAction.NONE || getAction() == LumenAction.CHARGE) {
                state.getController().setAnimation(RawAnimation.begin().thenLoop(desired));
            } else {
                state.getController().setAnimation(RawAnimation.begin().thenPlay(desired));
            }
        }
        return PlayState.CONTINUE;
    }

    private String desiredAnimation() {
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
            case MELEE -> switch (Math.min(this.entityData.get(DATA_COMBO_INDEX), 4)) {
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
