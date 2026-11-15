package dev.portih;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Крутящийся маркер на таргете.
 * Стили: 0 - квадрат, 1 - двойной квадрат, 2 - квадрат с уголками-прицелом,
 * 3 - 3D-квадрат, который скользит вверх-вниз вокруг тела цели.
 */
public final class TargetMarker {
    public static final String[] STYLE_NAMES = {"Квадрат", "Двойной", "Прицел", "3D вокруг тела"};

    private static final long T0 = System.nanoTime();
    private static final float TAU = (float) (Math.PI * 2.0);

    private static final float[] SX = new float[4];
    private static final float[] SY = new float[4];
    private static final float[] P = new float[2];

    private static LivingEntity tracked;
    private static long bornMs;

    private TargetMarker() {}

    /** Секунды с запуска: от них зависит вращение (не зависит от FPS). */
    public static float time() {
        return (System.nanoTime() - T0) / 1_000_000_000f;
    }

    // ------------------------------------------------------------- на цели

    /**
     * @param box  {minX, minY, maxX, maxY} рамки цели на экране
     * @param cam  камера из Esp (нужна для 3D-стиля)
     * @param life 1 = цель активна, к 0 затухает, когда цель потеряна
     */
    public static void draw(GuiGraphics g, Config c, LivingEntity e, float[] box, double[] cam,
                            float pt, float life) {
        long now = System.currentTimeMillis();
        if (e != tracked) {
            tracked = e;
            bornMs = now;
        }
        float appear = Draw.easeOutBack((now - bornMs) / 380f);   // выплывает с лёгким перелётом
        float alpha = Draw.clamp01(appear) * Draw.clamp01(life);
        if (alpha < 0.02f) return;

        float pulse = 0f;
        if (HitTracker.lastHitEntity == e) {
            long age = now - HitTracker.lastHitMs;
            if (age < 280L) pulse = 1f - age / 280f;
        }

        int col = c.markHealthColor ? healthColor(e) : Theme.accent();
        col = Draw.lerpColor(col, 0xFFFFFFFF, pulse * 0.75f);

        float ang = time() * TAU * c.markSpeed;
        float scaleK = 0.55f + 0.45f * Math.min(1.15f, appear);

        if (c.markStyle == 3) {
            drawWorld(g, c, e, cam, pt, col, alpha, ang, scaleK, pulse);
            return;
        }

        float boxH = box[3] - box[1];
        float half = Math.max(8f, Math.min(70f, boxH * 0.28f)) * c.markSize * scaleK * (1f + 0.22f * pulse);
        float cx = (box[0] + box[2]) * 0.5f;
        float cy = box[1] + boxH * 0.45f;
        drawFlat(g, c.markStyle, cx, cy, half, ang, col, alpha, c.quality);
    }

    private static int healthColor(LivingEntity e) {
        float ratio = Draw.clamp01(e.getHealth() / Math.max(1f, e.getMaxHealth()));
        return Draw.lerpColor(Theme.danger(), Theme.accent(), Draw.clamp01((ratio - 0.2f) / 0.6f));
    }

    // -------------------------------------------------------------- стили 0-2

    private static void drawFlat(GuiGraphics g, int style, float cx, float cy, float half, float ang,
                                 int col, float alpha, int q) {
        int thick = half > 30f ? 2 : 1;

        // мягкое свечение
        if (q >= 1) Draw.squareFill(g, cx, cy, half * 0.92f, ang, Draw.fade(col, 0.10f * alpha));
        if (q >= 2) Draw.squareFill(g, cx, cy, half * 1.30f, ang * 0.5f, Draw.fade(col, 0.05f * alpha));

        switch (style) {
            case 1 -> {
                if (q >= 2) Draw.squareOutline(g, cx, cy, half, ang - 0.28f, thick, Draw.fade(col, 0.22f * alpha));
                Draw.squareOutline(g, cx, cy, half, ang, thick, Draw.fade(col, alpha));
                Draw.squareOutline(g, cx, cy, half * 0.60f, -ang * 1.4f, thick, Draw.fade(col, 0.85f * alpha));
                corners(g, cx, cy, half, ang, alpha);
            }
            case 2 -> {
                float pulse = 1f + 0.06f * (float) Math.sin(time() * 3.0);
                Draw.squareOutline(g, cx, cy, half * 0.78f, ang, thick, Draw.fade(col, alpha));
                float b = half * 1.25f * pulse;
                int len = Math.max(3, Math.round(half * 0.42f));
                Draw.corners(g, Math.round(cx - b), Math.round(cy - b), Math.round(cx + b), Math.round(cy + b),
                        len, thick, Draw.fade(col, 0.9f * alpha));
            }
            default -> {
                if (q >= 2) {
                    Draw.squareOutline(g, cx, cy, half, ang - 0.30f, thick, Draw.fade(col, 0.16f * alpha));
                    Draw.squareOutline(g, cx, cy, half, ang - 0.15f, thick, Draw.fade(col, 0.34f * alpha));
                }
                Draw.squareOutline(g, cx, cy, half, ang, thick, Draw.fade(col, alpha));
                corners(g, cx, cy, half, ang, alpha);
            }
        }
        // точка в центре
        Draw.squareFill(g, cx, cy, 1f, 0f, Draw.fade(0xFFFFFFFF, 0.9f * alpha));
    }

    /** Четыре яркие точки в углах вращающегося квадрата. */
    private static void corners(GuiGraphics g, float cx, float cy, float half, float ang, float alpha) {
        float cs = (float) Math.cos(ang);
        float sn = (float) Math.sin(ang);
        int dot = Draw.fade(0xFFFFFFFF, 0.95f * alpha);
        for (int i = 0; i < 4; i++) {
            float ox = (i & 1) == 0 ? -half : half;
            float oy = (i & 2) == 0 ? -half : half;
            Draw.squareFill(g, cx + ox * cs - oy * sn, cy + ox * sn + oy * cs, 1f, 0f, dot);
        }
    }

    // ------------------------------------------------------------ стиль 3 (3D)

    private static void drawWorld(GuiGraphics g, Config c, LivingEntity e, double[] cam, float pt, int col,
                                  float alpha, float ang, float scaleK, float pulse) {
        Vec3 pos = e.getPosition(pt);
        double rad = Math.max(0.55, e.getBbWidth() * 1.05) * c.markSize * scaleK * (1.0 + 0.2 * pulse);
        double h = e.getBbHeight();
        float t = time();
        int levels = c.quality == 0 ? 1 : 3;

        for (int k = 0; k < levels; k++) {
            float lvl = 0.5f + 0.5f * (float) Math.sin(t * 1.8f - k * 0.38f);   // 0..1 вдоль тела
            double y = pos.y + h * (0.04 + 0.92 * lvl);
            float a = alpha * (k == 0 ? 1f : (k == 1 ? 0.34f : 0.15f));

            boolean ok = true;
            for (int i = 0; i < 4 && ok; i++) {
                double an = ang + Math.PI / 4.0 + i * (Math.PI / 2.0);
                ok = Esp.projectPoint(pos.x + Math.cos(an) * rad, y, pos.z + Math.sin(an) * rad, cam, P);
                SX[i] = P[0];
                SY[i] = P[1];
            }
            if (!ok) continue;

            int lc = Draw.fade(col, a);
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3;
                Draw.line(g, SX[i], SY[i], SX[j], SY[j], k == 0 ? 2f : 1f, lc);
            }
            if (k == 0) {
                int dot = Draw.fade(0xFFFFFFFF, 0.95f * a);
                for (int i = 0; i < 4; i++) Draw.squareFill(g, SX[i], SY[i], 1f, 0f, dot);
            }
        }
    }

    // ------------------------------------------------------- превью в меню

    /** Мини-превью для меню: силуэт цели и выбранный маркер (без 3D-проекции мира). */
    public static void drawPreview(GuiGraphics g, Config c, int cx, int cy, int size) {
        // силуэт
        int body = Theme.withAlpha(Theme.muted() & 0xFFFFFF, 0.55f);
        float k = size / 22f;
        int hw = Math.max(2, Math.round(3.5f * k));
        g.fill(cx - hw, cy - Math.round(15 * k), cx + hw, cy - Math.round(8 * k), body);              // голова
        g.fill(cx - Math.round(5 * k), cy - Math.round(7 * k), cx + Math.round(5 * k), cy + Math.round(5 * k), body); // тело
        g.fill(cx - Math.round(5 * k), cy + Math.round(5 * k), cx - 1, cy + Math.round(15 * k), body);   // ноги
        g.fill(cx + 1, cy + Math.round(5 * k), cx + Math.round(5 * k), cy + Math.round(15 * k), body);

        float t = time();
        float ang = t * TAU * c.markSpeed;
        int col = Theme.accent();
        float half = Math.min(18f, size * 0.62f * c.markSize);

        if (c.markStyle == 3) {
            int levels = c.quality == 0 ? 1 : 3;
            for (int lv = 0; lv < levels; lv++) {
                float l = 0.5f + 0.5f * (float) Math.sin(t * 1.8f - lv * 0.38f);
                float y = cy + size * 0.75f - l * size * 1.5f;
                float a = lv == 0 ? 1f : (lv == 1 ? 0.34f : 0.15f);
                for (int i = 0; i < 4; i++) {
                    double a1 = ang + Math.PI / 4.0 + i * (Math.PI / 2.0);
                    double a2 = a1 + Math.PI / 2.0;
                    float x1 = cx + (float) Math.cos(a1) * half;
                    float y1 = y + (float) Math.sin(a1) * half * 0.38f;
                    float x2 = cx + (float) Math.cos(a2) * half;
                    float y2 = y + (float) Math.sin(a2) * half * 0.38f;
                    Draw.line(g, x1, y1, x2, y2, lv == 0 ? 2f : 1f, Draw.fade(col, a));
                }
            }
        } else {
            drawFlat(g, c.markStyle, cx, cy, half, ang, col, 1f, c.quality);
        }
    }
}
