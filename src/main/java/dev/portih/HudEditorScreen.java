package dev.portih;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Пустой экран-редактор: HUD рисуется под ним, панели можно двигать мышью. */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private boolean wasLeft;
    private float hoverAnim;
    private long lastNs;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // без размытия
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long ns = System.nanoTime();
        float dt = lastNs == 0L ? 0.016f : Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;

        float e = Draw.easeOut((System.currentTimeMillis() - openedAt) / 220f);
        g.fill(0, 0, this.width, this.height, Theme.withAlpha(0x000000, 0.28f * e));

        String hint = "Перетаскивай панели мышью";
        int hw = this.font.width(hint) + 24;
        Draw.panel(g, this.width / 2 - hw / 2, 8, hw, 20, 10, Theme.withAlpha(0x000000, 0.55f * e), e);
        g.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 14,
                Draw.fade(0xFFFFFFFF, e), false);

        // кнопка "Готово" - мягкая капсула
        int bw = 86;
        int bh = 22;
        int bx = this.width / 2 - bw / 2;
        int by = this.height - 40;
        boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        hoverAnim = Draw.approach(hoverAnim, over ? 1f : 0f, dt, 16f);
        Draw.shadow(g, bx, by, bw, bh, 11, e);
        Draw.pill(g, bx, by, bw, bh, Draw.lerpColor(Theme.withAlpha(Theme.accent(), 0.9f), Theme.accent(), hoverAnim));
        String label = "Готово";
        g.drawString(this.font, label, this.width / 2 - this.font.width(label) / 2, by + 7, 0xFFFFFFFF, false);

        long handle = GLFW.glfwGetCurrentContext();
        boolean left = handle != 0L
                && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (left && !wasLeft && over && System.currentTimeMillis() - openedAt > 150L) onClose();
        wasLeft = left;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        Config.save();
    }
}
