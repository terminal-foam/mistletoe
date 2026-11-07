package dev.xingguang.terminalblade.combat;

/**
 * 施法冷却用游戏时间比较。没有上一次记录时不算冷却，
 * 避免世界刚创建、gameTime 仍小于冷却长度时第一次施法被拦住。
 */
public final class MistletoeCastGate {
    private MistletoeCastGate() {
    }

    public static boolean isCoolingDown(boolean hasPrevious, long previousTick, long now, long cooldownTicks) {
        if (!hasPrevious || cooldownTicks <= 0L) {
            return false;
        }
        return now - previousTick < cooldownTicks;
    }
}
