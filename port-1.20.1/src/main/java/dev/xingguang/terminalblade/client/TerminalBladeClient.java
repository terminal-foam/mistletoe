package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = TerminalBlade.MOD_ID, value = Dist.CLIENT)
public final class TerminalBladeClient {
    private TerminalBladeClient() {
    }

    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.PETAL.get(), MistletoeParticle.PetalProvider::new);
        event.registerSpriteSet(ModParticles.BERRY.get(), MistletoeParticle.BerryProvider::new);
        event.registerSpriteSet(ModParticles.SPARK.get(), MistletoeParticle.SparkProvider::new);
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!MistletoeBlade.isMistletoe(event.getItemStack())) {
            return;
        }
        String base = MistletoeBlade.isDawn(event.getItemStack())
                ? "item.mistletoe.dawn"
                : "item.mistletoe.mistletoe";
        event.getToolTip().add(Component.translatable(base + ".desc").withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable(base + ".hint").withStyle(ChatFormatting.DARK_GREEN));
    }
}
