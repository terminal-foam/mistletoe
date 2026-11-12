package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 把本模组的物品登记到创造模式物品栏。
 *
 * <p>之前完全没有这段代码，所以两个刷怪蛋在任何创造页里都找不到。
 * 现在放进原版「刷怪蛋」页；{@code accept} 的目标是去重集合，重复登记不会出现两个。</p>
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    @SubscribeEvent
    public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.SPAWN_EGGS) {
            return;
        }
        add(event, ModItems.LUMEN_SEISHIN_SPAWN_EGG.get());
        add(event, ModItems.LUMEN_COMPANION_SPAWN_EGG.get());
    }

    private static void add(BuildCreativeModeTabContentsEvent event, Item item) {
        event.accept(new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
