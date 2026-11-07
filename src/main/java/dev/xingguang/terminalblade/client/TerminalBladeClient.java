package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.registry.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = TerminalBlade.MOD_ID, value = Dist.CLIENT)
public final class TerminalBladeClient {
    private TerminalBladeClient() {
    }

    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.PETAL.get(), MistletoeParticle.PetalProvider::new);
        event.registerSpriteSet(ModParticles.BERRY.get(), MistletoeParticle.BerryProvider::new);
        event.registerSpriteSet(ModParticles.SPARK.get(), MistletoeParticle.SparkProvider::new);
    }
}
