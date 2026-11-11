package dev.xingguang.terminalblade.combat;

/** A single cast's ordered, once-only hit window, measured in server game ticks. */
public final class FrostCastWindow {
    private final long startedAt;
    private final int firstTick;
    private final int interval;
    private final int hitCount;
    private int nextHit;

    public FrostCastWindow(long startedAt, int firstTick, int interval, int hitCount) {
        this.startedAt = startedAt;
        this.firstTick = firstTick;
        this.interval = interval;
        this.hitCount = hitCount;
    }

    public boolean claim(long now, int index) {
        if (index != nextHit || expired(now)
                || now - startedAt != firstTick + (long) index * interval) {
            return false;
        }
        nextHit++;
        return true;
    }

    public boolean expired(long now) {
        return nextHit >= hitCount || now < startedAt
                || now - startedAt > firstTick + (long) nextHit * interval;
    }
}
