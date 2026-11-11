package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LumenGeoRenderer extends GeoEntityRenderer<LumenSeishinEntity> {
    public LumenGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new LumenGeoModel());
        this.shadowRadius = 0.5F;
    }
}
