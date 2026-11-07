package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModSlashArts {
    public static final DeferredRegister<SlashArts> SLASH_ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, TerminalBlade.MOD_ID);

    public static final RegistryObject<SlashArts> GODSLAYER = SLASH_ARTS.register("godslayer",
            () -> {
                SlashArts art = new SlashArts(user -> ModComboStates.SLASH_ART.getId())
                        .setComboStateJust(user -> ModComboStates.SLASH_ART_JUST.getId())
                        .setComboStateSuper(user -> ModComboStates.SUPER.getId());
                art.setProudSoulCost(0);
                return art;
            });

    private ModSlashArts() {
    }
}
