package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 流明·星的 GeckoLib 模型。直接使用 YSM 导出的 Bedrock 资源（64 骨骼 + 拔刀剑动画）。
 */
public class LumenGeoModel extends GeoModel<LumenSeishinEntity> {
    private static final ResourceLocation MODEL = TerminalBlade.id("geo/lumen_seishin.geo.json");
    private static final ResourceLocation TEXTURE = TerminalBlade.id("textures/entity/lumen_seishin_ysm.png");
    private static final ResourceLocation ANIMATION = TerminalBlade.id("animations/lumen_seishin.animation.json");

    @Override
    public ResourceLocation getModelResource(LumenSeishinEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LumenSeishinEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LumenSeishinEntity animatable) {
        return ANIMATION;
    }
}
