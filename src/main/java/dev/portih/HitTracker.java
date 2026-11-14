package dev.portih;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Определяет клики и реальные попадания.
 * Попадание = после клика по сущности у неё началась анимация урона (hurtTime вырос).
 */
public final class HitTracker {
    public static long lastClickMs = 0L;
    public static long lastHitMs = 0L;
    public static LivingEntity lastHitEntity;

    private static LivingEntity pending;
    private static long pendingTime;
    private static int lastHurt;
    private static boolean wasDown;
    private static boolean wasSwinging;

    private HitTracker() {}

    /** Можно вызывать сколько угодно раз за кадр/тик. */
    public static void update(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            pending = null;
            wasDown = false;
            wasSwinging = false;
            return;
        }
        long now = System.currentTimeMillis();

        boolean down = mc.screen == null && mc.options.keyAttack.isDown();
        boolean swinging = player.swinging;
        boolean click = (down && !wasDown) || (swinging && !wasSwinging);
        wasDown = down;
        wasSwinging = swinging;

        if (click) {
            lastClickMs = now;
            if (mc.hitResult instanceof EntityHitResult r
                    && r.getEntity() instanceof LivingEntity le
                    && le != player) {
                pending = le;
                pendingTime = now;
                lastHurt = le.hurtTime;
            }
        }

        if (pending != null) {
            if (now - pendingTime > 700L || pending.isRemoved()) {
                pending = null;
            } else {
                int h = pending.hurtTime;
                if (h > lastHurt) {
                    LivingEntity t = pending;
                    pending = null;
                    onHit(mc, t, now);
                } else {
                    lastHurt = h;
                }
            }
        }
    }

    private static void onHit(Minecraft mc, LivingEntity target, long now) {
        lastHitMs = now;
        lastHitEntity = target;
        Effects.onHit(mc, target);
    }
}
