package dev.xingguang.terminalblade;

import dev.xingguang.terminalblade.registry.ModComboStates;
import dev.xingguang.terminalblade.registry.ModEntities;
import dev.xingguang.terminalblade.registry.ModItems;
import dev.xingguang.terminalblade.registry.ModParticles;
import dev.xingguang.terminalblade.registry.ModSlashArts;
import dev.xingguang.terminalblade.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import dev.xingguang.terminalblade.client.MistletoePlayerAnimations;
import dev.xingguang.terminalblade.client.TerminalBladeClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TerminalBlade.MOD_ID)
public class TerminalBlade {
    public static final String MOD_ID = "mistletoe";
    public static final Logger LOGGER = LogManager.getLogger("TerminalBlade");

    public TerminalBlade(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, TerminalBladeConfig.SPEC);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModComboStates.COMBO_STATES.register(modBus);
        ModSlashArts.SLASH_ARTS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(TerminalBladeClient::registerParticles);
            modBus.addListener(TerminalBladeClient::registerEntityRenderers);
            modBus.addListener(MistletoePlayerAnimations::install);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
