package dev.xingguang.terminalblade.client;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationTableFormatTest {
    private static final class LegacyAnimation {}

    @Test
    void identifiesBothSupportedFormats() {
        assertEquals(AnimationTableFormat.LEGACY,
                AnimationTableFormat.detect(new LegacyAnimation(), LegacyAnimation.class));
        assertEquals(AnimationTableFormat.SUPPLIER,
                AnimationTableFormat.detect((Supplier<LegacyAnimation>) LegacyAnimation::new, LegacyAnimation.class));
    }

    @Test
    void missingOrUnknownEntryMustNotSelectLegacyFormat() {
        assertThrows(IllegalStateException.class, () -> AnimationTableFormat.detect(null, LegacyAnimation.class));
        assertThrows(IllegalStateException.class, () -> AnimationTableFormat.detect(new Object(), LegacyAnimation.class));
    }
}
