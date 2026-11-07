package dev.xingguang.terminalblade.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MistletoeCastGateTest {
    @Test
    void firstCastAtTheStartOfTheWorldIsNotCoolingDown() {
        for (long now : new long[] {0L, 1L, 7L}) {
            assertFalse(MistletoeCastGate.isCoolingDown(false, 0L, now, 8L));
        }
    }

    @Test
    void repeatInsideTheCooldownIsBlocked() {
        assertTrue(MistletoeCastGate.isCoolingDown(true, 0L, 7L, 8L));
        assertFalse(MistletoeCastGate.isCoolingDown(true, 0L, 8L, 8L));
    }
}
