package dev.xingguang.terminalblade.entity;

/**
 * 流明·星的招式动画区间，复用 SlashBlade 的 {@code player_motion.vmd} 帧段。
 *
 * <p>帧区间抄自 {@code PlayerAnimationOverrider}：普攻用 combo_a1，霜刃/斩星用 400 段。</p>
 */
public enum LumenAction {
    NONE(0.0D, 0.0D),
    MELEE(1.0D, 41.0D),
    FROST(400.0D, 449.0D),
    SLASH_ART(400.0D, 488.0D),
    PHASE(400.0D, 488.0D),
    CHARGE(459.0D, 488.0D);

    private final double start;
    private final double end;

    LumenAction(double start, double end) {
        this.start = start;
        this.end = end;
    }

    public double start() {
        return start;
    }

    public double end() {
        return end;
    }

    public boolean isAnimated() {
        return this != NONE;
    }

    public static LumenAction byOrdinal(int ordinal) {
        LumenAction[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
