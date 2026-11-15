package dev.portih.training;

import com.mojang.blaze3d.platform.InputConstants;
import dev.portih.Draw;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Тренировочный манекен: НЕ настоящая сущность, никакого сетевого пакета не отправляется и на сервере
 * манекен не существует - это просто неподвижная точка в мире, нарисованная только в вашем HUD (как
 * ESP/BaseFinder, через 2D-проекцию). По манекену нельзя ударить "по-настоящему": удар засчитывается,
 * только если ваш собственный клик (ЛКМ) совпал по времени с тем, что прицел смотрел точно в манекен.
 * Это чисто ваша статистика клика и прицеливания, она ни на кого не действует.
 */
public final class TrainingDummy {
    private TrainingDummy() {}

    public static boolean enabled = false;
    public static float distance = 3.0f;     // на каком расстоянии ставится манекен
    public static float size = 0.9f;         // половина стороны куба-мишени

    private static Vec3 pos;                 // мировые координаты манекена
    private static boolean wasAttack;
    private static long lastHitMs, lastMissMs;
    private static int hits, misses;
    private static final Deque<Long> clickTimes = new ArrayDeque<>();   // для CPS за последнюю секунду

    public static KeyMapping resetKey;

    public static void init(KeyMapping.Category category) {
        resetKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portihmod.dummy_reset", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, category));
    }

    public static void setEnabled(Minecraft mc, boolean on) {
        enabled = on;
        if (on) place(mc);
        else pos = null;
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("Манекен: " + (on ? "ВКЛ" : "ВЫКЛ"))
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        }
    }

    /** Ставит манекен перед вами на заданном расстоянии; сбрасывает статистику. */
    public static void place(Minecraft mc) {
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getLookAngle();
        pos = eye.add(look.scale(distance));
        hits = 0;
        misses = 0;
        clickTimes.clear();
    }

    public static void tick(Minecraft mc) {
        while (resetKey.consumeClick()) if (enabled) place(mc);
        if (!enabled || mc.player == null || pos == null) {
            wasAttack = false;
            return;
        }
        long handle = GLFW.glfwGetCurrentContext();
        boolean attack = handle != 0L
                && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (attack && !wasAttack) registerClick(mc);
        wasAttack = attack;

        long now = System.currentTimeMillis();
        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > 1000L) clickTimes.pollFirst();
    }

    /** Один клик: проверяем, смотрит ли прицел точно в куб манекена (как обычный raycast по AABB). */
    private static void registerClick(Minecraft mc) {
        long now = System.currentTimeMillis();
        clickTimes.addLast(now);
        Vec3 eye = mc.player.getEyePosition(1f);
        Vec3 dir = mc.player.getLookAngle();
        boolean hit = rayHitsCube(eye, dir, pos, size) && eye.distanceTo(pos) <= distance + 1.0;
        if (hit) {
            hits++;
            lastHitMs = now;
        } else {
            misses++;
            lastMissMs = now;
        }
    }

    /** Пересечение луча с кубом половинной стороной half вокруг center (классический slab-тест). */
    private static boolean rayHitsCube(Vec3 origin, Vec3 dir, Vec3 center, float half) {
        double tmin = -1e9, tmax = 1e9;
        double[] o = {origin.x, origin.y, origin.z}, d = {dir.x, dir.y, dir.z}, c = {center.x, center.y, center.z};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (o[i] < c[i] - half || o[i] > c[i] + half) return false;
            } else {
                double t1 = (c[i] - half - o[i]) / d[i];
                double t2 = (c[i] + half - o[i]) / d[i];
                if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return false;
            }
        }
        return tmax >= 0;
    }

    public static int cps() {
        return clickTimes.size();
    }

    public static int accuracy() {
        int total = hits + misses;
        return total == 0 ? 100 : Math.round(hits * 100f / total);
    }

    // ------------------------------------------------------------------ рендер

    /** Своя лёгкая проекция (независимая от BaseFinderRender), чтобы модуль ничего не завязывал на поиск построек. */
    public static void render(GuiGraphics g, Minecraft mc, DeltaTracker delta) {
        if (!enabled || pos == null || mc.player == null || mc.level == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        float pt = delta.getGameTimeDeltaPartialTick(false);
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        Vec3 eye = mc.player.getEyePosition(pt);
        double yaw = Math.toRadians(mc.player.getViewYRot(pt));
        double pitch = Math.toRadians(mc.player.getViewXRot(pt));
        double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = (double) sw / sh;

        double dx = pos.x - eye.x, dy = pos.y - eye.y, dz = pos.z - eye.z;
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.1) return;
        double side = dx * rx + dz * rz;
        double up = dx * ux + dy * uy + dz * uz;
        float px = (float) (sw * 0.5 + side / depth * (sw / 2.0 / (tan * aspect)));
        float py = (float) (sh * 0.5 - up / depth * (sh / 2.0 / tan));

        long now = System.currentTimeMillis();
        boolean flashHit = now - lastHitMs < 180L;
        boolean flashMiss = now - lastMissMs < 180L;
        int col = flashHit ? 0xFF4CD964 : flashMiss ? 0xFFFF4D4D : 0xFF6C5CE7;
        float screenSize = (float) Math.max(10.0, Math.min(140.0, size * 2.0 * sh / (depth * 2.0 * tan)));

        Draw.squareOutline(g, px, py, screenSize / 2f, 0f, 2f, col);
        Draw.roundRect(g, Math.round(px - 3), Math.round(py - 3), 6, 6, 3, col);

        // статистика над манекеном
        String line1 = "CPS " + cps() + "   Точность " + accuracy() + "%";
        int tw1 = mc.font.width(line1);
        g.drawString(mc.font, line1, Math.round(px - tw1 / 2f), Math.round(py - screenSize / 2f - 16), 0xFFF2F2F7, true);
    }
}
