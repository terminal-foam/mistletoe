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
 * 初雪按住左键自动攻击。
 *
 * 原版只在左键按下的瞬间发起实体攻击；SlashBlade 的连段又依赖一次成功的近战攻击。
 * 这里在攻击冷却就绪时补发攻击，让攻速属性真正决定“每秒打几下”。
 * 只在主手初雪、准星对准活体、且没有打开界面时工作。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID, value = Dist.CLIENT)
public final class FirstSnowAutoAttack {
    private FirstSnowAutoAttack() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null) {
            return;
        }
        if (!client.options.keyAttack.isDown() || !MistletoeBlade.isFirstSnow(client.player)) {
            return;
        }
        if (client.player.getAttackStrengthScale(0.5F) < 0.9F) {
            return;
        }
        Entity target = client.crosshairPickEntity;
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living == client.player) {
            return;
        }
        client.gameMode.attack(client.player, living);
        client.player.swing(InteractionHand.MAIN_HAND);
    }
}
