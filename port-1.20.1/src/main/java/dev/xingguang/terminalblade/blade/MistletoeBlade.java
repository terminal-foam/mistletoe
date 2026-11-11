package dev.xingguang.terminalblade.blade;

import dev.xingguang.terminalblade.TerminalBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;

public final class MistletoeBlade {
    public static final ResourceLocation NAME = TerminalBlade.id("mistletoe");
    public static final ResourceLocation DAWN_NAME = TerminalBlade.id("dawn");
    public static final ResourceLocation FIRST_SNOW_NAME = TerminalBlade.id("first_snow");
    public static final String TRANSLATION_KEY = "item.mistletoe.mistletoe";
    public static final String DAWN_TRANSLATION_KEY = "item.mistletoe.dawn";
    public static final String FIRST_SNOW_TRANSLATION_KEY = "item.mistletoe.first_snow";
    private static final Set<String> BLADE_KEYS = Set.of(TRANSLATION_KEY, DAWN_TRANSLATION_KEY);

    private MistletoeBlade() {
    }

    /**
     * 刀铭（display.Name）。显式关闭斜体，但保留 SlashBlade/稀有度给名字上的颜色。
     */
    public static Component dawnName() {
        return Component.translatable(DAWN_TRANSLATION_KEY)
                .withStyle(style -> style.withItalic(false));
    }

    public static Optional<ISlashBladeState> stateOptional(ItemStack stack) {
        return stack.getCapability(ItemSlashBlade.BLADESTATE).resolve();
    }

    public static boolean isMistletoe(ItemStack stack) {
        return stateOptional(stack)
                .map(state -> BLADE_KEYS.contains(state.getTranslationKey()))
                .orElse(false);
    }

    public static boolean isMistletoe(LivingEntity user) {
        return user != null && isMistletoe(user.getMainHandItem());
    }

    public static boolean isDawn(ItemStack stack) {
        return stateOptional(stack)
                .map(state -> DAWN_TRANSLATION_KEY.equals(state.getTranslationKey()))
                .orElse(false);
    }

    public static boolean isDawn(LivingEntity user) {
        return user != null && isDawn(user.getMainHandItem());
    }

    public static boolean isFirstSnow(ItemStack stack) {
        return stateOptional(stack)
                .map(state -> FIRST_SNOW_TRANSLATION_KEY.equals(state.getTranslationKey()))
                .orElse(false);
    }

    public static boolean isFirstSnow(LivingEntity user) {
        return user != null && isFirstSnow(user.getMainHandItem());
    }

    public static boolean isBrokenOrSealed(ItemStack stack) {
        return stateOptional(stack)
                .map(state -> state.isBroken() || state.isSealed())
                .orElse(true);
    }

    public static ISlashBladeState state(ItemStack stack) {
        return stateOptional(stack).orElseThrow();
    }
}
