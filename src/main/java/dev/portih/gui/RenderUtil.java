package dev.portih.gui;

import dev.portih.Config;
import dev.portih.Draw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Рисование для ClickGUI. Состояние кадра (graphics, шрифт, общая прозрачность) задаётся один раз через begin().
 * Скругления со сглаживанием берутся из Draw.roundRect (покрытие краёв углов), тонкий контур - outline().
 * Все цвета - ARGB; общая прозрачность (fade-анимация меню) применяется автоматически.
 */
public final class RenderUtil {
    private RenderUtil() {}

    // --- палитра (Modern Minimalism, тёмная) ---
    public static final int BG_WINDOW = 0xFF0B0B0E;
    public static final int BG_SIDEBAR = 0xFF0F0F13;
    public static final int BG_CARD = 0xFF121216;        // карточки: #121216 при 90% непрозрачности
    public static final int BG_CARD_HOVER = 0xFF1A1A21;
    public static final int BG_FIELD = 0xFF17171C;
    public static final int OUTLINE = 0xFF24242C;        // тонкий контур
    public static final int TEXT = 0xFFF2F2F7;
    public static final int MUTED = 0xFF8E8E93;          // описания
    public static final int TRACK = 0xFF2C2C35;
    public static final int WHITE = 0xFFFFFFFF;

    // --- состояние кадра ---
    public static GuiGraphics g;
    public static Font font;
    public static float alpha = 1f;       // общая прозрачность (анимация открытия)
    public static float bgAlpha = 1f;     // прозрачность фонов (настройка пользователя)
    public static int clipTop = 0;        // видимая область прокрутки (для hover)
    public static int clipBottom = Integer.MAX_VALUE;

    private static final Map<String, Component> BOLD = new HashMap<>();

    public static void begin(GuiGraphics graphics, Font f, float a, float bgA) {
        g = graphics;
        font = f;
        alpha = a;
        bgAlpha = bgA;
        clipTop = 0;
        clipBottom = Integer.MAX_VALUE;
    }

    /** Акцентный цвет (настраивается в Настройки -> Интерфейс). */
    public static int accent() {
        return 0xFF000000 | (Config.get().guiAccent & 0xFFFFFF);
    }

    public static boolean inClip(int my) {
        return my >= clipTop && my < clipBottom;
    }

    private static int fade(int argb) {
        return Draw.fade(argb, alpha);
    }

    /** Полупрозрачный фон: k - непрозрачность фона (0..1), учитывает настройку прозрачности окна. */
    public static int bg(int argb, float k) {
        return Draw.fade(argb, k * bgAlpha);
    }

    // ------------------------------------------------------------------ фигуры

    public static void rect(float x, float y, float w, float h, int r, int argb) {
        Draw.roundRect(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), r, fade(argb));
    }

    public static void fill(float x1, float y1, float x2, float y2, int argb) {
        g.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), fade(argb));
    }

    public static void circle(float cx, float cy, int radius, int argb) {
        Draw.circle(g, Math.round(cx), Math.round(cy), radius, fade(argb));
    }

    public static void line(float x1, float y1, float x2, float y2, float thick, int argb) {
        Draw.line(g, x1, y1, x2, y2, thick, fade(argb));
    }

    /** Вертикальный градиент (используется палитрой цвета). */
    public static void gradientV(int x1, int y1, int x2, int y2, int top, int bottom) {
        g.fillGradient(x1, y1, x2, y2, fade(top), fade(bottom));
    }

    // Профиль дуги угла для outline(): для каждого радиуса - начало и длина сегмента в каждой строке.
    private static final int[][] ARC_START = new int[33][];
    private static final int[][] ARC_LEN = new int[33][];

    private static void arc(int r) {
        if (ARC_START[r] != null) return;
        int[] st = new int[r];
        int[] ln = new int[r];
        int prev = r;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int s = (int) Math.round(r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy)));
            s = Math.max(0, Math.min(prev - 1, s));
            st[i] = s;
            ln[i] = Math.max(1, prev - s);
            prev = s;
        }
        ARC_START[r] = st;
        ARC_LEN[r] = ln;
    }

    /** Тонкий контур со скруглением (1 px): прямые стороны + дуги углов, около 4r+4 fill-вызовов. */
    public static void outline(float fx, float fy, float fw, float fh, int r, int argb) {
        int x = Math.round(fx), y = Math.round(fy), w = Math.round(fw), h = Math.round(fh);
        if (w < 3 || h < 3) return;
        int c = fade(argb);
        r = Math.max(0, Math.min(Math.min(r, 32), Math.min(w, h) / 2));
        if (r <= 1) {
            g.fill(x, y, x + w, y + 1, c);
            g.fill(x, y + h - 1, x + w, y + h, c);
            g.fill(x, y + 1, x + 1, y + h - 1, c);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
            return;
        }
        arc(r);
        g.fill(x + r, y, x + w - r, y + 1, c);
        g.fill(x + r, y + h - 1, x + w - r, y + h, c);
        g.fill(x, y + r, x + 1, y + h - r, c);
        g.fill(x + w - 1, y + r, x + w, y + h - r, c);
        int[] st = ARC_START[r], ln = ARC_LEN[r];
        for (int i = 0; i < r; i++) {
            int s = st[i], l = ln[i];
            g.fill(x + s, y + i, x + s + l, y + i + 1, c);                       // верх-лево
            g.fill(x + w - s - l, y + i, x + w - s, y + i + 1, c);               // верх-право
            g.fill(x + s, y + h - 1 - i, x + s + l, y + h - i, c);               // низ-лево
            g.fill(x + w - s - l, y + h - 1 - i, x + w - s, y + h - i, c);       // низ-право
        }
    }

    // ------------------------------------------------------------------ текст (иерархия шрифтов)

    /** Обычный текст: названия функций. */
    public static void text(String s, float x, float y, int argb) {
        int c = fade(argb);
        if (((c >>> 24) & 0xFF) < 8) return;   // почти прозрачный текст в ваниле рисуется непрозрачным
        g.drawString(font, s, Math.round(x), Math.round(y), c, false);
    }

    /** Жирный текст: заголовки категорий. */
    public static void bold(String s, float x, float y, int argb) {
        int c = fade(argb);
        if (((c >>> 24) & 0xFF) < 8) return;
        g.drawString(font, boldComponent(s), Math.round(x), Math.round(y), c, false);
    }

    public static void boldScaled(String s, float x, float y, int argb, float scale) {
        int c = fade(argb);
        if (((c >>> 24) & 0xFF) < 8) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(font, boldComponent(s), 0, 0, c, false);
        g.pose().popMatrix();
    }

    /** Мелкий приглушённый текст (75%): описания. */
    public static void small(String s, float x, float y, int argb) {
        int c = fade(argb);
        if (((c >>> 24) & 0xFF) < 8) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75f, 0.75f);
        g.drawString(font, s, 0, 0, c, false);
        g.pose().popMatrix();
    }

    public static int width(String s) {
        return font.width(s);
    }

    public static int boldWidth(String s) {
        return font.width(boldComponent(s));
    }

    private static Component boldComponent(String s) {
        Component c = BOLD.get(s);
        if (c == null) {
            c = Component.literal(s).withStyle(ChatFormatting.BOLD);
            if (BOLD.size() > 256) BOLD.clear();
            BOLD.put(s, c);
        }
        return c;
    }

    // ------------------------------------------------------------------ элементы

    /** Переключатель; anim 0..1 плавно перетекает из серого в акцентный цвет. */
    public static void toggle(float x, float y, float w, float h, float anim) {
        int track = Draw.lerpColor(TRACK, accent(), anim);
        rect(x, y, w, h, Math.round(h / 2f), track);
        float kr = h / 2f - 1.5f;
        float kx = x + h / 2f + anim * (w - h);
        float ky = y + h / 2f;
        circle(kx, ky + 1, Math.max(2, Math.round(kr)), Draw.fade(0xFF000000, 0.22f));   // мягкая тень
        circle(kx, ky, Math.max(2, Math.round(kr)), WHITE);
    }

    /** Шеврон-стрелка вниз/вверх (7 px). */
    public static void chevron(float cx, float cy, boolean down, int argb) {
        int x = Math.round(cx), y = Math.round(cy);
        int c = fade(argb);
        for (int i = 0; i < 4; i++) {
            int half = down ? 3 - i : i;
            g.fill(x - half, y - 2 + i, x + half + 1, y - 1 + i, c);
        }
    }

    /** Иконки категорий из примитивов (без текстур). bgArgb - цвет подложки для "вырезов". */
    public static void icon(Category cat, float cx, float cy, int argb, int bgArgb) {
        int x = Math.round(cx), y = Math.round(cy);
        switch (cat) {
            case VISUALS -> {                       // глаз
                rect(x - 7, y - 4, 14, 8, 4, argb);
                circle(x, y, 3, bgArgb);
                circle(x, y, 1, argb);
            }
            case HUD -> {                           // 4 плитки
                rect(x - 7, y - 7, 6, 6, 2, argb);
                rect(x + 1, y - 7, 6, 6, 2, argb);
                rect(x - 7, y + 1, 6, 6, 2, argb);
                rect(x + 1, y + 1, 6, 6, 2, argb);
            }
            case PLAYER -> {                        // человечек
                circle(x, y - 3, 3, argb);
                rect(x - 5, y + 1, 10, 6, 3, argb);
            }
            case MISC -> {                          // три точки
                rect(x - 7, y - 1, 3, 3, 1, argb);
                rect(x - 1, y - 1, 3, 3, 1, argb);
                rect(x + 5, y - 1, 3, 3, 1, argb);
            }
            case SETTINGS -> {                      // ползунки
                rect(x - 7, y - 5, 14, 2, 1, argb);
                rect(x - 7, y + 3, 14, 2, 1, argb);
                circle(x - 2, y - 4, 3, argb);
                circle(x + 3, y + 4, 3, argb);
            }
        }
    }

    /** Пятиконечная звезда (логотип). */
    public static void star(float cx, float cy, float r, int argb) {
        float ir = r * 0.42f;
        float[] px = new float[10], py = new float[10];
        for (int i = 0; i < 10; i++) {
            float a = (float) (-Math.PI / 2 + i * Math.PI / 5);
            float rr = (i % 2 == 0) ? r : ir;
            px[i] = cx + rr * (float) Math.cos(a);
            py[i] = cy + rr * (float) Math.sin(a);
        }
        // заливка веером треугольников из центра (десятиугольник звезды выпуклый относительно центра)
        for (int i = 0; i < 10; i++) {
            int j = (i + 1) % 10;
            fillTri(cx, cy, px[i], py[i], px[j], py[j], argb);
        }
    }

    private static void fillTri(float ax, float ay, float bx, float by, float cx2, float cy2, int argb) {
        int minX = Math.round(Math.min(ax, Math.min(bx, cx2)));
        int maxX = Math.round(Math.max(ax, Math.max(bx, cx2)));
        int minY = Math.round(Math.min(ay, Math.min(by, cy2)));
        int maxY = Math.round(Math.max(ay, Math.max(by, cy2)));
        int c = fade(argb);
        for (int yy = minY; yy <= maxY; yy++) {
            float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE;
            float[][] e = {{ax, ay, bx, by}, {bx, by, cx2, cy2}, {cx2, cy2, ax, ay}};
            for (float[] seg : e) {
                float y0 = seg[1], y1 = seg[3];
                if ((yy >= y0 && yy < y1) || (yy >= y1 && yy < y0)) {
                    float t = (yy - y0) / (y1 - y0);
                    float x = seg[0] + (seg[2] - seg[0]) * t;
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                }
            }
            if (x1 >= x0) g.fill(Math.round(x0), yy, Math.round(x1) + 1, yy + 1, c);
        }
    }

    // ------------------------------------------------------------------ цвет (HSV)

    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float c = v * s;
        float hh = h * 6f;
        float x = c * (1f - Math.abs(hh % 2f - 1f));
        float r, gr, b;
        if (hh < 1) { r = c; gr = x; b = 0; }
        else if (hh < 2) { r = x; gr = c; b = 0; }
        else if (hh < 3) { r = 0; gr = c; b = x; }
        else if (hh < 4) { r = 0; gr = x; b = c; }
        else if (hh < 5) { r = x; gr = 0; b = c; }
        else { r = c; gr = 0; b = x; }
        float m = v - c;
        int ri = Math.round((r + m) * 255f), gi = Math.round((gr + m) * 255f), bi = Math.round((b + m) * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }

    /** out = {h, s, v}, все 0..1. */
    public static void rgbToHsv(int rgb, float[] out) {
        float r = ((rgb >> 16) & 0xFF) / 255f, gr = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(gr, b)), min = Math.min(r, Math.min(gr, b));
        float d = max - min;
        float h;
        if (d < 1e-6f) h = 0f;
        else if (max == r) h = ((gr - b) / d) % 6f;
        else if (max == gr) h = (b - r) / d + 2f;
        else h = (r - gr) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        out[0] = h;
        out[1] = max <= 0f ? 0f : d / max;
        out[2] = max;
    }
}
