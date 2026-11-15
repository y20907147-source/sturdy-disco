package dev.portih.gui;

/**
 * Базовый элемент настройки внутри карточки модуля. Положение (x, y, w) задаёт Module.layout().
 * mouseDown возвращает true, если элемент "захватил" мышь (перетаскивание, ввод) - тогда ClickGuiScreen
 * будет слать ему mouseDrag/mouseUp.
 */
public abstract class GuiComponent {
    public final String label;
    public float x, y, w;
    protected float hover;

    protected GuiComponent(String label) {
        this.label = label;
    }

    public abstract float height();

    public abstract void draw(int mx, int my, float dt);

    public boolean mouseDown(int mx, int my, int button) { return false; }

    public void mouseDrag(int mx, int my) {}

    public void mouseUp() {}

    /** Идёт ли точный ввод с клавиатуры. */
    public boolean isEditing() { return false; }

    public void stopEditing(boolean apply) {}

    public void keyPressed(int key) {}

    public boolean charTyped(int codepoint) { return false; }

    public boolean contains(int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + height();
    }

    protected final boolean hovered(int mx, int my) {
        return contains(mx, my) && RenderUtil.inClip(my);
    }

    protected final void tickHover(int mx, int my, float dt) {
        hover = AnimationUtil.approach(hover, hovered(mx, my) ? 1f : 0f, dt, 14f);
    }
}
