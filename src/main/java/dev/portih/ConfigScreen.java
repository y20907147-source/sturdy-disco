package dev.portih;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ConfigScreen extends Screen {
    private static final String[] TABS = {"HUD", "Меч", "Звук", "Вид"};
    private static final String[] AXES = {"Вертикальная (Y)", "Вперёд (Z)", "Боковая (X)"};
    private static final String[] SPIN_MODES = {"При ударе", "Постоянно"};

    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private int tab;

    private int px, py, pw, ph;
    private final int sidebarW = 112;
    private int contentX, contentY, contentW, colW;
    private int slot;
    private int infoY;

    public ConfigScreen(Screen parent) {
        super(Component.literal("PortihMod"));
        this.parent = parent;
        this.tab = Math.floorMod(Config.get().lastTab, TABS.length);
    }

    @Override
    protected void init() {
        int margin = 12;
        px = margin;
        py = margin;
        pw = this.width - margin * 2;
        ph = this.height - margin * 2;
        contentX = px + sidebarW + 14;
        contentY = py + 38;
        contentW = pw - sidebarW - 28;
        colW = (contentW - 8) / 2;
        slot = 0;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            String label = (tab == i ? "> " : "") + TABS[i];
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                tab = idx;
                Config.get().lastTab = idx;
                Config.save();
                rebuildWidgets();
            }).bounds(px + 8, py + 44 + i * 26, sidebarW - 16, 22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(px + 8, py + ph - 30, sidebarW - 16, 22).build());

        Config c = Config.get();
        switch (tab) {
            case 0 -> {
                toggle("HUD", () -> c.hudEnabled, v -> c.hudEnabled = v);
                nextRow();
                toggle("Таргет худ", () -> c.targetHud, v -> c.targetHud = v);
                slider("Таргет X", 0, 100, c.targetX * 100, v -> c.targetX = (float) (v / 100), "%.0f%%");
                nextRow();
                slider("Таргет Y", 0, 100, c.targetY * 100, v -> c.targetY = (float) (v / 100), "%.0f%%");
                nextRow();
                toggle("Кейстроки (WASD)", () -> c.keystrokes, v -> c.keystrokes = v);
                toggle("Мышь (ЛКМ/ПКМ)", () -> c.showMouse, v -> c.showMouse = v);
                toggle("КПС", () -> c.showCps, v -> c.showCps = v);
                toggle("Прыжок", () -> c.showSpace, v -> c.showSpace = v);
                slider("Кейстроки X", 0, 100, c.keysX * 100, v -> c.keysX = (float) (v / 100), "%.0f%%");
                slider("Кейстроки Y", 0, 100, c.keysY * 100, v -> c.keysY = (float) (v / 100), "%.0f%%");
            }
            case 1 -> {
                toggle("Маленький меч", () -> c.smallSword, v -> c.smallSword = v);
                slider("Размер", 0.10, 1.50, c.swordScale, v -> c.swordScale = (float) v, "%.2f");
                slider("Высота", -0.60, 0.60, c.swordY, v -> c.swordY = (float) v, "%.2f");
                toggle("Все предметы", () -> c.allItems, v -> c.allItems = v);
                toggle("Кручение", () -> c.spin, v -> c.spin = v);
                cycle("Режим", SPIN_MODES, () -> c.spinMode, v -> c.spinMode = v);
                toggle("Только по цели", () -> c.spinOnlyOnTarget, v -> c.spinOnlyOnTarget = v);
                slider("Длит. удара (с)", 0.10, 1.50, c.spinDuration, v -> c.spinDuration = (float) v, "%.2f");
                slider("Оборотов за удар", 1, 5, c.spinTurns, v -> c.spinTurns = (int) Math.round(v), "%.0f");
                slider("Скорость (об/с)", 0.1, 5.0, c.spinSpeed, v -> c.spinSpeed = (float) v, "%.1f");
                cycle("Ось", AXES, () -> c.spinAxis, v -> c.spinAxis = v);
            }
            case 2 -> {
                toggle("Звук победы", () -> c.victorySound, v -> c.victorySound = v);
                cycle("Звук", VictorySound.NAMES, () -> c.soundIndex, v -> c.soundIndex = v);
                slider("Громкость", 0.0, 2.0, c.volume, v -> c.volume = (float) v, "%.2f");
                slider("Тон", 0.5, 2.0, c.pitch, v -> c.pitch = (float) v, "%.2f");
                button("Проверить звук", VictorySound::play);
                infoY = contentY + ((slot + 1) / 2) * 26 + 8;
            }
            default -> {
                toggle("Тема", () -> c.lightTheme, v -> c.lightTheme = v, "§fСветлая", "§8Тёмная");
                cycle("Акцент", Theme.ACCENT_NAMES, () -> c.accent, v -> c.accent = v);
                slider("Прозрачность", 0.2, 1.0, c.panelOpacity, v -> c.panelOpacity = (float) v, "%.2f");
                button("Сбросить всё", () -> {
                    Config.reset();
                    this.tab = 0;
                    rebuildWidgets();
                });
                infoY = contentY + ((slot + 1) / 2) * 26 + 8;
            }
        }
    }

    // ------------------------------------------------------------ раскладка

    private int sx() {
        return contentX + (slot % 2) * (colW + 8);
    }

    private int sy() {
        return contentY + (slot / 2) * 26;
    }

    private void nextRow() {
        if (slot % 2 == 1) slot++;
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        toggle(label, get, set, "§aВКЛ", "§cВЫКЛ");
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String onText, String offText) {
        Button b = Button.builder(Component.empty(), btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(Component.literal(label + ": " + (get.getAsBoolean() ? onText : offText)));
        }).bounds(sx(), sy(), colW, 20).build();
        b.setMessage(Component.literal(label + ": " + (get.getAsBoolean() ? onText : offText)));
        addRenderableWidget(b);
        slot++;
    }

    private void cycle(String label, String[] names, IntSupplier get, IntConsumer set) {
        Button b = Button.builder(Component.empty(), btn -> {
            set.accept(Math.floorMod(get.getAsInt() + 1, names.length));
            btn.setMessage(Component.literal(label + ": " + names[Math.floorMod(get.getAsInt(), names.length)]));
        }).bounds(sx(), sy(), colW, 20).build();
        b.setMessage(Component.literal(label + ": " + names[Math.floorMod(get.getAsInt(), names.length)]));
        addRenderableWidget(b);
        slot++;
    }

    private void button(String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(sx(), sy(), colW, 20).build());
        slot++;
    }

    private void slider(String label, double min, double max, double cur, DoubleConsumer set, String fmt) {
        addRenderableWidget(new ValueSlider(sx(), sy(), colW, 20, label, min, max, cur, set, fmt));
        slot++;
    }

    // ------------------------------------------------------------ ввод

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Та же клавиша, что открывает меню (по умолчанию Right Shift), его закрывает.
        if (PortihClient.openMenu != null
                && PortihClient.openMenu.matches(event)
                && System.currentTimeMillis() - openedAt > 250L) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------ рисование

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Свой фон рисуется в render(); отключаем ванильное размытие.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        g.fill(px, py, px + pw, py + ph, Theme.panelSolid());
        g.fill(px, py, px + sidebarW, py + ph, Theme.sidebar());
        g.fill(px, py, px + pw, py + 3, Theme.accent());

        g.drawString(this.font, "PortihMod", px + 10, py + 14, Theme.accent(), false);
        g.drawString(this.font, TABS[tab], contentX, py + 16, Theme.text(), false);

        super.render(g, mouseX, mouseY, partialTick);

        if (tab == 2) {
            g.drawString(this.font, "Звук играет, когда умирает цель, которую ты бил.",
                    contentX, infoY, Theme.subText(), false);
        } else if (tab == 3) {
            g.drawString(this.font, "Бинды: Настройки -> Управление -> Бинды клавиш -> PortihMod",
                    contentX, infoY, Theme.subText(), false);
            g.drawString(this.font, "Right Shift открывает и закрывает меню (меняется там же).",
                    contentX, infoY + 12, Theme.subText(), false);
        }
    }

    @Override
    public void removed() {
        Config.get().lastTab = tab;
        Config.save();
    }

    // --------------------------------------------------------------- слайдер

    private static class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final DoubleConsumer setter;
        private final String fmt;

        ValueSlider(int x, int y, int w, int h, String label, double min, double max,
                    double cur, DoubleConsumer setter, String fmt) {
            super(x, y, w, h, Component.empty(), Math.max(0.0, Math.min(1.0, (cur - min) / (max - min))));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.fmt = fmt;
            updateMessage();
        }

        private double real() {
            return min + (max - min) * this.value;
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, fmt, real())));
        }

        @Override
        protected void applyValue() {
            setter.accept(real());
        }
    }
}
