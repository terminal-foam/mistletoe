package dev.xingguang.terminalblade.combat.execution;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.TerminalBladeConfig;
import dev.xingguang.terminalblade.combat.MistletoeDamage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Super SA 的持续处决。
 *
 * <p>第一次绝对伤害被有限伤、阶段窗口或自定义生命值模型挡下后，每隔
 * {@code persistent_interval} tick 再打一次绝对伤害，直到目标死亡、阶段锁、
 * 超出 32 格、施法者死亡或达到 {@code persistent_ticks} 上限。</p>
 *
 * <p>它不会调用 {@code setHealth(0)} 或 {@code die()} 来伪造死亡；每一次都是
 * 正常的 {@code hurt}，所以不会把 LM 这类自定义生命值 Boss 卡进死亡动画。</p>
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class SuperExecutionChannel {
    private static final double MAX_DISTANCE_SQR = 32.0D * 32.0D;
    private static final Map<UUID, Execution> ACTIVE = new ConcurrentHashMap<>();

    private SuperExecutionChannel() {
    }

    public static void start(LivingEntity caster, LivingEntity target) {
        if (!TerminalBladeConfig.superPersistentExecution()
                || caster == null || target == null
                || caster.level().isClientSide() || target.level().isClientSide()
                || caster == target || !caster.isAlive() || !target.isAlive()
                || target.isRemoved() || target instanceof Player
                || PhaseLockResolvers.isLocked(target)) {
            return;
        }

        int configuredTicks = TerminalBladeConfig.superPersistentTicks();
        int remaining = configuredTicks <= 0 ? Integer.MAX_VALUE : configuredTicks;
        int interval = Math.max(1, TerminalBladeConfig.superPersistentInterval());
        long nextTick = caster.level().getGameTime() + interval;
        Execution execution = new Execution(caster.getUUID(), target.getUUID(),
                caster.level().dimension(), remaining, interval, nextTick);
        ACTIVE.put(target.getUUID(), execution);

        if (TerminalBladeConfig.logDamage()) {
            TerminalBlade.LOGGER.info(
                    "[槲寄生] Super SA 持续处决开始：target={} 上限={}tick 间隔={}tick",
                    target.getName().getString(), configuredTicks, interval);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Execution execution : List.copyOf(ACTIVE.values())) {
            execution.tick(server);
        }
    }

    private static void stop(Execution execution, String reason) {
        if (ACTIVE.remove(execution.targetId, execution) && TerminalBladeConfig.logDamage()) {
            TerminalBlade.LOGGER.info("[槲寄生] Super SA 持续处决结束：{} ({})",
                    execution.targetId, reason);
        }
    }

    private static final class Execution {
        private final UUID casterId;
        private final UUID targetId;
        private final ResourceKey<Level> dimension;
        private int remainingTicks;
        private final int interval;
        private long nextTick;

        private Execution(UUID casterId, UUID targetId, ResourceKey<Level> dimension,
                          int remainingTicks, int interval, long nextTick) {
            this.casterId = casterId;
            this.targetId = targetId;
            this.dimension = dimension;
            this.remainingTicks = remainingTicks;
            this.interval = interval;
            this.nextTick = nextTick;
        }

        private void tick(MinecraftServer server) {
            if (remainingTicks <= 0) {
                stop(this, "timeout");
                return;
            }
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                stop(this, "dimension_missing");
                return;
            }
            Entity casterEntity = level.getEntity(casterId);
            Entity targetEntity = level.getEntity(targetId);
            if (!(casterEntity instanceof LivingEntity caster)
                    || !(targetEntity instanceof LivingEntity target)) {
                stop(this, "entity_unloaded");
                return;
            }
            if (caster.isRemoved() || !caster.isAlive() || target.isRemoved() || !target.isAlive()) {
                stop(this, "caster_or_target_gone");
                return;
            }
            if (caster.distanceToSqr(target) > MAX_DISTANCE_SQR) {
                stop(this, "out_of_range");
                return;
            }
            if (target instanceof Player || PhaseLockResolvers.isLocked(target)) {
                stop(this, "protected");
                return;
            }

            long now = level.getGameTime();
            if (now < nextTick) {
                return;
            }
            nextTick = now + interval;
            remainingTicks -= interval;

            float amount = target.getMaxHealth() + target.getAbsorptionAmount() + 1000.0F;
            boolean hit = MistletoeDamage.hurt(caster, target, MistletoeDamage.GODSLAYER_ABSOLUTE, amount);
            if (target.isRemoved() || target.isDeadOrDying()) {
                stop(this, "killed");
                return;
            }
            if (TerminalBladeConfig.logDamage()) {
                TerminalBlade.LOGGER.info(
                        "[槲寄生] Super SA 持续处决：target={} hit={} health={} 剩余={}tick",
                        target.getName().getString(), hit, target.getHealth(), remainingTicks);
            }
        }
    }
}
