package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.registry.ModComboStates;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 初雪的连段重定向：
 * 1. 地面 A 连段 A1..A5 换成 mistletoe 的快速副本（自闭环 A1→A2→…→A5→A1）；
 * 2. 迅冲斩（rapid_slash）常态也允许续段：原版只有力量/饥饿时 next 才指向
 *    {@code rapid_slash_quick}，无增益时 next 指回自己、被 progressCombo 丢弃。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class FastComboBridge {
    private static final Map<ResourceLocation, ResourceLocation> FAST_GROUND = Map.of(
            ComboStateRegistry.COMBO_A1.getId(), ModComboStates.FAST_COMBO_A1.getId(),
            ComboStateRegistry.COMBO_A2.getId(), ModComboStates.FAST_COMBO_A2.getId(),
            ComboStateRegistry.COMBO_A3.getId(), ModComboStates.FAST_COMBO_A3.getId(),
            ComboStateRegistry.COMBO_A4.getId(), ModComboStates.FAST_COMBO_A4.getId(),
            ComboStateRegistry.COMBO_A4_EX.getId(), ModComboStates.FAST_COMBO_A4_EX.getId(),
            ComboStateRegistry.COMBO_A5.getId(), ModComboStates.FAST_COMBO_A5.getId());

    private static final ResourceLocation RAPID_SLASH = ComboStateRegistry.RAPID_SLASH.getId();
    private static final ResourceLocation RAPID_SLASH_QUICK = ComboStateRegistry.RAPID_SLASH_QUICK.getId();

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
        // 迅冲斩无限连：只在“已经在 rapid_slash 里、下一次又算回 rapid_slash”时改到快速段，
        // 首次触发（待机 → rapid_slash）保持原样。rapid_slash_quick 会自己超时回 rapid_slash。
        if (RAPID_SLASH.equals(next) && isCurrentCombo(user, RAPID_SLASH)) {
            setter.accept(RAPID_SLASH_QUICK);
            return;
        }
        ResourceLocation fast = FAST_GROUND.get(next);
        if (fast != null) {
            setter.accept(fast);
        }
    }

    private static boolean isCurrentCombo(LivingEntity user, ResourceLocation id) {
        return BladeStateAccess.of(user.getMainHandItem())
                .map(state -> id.equals(state.getComboSeq()))
                .orElse(false);
    }
}
