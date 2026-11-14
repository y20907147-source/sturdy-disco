package dev.portih;

import net.minecraft.client.gui.GuiGraphics;

/** Мелкие помощники для современного вида: скруглённые панели, тени, плавные переходы. */
public final class Draw {
    private Draw() {}

    // ------------------------------------------------------------- цвета/анимация

    public static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t);
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    // ----------------------------------------------------------------- формы

    /** Сколько пикселей срезать с края в строке i (от верха) угла радиуса r. */
    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        double dx = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
        return (int) Math.ceil(r - dx);
    }

    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            g.fill(x + in, y + i, x + w - in, y + i + 1, color);
            g.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        if (h - 2 * r > 0) g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Скруглённая рамка толщиной 1 px (внутри прямоугольника, ничего не закрашивает). */
    public static void border(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w < 3 || h < 3) return;
        r = Math.max(1, Math.min(r, Math.min(w, h) / 2));
        int r2 = r - 1;
        int h2 = h - 2;
        for (int row = 0; row < h; row++) {
            int ci = row < r ? row : (row >= h - r ? h - 1 - row : -1);
            int o = ci >= 0 ? inset(r, ci) : 0;
            if (row == 0 || row == h - 1) {
                g.fill(x + o, y + row, x + w - o, y + row + 1, color);
                continue;
            }
            int y2 = row - 1;
            int ci2 = (r2 > 0 && y2 < r2) ? y2 : ((r2 > 0 && y2 >= h2 - r2) ? h2 - 1 - y2 : -1);
            int in = 1 + (ci2 >= 0 ? inset(r2, ci2) : 0);
            in = Math.max(in, o + 1);
            g.fill(x + o, y + row, x + in, y + row + 1, color);
            g.fill(x + w - in, y + row, x + w - o, y + row + 1, color);
        }
    }

    /** Мягкая тень под панелью. */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int r) {
        for (int k = 4; k >= 1; k--) {
            roundRect(g, x - k, y - k + 2, w + 2 * k, h + 2 * k, r + k, 0x0E000000);
        }
    }
}
