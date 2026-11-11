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

    /**
     * 熹微复用斩星的 ComboState 与斩击特效；实际伤害/恢复在 MistletoeCombat 里按 blade 分流。
     */
    public static final RegistryObject<SlashArts> XIWEI = SLASH_ARTS.register("xiwei",
            () -> {
                SlashArts art = new SlashArts(user -> ModComboStates.SLASH_ART.getId())
                        .setComboStateJust(user -> ModComboStates.SLASH_ART_JUST.getId())
                        .setComboStateSuper(user -> ModComboStates.SUPER.getId());
                art.setProudSoulCost(0);
                return art;
            });

    /**
     * 初雪的「霜刃」：7 段高速斩击，走普通攻击伤害，不套暮色的生命差公式。
     */
    public static final RegistryObject<SlashArts> FROST_BLADE = SLASH_ARTS.register("frost_blade",
            () -> {
                SlashArts art = new SlashArts(user -> ModComboStates.FROST_BLADE.getId())
                        .setComboStateJust(user -> ModComboStates.FROST_BLADE.getId())
                        .setComboStateSuper(user -> mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
                art.setProudSoulCost(0);
                return art;
            });

    private ModSlashArts() {
    }
}
