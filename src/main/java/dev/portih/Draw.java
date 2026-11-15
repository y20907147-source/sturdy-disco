package dev.portih;

import net.minecraft.client.gui.GuiGraphics;

/** Помощники для мягкого минималистичного вида: сглаженные скругления, плавные переходы. */
public final class Draw {
    private Draw() {}

    // ------------------------------------------------------------- анимация

    public static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    public static float easeOut(float t) {
        t = clamp01(t);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** Мягкий "выплыв" с лёгким перелётом. */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.4f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Плавное приближение значения к цели (не зависит от FPS). */
    public static float approach(float cur, float target, float dt, float speed) {
        cur += (target - cur) * Math.min(1f, dt * speed);
        return Math.abs(target - cur) < 0.004f ? target : cur;
    }

    // ----------------------------------------------------------------- цвета

    public static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t);
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** Умножает альфу цвета на k (0..1). */
    public static int fade(int color, float k) {
        int a = (color >>> 24) & 0xFF;
        int na = Math.max(0, Math.min(255, Math.round(a * k)));
        return (na << 24) | (color & 0xFFFFFF);
    }

    // ----------------------------------------------------------------- формы

    /** Скруглённый прямоугольник со сглаженными краями углов. */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || ((color >>> 24) & 0xFF) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double edge = r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
            int full = (int) Math.ceil(edge);
            float cov = (float) (full - edge);
            g.fill(x + full, y + i, x + w - full, y + i + 1, color);
            g.fill(x + full, y + h - 1 - i, x + w - full, y + h - i, color);
            if (cov > 0.04f && full > 0) {
                int c2 = fade(color, cov);
                g.fill(x + full - 1, y + i, x + full, y + i + 1, c2);
                g.fill(x + w - full, y + i, x + w - full + 1, y + i + 1, c2);
                g.fill(x + full - 1, y + h - 1 - i, x + full, y + h - i, c2);
                g.fill(x + w - full, y + h - 1 - i, x + w - full + 1, y + h - i, c2);
            }
        }
        if (h - 2 * r > 0) g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Капсула: радиус = половина высоты. */
    public static void pill(GuiGraphics g, int x, int y, int w, int h, int color) {
        roundRect(g, x, y, w, h, h / 2, color);
    }

    public static void circle(GuiGraphics g, int cx, int cy, int radius, int color) {
        roundRect(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /** Тонкая рамка (используется только как подсветка в редакторе HUD). */
    public static void border(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w < 3 || h < 3) return;
        r = Math.max(1, Math.min(r, Math.min(w, h) / 2));
        int r2 = r - 1;
        int h2 = h - 2;
        for (int row = 0; row < h; row++) {
            int ci = row < r ? row : (row >= h - r ? h - 1 - row : -1);
            int o = 0;
            if (ci >= 0) {
                double dy = r - ci - 0.5;
                o = (int) Math.ceil(r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy)));
            }
            if (row == 0 || row == h - 1) {
                g.fill(x + o, y + row, x + w - o, y + row + 1, color);
                continue;
            }
            int y2 = row - 1;
            int ci2 = (r2 > 0 && y2 < r2) ? y2 : ((r2 > 0 && y2 >= h2 - r2) ? h2 - 1 - y2 : -1);
            int in = 1;
            if (ci2 >= 0) {
                double dy = r2 - ci2 - 0.5;
                in += (int) Math.ceil(r2 - Math.sqrt(Math.max(0.0, (double) r2 * r2 - dy * dy)));
            }
            in = Math.max(in, o + 1);
            g.fill(x + o, y + row, x + in, y + row + 1, color);
            g.fill(x + w - in, y + row, x + w - o, y + row + 1, color);
        }
    }

    /** Равнобедренный треугольник вершиной вверх со сглаженными боками. */
    public static void triangle(GuiGraphics g, int cx, int top, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        for (int row = 0; row < h; row++) {
            double half = (w / 2.0) * (row + 0.5) / h;
            half = Math.max(half, 0.9);
            double left = cx - half;
            double right = cx + half;
            int li = (int) Math.ceil(left);
            int ri = (int) Math.floor(right);
            g.fill(li, top + row, ri, top + row + 1, color);
            float cl = (float) (li - left);
            float cr = (float) (right - ri);
            if (cl > 0.04f) g.fill(li - 1, top + row, li, top + row + 1, fade(color, cl));
            if (cr > 0.04f) g.fill(ri, top + row, ri + 1, top + row + 1, fade(color, cr));
        }
    }

    // ----------------------------------------------------------------- панели

    /** Мягкая панель: тень и заливка, без рамки. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int r, int bg, float vis) {
        shadow(g, x, y, w, h, r, vis);
        roundRect(g, x, y, w, h, r, bg);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int r, int bg) {
        panel(g, x, y, w, h, r, bg, 1f);
    }

    /** Очень лёгкая рассеянная тень. */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int r, float vis) {
        for (int k = 5; k >= 1; k--) {
            roundRect(g, x - k, y - k + 3, w + 2 * k, h + 2 * k, r + k, fade(0x07000000, vis));
        }
    }
}
