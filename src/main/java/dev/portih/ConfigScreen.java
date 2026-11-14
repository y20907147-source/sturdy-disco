package dev.portih;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ConfigScreen extends Screen {
    private static final String[] TABS = {"HUD", "Меч", "Эффекты", "Звук", "Вид"};
    private static final String[] AXES = {"Вертикальная (Y)", "Вперёд (Z)", "Боковая (X)"};
    private static final String[] SPIN_MODES = {"По удару", "Постоянно"};
    private static final String[] TRIGGERS = {"При попадании", "При клике"};

    private static final int TOGGLE = 0;
    private static final int SLIDER = 1;
    private static final int CYCLE = 2;
    private static final int ACTION = 3;

    private static final class Row {
        int type;
        String label;
        int x, y, w, h;

        BooleanSupplier getB;
        Consumer<Boolean> setB;

        double min, max;
        DoubleSupplier getD;
        DoubleConsumer setD;
        String fmt;

        String[] names;
        IntSupplier getI;
        IntConsumer setI;

        Runnable action;
    }

    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private final List<Row> rows = new ArrayList<>();
    private int tab;

    private boolean wasLeft;
    private boolean wasRight;
    private Row active;

    private int px, py, pw, ph, colW, rowH, x0, y0;
    private final int[] tabX = new int[TABS.length];
    private final int[] tabW = new int[TABS.length];
    private int tabY;

    public ConfigScreen(Screen parent) {
        super(Component.literal("PortihMod"));
        this.parent = parent;
        this.tab = Math.floorMod(Config.get().lastTab, TABS.length);
    }

    @Override
    protected void init() {
        buildRows();
    }

    // -------------------------------------------------------------- строки

    private void buildRows() {
        rows.clear();
        active = null;
        Config c = Config.get();
        switch (tab) {
            case 0 -> {
                toggle("HUD", () -> c.hudEnabled, v -> c.hudEnabled = v);
                action("Редактор HUD", () -> this.minecraft.setScreen(new HudEditorScreen(this)));
                toggle("Таргет худ", () -> c.targetHud, v -> c.targetHud = v);
                toggle("Кейстроки (WASD)", () -> c.keystrokes, v -> c.keystrokes = v);
                toggle("Мышь + КПС", () -> c.showMouse, v -> c.showMouse = v);
                toggle("Показывать КПС", () -> c.showCps, v -> c.showCps = v);
                toggle("Прыжок", () -> c.showSpace, v -> c.showSpace = v);
                slider("Таргет X", 0, 100, () -> c.targetX * 100, v -> c.targetX = (float) (v / 100), "%.0f%%");
                slider("Таргет Y", 0, 100, () -> c.targetY * 100, v -> c.targetY = (float) (v / 100), "%.0f%%");
                slider("Кейстроки X", 0, 100, () -> c.keysX * 100, v -> c.keysX = (float) (v / 100), "%.0f%%");
                slider("Кейстроки Y", 0, 100, () -> c.keysY * 100, v -> c.keysY = (float) (v / 100), "%.0f%%");
            }
            case 1 -> {
                toggle("Маленький меч", () -> c.smallSword, v -> c.smallSword = v);
                slider("Размер", 0.10, 1.50, () -> c.swordScale, v -> c.swordScale = (float) v, "%.2f");
                slider("Высота", -0.60, 0.60, () -> c.swordY, v -> c.swordY = (float) v, "%.2f");
                toggle("Все предметы", () -> c.allItems, v -> c.allItems = v);
                toggle("Кручение", () -> c.spin, v -> c.spin = v);
                cycle("Режим", SPIN_MODES, () -> c.spinMode, v -> c.spinMode = v);
                cycle("Триггер", TRIGGERS, () -> c.spinTrigger, v -> c.spinTrigger = v);
                slider("Длит. удара (с)", 0.10, 1.50, () -> c.spinDuration, v -> c.spinDuration = (float) v, "%.2f");
                slider("Оборотов за удар", 1, 5, () -> c.spinTurns, v -> c.spinTurns = (int) Math.round(v), "%.0f");
                slider("Скорость (об/с)", 0.1, 5.0, () -> c.spinSpeed, v -> c.spinSpeed = (float) v, "%.1f");
                cycle("Ось", AXES, () -> c.spinAxis, v -> c.spinAxis = v);
            }
            case 2 -> {
                toggle("Частицы при ударе", () -> c.hitParticles, v -> c.hitParticles = v);
                cycle("Тип частиц", Effects.PARTICLE_NAMES, () -> c.particleType, v -> c.particleType = v);
                slider("Кол-во частиц", 1, 30, () -> c.particleCount, v -> c.particleCount = (int) Math.round(v), "%.0f");
                toggle("Хитмаркер", () -> c.hitMarker, v -> c.hitMarker = v);
                toggle("Вспышка экрана", () -> c.hitFlash, v -> c.hitFlash = v);
                slider("Сила вспышки", 0.1, 1.0, () -> c.flashStrength, v -> c.flashStrength = (float) v, "%.2f");
                toggle("Звук удара", () -> c.hitSound, v -> c.hitSound = v);
                cycle("Тип звука", Effects.HIT_SOUND_NAMES, () -> c.hitSoundIndex, v -> c.hitSoundIndex = v);
                slider("Громкость удара", 0.0, 2.0, () -> c.hitVolume, v -> c.hitVolume = (float) v, "%.2f");
                toggle("Частицы победы", () -> c.victoryParticles, v -> c.victoryParticles = v);
            }
            case 3 -> {
                toggle("Звук победы", () -> c.victorySound, v -> c.victorySound = v);
                cycle("Звук", VictorySound.NAMES, () -> c.soundIndex, v -> c.soundIndex = v);
                slider("Громкость", 0.0, 2.0, () -> c.volume, v -> c.volume = (float) v, "%.2f");
                slider("Тон", 0.5, 2.0, () -> c.pitch, v -> c.pitch = (float) v, "%.2f");
                action("Проверить звук", VictorySound::play);
            }
            default -> {
                toggle("Светлая тема", () -> c.lightTheme, v -> c.lightTheme = v);
                cycle("Акцент", Theme.ACCENT_NAMES, () -> c.accent, v -> c.accent = v);
                slider("Прозрачность", 0.2, 1.0, () -> c.panelOpacity, v -> c.panelOpacity = (float) v, "%.2f");
                action("Сбросить всё", () -> {
                    Config.reset();
                    this.tab = 0;
                    buildRows();
                });
            }
        }
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        Row r = new Row();
        r.type = TOGGLE;
        r.label = label;
        r.getB = get;
        r.setB = set;
        rows.add(r);
    }

    private void slider(String label, double min, double max, DoubleSupplier get, DoubleConsumer set, String fmt) {
        Row r = new Row();
        r.type = SLIDER;
        r.label = label;
        r.min = min;
        r.max = max;
        r.getD = get;
        r.setD = set;
        r.fmt = fmt;
        rows.add(r);
    }

    private void cycle(String label, String[] names, IntSupplier get, IntConsumer set) {
        Row r = new Row();
        r.type = CYCLE;
        r.label = label;
        r.names = names;
        r.getI = get;
        r.setI = set;
        rows.add(r);
    }

    private void action(String label, Runnable run) {
        Row r = new Row();
        r.type = ACTION;
        r.label = label;
        r.action = run;
        rows.add(r);
    }

    // ------------------------------------------------------------ раскладка

    private void layout() {
        pw = Math.min(this.width - 24, 480);
        ph = Math.min(this.height - 24, 216);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        int pad = 16;
        x0 = px + pad;
        y0 = py + 40;
        colW = (pw - pad * 2 - 20) / 2;
        rowH = Math.max(16, Math.min(22, (ph - 40 - 32) / 6));

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            r.x = x0 + (i % 2) * (colW + 20);
            r.y = y0 + (i / 2) * rowH;
            r.w = colW;
            r.h = rowH;
        }

        tabY = py + 12;
        int tx = px + pad;
        for (int i = 0; i < TABS.length; i++) {
            tabX[i] = tx;
            tabW[i] = this.font.width(TABS[i]);
            tx += tabW[i] + 16;
        }
    }

    // ------------------------------------------------------------ ввод

    private void press(int mx, int my, int button) {
        for (int i = 0; i < TABS.length; i++) {
            if (mx >= tabX[i] - 3 && mx < tabX[i] + tabW[i] + 3 && my >= tabY - 3 && my < tabY + 14) {
                if (button == 0 && i != tab) {
                    tab = i;
                    Config.get().lastTab = i;
                    Config.save();
                    buildRows();
                }
                return;
            }
        }
        for (Row r : rows) {
            if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) {
                click(r, button, mx);
                return;
            }
        }
    }

    private void click(Row r, int button, int mx) {
        switch (r.type) {
            case TOGGLE -> r.setB.accept(!r.getB.getAsBoolean());
            case CYCLE -> {
                int delta = button == 1 ? -1 : 1;
                r.setI.accept(Math.floorMod(r.getI.getAsInt() + delta, r.names.length));
            }
            case ACTION -> {
                if (button == 0) r.action.run();
            }
            case SLIDER -> {
                if (button == 0) {
                    active = r;
                    drag(r, mx);
                }
            }
            default -> { }
        }
    }

    private void drag(Row r, int mx) {
        double t = Math.max(0.0, Math.min(1.0, (mx - r.x) / (double) r.w));
        r.setD.accept(r.min + (r.max - r.min) * t);
    }

    private void pollMouse(int mouseX, int mouseY) {
        // Мышь читаем напрямую из окна: не зависит от версии событий ввода.
        long handle = GLFW.glfwGetCurrentContext();
        boolean left = handle != 0L
                && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = handle != 0L
                && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (System.currentTimeMillis() - openedAt > 150L) {
            if (left && !wasLeft) press(mouseX, mouseY, 0);
            if (right && !wasRight) press(mouseX, mouseY, 1);
        }

        if (!left) {
            if (active != null) {
                active = null;
                Config.save();
            }
        } else if (active != null) {
            drag(active, mouseX);
        }

        wasLeft = left;
        wasRight = right;
    }

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

    private String note() {
        return switch (tab) {
            case 0 -> "Панели можно двигать и в чате (T).";
            case 1 -> SwordSpin.handRenders > 0
                    ? "Рука: патч работает"
                    : "Рука: патч не сработал (возьми меч в руку)";
            case 3 -> "Звук играет, когда умирает цель, по которой ты попал.";
            case 4 -> "Бинды: Настройки - Управление - Бинды клавиш - PortihMod";
            default -> "";
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        pollMouse(mouseX, mouseY);
        layout();

        g.fill(0, 0, this.width, this.height, 0x70000000);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Theme.border());
        g.fill(px, py, px + pw, py + ph, Theme.panelSolid());

        // вкладки
        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == tab;
            boolean hover = mouseX >= tabX[i] - 3 && mouseX < tabX[i] + tabW[i] + 3
                    && mouseY >= tabY - 3 && mouseY < tabY + 14;
            int col = on ? Theme.text() : (hover ? Theme.accent() : Theme.subText());
            g.drawString(this.font, TABS[i], tabX[i], tabY, col, false);
            if (on) g.fill(tabX[i], tabY + 11, tabX[i] + tabW[i], tabY + 12, Theme.accent());
        }
        String title = "PortihMod";
        g.drawString(this.font, title, px + pw - 16 - this.font.width(title), tabY, Theme.subText(), false);
        g.fill(px + 16, py + 30, px + pw - 16, py + 31, Theme.border());

        // строки
        for (Row r : rows) drawRow(g, r, mouseX, mouseY);

        // низ
        String n = note();
        if (!n.isEmpty()) g.drawString(this.font, n, px + 16, py + ph - 26, Theme.subText(), false);
        g.drawString(this.font, "Esc / Right Shift - закрыть   ПКМ - назад по списку",
                px + 16, py + ph - 14, Theme.muted(), false);
    }

    private void drawRow(GuiGraphics g, Row r, int mx, int my) {
        boolean hover = mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h;
        int ty = r.type == SLIDER ? r.y + 2 : r.y + (r.h - 8) / 2;
        g.drawString(this.font, r.label, r.x, ty, hover ? Theme.accent() : Theme.text(), false);

        switch (r.type) {
            case TOGGLE -> {
                boolean on = r.getB.getAsBoolean();
                int tx = r.x + r.w - 22;
                int tyy = r.y + (r.h - 10) / 2;
                g.fill(tx, tyy, tx + 22, tyy + 10, on ? Theme.accent() : Theme.muted());
                int kx = on ? tx + 13 : tx + 1;
                g.fill(kx, tyy + 1, kx + 8, tyy + 9, 0xFFFFFFFF);
            }
            case SLIDER -> {
                double v = r.getD.getAsDouble();
                String s = String.format(Locale.ROOT, r.fmt, v);
                g.drawString(this.font, s, r.x + r.w - this.font.width(s), ty, Theme.subText(), false);
                int by = r.y + r.h - 5;
                double t = Math.max(0.0, Math.min(1.0, (v - r.min) / (r.max - r.min)));
                g.fill(r.x, by, r.x + r.w, by + 2, Theme.muted());
                g.fill(r.x, by, r.x + (int) (r.w * t), by + 2, Theme.accent());
            }
            case CYCLE -> {
                String s = r.names[Math.floorMod(r.getI.getAsInt(), r.names.length)];
                g.drawString(this.font, s, r.x + r.w - this.font.width(s), ty, Theme.accent(), false);
            }
            case ACTION -> {
                String s = ">";
                g.drawString(this.font, s, r.x + r.w - this.font.width(s), ty, Theme.accent(), false);
            }
            default -> { }
        }
    }

    @Override
    public void removed() {
        Config.get().lastTab = tab;
        Config.save();
    }
}
