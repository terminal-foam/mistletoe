package dev.xingguang.terminalblade.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenAction;
import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 流明·星的客户端 VMD 动画桥。
 *
 * <p>player-animation-lib 的 {@code AnimationStack} 只挂在玩家身上，所以这里自己维护
 * 每个 Boss 一个栈：服务端把当前招式同步到 {@code EntityDataAccessor}，客户端看到
 * 序号变化就压入对应帧段的 {@code VmdAnimation}，再由 {@link LumenPlayerModel} 把骨骼
 * 变换写回 {@code PlayerModel}。</p>
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class LumenAnimClient {
    private static final ResourceLocation PLAYER_MOTION =
            ResourceLocation.fromNamespaceAndPath("slashblade", "model/pa/player_motion.vmd");
    private static final Map<LumenSeishinEntity, AnimationStack> STACKS = new WeakHashMap<>();
    private static final Map<LumenSeishinEntity, Integer> LAST_SEQ = new WeakHashMap<>();

    private LumenAnimClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LumenSeishinEntity boss)) {
                continue;
            }
            AnimationStack stack = STACKS.computeIfAbsent(boss, ignored -> new AnimationStack());
            Integer last = LAST_SEQ.get(boss);
            int seq = boss.getAnimSeq();
            if (last == null || last != seq) {
                LAST_SEQ.put(boss, seq);
                pushAction(stack, boss.getAction());
            }
            stack.tick();
        }
    }

    private static void pushAction(AnimationStack stack, LumenAction action) {
        if (!action.isAnimated()) {
            return;
        }
        VmdAnimation animation = new VmdAnimation(PLAYER_MOTION, action.start(), action.end(), false);
        stack.addAnimLayer(0, animation.getClone());
    }

    /** 在原版姿势之后把 VMD 的旋转 / 位移 / 缩放写回模型骨骼。 */
    static void applyBones(LumenSeishinEntity boss, PlayerModel<LumenSeishinEntity> model, float partialTick) {
        AnimationStack stack = STACKS.get(boss);
        if (stack == null || !stack.isActive()) {
            return;
        }
        applyPart(stack, "head", model.head, partialTick);
        applyPart(stack, "torso", model.body, partialTick);
        applyPart(stack, "leftArm", model.leftArm, partialTick);
        applyPart(stack, "rightArm", model.rightArm, partialTick);
        applyPart(stack, "leftLeg", model.leftLeg, partialTick);
        applyPart(stack, "rightLeg", model.rightLeg, partialTick);
    }

    /**
     * 只套旋转。
     *
     * <p>VMD 的「位移」分量是 MMD 骨骼空间里的偏移，而 MC 的 {@code HumanoidModel} 里四肢与身体是
     * 兄弟节点（MMD 里手臂挂在「上半身」下），直接套 position 会把肢体推离身体、看起来断开。
     * 先只取旋转，位移/缩放都不动。</p>
     */
    private static void applyPart(AnimationStack stack, String bone, ModelPart part, float partialTick) {
        Vec3f rotation = stack.get3DTransform(bone, TransformType.ROTATION, partialTick,
                new Vec3f(part.xRot, part.yRot, part.zRot));
        part.setRotation((Float) rotation.getX(), (Float) rotation.getY(), (Float) rotation.getZ());
    }
}
