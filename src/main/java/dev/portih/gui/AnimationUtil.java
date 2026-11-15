package dev.portih.gui;

/** Анимации по времени кадра (не зависят от FPS). */
public final class AnimationUtil {
    private AnimationUtil() {}

    /** Экспоненциальное приближение cur -> target; speed - примерно "долей в секунду". */
    public static float approach(float cur, float target, float dt, float speed) {
        cur += (target - cur) * Math.min(1f, dt * speed);
        return Math.abs(target - cur) < 0.004f ? target : cur;
    }

    public static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Считает dt между кадрами (секунды), с ограничением, чтобы лаги не ломали анимации. */
    public static final class FrameClock {
        private long last = System.nanoTime();

        public float tick() {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;
            return Math.max(0.001f, Math.min(0.1f, dt));
        }
    }
}
