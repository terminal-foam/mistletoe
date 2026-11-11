package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * M1 占位渲染：玩家比例的人形模型 + 原版皮肤，手里由 {@code ItemInHandLayer} 渲染拔刀剑 OBJ。
 * 正式版会换成流明·星自己的皮肤 / 模型。
 */
public class LumenSeishinRenderer extends HumanoidMobRenderer<LumenSeishinEntity, PlayerModel<LumenSeishinEntity>> {
    private static final ResourceLocation PLACEHOLDER_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public LumenSeishinRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(LumenSeishinEntity entity) {
        return PLACEHOLDER_SKIN;
    }
}
