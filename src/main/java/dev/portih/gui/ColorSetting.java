package dev.portih.gui;

import dev.portih.Draw;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Выбор цвета: строка с образцом; клик раскрывает палитру (насыщенность/яркость + оттенок). */
public final class ColorSetting extends GuiComponent {
    private static final int HEAD = 22, SV_H = 60, HUE_H = 10, PAD = 6;
    private static final int PICKER_H = PAD + SV_H + 6 + HUE_H + PAD;

    private final IntSupplier get;
    private final IntConsumer set;

    private boolean open;
    private float openAnim;
    private int drag;                 // 0 = нет, 1 = поле SV, 2 = полоса оттенка
    private final float[] hsv = new float[3];
    private int lastRgb = -1;

    public ColorSetting(String label, IntSupplier get, IntConsumer set) {
        super(label);
        this.get = get;
        this.set = set;
    }

    @Override
    public float height() {
        return HEAD + PICKER_H * openAnim;
    }

    private int rgb() {
        return get.getAsInt() & 0xFFFFFF;
    }

    /** Синхронизирует HSV с внешним значением (оттенок не теряется на серых цветах, пока цвет наш). */
    private void sync() {
        int c = rgb();
        if (c != lastRgb) {
            RenderUtil.rgbToHsv(c, hsv);
            lastRgb = c;
        }
    }

    private void push() {
        int c = RenderUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        lastRgb = c;
        set.accept(c);
    }

    @Override
    public void draw(int mx, int my, float dt) {
        tickHover(mx, my, dt);
        openAnim = AnimationUtil.approach(openAnim, open ? 1f : 0f, dt, 14f);
        sync();
        int acc = RenderUtil.accent();
        int col = 0xFF000000 | rgb();

        RenderUtil.text(label, x, y + 7, Draw.lerpColor(RenderUtil.TEXT, acc, hover * 0.35f));
        RenderUtil.rect(x + w - 34, y + 3, 34, 16, 6, col);
        RenderUtil.outline(x + w - 34, y + 3, 34, 16, 6, Draw.lerpColor(RenderUtil.OUTLINE, RenderUtil.WHITE, 0.25f + hover * 0.3f));

        if (openAnim < 0.02f) return;
        float old = RenderUtil.alpha;
        RenderUtil.alpha = old * openAnim;

        int px = Math.round(x), py = Math.round(y) + HEAD + PAD;
        int pw = Math.round(w);
        int hueTop = py + SV_H + 6;

        // поле насыщенность/яркость: колонки по 3 px, каждая - вертикальный градиент яркости
        int hueRgb = 0xFF000000 | RenderUtil.hsvToRgb(hsv[0], 1f, 1f);
        for (int cx = 0; cx < pw; cx += 3) {
            float s = Math.min(1f, (cx + 1.5f) / pw);
            int top = 0xFF000000 | RenderUtil.hsvToRgb(hsv[0], s, 1f);
            RenderUtil.gradientV(px + cx, py, px + Math.min(pw, cx + 3), py + SV_H, top, 0xFF000000);
        }
        RenderUtil.outline(px, py, pw, SV_H, 4, RenderUtil.OUTLINE);
        float mxp = px + hsv[1] * pw, myp = py + (1f - hsv[2]) * SV_H;
        RenderUtil.circle(mxp, myp, 4, RenderUtil.WHITE);
        RenderUtil.circle(mxp, myp, 2, 0xFF000000 | rgb());

        // полоса оттенка
        for (int cx = 0; cx < pw; cx += 3) {
            int c = 0xFF000000 | RenderUtil.hsvToRgb((cx + 1.5f) / pw, 1f, 1f);
            RenderUtil.fill(px + cx, hueTop, px + Math.min(pw, cx + 3), hueTop + HUE_H, c);
        }
        RenderUtil.outline(px, hueTop, pw, HUE_H, 3, RenderUtil.OUTLINE);
        float hx = px + hsv[0] * pw;
        RenderUtil.rect(hx - 2, hueTop - 2, 4, HUE_H + 4, 2, RenderUtil.WHITE);

        RenderUtil.alpha = old;
    }

    @Override
    public boolean mouseDown(int mx, int my, int button) {
        if (my < y + HEAD) {
            if (button == 0) open = !open;
            return false;
        }
        if (!open || button != 0) return false;
        sync();
        int svTop = Math.round(y) + HEAD + PAD;
        int hueTop = svTop + SV_H + 6;
        if (my >= svTop && my < svTop + SV_H) drag = 1;
        else if (my >= hueTop - 3 && my < hueTop + HUE_H + 3) drag = 2;
        else return false;
        mouseDrag(mx, my);
        return true;
    }

    @Override
    public void mouseDrag(int mx, int my) {
        if (drag == 0) return;
        float fx = Math.max(0f, Math.min(1f, (mx - x) / w));
        if (drag == 1) {
            float top = y + HEAD + PAD;
            hsv[1] = fx;
            hsv[2] = 1f - Math.max(0f, Math.min(1f, (my - top) / SV_H));
        } else {
            hsv[0] = Math.min(0.9999f, fx);
        }
        push();
    }

    @Override
    public void mouseUp() {
        drag = 0;
    }
}
