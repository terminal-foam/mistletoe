package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, TerminalBlade.MOD_ID);

    public static final DeferredHolder<Item, Item> LUMEN_SEISHIN_SPAWN_EGG = ITEMS.register("lumen_seishin_spawn_egg",
            () -> new SpawnEggItem(ModEntities.LUMEN_SEISHIN.get(), 0x9FD8FF, 0x2A3B66, new Item.Properties()));

    public static final DeferredHolder<Item, Item> LUMEN_COMPANION_SPAWN_EGG = ITEMS.register("lumen_companion_spawn_egg",
    () -> new SpawnEggItem(ModEntities.LUMEN_COMPANION.get(), 0xE0C4FF, 0x3A2A66, new Item.Properties()));

    private ModItems() {
    }
}
