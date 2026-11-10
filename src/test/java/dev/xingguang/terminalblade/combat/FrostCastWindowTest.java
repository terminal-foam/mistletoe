package dev.xingguang.terminalblade.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrostCastWindowTest {
    @Test
    void acceptsExactlySevenHitsAtTheirScheduledTicks() {
        FrostCastWindow cast = new FrostCastWindow(100, 2, 1, 7);
        assertFalse(cast.claim(101, 0));
        for (int i = 0; i < 7; i++) {
            assertTrue(cast.claim(102 + i, i));
            assertFalse(cast.claim(102 + i, i), "duplicate hit must not deal damage");
        }
        assertTrue(cast.expired(108));
        assertFalse(cast.claim(109, 7));
    }

    @Test
    void oldCallbacksCannotConsumeANewCast() {
        FrostCastWindow cast = new FrostCastWindow(104, 2, 1, 7);
        assertFalse(cast.claim(104, 2));
        assertFalse(cast.claim(104, 0));
        assertTrue(cast.claim(106, 0));
    }

    @Test
    void skippedHitExpiresInsteadOfBurstingLater() {
        FrostCastWindow cast = new FrostCastWindow(100, 2, 1, 7);
        assertTrue(cast.expired(103));
        assertFalse(cast.claim(103, 0));
        assertFalse(cast.claim(103, 1));
    }

    @Test
    void outOfOrderCallbacksDoNotConsumeCorrectHit() {
        FrostCastWindow cast = new FrostCastWindow(100, 2, 1, 7);
        assertFalse(cast.claim(102, 1));
        assertTrue(cast.claim(102, 0));
        assertFalse(cast.claim(102, 1));
        assertTrue(cast.claim(103, 1));
    }

    @Test
    void resetWorldClockDoesNotResumeCast() {
        FrostCastWindow cast = new FrostCastWindow(100, 2, 1, 7);
        assertTrue(cast.expired(0));
        assertFalse(cast.claim(0, 0));
    }
}
