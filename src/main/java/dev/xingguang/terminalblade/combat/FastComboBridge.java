package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.registry.ModComboStates;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 初雪的普通地面 A 连段重定向。
 *
 * SlashBlade 的 ComboCommands 默认把左键映射到 slashblade:combo_a1，不能按刀改。
 * 这里不改全局映射，只在 NextComboEvent 里把 A1..A5 换成 mistletoe 的快速副本；
 * 后续 next 仍走原版逻辑，但每次进入 A1..A5 都会被再次重定向。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class FastComboBridge {
    private static final Map<ResourceLocation, ResourceLocation> FAST_GROUND = Map.of(
            ComboStateRegistry.COMBO_A1.getId(), ModComboStates.FAST_COMBO_A1.getId(),
            ComboStateRegistry.COMBO_A2.getId(), ModComboStates.FAST_COMBO_A2.getId(),
            ComboStateRegistry.COMBO_A3.getId(), ModComboStates.FAST_COMBO_A3.getId(),
            ComboStateRegistry.COMBO_A4.getId(), ModComboStates.FAST_COMBO_A4.getId(),
            ComboStateRegistry.COMBO_A5.getId(), ModComboStates.FAST_COMBO_A5.getId());

    private FastComboBridge() {
    }

    @SubscribeEvent
    public static void onNextCombo(SlashBladeEvent.NextComboEvent event) {
        redirect(event.getUser(), event.getNextCombo(), event::setNextCombo);
    }

    @SubscribeEvent
    public static void onNextOfTimeoutCombo(SlashBladeEvent.NextOfTimeOutComboEvent event) {
        redirect(event.getUser(), event.getNextCombo(), event::setNextCombo);
    }

    private static void redirect(LivingEntity user, ResourceLocation next, Consumer<ResourceLocation> setter) {
        if (!MistletoeBlade.isFirstSnow(user)) {
            return;
        }
        ResourceLocation fast = FAST_GROUND.get(next);
        if (fast != null) {
            setter.accept(fast);
        }
    }
}
