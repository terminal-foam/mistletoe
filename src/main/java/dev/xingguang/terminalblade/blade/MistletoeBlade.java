package dev.xingguang.terminalblade.blade;

import dev.xingguang.terminalblade.TerminalBlade;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class MistletoeBlade {
    public static final ResourceLocation NAME = TerminalBlade.id("mistletoe");
    public static final ResourceLocation DAWN_NAME = TerminalBlade.id("dawn");
    public static final String TRANSLATION_KEY = "item.mistletoe.mistletoe";
    public static final String DAWN_TRANSLATION_KEY = "item.mistletoe.dawn";
    private static final Set<String> BLADE_KEYS = Set.of(TRANSLATION_KEY, DAWN_TRANSLATION_KEY);

    private MistletoeBlade() {
    }

    public static boolean isMistletoe(ItemStack stack) {
        return BladeStateAccess.of(stack)
                .map(state -> BLADE_KEYS.contains(state.getTranslationKey()))
                .orElse(false);
    }

    public static boolean isMistletoe(LivingEntity user) {
        return user != null && isMistletoe(user.getMainHandItem());
    }

    public static boolean isDawn(ItemStack stack) {
        return BladeStateAccess.of(stack)
                .map(state -> DAWN_TRANSLATION_KEY.equals(state.getTranslationKey()))
                .orElse(false);
    }

    public static boolean isDawn(LivingEntity user) {
        return user != null && isDawn(user.getMainHandItem());
    }

    public static boolean isBrokenOrSealed(ItemStack stack) {
        return BladeStateAccess.of(stack)
                .map(state -> state.isBroken() || state.isSealed())
                .orElse(true);
    }

    public static ISlashBladeState state(ItemStack stack) {
        return BladeStateAccess.of(stack).orElseThrow();
    }
}
