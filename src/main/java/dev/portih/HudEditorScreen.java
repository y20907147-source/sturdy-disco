package dev.portih;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Пустой экран-редактор: HUD рисуется под ним, панели можно двигать мышью. */
public class HudEditorScreen extends Screen {
    private final Screen parent;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
                .bounds(this.width / 2 - 40, this.height - 32, 80, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // без размытия
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x50000000);
        String hint = "Перетаскивай панели мышью";
        int hw = this.font.width(hint) + 16;
        Draw.roundRect(g, this.width / 2 - hw / 2, 6, hw, 18, 4, Theme.withAlpha(0x000000, 0.6f));
        g.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 11, 0xFFFFFFFF, false);
        super.render(g, mouseX, mouseY, partialTick);
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
