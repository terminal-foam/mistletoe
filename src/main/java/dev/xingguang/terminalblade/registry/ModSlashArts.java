package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSlashArts {
    public static final DeferredRegister<SlashArts> SLASH_ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, TerminalBlade.MOD_ID);

    public static final DeferredHolder<SlashArts, SlashArts> GODSLAYER = SLASH_ARTS.register("godslayer",
            () -> new SlashArts(user -> ModComboStates.SLASH_ART.getId())
                    .setComboStateJust(user -> ModComboStates.SLASH_ART_JUST.getId())
                    .setComboStateSuper(user -> ModComboStates.SUPER.getId())
                    .setProudSoulCost(0));

    private ModSlashArts() {
    }
}
