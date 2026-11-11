package dev.xingguang.terminalblade.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FastComboTimingTest {
    @Test
    void legacyVersionsUseTheSameOneTimesTimingAsTheirAnimations() {
        assertEquals(1.0F, FastComboTiming.speedForVersion(2, 0, 7));
        assertEquals(1.0F, FastComboTiming.speedForVersion(2, 1, 8));
    }

    @Test
    void currentAnimationApiEnablesTwoTimesTiming() {
        assertEquals(2.0F, FastComboTiming.speedForVersion(2, 1, 9));
    }
}
