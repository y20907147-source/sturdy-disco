package dev.portih.gui;

import dev.portih.Draw;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Слайдер. ЛКМ по полосе - тянуть; ПКМ или клик по числу - точный ввод с клавиатуры
 * (Enter - применить, Esc - отмена).
 */
public final class NumberSetting extends GuiComponent {
    private final double min, max, step;
    private final int decimals;
    private final DoubleSupplier get;
    private final DoubleConsumer set;

    private boolean dragging;
    private boolean editing;
    private final StringBuilder buffer = new StringBuilder();
    private float anim = -1f;

    public NumberSetting(String label, double min, double max, double step, int decimals,
                         DoubleSupplier get, DoubleConsumer set) {
        super(label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = decimals;
        this.get = get;
        this.set = set;
    }

    @Override
    public float height() {
        return 30;
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    private String fmt(double v) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    private double snap(double v) {
        if (step > 0) v = min + Math.round((v - min) / step) * step;
        double p = Math.pow(10, Math.max(decimals, 0));
        return clamp(Math.round(v * p) / p);
    }

    private float trackX() { return x; }

    private float trackW() { return w; }

    private boolean onValueText(int mx, int my) {
        int tw = RenderUtil.width(fmt(clamp(get.getAsDouble()))) + 12;
        return my >= y && my < y + 14 && mx >= x + w - tw;
    }

    @Override
    public void draw(int mx, int my, float dt) {
        tickHover(mx, my, dt);
        int acc = RenderUtil.accent();
        double v = clamp(get.getAsDouble());
        float frac = max > min ? (float) ((v - min) / (max - min)) : 0f;
        anim = anim < 0f ? frac : AnimationUtil.approach(anim, frac, dt, 20f);

        RenderUtil.text(label, x, y + 2, Draw.lerpColor(RenderUtil.TEXT, acc, hover * 0.35f));

        if (editing) {
            String s = buffer.toString();
            int tw = RenderUtil.width(s) + 10;
            RenderUtil.rect(x + w - tw - 2, y - 1, tw + 2, 13, 5, RenderUtil.BG_FIELD);
            RenderUtil.outline(x + w - tw - 2, y - 1, tw + 2, 13, 5, acc);
            RenderUtil.text(s, x + w - tw + 3, y + 2, RenderUtil.TEXT);
            if ((System.currentTimeMillis() / 450L) % 2L == 0L) {
                float cx = x + w - 5;
                RenderUtil.fill(cx, y + 1, cx + 1, y + 10, acc);
            }
        } else {
            String s = fmt(v);
            RenderUtil.text(s, x + w - RenderUtil.width(s), y + 2, Draw.lerpColor(RenderUtil.MUTED, acc, Math.max(hover * 0.5f, dragging ? 1f : 0f)));
        }

        float ty = y + 20;
        RenderUtil.rect(trackX(), ty, trackW(), 4, 2, RenderUtil.TRACK);
        float fw = Math.max(0f, trackW() * anim);
        if (fw > 0.5f) RenderUtil.rect(trackX(), ty, Math.max(4f, fw), 4, 2, acc);
        float kx = trackX() + trackW() * anim;
        RenderUtil.circle(kx, ty + 2, dragging ? 6 : 5, RenderUtil.WHITE);
    }

    private void applyFromMouse(int mx) {
        double f = (mx - trackX()) / trackW();
        f = Math.max(0.0, Math.min(1.0, f));
        set.accept(snap(min + f * (max - min)));
    }

    @Override
    public boolean mouseDown(int mx, int my, int button) {
        if (button == 1 || (button == 0 && onValueText(mx, my))) {
            editing = true;
            buffer.setLength(0);
            return true;
        }
        if (button == 0) {
            dragging = true;
            applyFromMouse(mx);
            return true;
        }
        return false;
    }

    @Override
    public void mouseDrag(int mx, int my) {
        if (dragging) applyFromMouse(mx);
    }

    @Override
    public void mouseUp() {
        dragging = false;
    }

    @Override
    public boolean isEditing() {
        return editing;
    }

    @Override
    public void stopEditing(boolean apply) {
        if (editing && apply && buffer.length() > 0) {
            try {
                double v = Double.parseDouble(buffer.toString().replace(',', '.'));
                set.accept(snap(v));
            } catch (NumberFormatException ignored) {
                // некорректный ввод - оставляем прежнее значение
            }
        }
        editing = false;
        buffer.setLength(0);
    }

    @Override
    public void keyPressed(int key) {
        if (!editing) return;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) stopEditing(true);
        else if (key == GLFW.GLFW_KEY_ESCAPE) stopEditing(false);
        else if (key == GLFW.GLFW_KEY_BACKSPACE && buffer.length() > 0) buffer.setLength(buffer.length() - 1);
    }

    @Override
    public boolean charTyped(int cp) {
        if (!editing) return false;
        boolean ok = (cp >= '0' && cp <= '9') || cp == '.' || cp == ',' || (cp == '-' && buffer.length() == 0);
        if (ok && buffer.length() < 8) buffer.append((char) cp);
        return true;
    }
}
