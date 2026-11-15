package dev.portih.gui;

import dev.portih.AutoSwap;
import dev.portih.Config;
import dev.portih.ConfigScreen;
import dev.portih.Effects;
import dev.portih.HudEditorScreen;
import dev.portih.TargetMarker;
import dev.portih.VictorySound;
import dev.portih.basefinder.BaseFinder;
import dev.portih.basefinder.BaseFinderConfig;
import dev.portih.training.TrainingDummy;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Реестр модулей ClickGUI. Каждый модуль - это обёртка над полями Config / BaseFinderConfig:
 * лямбды всегда обращаются к Config.get() (а не к сохранённой ссылке), потому что "Сбросить всё"
 * подменяет экземпляр конфига.
 */
public final class ModuleManager {
    private ModuleManager() {}

    private static List<Module> all;

    private static final String[] ESP_STYLES = {"Рамка", "Уголки"};
    private static final String[] TRACERS = {"Выкл", "Только цель", "Все игроки"};
    private static final String[] BF_TRACERS = {"Выкл", "К скоплениям", "Ко всем рамкам"};
    private static final String[] SPIN_MODES = {"По удару", "Постоянно"};
    private static final String[] TRIGGERS = {"При попадании", "При клике"};
    private static final String[] AXES = {"Волчок (Y)", "Колесо (Z)", "Кувырок (X)"};
    private static final String[] QUALITY = {"Макс. FPS", "Баланс", "Красиво"};

    public static List<Module> all() {
        if (all == null) all = build();
        return all;
    }

    private static Config c() {
        return Config.get();
    }

    private static BaseFinderConfig bf() {
        return BaseFinderConfig.get();
    }

    private static NumberSetting pct(String label, java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
        return new NumberSetting(label, 0, 100, 1, 0, get, set);
    }

    private static List<Module> build() {
        List<Module> m = new ArrayList<>();

        // ================================================================ ВИЗУАЛ
        m.add(new Module(Category.VISUALS, "ESP игроков", "Player ESP", "Рамки игроков сквозь стены",
                () -> c().esp, v -> c().esp = v)
                .add(new ModeSetting("Вид рамки", ESP_STYLES, () -> c().espStyle, v -> c().espStyle = v))
                .add(new BoolSetting("Заливка", () -> c().espFill, v -> c().espFill = v))
                .add(new BoolSetting("Ник и здоровье", () -> c().espInfo, v -> c().espInfo = v))
                .add(new BoolSetting("Дистанция", () -> c().espDistance, v -> c().espDistance = v))
                .add(new ModeSetting("Трейсеры", TRACERS, () -> c().espTracers, v -> c().espTracers = v))
                .add(new NumberSetting("Дальность", 16, 192, 1, 0, () -> c().espRange, v -> c().espRange = (float) v)));

        m.add(new Module(Category.VISUALS, "Маркер цели", "Target Marker", "Крутящийся квадрат на цели",
                () -> c().targetMark, v -> c().targetMark = v)
                .add(new ModeSetting("Стиль", TargetMarker.STYLE_NAMES, () -> c().markStyle, v -> c().markStyle = v))
                .add(new NumberSetting("Размер", 0.4, 2.5, 0.05, 2, () -> c().markSize, v -> c().markSize = (float) v))
                .add(new NumberSetting("Скорость", 0.1, 3.0, 0.1, 1, () -> c().markSpeed, v -> c().markSpeed = (float) v))
                .add(new BoolSetting("Цвет по здоровью", () -> c().markHealthColor, v -> c().markHealthColor = v)));

        m.add(new Module(Category.VISUALS, "Инфо о цели", "Target Info", "Броня, яблоки и тотемы цели",
                () -> c().targetEsp, v -> c().targetEsp = v)
                .add(new BoolSetting("Броня", () -> c().targetArmor, v -> c().targetArmor = v))
                .add(new BoolSetting("Яблоки", () -> c().targetApples, v -> c().targetApples = v))
                .add(new BoolSetting("Тотемы", () -> c().targetTotems, v -> c().targetTotems = v)));

        m.add(new Module(Category.VISUALS, "Цифры урона", "Damage Numbers", "Всплывающие -N над целью",
                () -> c().damagePops, v -> c().damagePops = v));

        // --- поиск построек (BaseFinder) ---
        m.add(new Module(Category.VISUALS, "Поиск построек", "Base Finder", "Рамки контейнеров и блоков сквозь стены",
                () -> bf().enabled, v -> BaseFinder.setEnabled(Minecraft.getInstance(), v))
                .add(new BoolSetting("Указатель направления", () -> bf().compass, v -> bf().compass = v))
                .add(new NumberSetting("Целей в указателе", 1, 5, 1, 0, () -> bf().compassCount, v -> bf().compassCount = (int) Math.round(v)))
                .add(new ModeSetting("Трейсеры", BF_TRACERS, () -> bf().tracers, v -> bf().tracers = v))
                .add(new BoolSetting("Трейсер от прицела", () -> bf().tracerFromCrosshair, v -> bf().tracerFromCrosshair = v))
                .add(new NumberSetting("Дальность", 16, 256, 1, 0, () -> bf().range, v -> bf().range = (int) Math.round(v)))
                .add(new NumberSetting("Макс. рамок", 20, 1000, 10, 0, () -> bf().maxBoxes, v -> bf().maxBoxes = (int) Math.round(v)))
                .add(new NumberSetting("Порог скопления", 1, 100, 1, 0, () -> bf().clusterThreshold,
                        v -> bf().clusterThreshold = (int) Math.round(v)))
                .add(new ButtonSetting("Отчёт в чат", () -> BaseFinder.report(Minecraft.getInstance()))));

        Module targets = new Module(Category.VISUALS, "Цели поиска", "Finder Targets", "Какие группы блоков искать", null, null);
        Module prio = new Module(Category.VISUALS, "Указатель: цели", "Compass Targets", "Что показывать стрелкой-направлением", null, null);
        Module colors = new Module(Category.VISUALS, "Цвета поиска", "Finder Colors", "Цвет рамки для каждой группы", null, null);
        List<BaseFinderConfig.Group> groups = bf().groups;
        for (int i = 0; i < groups.size(); i++) {
            final int gi = i;
            String gname = groups.get(i).name;
            targets.add(new BoolSetting(gname, () -> grp(gi).enabled, v -> {
                grp(gi).enabled = v;
                BaseFinder.rebuildLookup();
            }));
            prio.add(new BoolSetting(gname, () -> grp(gi).priority, v -> grp(gi).priority = v));
            colors.add(new ColorSetting(gname, () -> grp(gi).color, v -> grp(gi).color = v & 0xFFFFFF));
        }
        m.add(targets);
        m.add(prio);
        m.add(colors);

        // ================================================================ HUD
        m.add(new Module(Category.HUD, "HUD", "HUD", "Общий переключатель панелей",
                () -> c().hudEnabled, v -> c().hudEnabled = v)
                .add(new BoolSetting("Плашка (ник/fps/время)", () -> c().panelInfo, v -> c().panelInfo = v))
                .add(new BoolSetting("Панель зелий", () -> c().panelPotions, v -> c().panelPotions = v))
                .add(new BoolSetting("Панель клавиш", () -> c().panelKeybinds, v -> c().panelKeybinds = v))
                .add(new BoolSetting("Панель кулдаунов", () -> c().panelCooldowns, v -> c().panelCooldowns = v))
                .add(new BoolSetting("Крестик при попадании", () -> c().hitMarker, v -> c().hitMarker = v)));

        m.add(new Module(Category.HUD, "Таргет HUD", "Target HUD", "Здоровье и имя цели",
                () -> c().targetHud, v -> c().targetHud = v)
                .add(new BoolSetting("Победа / поражение", () -> c().winLose, v -> c().winLose = v))
                .add(pct("Позиция X, %", () -> c().targetX * 100, v -> c().targetX = (float) (v / 100)))
                .add(pct("Позиция Y, %", () -> c().targetY * 100, v -> c().targetY = (float) (v / 100))));

        m.add(new Module(Category.HUD, "Кейстроки", "Keystrokes", "WASD, мышь и CPS",
                () -> c().keystrokes, v -> c().keystrokes = v)
                .add(new BoolSetting("Мышь", () -> c().showMouse, v -> c().showMouse = v))
                .add(new BoolSetting("КПС", () -> c().showCps, v -> c().showCps = v))
                .add(new BoolSetting("Прыжок", () -> c().showSpace, v -> c().showSpace = v))
                .add(pct("Позиция X, %", () -> c().keysX * 100, v -> c().keysX = (float) (v / 100)))
                .add(pct("Позиция Y, %", () -> c().keysY * 100, v -> c().keysY = (float) (v / 100))));

        m.add(new Module(Category.HUD, "Редактор HUD", "HUD Editor", "Перетаскивание панелей мышью", null, null)
                .add(new ButtonSetting("Открыть редактор", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.setScreen(new HudEditorScreen(mc.screen));
                })));

        // ================================================================ ИГРОК
        m.add(new Module(Category.PLAYER, "Тренировочный манекен", "Training Dummy",
                "Неподвижная мишень в воздухе - видна только вам, не бьёт и не существует на сервере",
                () -> TrainingDummy.enabled, v -> TrainingDummy.setEnabled(Minecraft.getInstance(), v))
                .add(new NumberSetting("Дистанция", 1.5, 6.0, 0.5, 1, () -> TrainingDummy.distance, v -> TrainingDummy.distance = (float) v))
                .add(new NumberSetting("Размер", 0.4, 1.6, 0.1, 1, () -> TrainingDummy.size, v -> TrainingDummy.size = (float) v))
                .add(new ButtonSetting("Переставить (M)", () -> TrainingDummy.place(Minecraft.getInstance()))));

        m.add(new Module(Category.PLAYER, "Автосвап", "Auto Swap", "Смена предмета в левой руке по кнопке G",
                () -> c().autoSwap, v -> c().autoSwap = v)
                .add(new ModeSetting("Первый", AutoSwap.NAMES, () -> c().swapA, v -> c().swapA = v))
                .add(new ModeSetting("Второй", AutoSwap.NAMES, () -> c().swapB, v -> c().swapB = v)));

        m.add(new Module(Category.PLAYER, "Авто-тотем", "Auto Totem", "Ставит тотем в пустую левую руку",
                () -> c().autoTotem, v -> c().autoTotem = v));

        m.add(new Module(Category.PLAYER, "Невидимки", "Invisible ESP", "Подсветка и локатор невидимых игроков",
                () -> c().invisEsp, v -> c().invisEsp = v)
                .add(new BoolSetting("Локатор у прицела", () -> c().invisLocator, v -> c().invisLocator = v))
                .add(new NumberSetting("Дальность", 16, 128, 1, 0, () -> c().invisRange, v -> c().invisRange = (float) v)));

        m.add(new Module(Category.PLAYER, "Маленький меч", "Small Sword", "Уменьшенный предмет в руке",
                () -> c().smallSword, v -> c().smallSword = v)
                .add(new NumberSetting("Размер", 0.1, 1.5, 0.05, 2, () -> c().swordScale, v -> c().swordScale = (float) v))
                .add(new NumberSetting("Высота", -0.6, 0.6, 0.05, 2, () -> c().swordY, v -> c().swordY = (float) v))
                .add(new BoolSetting("Все предметы", () -> c().allItems, v -> c().allItems = v))
                .add(new BoolSetting("Без взмаха", () -> c().noSwing, v -> c().noSwing = v))
                .add(new BoolSetting("Без опускания", () -> c().noDip, v -> c().noDip = v)));

        m.add(new Module(Category.PLAYER, "Кручение меча", "Sword Spin", "Анимация вращения предмета",
                () -> c().spin, v -> c().spin = v)
                .add(new ModeSetting("Режим", SPIN_MODES, () -> c().spinMode, v -> c().spinMode = v))
                .add(new ModeSetting("Триггер", TRIGGERS, () -> c().spinTrigger, v -> c().spinTrigger = v))
                .add(new ModeSetting("Ось", AXES, () -> c().spinAxis, v -> c().spinAxis = v))
                .add(new NumberSetting("Длительность, с", 0.1, 1.5, 0.05, 2, () -> c().spinDuration, v -> c().spinDuration = (float) v))
                .add(new NumberSetting("Оборотов", 1, 5, 1, 0, () -> c().spinTurns, v -> c().spinTurns = (int) Math.round(v)))
                .add(new NumberSetting("Скорость, об/с", 0.1, 5.0, 0.1, 1, () -> c().spinSpeed, v -> c().spinSpeed = (float) v)));

        // ================================================================ РАЗНОЕ
        m.add(new Module(Category.MISC, "Частицы удара", "Hit Particles", "Частицы при попадании",
                () -> c().hitParticles, v -> c().hitParticles = v)
                .add(new ModeSetting("Тип", Effects.PARTICLE_NAMES, () -> c().particleType, v -> c().particleType = v))
                .add(new NumberSetting("Количество", 1, 30, 1, 0, () -> c().particleCount, v -> c().particleCount = (int) Math.round(v)))
                .add(new BoolSetting("Частицы победы", () -> c().victoryParticles, v -> c().victoryParticles = v)));

        m.add(new Module(Category.MISC, "Вспышка экрана", "Hit Flash", "Короткая вспышка при ударе",
                () -> c().hitFlash, v -> c().hitFlash = v)
                .add(new NumberSetting("Сила", 0.1, 1.0, 0.05, 2, () -> c().flashStrength, v -> c().flashStrength = (float) v)));

        m.add(new Module(Category.MISC, "Звук удара", "Hit Sound", "Звук при попадании",
                () -> c().hitSound, v -> c().hitSound = v)
                .add(new ModeSetting("Звук", Effects.HIT_SOUND_NAMES, () -> c().hitSoundIndex, v -> {
                    c().hitSoundIndex = v;
                    c().hitCustom = "";
                }))
                .add(new NumberSetting("Громкость", 0, 2, 0.05, 2, () -> c().hitVolume, v -> c().hitVolume = (float) v))
                .add(new ButtonSetting("Проверить", Effects::playHit)));

        m.add(new Module(Category.MISC, "Звук победы", "Victory Sound", "Звук при убийстве цели",
                () -> c().victorySound, v -> c().victorySound = v)
                .add(new ModeSetting("Звук", VictorySound.NAMES, () -> c().soundIndex, v -> {
                    c().soundIndex = v;
                    c().victoryCustom = "";
                }))
                .add(new NumberSetting("Громкость", 0, 2, 0.05, 2, () -> c().volume, v -> c().volume = (float) v))
                .add(new NumberSetting("Тон", 0.5, 2, 0.05, 2, () -> c().pitch, v -> c().pitch = (float) v))
                .add(new ButtonSetting("Проверить", VictorySound::play)));

        m.add(new Module(Category.MISC, "Оповещения ивентов", "Event Alerts", "Плашка при ивенте на сервере",
                () -> c().eventAlerts, v -> c().eventAlerts = v)
                .add(new BoolSetting("Звук", () -> c().eventSound, v -> c().eventSound = v))
                .add(new NumberSetting("Длительность, с", 2, 15, 1, 0, () -> c().eventDuration, v -> c().eventDuration = (float) v))
                .add(new ButtonSetting("Проверить", dev.portih.EventAlert::test)));

        m.add(new Module(Category.MISC, "Лента действий", "Action Feed", "Что делает цель: ест, кидает зелья",
                () -> c().actionFeed, v -> c().actionFeed = v));

        // ================================================================ НАСТРОЙКИ
        m.add(new Module(Category.SETTINGS, "Интерфейс", "Interface", "Цвет и вид этого меню", null, null)
                .add(new ColorSetting("Акцент", () -> c().guiAccent, v -> c().guiAccent = v & 0xFFFFFF))
                .add(new NumberSetting("Прозрачность окна", 0.5, 1.0, 0.05, 2, () -> c().guiOpacity, v -> c().guiOpacity = (float) v))
                .add(new ModeSetting("Качество эффектов", QUALITY, () -> c().quality, v -> c().quality = v)));

        m.add(new Module(Category.SETTINGS, "Меню", "Menu", "Другое меню и сброс", null, null)
                .add(new ButtonSetting("Классическое меню", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.setScreen(new ConfigScreen(mc.screen));
                }))
                .add(new ButtonSetting("Сбросить всё", Config::reset)));

        return m;
    }

    /** Группа блоков по индексу (с защитой, если список в JSON изменили). */
    private static BaseFinderConfig.Group grp(int i) {
        List<BaseFinderConfig.Group> g = bf().groups;
        return g.get(Math.max(0, Math.min(g.size() - 1, i)));
    }
}
