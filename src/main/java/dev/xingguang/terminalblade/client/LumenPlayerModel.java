package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * 流明·星专用的玩家模型：在原版 {@code setupAnim} 之后套上 VMD 骨骼变换。
 *
 * <p>之所以不直接用 {@code PlayerModel}，是因为 player-animation-lib 的逐骨骼动画只认
 * {@code AbstractClientPlayer}；这里在模型层自己把 {@code AnimationStack} 的变换写上去。</p>
 */
public class LumenPlayerModel extends PlayerModel<LumenSeishinEntity> {
    public LumenPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(LumenSeishinEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // 原版姿势算完之后再覆盖，否则会被 setupAnim 重置。
        LumenAnimClient.applyBones(entity, this, ageInTicks - entity.tickCount);
    }
}
