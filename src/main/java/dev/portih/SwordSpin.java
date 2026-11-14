package dev.portih;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;

public final class SwordSpin {
    private static long startMs = 0L;
    private static boolean wasDown;
    private static boolean wasSwinging;

    private SwordSpin() {}

    /**
     * Ловит удар: нажатие атаки ИЛИ начало замаха руки.
     * Вызывается каждый тик и каждый кадр отрисовки меча.
     */
    public static void tick(Minecraft mc) {
        Config c = Config.get();
        LocalPlayer player = mc.player;
        if (player == null) {
            wasDown = false;
            wasSwinging = false;
            return;
        }

        boolean down = mc.screen == null && mc.options.keyAttack.isDown();
        boolean swinging = player.swinging;

        boolean fired = (down && !wasDown) || (swinging && !wasSwinging);
        wasDown = down;
        wasSwinging = swinging;

        if (!fired) return;
        if (c.spinOnlyOnTarget && !(mc.hitResult instanceof EntityHitResult)) return;

        startMs = System.currentTimeMillis();
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
