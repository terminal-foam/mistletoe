package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenCompanionEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 随从形态复用同一套 YSM 资源（模型 / 贴图 / 动画）。 */
public class LumenCompanionGeoModel extends GeoModel<LumenCompanionEntity> {
    private static final ResourceLocation MODEL = TerminalBlade.id("geo/lumen_seishin.geo.json");
    private static final ResourceLocation TEXTURE = TerminalBlade.id("textures/entity/lumen_seishin_ysm.png");
    private static final ResourceLocation ANIMATION = TerminalBlade.id("animations/lumen_seishin.animation.json");
    private static final ResourceLocation MOTION = TerminalBlade.id("animations/lumen_seishin_motion.animation.json");

    @Override
    public ResourceLocation getModelResource(LumenCompanionEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LumenCompanionEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LumenCompanionEntity animatable) {
        return ANIMATION;
    }

    @Override
    public ResourceLocation[] getAnimationResourceFallbacks(LumenCompanionEntity animatable) {
        return new ResourceLocation[]{MOTION};
    }
}
