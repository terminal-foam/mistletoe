package dev.xingguang.terminalblade;

import dev.xingguang.terminalblade.registry.ModComboStates;
import dev.xingguang.terminalblade.registry.ModParticles;
import dev.xingguang.terminalblade.registry.ModSlashArts;
import dev.xingguang.terminalblade.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import dev.xingguang.terminalblade.client.TerminalBladeClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TerminalBlade.MOD_ID)
public class TerminalBlade {
    public static final String MOD_ID = "mistletoe";
    public static final Logger LOGGER = LogManager.getLogger("TerminalBlade");

    public TerminalBlade() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TerminalBladeConfig.SPEC);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModComboStates.COMBO_STATES.register(modBus);
        ModSlashArts.SLASH_ARTS.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(TerminalBladeClient::registerParticles);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
