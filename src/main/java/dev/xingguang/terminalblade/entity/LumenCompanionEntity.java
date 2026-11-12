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
    public static final float MAX_HEALTH = 100.0F;
    private static final double ARMOR = 12.0D;
    private static final double ARMOR_TOUGHNESS = 4.0D;
    private static final double MOVEMENT_SPEED = 0.30D;
    private static final double KNOCKBACK_RESISTANCE = 0.50D;
    private static final double FOLLOW_RANGE = 24.0D;

    /** 暮色普攻：目标最大生命 10%。 */
    private static final float MELEE_RATIO = 0.10F;
    private static final int MELEE_CHAIN = 2;
    private static final int MELEE_INTERVAL = 10;

    /** 暮色斩星：25%，前摇 24 tick，冷却 300 tick。 */
    private static final float SLASH_RATIO = 0.25F;
    private static final long SLASH_WINDUP = 24L;
    private static final long SLASH_COOLDOWN = 300L;
    private static final double SLASH_RANGE = 16.0D;
    private static final int SLASH_COLOR = 0xC9A0FF;

    private static final int REGEN_INTERVAL = 40;
    private static final int INVENTORY_SIZE = 36;

    private static final EntityDataAccessor<Byte> DATA_ACTION =
            SynchedEntityData.defineId(LumenCompanionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_ANIM_SEQ =
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
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0D, 10.0F, 3.0F));
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
        if (target == null || !target.isAlive()) {
            this.slashWindupUntil = 0L;
            return;
        }
        long now = this.level().getGameTime();
        if (this.slashWindupUntil > 0L) {
            if (now >= this.slashWindupUntil) {
                this.slashWindupUntil = 0L;
                slashStrike(target);
            }
            return;
        }
        if (now >= this.slashCooldownUntil && distanceToSqr(target) <= SLASH_RANGE * SLASH_RANGE) {
            this.slashWindupUntil = now + SLASH_WINDUP;
            this.slashCooldownUntil = now + SLASH_COOLDOWN;
            playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
        }
    }

    // ── 普攻连段 ────────────────────────────────────────────────────────

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.level().isClientSide() || !(target instanceof LivingEntity living) || !living.isAlive()) {
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
        if (this.meleeHitsLeft <= 0) {
            return;
        }
        if (--this.meleeCooldownTicks <= 0) {
            meleeHit();
        }
    }

    private void meleeHit() {
        LivingEntity target = this.meleeTarget;
        if (target == null || !target.isAlive()) {
            this.meleeHitsLeft = 0;
            return;
        }
        float amount = Math.max(1.0F, target.getMaxHealth() * MELEE_RATIO);
        playAction(LumenAction.MELEE);
        spawnCosmeticSlash(-25.0F + getRandom().nextFloat() * 50.0F, 1.8F, SLASH_COLOR);
        target.hurt(MistletoeDamage.source(this, MistletoeDamage.LUMEN_TRUE), amount);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.0F);
        this.meleeHitIndex++;
        this.meleeHitsLeft--;
        this.meleeCooldownTicks = MELEE_INTERVAL;
    }

    public void slashStrike(LivingEntity target) {
        playAction(LumenAction.SLASH_ART);
        spawnCosmeticSlash(-30.0F, 2.6F, SLASH_COLOR);
        float amount = Math.max(1.0F, target.getMaxHealth() * SLASH_RATIO);
        target.hurt(MistletoeDamage.source(this, MistletoeDamage.LUMEN_TRUE), amount);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
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
        if (!desired.equals(this.lastAnimName)) {
            this.lastAnimName = desired;
            this.lastAnimSeq = this.getAnimSeq();
            state.getController().setAnimation(RawAnimation.begin().thenPlay(desired));
        }
        return PlayState.CONTINUE;
    }

    private String desiredAnimation() {
        if (this.isInSittingPose()) {
            return "sit";
        }
        return switch (getAction()) {
            case MELEE -> switch (Math.min(this.meleeHitIndex, 4)) {
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
