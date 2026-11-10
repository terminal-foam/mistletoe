package dev.xingguang.terminalblade.client;

import java.util.function.Supplier;

/** Do not mistake an uninitialised animation table for the legacy format. */
public enum AnimationTableFormat {
    SUPPLIER, LEGACY;

    public static AnimationTableFormat detect(Object sample, Class<?> legacyType) {
        if (sample instanceof Supplier<?>) {
            return SUPPLIER;
        }
        if (legacyType.isInstance(sample)) {
            return LEGACY;
        }
        throw new IllegalStateException("SlashBlade animation table is missing or has an unsupported value type");
    }
}
