package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 流明·星的玩家比例人形渲染。皮肤为 {@code textures/entity/lumen_seishin.png}，
 * 手里由 {@code ItemInHandLayer} 渲染拔刀剑 OBJ。
 */
public class LumenSeishinRenderer extends HumanoidMobRenderer<LumenSeishinEntity, PlayerModel<LumenSeishinEntity>> {
    private static final ResourceLocation SKIN =
            TerminalBlade.id("textures/entity/lumen_seishin.png");

    public LumenSeishinRenderer(EntityRendererProvider.Context context) {
        // 经典（4px 手臂）模型；如果皮肤是 Alex 瘦手臂，把 false 改成 true。
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(LumenSeishinEntity entity) {
        return SKIN;
    }
}
