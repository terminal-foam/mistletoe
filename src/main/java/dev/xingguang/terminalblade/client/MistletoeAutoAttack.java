package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.network.FirstSnowHoldPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 槲寄生按住左键的客户端侧。
 *
 * 初雪：只把“按住 + 目标”发给服务端，攻击由 FirstSnowHoldScheduler 权威执行。
 * 朝露：保留原来的客户端攻击触发。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID, value = Dist.CLIENT)
public final class MistletoeAutoAttack {
    private static long lastDebugTick = -100L;
    private static int sentTarget = -1;
    private static boolean sentHeld;
    private static int lastSentTick = -100;

    private MistletoeAutoAttack() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            reset();
            return;
        }
        if (client.screen != null) {
            release();
            return;
        }
        if (!client.options.keyAttack.isDown()) {
            release();
            return;
        }
        String blade = bladeName(client);
        if (blade == null) {
            release();
            if (client.player.tickCount - lastDebugTick > 40L) {
                lastDebugTick = client.player.tickCount;
                TerminalBlade.LOGGER.info("[GameTest] AutoAttack：左键按住，但主手不是槲寄生，物品={}",
                        client.player.getMainHandItem().getHoverName().getString());
            }
            return;
        }

        Entity target = client.crosshairPickEntity;
        if (client.player.tickCount - lastDebugTick > 40L) {
            lastDebugTick = client.player.tickCount;
            TerminalBlade.LOGGER.info("[GameTest] AutoAttack：{} 按住左键 target={}",
                    blade, target == null ? "null" : target.getType());
        }

        if ("初雪".equals(blade)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive() || living == client.player) {
                release();
                return;
            }
            int id = living.getId();
            if (!sentHeld || sentTarget != id || client.player.tickCount - lastSentTick >= 10) {
                PacketDistributor.sendToServer(new FirstSnowHoldPayload(id, true));
                sentHeld = true;
                sentTarget = id;
                lastSentTick = client.player.tickCount;
            }
            return;
        }

        // 朝露仍沿用原来的客户端攻击触发；服务端权威循环只服务初雪。
        float scale = client.player.getAttackStrengthScale(0.5F);
        if (scale < 0.9F) {
            return;
        }
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living == client.player) {
            return;
        }
        client.gameMode.attack(client.player, living);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    private static void release() {
        if (sentHeld) {
            PacketDistributor.sendToServer(new FirstSnowHoldPayload(sentTarget, false));
            reset();
        }
    }

    private static void reset() {
        sentHeld = false;
        sentTarget = -1;
        lastSentTick = -100;
    }

    private static String bladeName(Minecraft client) {
        if (MistletoeBlade.isFirstSnow(client.player)) {
            return "初雪";
        }
        if (MistletoeBlade.isDawn(client.player)) {
            return "朝露";
        }
        String name = client.player.getMainHandItem().getHoverName().getString();
        if (name.contains("初雪") || name.contains("First Snow")) {
            return "初雪";
        }
        if (name.contains("朝露") || name.contains("Dew")) {
            return "朝露";
        }
        return null;
    }
}
