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
 * 初雪的地面 A 连段入口重定向。
 *
 * <p>SlashBlade 的 ComboCommands 默认把地面攻击映射到 slashblade:combo_a1，不能按刀改。
 * 这里不改全局映射，只在 NextComboEvent / NextOfTimeOutComboEvent 里把 A1..A5 换成
 * mistletoe 的快速副本。快速副本内部已经自闭环
 * （fast_a1 → fast_a2 → … → fast_a5 → fast_a1），本桥只负责：
 * 1. 待机/超时后重新起步时进入 fast_a1；
 * 2. 原版 end 状态（combo_aN_end）超时或点击后仍能进入 fast 段。</p>
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
