package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.TerminalBladeConfig;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayDeque;

/**
 * 普攻差额只改写槲寄生打出的那一次伤害，不在原伤害上再加一遍。
 * SA 与 Super SA 使用本模组的伤害类型，并在结算期间关掉改写，避免和普攻钩子叠算。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class MistletoeDamage {
    public static final ResourceKey<DamageType> SLASH = ResourceKey.create(Registries.DAMAGE_TYPE, TerminalBlade.id("mistletoe_slash"));
    public static final ResourceKey<DamageType> SACRIFICE = ResourceKey.create(Registries.DAMAGE_TYPE, TerminalBlade.id("mistletoe_sacrifice"));
    public static final ResourceKey<DamageType> GODSLAYER = ResourceKey.create(Registries.DAMAGE_TYPE, TerminalBlade.id("mistletoe_godslayer"));
    public static final ResourceKey<DamageType> GODSLAYER_ABSOLUTE = ResourceKey.create(Registries.DAMAGE_TYPE, TerminalBlade.id("mistletoe_godslayer_absolute"));

    public static final String ORIGIN = "terminalblade:origin";
    public static final String ORIGIN_MISTLETOE = "mistletoe";
    public static final String ORIGIN_OTHER = "other";

    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<ArrayDeque<EntitySlashEffect>> ACTIVE_SLASH = ThreadLocal.withInitial(ArrayDeque::new);

    private MistletoeDamage() {
    }

    public static void pushSlash(EntitySlashEffect slash) {
        ACTIVE_SLASH.get().push(slash);
    }

    public static void popSlash(EntitySlashEffect slash) {
        ArrayDeque<EntitySlashEffect> active = ACTIVE_SLASH.get();
        if (!active.isEmpty() && active.peek() == slash) {
            active.pop();
        } else {
            active.remove(slash);
        }
    }

    @SubscribeEvent
    public static void onSlashJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntitySlashEffect slash)) {
            return;
        }
        CompoundTag data = slash.getPersistentData();
        if (data.contains(ORIGIN)) {
            return;
        }
        String origin = ORIGIN_OTHER;
        if (slash.getOwner() instanceof LivingEntity owner
                && MistletoeBlade.isMistletoe(owner)
                && !MistletoeBlade.isBrokenOrSealed(owner.getMainHandItem())) {
            origin = ORIGIN_MISTLETOE;
        }
        data.putString(ORIGIN, origin);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (APPLYING.get()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker.level().isClientSide() || event.getEntity() == attacker) {
            return;
        }
        ResourceKey<DamageType> type = event.getSource().typeHolder().unwrapKey().orElse(null);
        if (type != null && TerminalBlade.MOD_ID.equals(type.location().getNamespace())) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        EntitySlashEffect slash = currentSlash();
        if (slash != null) {
            if (!ORIGIN_MISTLETOE.equals(slash.getPersistentData().getString(ORIGIN))) {
                return;
            }
            if (direct != attacker && direct != slash) {
                return;
            }
        } else if (!(direct == attacker
                && MistletoeBlade.isMistletoe(attacker)
                && !MistletoeBlade.isBrokenOrSealed(attacker.getMainHandItem()))) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        float amount = MistletoeFormulas.normalDamage(
                TerminalBladeConfig.baseDamage(),
                TerminalBladeConfig.gapCoefficient(),
                target.getMaxHealth(),
                attacker.getHealth(),
                TerminalBladeConfig.clampNegativeGap());
        event.setAmount(Math.max(0.0F, amount));
        if (TerminalBladeConfig.logDamage()) {
            TerminalBlade.LOGGER.info(
                    "[槲寄生] 普攻 攻击者当前生命={} 目标={} 目标最大生命={} 公式伤害={}",
                    attacker.getHealth(), target.getName().getString(), target.getMaxHealth(), amount);
        }
    }

    private static EntitySlashEffect currentSlash() {
        ArrayDeque<EntitySlashEffect> active = ACTIVE_SLASH.get();
        return active.isEmpty() ? null : active.peek();
    }

    public static boolean hurt(LivingEntity attacker, LivingEntity target, ResourceKey<DamageType> type, float amount) {
        if (attacker.level().isClientSide() || target == attacker || amount <= 0.0F) {
            return false;
        }
        DamageSource source = source(attacker, type);
        target.invulnerableTime = 0;
        APPLYING.set(true);
        try {
            return target.hurt(source, amount);
        } finally {
            APPLYING.set(false);
        }
    }

    public static void drainHealth(LivingEntity entity, float amount) {
        if (entity.level().isClientSide() || amount <= 0.0F || !entity.isAlive()) {
            return;
        }
        float next = Math.max(0.0F, entity.getHealth() - amount);
        if (next <= 0.0F) {
            finish(entity, SACRIFICE);
            return;
        }
        entity.setHealth(next);
        entity.hurtTime = 10;
        entity.hurtDuration = 10;
        entity.level().broadcastEntityEvent(entity, (byte) 2);
    }

    public static void finish(LivingEntity entity, ResourceKey<DamageType> type) {
        if (!entity.isAlive()) {
            return;
        }
        DamageSource source = source(entity, type);
        entity.invulnerableTime = 0;
        APPLYING.set(true);
        try {
            entity.hurt(source, entity.getMaxHealth() + entity.getAbsorptionAmount() + 100.0F);
        } finally {
            APPLYING.set(false);
        }
        if (entity.isAlive()) {
            entity.kill();
        }
    }

    public static DamageSource source(Entity attacker, ResourceKey<DamageType> type) {
        Holder<DamageType> holder = attacker.level().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(type);
        return new DamageSource(holder, attacker);
    }

    public static void burst(ServerLevel level, double x, double y, double z, int count, double spread) {
        level.sendParticles(dev.xingguang.terminalblade.registry.ModParticles.PETAL.get(), x, y, z, count, spread, spread, spread, 0.02D);
        level.sendParticles(dev.xingguang.terminalblade.registry.ModParticles.BERRY.get(), x, y, z, Math.max(1, count / 3), spread * 0.6D, spread * 0.6D, spread * 0.6D, 0.01D);
        level.sendParticles(dev.xingguang.terminalblade.registry.ModParticles.SPARK.get(), x, y, z, count, spread, spread * 0.4D, spread, 0.04D);
    }
}
