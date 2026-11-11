package dev.xingguang.terminalblade.combat;

/** Version policy shared by logical combo timing and client animation playback. */
public final class FastComboTiming {
    private FastComboTiming() {}

    public static float speedForVersion(int major, int minor, int patch) {
        return major > 2 || (major == 2 && (minor > 1 || (minor == 1 && patch >= 9)))
                ? 2.0F : 1.0F;
    }
}
