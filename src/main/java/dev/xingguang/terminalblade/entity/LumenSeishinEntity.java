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

import java.util.List;

/**
 * 流明·星：三阶段人形剑士 Boss。
 *
 * <p>外观拿刀，但伤害完全由本实体结算，不走玩家的 {@code attack_base} 连段公式，
 * 也不使用 SlashBlade 的输入/连段状态机 —— 这样最不容易出 bug。</p>
 *
 * <p>当前实现：M1 实体 + 三阶段，M2 招式（普攻、霜刃 7×2、暮色斩星 25%、朝露回血）。</p>
 */
public class LumenSeishinEntity extends Monster {
    public static final float MAX_HEALTH = 360.0F;
    private static final int PHASE_LOCK_TICKS = 20;

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

    private int frostHitsLeft;
    private int frostHitIndex;
    private long nextFrostTick;
    private long frostCooldownUntil;
    private long slashWindupUntil;
    private long slashCooldownUntil;
    private long healCooldownUntil;
    private float healedTotal;
    private int finalChargeLeft;
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

    public void setPhase(LumenPhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
        this.bossEvent.setColor(phase.barColor());
        this.bossEvent.setName(getDisplayName());
        this.lockTicks = PHASE_LOCK_TICKS;
        // 跨阶段时取消进行中的技能，避免换刀后还在用旧阶段的刀光/计时。
        this.frostHitsLeft = 0;
        this.slashWindupUntil = 0L;
        refreshBlade(phase);
        playPhaseChangeEffect(phase);
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
        LumenPhase expected = LumenPhase.forHealth(getHealth());
        if (expected.ordinal() > getPhase().ordinal()) {
            setPhase(expected);
        }
        if (isFinalCharging()) {
            tickFinalCharge();
            return;
        }
        if (getPhase() == LumenPhase.MISTLETOE && !this.finalChargeDone && getHealth() <= FINAL_CHARGE_THRESHOLD) {
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
        this.finalChargeLeft--;
        if (this.finalChargeLeft % 4 == 0) {
            spawnChargeRing();
        }
        if (this.finalChargeLeft == FINAL_WARN_1 || this.finalChargeLeft == FINAL_WARN_2) {
            playSound(SoundEvents.BEACON_ACTIVATE, 1.2F, 0.5F);
        }
        if (this.finalChargeLeft <= 0) {
            resolveFinalCharge();
        }
    }

    /** 在 12 格半径上画一圈粒子，作为可读的危险区边界。 */
    private void spawnChargeRing() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        int points = 36;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            double x = getX() + Math.cos(angle) * FINAL_CHARGE_RADIUS;
            double z = getZ() + Math.sin(angle) * FINAL_CHARGE_RADIUS;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    x, getY() + 0.4D, z, 1, 0.0D, 0.25D, 0.0D, 0.0D);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                getX(), getY() + 1.0D, getZ(), 6, 0.4D, 1.0D, 0.4D, 0.02D);
    }

    /**
     * 结局 A：蓄力期间被击杀 → 本方法不会执行（实体已死亡）。
     * 结局 B：范围内没有玩家 → 没人受伤，Boss 仍死亡。
     * 结局 C：范围内玩家吃处决。
     */
    public void resolveFinalCharge() {
        if (this.level().isClientSide()) {
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
        // 无论是否命中都献祭死亡；走正常 hurt，尊重特殊免疫与锁血标签。
        this.hurt(MistletoeDamage.source(this, MistletoeDamage.SACRIFICE),
                getHealth() + getMaxHealth());
        TerminalBlade.LOGGER.info("[流明·星] 终局释放");
    }

    private void tickSkills() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            this.frostHitsLeft = 0;
            this.slashWindupUntil = 0L;
            return;
        }
        long now = this.level().getGameTime();

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
        float amount = meleeDamage(living);
        playAction(LumenAction.MELEE);
        spawnCosmeticSlash(-25.0F + getRandom().nextFloat() * 50.0F, 1.8F, phaseColor(getPhase()));
        boolean hurt = living.hurt(MistletoeDamage.source(this, meleeDamageType()), amount);
        if (hurt) {
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.0F);
        }
        return hurt;
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
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
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

    private void refreshBlade(LumenPhase phase) {
        if (this.level().isClientSide()) {
            return;
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, bladeFor(phase));
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
        if (!this.level().isClientSide() && this.tickCount == 1) {
            refreshBlade(getPhase());
            TerminalBlade.LOGGER.debug("[流明·星] spawned phase={}", getPhase());
        }
    }

    @Override
    public Component getName() {
        return Component.translatable("entity.mistletoe.lumen_seishin");
    }
}
