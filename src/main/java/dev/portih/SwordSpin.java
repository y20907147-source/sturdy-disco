package dev.portih;

public final class SwordSpin {
    /** Сколько раз сработал патч руки (для диагностики в меню). */
    public static long handRenders = 0L;

    private static long seenStamp = 0L;
    private static long animStart = 0L;
    private static float animDurMs = 0f;
    private static int animTurns = 1;

    private SwordSpin() {}

    /** Текущий угол поворота меча в градусах. */
    public static float angle() {
        Config c = Config.get();
        long now = System.currentTimeMillis();

        if (c.spinMode == 1) {
            return ((now % 3_600_000L) / 1000f) * c.spinSpeed * 360f % 360f;
        }

        // Новый удар/клик НЕ перезапускает анимацию, пока она идёт:
        // иначе при частых ударах меч дёргается и не доворачивается до конца.
        long stamp = (c.spinTrigger == 0) ? HitTracker.lastHitMs : HitTracker.lastClickMs;
        if (stamp != 0L && stamp != seenStamp) {
            seenStamp = stamp;
            boolean running = animStart != 0L && (now - animStart) < animDurMs;
            if (!running) {
                animStart = stamp;
                animDurMs = Math.max(0.05f, c.spinDuration) * 1000f;
                animTurns = Math.max(1, c.spinTurns);
            }
        }
        if (animStart == 0L) return 0f;

        float t = (now - animStart) / animDurMs;
        if (t >= 1f) return 0f;

        float inv = 1f - t;
        float eased = 1f - inv * inv * inv; // easeOutCubic: быстрый старт, мягкая остановка
        return eased * 360f * animTurns;
    }
}
