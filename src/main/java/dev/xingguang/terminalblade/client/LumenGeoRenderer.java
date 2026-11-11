package dev.xingguang.terminalblade.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LumenGeoRenderer extends GeoEntityRenderer<LumenSeishinEntity> {
    /**
     * YSM 模型自带的缩放（ysm.json 的 height_scale / width_scale）。
     * 模型自然高度 38.8 单位 = 2.425 格，乘 0.7423 正好 1.8 格，与碰撞箱一致。
     * GeckoLib 不会读 ysm.json，必须自己补上，否则会大三分之一。
     */
    private static final float MODEL_SCALE = 0.7423F;

    public LumenGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new LumenGeoModel());
        this.shadowRadius = 0.4F;
    }

    @Override
    public void preRender(PoseStack poseStack, LumenSeishinEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        // 只在主渲染缩放一次；渲染层（isReRender）会再进这里，不能重复缩放
        if (!isReRender) {
            poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }
}
