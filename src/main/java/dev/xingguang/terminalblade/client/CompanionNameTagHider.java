package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenCompanionEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * 去掉随从头顶的名牌。
 *
 * <p>注意：实体侧的 {@code Entity#shouldShowName()} 对生物无效 ——
 * {@code LivingEntityRenderer} 重写了 {@code shouldShowName(T)} 且不调用它，
 * 所以只能从 NeoForge 的名字牌渲染事件里直接否决。</p>
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CompanionNameTagHider {
    private CompanionNameTagHider() {
    }

    @SubscribeEvent
    public static void hideNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof LumenCompanionEntity) {
            event.setCanRender(TriState.FALSE);
        }
    }
}
