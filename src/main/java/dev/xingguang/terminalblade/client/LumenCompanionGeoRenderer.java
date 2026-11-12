package dev.xingguang.terminalblade.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xingguang.terminalblade.entity.LumenCompanionEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LumenCompanionGeoRenderer extends GeoEntityRenderer<LumenCompanionEntity> {
    /** 与 Boss 一致：YSM 模型自带的 0.7423 缩放。 */
    private static final float MODEL_SCALE = 0.7423F;

    public LumenCompanionGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new LumenCompanionGeoModel());
        this.shadowRadius = 0.4F;
    }

    @Override
    public void preRender(PoseStack poseStack, LumenCompanionEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender) {
            poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }
}
