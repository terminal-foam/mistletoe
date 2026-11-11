package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * 流明·星专用的玩家模型：在原版 {@code setupAnim} 之后套上 VMD 骨骼变换。
 *
 * <p>之所以不直接用 {@code PlayerModel}，是因为 player-animation-lib 的逐骨骼动画只认
 * {@code AbstractClientPlayer}；这里在模型层自己把 {@code AnimationStack} 的变换写上去。</p>
 *
 * <p>注意：{@code PlayerModel} 的手臂/腿/身体都是「内层 + 外层皮肤」两层，原版
 * {@code setupAnim} 会在最后把外层 {@code copyFrom} 内层。我们在那之后再改内层，
 * 就必须把外层重新 copy 一次，否则外层会停在原姿势——看起来就是多出来一条手臂。</p>
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
        syncOuterLayers();
    }

    /** 内层骨骼被 VMD 改动后，把外层皮肤层同步过去。 */
    private void syncOuterLayers() {
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }
}
