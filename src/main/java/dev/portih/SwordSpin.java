package dev.portih;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;

public final class SwordSpin {
    private static long startMs = 0L;
    private static boolean wasDown;

    private SwordSpin() {}

    /** Вызывается каждый клиентский тик: ловит удар (нажатие атаки). */
    public static void tick(Minecraft mc) {
        Config c = Config.get();
        if (mc.player == null) {
            wasDown = false;
            return;
        }
        boolean down = mc.screen == null && mc.options.keyAttack.isDown();
        if (down && !wasDown) {
            if (!c.spinOnlyOnTarget || mc.hitResult instanceof EntityHitResult) {
                startMs = System.currentTimeMillis();
            }
        }
        wasDown = down;
    }

    /** Текущий угол поворота меча в градусах. */
    public static float angle() {
        Config c = Config.get();
        long now = System.currentTimeMillis();

        if (c.spinMode == 1) {
            return ((now % 3_600_000L) / 1000f) * c.spinSpeed * 360f % 360f;
        }

        if (startMs == 0L) return 0f;
        float dur = Math.max(0.05f, c.spinDuration) * 1000f;
        float t = (now - startMs) / dur;
        if (t >= 1f) return 0f;
        float eased = 1f - (1f - t) * (1f - t);
        return eased * 360f * Math.max(1, c.spinTurns);
    }
}
