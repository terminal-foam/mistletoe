package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * 初雪的攻速加成。只作用于初雪，不改 SlashBlade 的全局 attackSpeedIn。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class FirstSnowAttributes {
    private static final double ATTACK_SPEED_BONUS = 0.5D;

    private FirstSnowAttributes() {
    }

    @SubscribeEvent
    public static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        if (!MistletoeBlade.isFirstSnow(event.getItemStack())) {
            return;
        }
        event.addModifier(
                Attributes.ATTACK_SPEED,
                new AttributeModifier(
                        TerminalBlade.id("first_snow_attack_speed"),
                        ATTACK_SPEED_BONUS,
                        AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }
}
