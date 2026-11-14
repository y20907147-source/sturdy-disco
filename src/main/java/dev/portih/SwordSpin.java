package dev.portih;

public final class SwordSpin {
    /** Сколько раз сработал патч руки (для диагностики в меню). */
    public static long handRenders = 0L;

    private SwordSpin() {}

    /** Текущий угол поворота меча в градусах. */
    public static float angle() {
        Config c = Config.get();
        long now = System.currentTimeMillis();

        if (c.spinMode == 1) {
            return ((now % 3_600_000L) / 1000f) * c.spinSpeed * 360f % 360f;
        }

        long start = (c.spinTrigger == 0) ? HitTracker.lastHitMs : HitTracker.lastClickMs;
        if (start == 0L) return 0f;

        float dur = Math.max(0.05f, c.spinDuration) * 1000f;
        float t = (now - start) / dur;
        if (t >= 1f) return 0f;
        float eased = 1f - (1f - t) * (1f - t);
        return eased * 360f * Math.max(1, c.spinTurns);
    }
}
