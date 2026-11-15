package dev.portih.gui;

import dev.portih.Draw;

/** Кнопка-действие (отчёт, проверка звука, открыть редактор...). */
public final class ButtonSetting extends GuiComponent {
    private final Runnable action;
    private float flash;

    public ButtonSetting(String label, Runnable action) {
        super(label);
        this.action = action;
    }

    @Override
    public float height() {
        return 24;
    }

    @Override
    public void draw(int mx, int my, float dt) {
        tickHover(mx, my, dt);
        flash = AnimationUtil.approach(flash, 0f, dt, 8f);
        int acc = RenderUtil.accent();
        int bg = Draw.lerpColor(RenderUtil.BG_FIELD, acc, Math.max(hover * 0.12f, flash * 0.45f));
        RenderUtil.rect(x, y, w, 24, 8, bg);
        RenderUtil.outline(x, y, w, 24, 8, Draw.lerpColor(RenderUtil.OUTLINE, acc, Math.min(1f, hover * 0.6f + flash)));
        float tx = x + (w - RenderUtil.width(label)) / 2f;
        RenderUtil.text(label, tx, y + 8, Draw.lerpColor(RenderUtil.TEXT, acc, hover * 0.3f));
    }

    @Override
    public boolean mouseDown(int mx, int my, int button) {
        if (button == 0) {
            flash = 1f;
            action.run();
        }
        return false;
    }
}
