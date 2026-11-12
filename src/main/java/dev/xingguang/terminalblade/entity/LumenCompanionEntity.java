package dev.xingguang.terminalblade.entity;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.combat.MistletoeDamage;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 臣服后的流明·星「随从」形态。
 *
 * <p>和 Boss（{@link LumenSeishinEntity}）是两个独立实体：Boss 被玩家打死时消失并在原地生成它。
 * 它固定使用暮色一套（刀、招式、伤害），没有终局蓄力，也不会自爆。</p>
 */
public class LumenCompanionEntity extends TamableAnimal implements GeoAnimatable {
    public static final float MAX_HEALTH = 20.0F;
    /** 下界合金全套：护甲 20。 */
    private static final double ARMOR = 20.0D;
    /** 下界合金全套：盔甲韧性 12。 */
    private static final double ARMOR_TOUGHNESS = 12.0D;
    private static final double MOVEMENT_SPEED = 0.30D;
    private static final double KNOCKBACK_RESISTANCE = 0.50D;
    private static final double FOLLOW_RANGE = 24.0D;
    private static final int MELEE_CHAIN = 2;
    private static final int MELEE_INTERVAL = 10;
    private static final long SLASH_WINDUP = 24L;
    private static final long SLASH_COOLDOWN = 300L;
    private static final double SLASH_RANGE = 8.0D;
    private static final double SLASH_CONE_DEGREES = 120.0D;
    private static final double MELEE_HIT_RANGE = 4.25D;
    private static final double OWNER_GUARD_RADIUS = 24.0D;
    private static final int SLASH_COLOR = 0xC9A0FF;

    private static final int REGEN_INTERVAL = 40;
    private static final int INVENTORY_SIZE = 36;

    /**
     * 全套保护 IV 的减伤系数。
     * 保护 IV 每件 4 EPF，四件 16 EPF → 减伤 16/25 = 64% → 只受 36% 伤害。
     */
    private static final float PROTECTION_FACTOR = 0.36F;

    private static final EntityDataAccessor<Byte> DATA_ACTION =
            SynchedEntityData.defineId(LumenCompanionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_ANIM_SEQ =
            SynchedEntityData.defineId(LumenCompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COMBO_INDEX =
            SynchedEntityData.defineId(LumenCompanionEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    private final net.minecraft.world.SimpleContainer inventory = new net.minecraft.world.SimpleContainer(INVENTORY_SIZE);

    private int lastAnimSeq = -1;
    private String lastAnimName = "";

    private LivingEntity meleeTarget;
    private int meleeHitsLeft;
    private int meleeHitIndex;
    private int meleeCooldownTicks;

    private int actionTicksLeft;

    private long slashWindupUntil;
    private float slashLockedYaw;
    private long slashCooldownUntil;

    public LumenCompanionEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.SWEEPING_DAMAGE_RATIO, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
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

    private void playAction(LumenAction action) {
        if (this.level().isClientSide() || action == null) {
            return;
        }
        this.entityData.set(DATA_ACTION, (byte) action.ordinal());
        this.entityData.set(DATA_ANIM_SEQ, this.entityData.get(DATA_ANIM_SEQ) + 1);
        this.actionTicksLeft = switch (action) {
            case MELEE -> 14;
            case SLASH_ART -> 30;
            default -> 14;
        };
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new LumenFootworkGoal(this, () -> this.isOrderedToSit()
                || this.isVehicle() || this.meleeHitsLeft > 0 || this.slashWindupUntil > 0L
                || !shouldGuardTarget(getTarget())));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0D, 10.0F, 3.0F));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new OwnerHurtTargetGoal(this));
        // 主动搜寻敌对生物（Enemy 标记接口），不会误打牛羊
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<net.minecraft.world.entity.Mob>(
                this, net.minecraft.world.entity.Mob.class, 10, true, false,
                target -> this.isTame() && target instanceof Enemy && !this.isAlliedTo(target)));
    }

    // ── 每 tick ─────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.tickCount == 1) {
                refreshBlade();
            }
            tickActionLifetime();
            tickMeleeChain();
        }
    }

    private void tickActionLifetime() {
        if (this.actionTicksLeft <= 0) {
            return;
        }
        if (--this.actionTicksLeft <= 0) {
            this.entityData.set(DATA_ACTION, (byte) LumenAction.NONE.ordinal());
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.isTame() && getHealth() < getMaxHealth() && this.tickCount % REGEN_INTERVAL == 0) {
            this.heal(1.0F);
        }
        tickSkills();
    }

    private void tickSkills() {
        LivingEntity target = getTarget();
        if (this.isOrderedToSit() || this.isVehicle() || !shouldGuardTarget(target)) {
            this.slashWindupUntil = 0L;
            this.meleeHitsLeft = 0;
            if (target != null && (!shouldGuardTarget(target) || this.isOrderedToSit())) {
                setTarget(null);
            }
            return;
        }
        if (target == null || !target.isAlive()) {
            this.slashWindupUntil = 0L;
            return;
        }
        long now = this.level().getGameTime();
        if (this.slashWindupUntil > 0L) {
            if (now >= this.slashWindupUntil) {
                this.slashWindupUntil = 0L;
                strikeWithYaw(target, this.slashLockedYaw);
            }
            return;
        }
        if (now >= this.slashCooldownUntil && distanceToSqr(target) <= SLASH_RANGE * SLASH_RANGE) {
            this.meleeHitsLeft = 0;
            faceTarget(target);
            this.slashLockedYaw = getYRot();
            this.slashWindupUntil = now + SLASH_WINDUP;
            this.slashCooldownUntil = now + SLASH_COOLDOWN;
            playAction(LumenAction.SLASH_ART);
            playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
        }
    }

    // ── 普攻连段 ────────────────────────────────────────────────────────

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.level().isClientSide() || this.isOrderedToSit() || this.isVehicle()
                || this.meleeHitsLeft > 0 || this.slashWindupUntil > 0L
                || !(target instanceof LivingEntity living) || !shouldGuardTarget(living)) {
            return false;
        }
        this.meleeTarget = living;
        this.meleeHitIndex = 0;
        this.meleeHitsLeft = MELEE_CHAIN;
        this.meleeCooldownTicks = 0;
        meleeHit();
        return true;
    }

    private void tickMeleeChain() {
        if (this.meleeHitsLeft <= 0 || this.slashWindupUntil > 0L || this.isOrderedToSit() || this.isVehicle()) {
            return;
        }
        if (--this.meleeCooldownTicks <= 0) {
            meleeHit();
        }
    }

    /**
     * 普攻伤害：完全沿用玩家那把刀的公式。
     * D = base_damage + k × (目标最大生命 − 自己当前生命)。
     * 自己掉血越多打得越疼，和玩家持刀时一致。
     */
    private float meleeAmount(LivingEntity target) {
        return Math.max(0.0F, dev.xingguang.terminalblade.combat.MistletoeFormulas.normalDamage(
                dev.xingguang.terminalblade.TerminalBladeConfig.baseDamage(),
                dev.xingguang.terminalblade.TerminalBladeConfig.gapCoefficient(),
                target.getMaxHealth(),
                this.getHealth(),
                dev.xingguang.terminalblade.TerminalBladeConfig.clampNegativeGap()));
    }

    /**
     * 斩星伤害：沿用玩家 SA 的两段结算。
     * 普通段吃护甲，最大生命加成段作为真伤单独结算（只对高血量目标生效）。
     */
    private void castSlashArtOn(LivingEntity target) {
        var split = dev.xingguang.terminalblade.combat.MistletoeFormulas.slashArtDamageSplit(
                dev.xingguang.terminalblade.TerminalBladeConfig.baseDamage(),
                dev.xingguang.terminalblade.TerminalBladeConfig.gapCoefficient(),
                target.getMaxHealth(),
                this.getHealth(),
                dev.xingguang.terminalblade.TerminalBladeConfig.clampNegativeGap(),
                dev.xingguang.terminalblade.TerminalBladeConfig.slashArtThreshold(),
                dev.xingguang.terminalblade.TerminalBladeConfig.slashArtMaxHealthBonus(),
                1.0F);
        boolean connected = MistletoeDamage.hurt(this, target, MistletoeDamage.SLASH, split.normal());
        if (connected && split.trueDamage() > 0.0F && target.isAlive()) {
            MistletoeDamage.hurt(this, target, MistletoeDamage.SLASH_TRUE, split.trueDamage());
        }
    }

    private void meleeHit() {
        LivingEntity target = this.meleeTarget;
        if (!shouldGuardTarget(target)) {
            this.meleeHitsLeft = 0;
            this.meleeTarget = null;
            return;
        }
        float amount = meleeAmount(target);
        this.entityData.set(DATA_COMBO_INDEX, this.meleeHitIndex + 1);
        playAction(LumenAction.MELEE);
        spawnCosmeticSlash(-25.0F + getRandom().nextFloat() * 50.0F, 1.8F, SLASH_COLOR);
        MistletoeDamage.hurt(this, target, MistletoeDamage.SLASH, amount);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.0F);
        this.meleeHitIndex++;
        this.meleeHitsLeft--;
        this.meleeCooldownTicks = MELEE_INTERVAL;
    }

    public void slashStrike(LivingEntity target) {
        if (target != null && target.isAlive()) {
            faceTarget(target);
        }
        playAction(LumenAction.SLASH_ART);
        strikeWithYaw(target, getYRot());
    }

    private void strikeWithYaw(LivingEntity target, float lockedYaw) {
        spawnCosmeticSlash(-30.0F, 2.6F, SLASH_COLOR);
        if (shouldGuardTarget(target)) {
            castSlashArtOn(target);
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
    }

    private boolean shouldGuardTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || !canAttack(target) || isAlliedTo(target)) {
            return false;
        }
        LivingEntity owner = getOwner();
        return owner == null || owner.distanceToSqr(target) <= OWNER_GUARD_RADIUS * OWNER_GUARD_RADIUS;
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        setYRot(yaw);
        setYHeadRot(yaw);
        this.yBodyRot = yaw;
    }

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

    private void refreshBlade() {
        if (this.level().isClientSide()) {
            return;
        }
        ResourceKey<SlashBladeDefinition> key = ResourceKey.create(
                SlashBladeDefinition.REGISTRY_KEY, LumenPhase.MISTLETOE.bladeId());
        ItemStack blade = this.level().registryAccess()
                .lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .get(key)
                .map(holder -> holder.value().getBlade(this.level().registryAccess()))
                .orElse(ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.MAINHAND, blade);
    }

    // ── 交互：骑乘 / 坐下 / 背包 ────────────────────────────────────────

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 未驯服：喂「任意食物」收服，每次 1/3 概率（参考原版狼喂骨头）
        if (!this.isTame()) {
            ItemStack held = player.getItemInHand(hand);
            if (this.isFood(held)) {
                if (!this.level().isClientSide()) {
                    this.usePlayerItem(player, hand, held);
                    if (this.getRandom().nextInt(3) == 0) {
                        this.tame(player);
                        this.setOrderedToSit(true);
                        this.setInSittingPose(true);
                        this.getNavigation().stop();
                        this.level().broadcastEntityEvent(this, (byte) 7);
                    } else {
                        this.level().broadcastEntityEvent(this, (byte) 6);
                    }
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide());
            }
            return super.mobInteract(player, hand);
        }
        if (!this.isOwnedBy(player)) {
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
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        if (!player.getItemInHand(hand).isEmpty()) {
            if (!this.level().isClientSide()) {
                this.openInventory(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        if (this.isOrderedToSit()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

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

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return new Vec3(this.getX(), this.getY() + 1.25D, this.getZ());
    }

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

    // ── 敌我判定 ────────────────────────────────────────────────────────

    @Override
    public boolean isAlliedTo(Entity entity) {
        if (super.isAlliedTo(entity)) {
            return true;
        }
        if (this.isTame() && entity instanceof LumenCompanionEntity other) {
            return Objects.equals(this.getOwnerUUID(), other.getOwnerUUID());
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

    /**
     * 模拟全套保护 IV 的减伤，在护甲减免之后调用（由 LumenDamageHandler 的 LivingDamageEvent.Pre 触发）。
     *
     * <p>注意：不能覆写 {@code getDamageAfterMagicAbsorb} —— NeoForge 1.21.1 的
     * {@code actuallyHurt} 会丢弃它的返回值，覆写等于无效。</p>
     */
    public float adjustFinalDamage(net.minecraft.world.damagesource.DamageSource source, float damage) {
        if (damage <= 0.0F || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            return damage;
        }
        return damage * PROTECTION_FACTOR;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        // 任意食物都能用来收服
        return stack.has(net.minecraft.core.component.DataComponents.FOOD);
    }

    @Override
    public AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level, AgeableMob other) {
        return null;
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

    private PlayState mainAnim(AnimationState<LumenCompanionEntity> state) {
        String desired = this.desiredAnimation();
        state.getController().setAnimationSpeed(switch (getAction()) {
            case MELEE -> 3.0D;
            case SLASH_ART -> 2.0D;
            default -> 1.0D;
        });
        int sequence = this.getAnimSeq();
        if (!desired.equals(this.lastAnimName) || sequence != this.lastAnimSeq) {
            // 坐姿诊断：确认到底哪个实体在播 sit
            if ("sit".equals(desired) || "sit".equals(this.lastAnimName)) {
                dev.xingguang.terminalblade.TerminalBlade.LOGGER.info("[流明] {} ({}) 动画 {} -> {}",
                        this.getUUID(), this.getClass().getSimpleName(), this.lastAnimName, desired);
            }
            this.lastAnimName = desired;
            this.lastAnimSeq = sequence;
            state.getController().forceAnimationReset();
            if (getAction() == LumenAction.NONE || this.isInSittingPose()) {
                state.getController().setAnimation(RawAnimation.begin().thenLoop(desired));
            } else {
                state.getController().setAnimation(RawAnimation.begin().thenPlay(desired));
            }
        }
        return PlayState.CONTINUE;
    }

    private String desiredAnimation() {
        if (this.isInSittingPose()) {
            return "sit";
        }
        return switch (getAction()) {
            case MELEE -> switch (Math.min(this.entityData.get(DATA_COMBO_INDEX), 4)) {
                case 1 -> "slashblade:combo_a1";
                case 2 -> "slashblade:combo_a2";
                case 3 -> "slashblade:combo_a3";
                default -> "slashblade:combo_a4";
            };
            case SLASH_ART -> "slashblade:combo_c";
            default -> {
                double dx = this.getX() - this.xo;
                double dz = this.getZ() - this.zo;
                double speedSq = dx * dx + dz * dz;
                if (speedSq > 0.15D) {
                    yield "run";
                }
                if (speedSq > 0.005D) {
                    yield "walk";
                }
                yield "idle";
            }
        };
    }

    // ── 存读档 ──────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("LumenInventory", this.inventory.createTag(this.registryAccess()));
        tag.putLong("LumenSlashCooldown", this.slashCooldownUntil);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("LumenInventory")) {
            this.inventory.fromTag(tag.getList("LumenInventory", 10), this.registryAccess());
        }
        this.slashCooldownUntil = tag.getLong("LumenSlashCooldown");
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ── 名字 ────────────────────────────────────────────────────────────

    /** 随机名字的翻译键：每次生成随机挑一个。 */
    private static final String[] NAME_KEYS = {
            "entity.mistletoe.lumen_companion.name_lumen",
            "entity.mistletoe.lumen_companion.name_star",
    };

    /** 生成时随机取一个显示名（流明 / 小星）。 */
    public void randomDisplayName() {
        if (this.level().isClientSide() || this.hasCustomName()) {
            return;
        }
        this.setCustomName(Component.translatable(
                NAME_KEYS[this.getRandom().nextInt(NAME_KEYS.length)]));
        this.setCustomNameVisible(true);
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType spawnType,
            net.minecraft.world.entity.SpawnGroupData spawnGroupData) {
        // 走刷怪蛋 / 自然生成这条路也要有名字
        randomDisplayName();
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Override
    public Component getName() {
        return Component.translatable("entity.mistletoe.lumen_companion");
    }

    /** 供死亡复活逻辑取物品栏引用。 */
    public net.minecraft.world.SimpleContainer getInventory() {
        return this.inventory;
    }

    /** 主人 UUID（复活时用于重新绑定）。 */
    public UUID ownerId() {
        return this.getOwnerUUID();
    }

    static {
        TerminalBlade.LOGGER.debug("[流明·星] 随从实体已加载");
    }
}
