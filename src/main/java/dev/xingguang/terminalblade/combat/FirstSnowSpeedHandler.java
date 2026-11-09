package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 用玩家 tick 强制维护初雪攻速。
 *
 * ItemAttributeModifierEvent 能给物品列表加 modifier，但玩家属性刷新没有稳定收取它；
 * 这里直接给玩家 ATTACK_SPEED 挂 transient modifier，主手换成别的刀时移除。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class FirstSnowSpeedHandler {
    private static final ResourceLocation SPEED_ID = TerminalBlade.id("first_snow_speed");
    private static final double SPEED_BONUS = 36.0D;

    private FirstSnowSpeedHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) {
            return;
        }
        boolean firstSnow = isFirstSnow(player);
        if (firstSnow) {
            if (!speed.hasModifier(SPEED_ID)) {
                speed.addTransientModifier(new AttributeModifier(
                        SPEED_ID, SPEED_BONUS, AttributeModifier.Operation.ADD_VALUE));
            }
        } else if (speed.hasModifier(SPEED_ID)) {
            speed.removeModifier(SPEED_ID);
        }
    }

    private static boolean isFirstSnow(Player player) {
        if (MistletoeBlade.isFirstSnow(player)) {
            return true;
        }
        String name = player.getMainHandItem().getHoverName().getString();
        return name.contains("初雪") || name.contains("First Snow");
    }
}
