package dev.xingguang.terminalblade.gametest;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TerminalBlade.MOD_ID)
public class MistletoeLoadGameTest {
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void namedBladeLoads(GameTestHelper helper) {
        ResourceKey<SlashBladeDefinition> key = ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, MistletoeBlade.NAME);
        var registry = helper.getLevel().registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
        helper.assertTrue(registry.get(key).isPresent(), "槲寄生 named blade 没有注册");
        var blade = registry.getOrThrow(key).value().getBlade(helper.getLevel().registryAccess());
        helper.assertFalse(blade.isEmpty(), "槲寄生物品生成失败");
        helper.assertTrue(MistletoeBlade.isMistletoe(blade), "生成的刀没有被识别为槲寄生");
        helper.succeed();
    }
}
