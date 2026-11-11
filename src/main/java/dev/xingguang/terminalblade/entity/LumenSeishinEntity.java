package dev.xingguang.terminalblade.entity;

import dev.xingguang.terminalblade.TerminalBlade;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 流明·星：三阶段人形剑士 Boss。
 *
 * <p>M1 范围：实体、属性、Boss 血条、阶段推进与阶段锁、换刀、存读档、召唤。
 * 招式、百分比真伤与终局蓄力在后续里程碑实现。</p>
 *
 * <p>外观拿刀，但伤害不走玩家的 {@code attack_base} 连段公式，由 Boss 自己结算。</p>
 */
public class LumenSeishinEntity extends Monster {
    public static final float MAX_HEALTH = 360.0F;
    /** 阶段切换时的锁血时长（tick）。 */
    private static final int PHASE_LOCK_TICKS = 20;

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(LumenSeishinEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.mistletoe.lumen_seishin"),
            BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarOverlay.PROGRESS);
    private int lockTicks;

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
                // 伤害由 Boss 自己结算，基础攻击力不参与。
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                // SlashBlade 的 AttackHelper 会无条件读这个属性；缺了会在出刀光时抛异常。
                .add(Attributes.SWEEPING_DAMAGE_RATIO, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, LumenPhase.DAWN.ordinal());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public LumenPhase getPhase() {
        return LumenPhase.byOrdinal(this.entityData.get(DATA_PHASE));
    }

    private void setPhase(LumenPhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
        this.bossEvent.setColor(phase.barColor());
        this.bossEvent.setName(getDisplayName());
        this.lockTicks = PHASE_LOCK_TICKS;
        refreshBlade(phase);
    }

    public boolean isPhaseLocked() {
        return this.lockTicks > 0;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return isPhaseLocked() || super.isInvulnerableTo(source);
    }

    /**
     * 阶段锁血：伤害只够把生命削到下一阶段的阈值，不允许一刀跳过阶段。
     * 由 {@code LivingIncomingDamageEvent} 在最终伤害上调用。
     */
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
    }

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
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_PHASE, LumenPhase.byOrdinal(tag.getInt("LumenPhase")).ordinal());
        this.lockTicks = tag.getInt("LumenLock");
        this.bossEvent.setColor(getPhase().barColor());
        this.bossEvent.setName(getDisplayName());
    }

    private void refreshBlade(LumenPhase phase) {
        if (this.level().isClientSide()) {
            return;
        }
        ItemStack blade = bladeFor(phase);
        if (!blade.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, blade);
        }
    }

    private ItemStack bladeFor(LumenPhase phase) {
        ResourceKey<SlashBladeDefinition> key = ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, phase.bladeId());
        return this.level().registryAccess()
                .lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .get(key)
                .map(holder -> holder.value().getBlade(this.level().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    /** 实体创建后设置初始刀；由 {@code finalizeSpawn} / 首个 tick 触发都安全。 */
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
