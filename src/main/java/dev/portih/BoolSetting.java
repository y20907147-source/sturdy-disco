package dev.portih.gui;

import dev.portih.Draw;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Переключатель (Toggle Switch) с плавным перетеканием цвета. */
public final class BoolSetting extends GuiComponent {
    private final BooleanSupplier get;
    private final Consumer<Boolean> set;
    private float anim = -1f;

    public BoolSetting(String label, BooleanSupplier get, Consumer<Boolean> set) {
        super(label);
        this.get = get;
        this.set = set;
    }

    @Override
    public float height() {
        return 22;
    }

    @Override
    public void draw(int mx, int my, float dt) {
        tickHover(mx, my, dt);
        float target = get.getAsBoolean() ? 1f : 0f;
        anim = anim < 0f ? target : AnimationUtil.approach(anim, target, dt, 16f);

        RenderUtil.text(label, x, y + 7, Draw.lerpColor(RenderUtil.TEXT, RenderUtil.accent(), hover * 0.35f));
        RenderUtil.toggle(x + w - 30, y + 3, 30, 16, anim);
    }

    @Override
    public boolean mouseDown(int mx, int my, int button) {
        if (button == 0) set.accept(!get.getAsBoolean());
        return false;
    }
}
