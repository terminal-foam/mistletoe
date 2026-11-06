package dev.xingguang.terminalblade.blade;

import dev.xingguang.terminalblade.TerminalBlade;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class MistletoeBlade {
    public static final ResourceLocation NAME = TerminalBlade.id("mistletoe");
    public static final String TRANSLATION_KEY = "item.terminalblade.mistletoe";

    private MistletoeBlade() {
    }

    public static boolean isMistletoe(ItemStack stack) {
        return BladeStateAccess.of(stack)
                .map(state -> TRANSLATION_KEY.equals(state.getTranslationKey()))
                .orElse(false);
    }

    public static boolean isMistletoe(LivingEntity user) {
        return user != null && isMistletoe(user.getMainHandItem());
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
