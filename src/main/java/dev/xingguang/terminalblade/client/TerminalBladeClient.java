package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

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
        event.getToolTip().add(Component.translatable("item.mistletoe.mistletoe.desc").withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable("item.mistletoe.mistletoe.hint").withStyle(ChatFormatting.DARK_GREEN));
    }
}
