package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenCompanionEntity;
import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = TerminalBlade.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {
    private ModEntityAttributes() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.LUMEN_SEISHIN.get(), LumenSeishinEntity.createAttributes().build());
        event.put(ModEntities.LUMEN_COMPANION.get(), LumenCompanionEntity.createAttributes().build());
    }
}
