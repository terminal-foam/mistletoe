package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 槲寄生按住左键自动攻击：初雪和朝露共用。
 *
 * 原版只在左键按下的瞬间发起实体攻击；SlashBlade 的连段又依赖一次成功的近战攻击。
 * 这里在攻击冷却就绪时补发攻击，让各自的攻速属性真正决定每秒打几下。
 * 朝露没有额外攻速，所以实际频率应明显低于初雪。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID, value = Dist.CLIENT)
public final class MistletoeAutoAttack {
    private static long lastDebugTick = -100L;

    private MistletoeAutoAttack() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null) {
            return;
        }
        if (!client.options.keyAttack.isDown()) {
            return;
        }
        String blade = bladeName(client);
        if (blade == null) {
            if (client.player.tickCount - lastDebugTick > 40L) {
                lastDebugTick = client.player.tickCount;
                TerminalBlade.LOGGER.info("[GameTest] AutoAttack：左键按住，但主手不是槲寄生，物品={}",
                        client.player.getMainHandItem().getHoverName().getString());
            }
            return;
        }
        float scale = client.player.getAttackStrengthScale(0.5F);
        Entity target = client.crosshairPickEntity;
        if (client.player.tickCount - lastDebugTick > 40L) {
            lastDebugTick = client.player.tickCount;
            TerminalBlade.LOGGER.info("[GameTest] AutoAttack：{} 按住左键 scale={} target={}",
                    blade, scale, target == null ? "null" : target.getType());
        }
        // 初雪做无冷却提交测试；朝露保持攻击冷却条件。
        if (!"初雪".equals(blade) && scale < 0.9F) {
            return;
        }
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living == client.player) {
            return;
        }
        client.gameMode.attack(client.player, living);
        client.player.swing(InteractionHand.MAIN_HAND);
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
