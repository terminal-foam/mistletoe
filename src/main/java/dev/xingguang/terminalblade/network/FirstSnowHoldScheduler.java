package dev.xingguang.terminalblade.network;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端权威的初雪按住左键攻击循环。
 * 客户端只报告“是否按住、目标是谁”，实际 attack() 由服务端按攻速执行。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class FirstSnowHoldScheduler {
    private static final Map<UUID, HoldState> HOLDING = new HashMap<>();

    private FirstSnowHoldScheduler() {
    }

    public static void handle(Player player, FirstSnowHoldPayload payload) {
        if (!(player instanceof ServerPlayer serverPlayer) || !payload.held()
                || !MistletoeBlade.isFirstSnow(serverPlayer)) {
            if (player != null) {
                HOLDING.remove(player.getUUID());
            }
            return;
        }
        Entity target = serverPlayer.level().getEntity(payload.targetId());
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            HOLDING.remove(serverPlayer.getUUID());
            return;
        }
        long now = serverPlayer.server.overworld().getGameTime();
        HoldState state = HOLDING.computeIfAbsent(serverPlayer.getUUID(), ignored -> new HoldState());
        state.targetId = living.getId();
        if (state.lastAttack == Long.MIN_VALUE) {
            state.lastAttack = now - 100L;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (HOLDING.isEmpty()) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        HOLDING.entrySet().removeIf(entry -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            HoldState state = entry.getValue();
            if (player == null || !player.isAlive() || !MistletoeBlade.isFirstSnow(player)) {
                return true;
            }
            Entity entity = player.level().getEntity(state.targetId);
            if (!(entity instanceof LivingEntity target) || !target.isAlive()
                    || player.distanceToSqr(target) > 64.0D) {
                return true;
            }
            int interval = attackInterval(player);
            if (now - state.lastAttack >= interval) {
                target.invulnerableTime = 0;
                float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                target.hurt(player.damageSources().playerAttack(player), Math.max(1.0F, damage));
                target.invulnerableTime = 0;
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                state.lastAttack = now;
            }
            return false;
        });
    }

    private static int attackInterval(ServerPlayer player) {
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        return Math.max(1, (int) Math.ceil(20.0D / Math.max(1.0D, speed)));
    }

    private static final class HoldState {
        private int targetId;
        private long lastAttack = Long.MIN_VALUE;
    }
}
