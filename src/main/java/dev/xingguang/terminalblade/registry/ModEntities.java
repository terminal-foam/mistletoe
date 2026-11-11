package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TerminalBlade.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<LumenSeishinEntity>> LUMEN_SEISHIN =
            ENTITIES.register("lumen_seishin", () -> EntityType.Builder
                    .of(LumenSeishinEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("lumen_seishin"));

    private ModEntities() {
    }
}
