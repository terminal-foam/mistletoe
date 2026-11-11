package dev.xingguang.terminalblade.entity;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

/**
 * 流明·星的三阶段。
 *
 * <p>阈值：生命 &gt; 240 为朝露，120 &lt; 生命 ≤ 240 为初雪，≤ 120 为暮色。
 * 阶段只前进不回退；刀与血条颜色随阶段切换。</p>
 */
public enum LumenPhase {
    DAWN("dawn", 240.0F, BossEvent.BossBarColor.BLUE),
    FIRST_SNOW("first_snow", 120.0F, BossEvent.BossBarColor.WHITE),
    MISTLETOE("mistletoe", 0.0F, BossEvent.BossBarColor.PURPLE);

    /** 朝露 / 初雪 阶段的最大生命值门槛。 */
    public static final float PHASE2_THRESHOLD = 240.0F;
    public static final float PHASE3_THRESHOLD = 120.0F;

    private final String bladePath;
    private final float nextThreshold;
    private final BossEvent.BossBarColor barColor;

    LumenPhase(String bladePath, float nextThreshold, BossEvent.BossBarColor barColor) {
        this.bladePath = bladePath;
        this.nextThreshold = nextThreshold;
        this.barColor = barColor;
    }

    public ResourceLocation bladeId() {
        return TerminalBlade.id(bladePath);
    }

    /** 生命值低于该值时进入下一阶段；0 表示已是最后一阶段。 */
    public float nextThreshold() {
        return nextThreshold;
    }

    public BossEvent.BossBarColor barColor() {
        return barColor;
    }

    public LumenPhase next() {
        return switch (this) {
            case DAWN -> FIRST_SNOW;
            case FIRST_SNOW -> MISTLETOE;
            case MISTLETOE -> MISTLETOE;
        };
    }

    public static LumenPhase byOrdinal(int ordinal) {
        LumenPhase[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DAWN;
    }

    public static LumenPhase forHealth(float health) {
        if (health > PHASE2_THRESHOLD) {
            return DAWN;
        }
        return health > PHASE3_THRESHOLD ? FIRST_SNOW : MISTLETOE;
    }
}
