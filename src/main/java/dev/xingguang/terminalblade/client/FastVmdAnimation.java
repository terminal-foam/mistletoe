package dev.xingguang.terminalblade.client;

import mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation;
import net.minecraft.resources.ResourceLocation;

/**
 * 按倍率播放的 {@link VmdAnimation}。
 *
 * <p>2.0.7 的 VmdAnimation 每 tick 固定 {@code currentTick++}，没有速度字段；这里在每次
 * player-anim 调用 {@code tick()} 时多推进几次 {@code super.tick()}，把动作播放速度提高到
 * {@code speed} 倍。player-anim 的 {@code AnimationStack.tick()} 每客户端 tick 只调一次
 * {@code tick()}，所以多推进的量就是实际倍率。</p>
 *
 * <p>倍率要和 {@code ComboState.speed} 保持一致，命中 tick 也按同样倍率收紧，
 * 否则伤害会重新和身体动作脱节。</p>
 */
public class FastVmdAnimation extends VmdAnimation {
    private final ResourceLocation motion;
    private final double startFrame;
    private final double endFrame;
    private final double speed;
    private double carry;

    public FastVmdAnimation(ResourceLocation motion, double startFrame, double endFrame, double speed) {
        super(motion, startFrame, endFrame, false);
        this.motion = motion;
        this.startFrame = startFrame;
        this.endFrame = endFrame;
        this.speed = speed;
    }

    @Override
    public VmdAnimation getClone() {
        return new FastVmdAnimation(motion, startFrame, endFrame, speed);
    }

    @Override
    public void tick() {
        if (!isActive()) {
            return;
        }
        super.tick();
        if (!isActive()) {
            return;
        }
        carry += speed - 1.0D;
        while (carry >= 1.0D && isActive()) {
            super.tick();
            carry -= 1.0D;
        }
    }
}
