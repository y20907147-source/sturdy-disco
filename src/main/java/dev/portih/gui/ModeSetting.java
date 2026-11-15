package dev.portih.gui;

import dev.portih.Draw;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Выбор из списка: ЛКМ - следующий вариант, ПКМ - предыдущий. */
public final class ModeSetting extends GuiComponent {
    private final String[] options;
    private final IntSupplier get;
    private final IntConsumer set;
    private float pulse;

    public ModeSetting(String label, String[] options, IntSupplier get, IntConsumer set) {
        super(label);
        this.options = options;
        this.get = get;
        this.set = set;
    }

    @Override
    public float height() {
        return 22;
    }

    private int index() {
        return Math.floorMod(get.getAsInt(), options.length);
    }

    @Override
    public void draw(int mx, int my, float dt) {
        tickHover(mx, my, dt);
        pulse = AnimationUtil.approach(pulse, 0f, dt, 10f);
        int acc = RenderUtil.accent();

        String val = options[index()];
        int pw = RenderUtil.width(val) + 22;
        float maxPill = w * 0.62f;
        while (pw > maxPill && val.length() > 2) {
            val = val.substring(0, val.length() - 1);
            pw = RenderUtil.width(val + "..") + 22;
        }
        if (!val.equals(options[index()])) val = val + "..";

        RenderUtil.text(label, x, y + 7, Draw.lerpColor(RenderUtil.TEXT, acc, hover * 0.35f));
        float px = x + w - pw;
        RenderUtil.rect(px, y + 1, pw, 20, 7, Draw.lerpColor(RenderUtil.BG_FIELD, acc, pulse * 0.25f));
        RenderUtil.outline(px, y + 1, pw, 20, 7, Draw.lerpColor(RenderUtil.OUTLINE, acc, Math.min(1f, hover * 0.5f + pulse)));
        RenderUtil.text(val, px + 11, y + 7, RenderUtil.TEXT);
    }

    @Override
    public boolean mouseDown(int mx, int my, int button) {
        if (options.length == 0) return false;
        if (button == 0) set.accept(Math.floorMod(index() + 1, options.length));
        else if (button == 1) set.accept(Math.floorMod(index() - 1, options.length));
        else return false;
        pulse = 1f;
        return false;
    }
}
