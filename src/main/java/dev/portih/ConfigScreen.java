package dev.portih;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
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
    private static final String[] AXES = {"Волчок (Y)", "Колесо (Z)", "Кувырок (X)"};
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

        float anim;
        boolean animInit;
    }

    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private final List<Row> rows = new ArrayList<>();
    private int tab;

    private boolean wasLeft;
    private boolean wasRight;
    private Row active;

    private static final int SB_W = 96;

    private int px, py, pw, ph, colW, rowH, cx, cw, y0, oy;
    private int sbX, sbY, sbH;
    private long lastNs;
    private float dt = 0.016f;
    private String flash = "";
    private long flashUntil;

    public ConfigScreen(Screen parent) {
        super(Component.literal("PortihMod"));
        this.parent = parent;
        this.tab = Math.floorMod(Config.get().lastTab, TABS.length);
        CustomSounds.rescan();
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
                toggle("Победа / поражение", () -> c.winLose, v -> c.winLose = v);
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
                toggle("Без взмаха", () -> c.noSwing, v -> c.noSwing = v);
                toggle("Без опускания", () -> c.noDip, v -> c.noDip = v);
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
                toggle("Частицы победы", () -> c.victoryParticles, v -> c.victoryParticles = v);
                toggle("Вспышка экрана", () -> c.hitFlash, v -> c.hitFlash = v);
                slider("Сила вспышки", 0.1, 1.0, () -> c.flashStrength, v -> c.flashStrength = (float) v, "%.2f");
                toggle("Звук удара", () -> c.hitSound, v -> c.hitSound = v);
                int hb = Effects.HIT_SOUND_NAMES.length;
                cycle("Тип звука", CustomSounds.names(Effects.HIT_SOUND_NAMES),
                        () -> CustomSounds.indexOf(c.hitCustom, c.hitSoundIndex, hb),
                        v -> {
                            if (v < hb) {
                                c.hitSoundIndex = v;
                                c.hitCustom = "";
                            } else {
                                c.hitCustom = CustomSounds.fileAt(v - hb);
                            }
                        });
                slider("Громкость удара", 0.0, 2.0, () -> c.hitVolume, v -> c.hitVolume = (float) v, "%.2f");
                action("Проверить звук удара", Effects::playHit);
            }
            case 3 -> {
                toggle("Звук победы", () -> c.victorySound, v -> c.victorySound = v);
                int vb = VictorySound.NAMES.length;
                cycle("Звук", CustomSounds.names(VictorySound.NAMES),
                        () -> CustomSounds.indexOf(c.victoryCustom, c.soundIndex, vb),
                        v -> {
                            if (v < vb) {
                                c.soundIndex = v;
                                c.victoryCustom = "";
                            } else {
                                c.victoryCustom = CustomSounds.fileAt(v - vb);
                            }
                        });
                slider("Громкость", 0.0, 2.0, () -> c.volume, v -> c.volume = (float) v, "%.2f");
                slider("Тон", 0.5, 2.0, () -> c.pitch, v -> c.pitch = (float) v, "%.2f");
                action("Проверить звук", VictorySound::play);
                action("Открыть папку со звуками", CustomSounds::openFolder);
                action("Обновить список файлов", () -> {
                    CustomSounds.rescan();
                    buildRows();
                    say("Найдено файлов: " + CustomSounds.files().size());
                });
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
        pw = Math.min(this.width - 24, 540);
        ph = Math.min(this.height - 24, 280);
        float e = Draw.easeOut((System.currentTimeMillis() - openedAt) / 200f);
        oy = Math.round((1f - e) * 12f);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2 + oy;

        sbX = px + 8;
        sbY = py + 8;
        sbH = ph - 16;
        cx = sbX + SB_W + 14;
        cw = pw - (cx - px) - 12;
        colW = (cw - 8) / 2;
        y0 = py + 30;

        int per = Math.max(6, (rows.size() + 1) / 2);
        int avail = ph - 30 - 30;
        rowH = Math.max(20, Math.min(26, avail / per));

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int col = i / per;
            r.x = cx + col * (colW + 8);
            r.y = y0 + (i % per) * rowH;
            r.w = colW;
            r.h = rowH - 3;
        }
    }

    private int tabTop(int i) {
        return sbY + 38 + i * 22;
    }

    private boolean overTab(int i, int mx, int my) {
        return mx >= sbX + 4 && mx < sbX + SB_W - 4 && my >= tabTop(i) && my < tabTop(i) + 20;
    }

    private void say(String msg) {
        flash = msg;
        flashUntil = System.currentTimeMillis() + 2500L;
    }

    // ------------------------------------------------------------ ввод

    private void press(int mx, int my, int button) {
        for (int i = 0; i < TABS.length; i++) {
            if (overTab(i, mx, my)) {
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
        int tx = r.x + 8;
        int tw = Math.max(1, r.w - 16);
        double t = Math.max(0.0, Math.min(1.0, (mx - tx) / (double) tw));
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

    /** Перетащил .mp3/.wav в окно игры - файл копируется в папку со звуками. */
    public void onFilesDrop(List<Path> paths) {
        int n = CustomSounds.importFiles(paths);
        if (n > 0) {
            buildRows();
            say("Добавлено звуков: " + n);
        } else {
            say("Нужен файл .mp3 или .wav");
        }
    }

    // ------------------------------------------------------------ рисование

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Свой фон рисуется в render(); отключаем ванильное размытие.
    }

    private String note() {
        if (!flash.isEmpty() && System.currentTimeMillis() < flashUntil) return flash;
        return switch (tab) {
            case 0 -> "Панели можно двигать и в чате (T).";
            case 1 -> SwordSpin.handRenders > 0
                    ? "Рука: патч работает"
                    : "Рука: патч не сработал (возьми меч в руку)";
            case 3 -> "Свои звуки: .mp3 / .wav в config/portihmod/sounds или перетащи файл в окно.";
            case 4 -> "Бинды: Настройки - Управление - Бинды клавиш - PortihMod";
            default -> "";
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long ns = System.nanoTime();
        dt = lastNs == 0L ? 0.016f : Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;

        layout();
        pollMouse(mouseX, mouseY);
        layout();

        float e = Draw.easeOut((System.currentTimeMillis() - openedAt) / 200f);
        g.fill(0, 0, this.width, this.height, Theme.withAlpha(0x000000, 0.5f * e));

        // панель
        Draw.shadow(g, px, py, pw, ph, 6);
        Draw.roundRect(g, px, py, pw, ph, 6, Theme.border());
        Draw.roundRect(g, px + 1, py + 1, pw - 2, ph - 2, 5, Theme.panelSolid());

        // боковая панель
        Draw.roundRect(g, sbX, sbY, SB_W, sbH, 5, Theme.sidebar());
        g.drawString(this.font, "PortihMod", sbX + 10, sbY + 9, Theme.accent(), false);
        String ver = "v" + FabricLoader.getInstance().getModContainer(PortihClient.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        g.drawString(this.font, ver, sbX + 10, sbY + 21, Theme.muted(), false);

        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == tab;
            boolean hover = overTab(i, mouseX, mouseY);
            int ty = tabTop(i);
            if (on) {
                Draw.roundRect(g, sbX + 4, ty, SB_W - 8, 20, 4, Theme.withAlpha(Theme.accent(), 0.20f));
                Draw.roundRect(g, sbX + 4, ty + 4, 2, 12, 1, Theme.accent());
            } else if (hover) {
                Draw.roundRect(g, sbX + 4, ty, SB_W - 8, 20, 4, Theme.withAlpha(Theme.accent(), 0.08f));
            }
            int col = on ? Theme.text() : (hover ? Theme.accent() : Theme.subText());
            g.drawString(this.font, TABS[i], sbX + 14, ty + 6, col, false);
        }

        // заголовок вкладки
        g.drawString(this.font, TABS[tab], cx, py + 14, Theme.text(), false);
        g.fill(cx + this.font.width(TABS[tab]) + 8, py + 18, cx + cw, py + 19, Theme.withAlpha(Theme.border() & 0xFFFFFF, 0.9f));

        for (Row r : rows) drawRow(g, r, mouseX, mouseY);

        // низ
        String n = note();
        if (!n.isEmpty()) g.drawString(this.font, n, cx, py + ph - 24, Theme.subText(), false);
        g.drawString(this.font, "Esc / Right Shift - закрыть   ПКМ - назад по списку",
                cx, py + ph - 13, Theme.muted(), false);
    }

    private void drawRow(GuiGraphics g, Row r, int mx, int my) {
        boolean hover = mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h;
        Draw.roundRect(g, r.x, r.y, r.w, r.h, 4, hover ? Theme.cardHover() : Theme.card());

        int ty = r.type == SLIDER ? r.y + 3 : r.y + (r.h - 8) / 2;
        g.drawString(this.font, r.label, r.x + 8, ty, hover ? Theme.accent() : Theme.text(), false);

        switch (r.type) {
            case TOGGLE -> {
                boolean on = r.getB.getAsBoolean();
                float target = on ? 1f : 0f;
                if (!r.animInit) {
                    r.anim = target;
                    r.animInit = true;
                }
                r.anim += (target - r.anim) * Math.min(1f, dt * 16f);
                if (Math.abs(target - r.anim) < 0.01f) r.anim = target;

                int pillW = 24;
                int pillH = 12;
                int tx = r.x + r.w - 8 - pillW;
                int tyy = r.y + (r.h - pillH) / 2;
                Draw.roundRect(g, tx, tyy, pillW, pillH, 6, Draw.lerpColor(Theme.muted(), Theme.accent(), r.anim));
                int kx = tx + 2 + Math.round(r.anim * (pillW - 12));
                Draw.roundRect(g, kx, tyy + 2, 8, 8, 4, 0xFFFFFFFF);
            }
            case SLIDER -> {
                double v = r.getD.getAsDouble();
                String s = String.format(Locale.ROOT, r.fmt, v);
                g.drawString(this.font, s, r.x + r.w - 8 - this.font.width(s), ty, Theme.subText(), false);

                int tx = r.x + 8;
                int tw = r.w - 16;
                int by = r.y + r.h - 7;
                double t = Math.max(0.0, Math.min(1.0, (v - r.min) / (r.max - r.min)));
                int fill = (int) (tw * t);
                Draw.roundRect(g, tx, by, tw, 3, 1, Theme.muted());
                Draw.roundRect(g, tx, by, Math.max(2, fill), 3, 1, Theme.accent());
                int kn = (hover || active == r) ? 8 : 6;
                Draw.roundRect(g, tx + fill - kn / 2, by + 1 - kn / 2, kn, kn, kn / 2, 0xFFFFFFFF);
            }
            case CYCLE -> {
                String s = r.names[Math.floorMod(r.getI.getAsInt(), r.names.length)];
                int chipW = this.font.width(s) + 10;
                int chipX = r.x + r.w - 6 - chipW;
                Draw.roundRect(g, chipX, r.y + (r.h - 12) / 2, chipW, 12, 4, Theme.chip());
                g.drawString(this.font, s, chipX + 5, r.y + (r.h - 8) / 2, Theme.accent(), false);
            }
            case ACTION -> {
                String s = ">";
                g.drawString(this.font, s, r.x + r.w - 10 - this.font.width(s) / 2, ty, Theme.accent(), false);
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
