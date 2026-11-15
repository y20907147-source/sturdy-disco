package dev.portih.basefinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Настройки поиска построек. Хранятся отдельно: config/portihmod-basefinder.json.
 * Группы блоков и их цвета можно править прямо в файле (id блоков вида "minecraft:chest").
 */
public final class BaseFinderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BaseFinderConfig instance = new BaseFinderConfig();

    /** Одна группа целевых блоков: общий цвет, включение и список id. */
    public static final class Group {
        public String name;
        public boolean enabled = true;
        public int color;                 // 0xRRGGBB
        public List<String> blocks = new ArrayList<>();

        public Group() {}

        public Group(String name, int rgb, String... ids) {
            this.name = name;
            this.color = rgb;
            for (String id : ids) blocks.add(id);
        }
    }

    public boolean enabled = false;          // главный переключатель (клавиша)
    public int range = 96;                   // дальность подсветки, блоков
    public int scanRadiusChunks = 8;         // радиус пересканирования вокруг игрока
    public int maxBoxes = 200;               // потолок рамок за кадр (FPS)
    public int tracers = 1;                  // 0 = выкл, 1 = к скоплениям, 2 = ко всем рамкам
    public boolean tracerFromCrosshair = true; // true: от прицела, false: от низа экрана
    public int clusterThreshold = 10;        // "крупное скопление" = больше N блоков в чанке
    public int maxPerChunk = 4096;           // защита памяти (например, огромные каменные стены)
    public int rescanSeconds = 15;           // как часто перепроверять чанки (0 = только при загрузке)
    public float scanBudgetMs = 2.0f;        // сколько мс в тик можно тратить на сканирование
    public List<Group> groups = defaults();

    public static BaseFinderConfig get() {
        return instance;
    }

    public static List<Group> defaults() {
        List<Group> g = new ArrayList<>();
        g.add(new Group("Контейнеры", 0xFFB020,
                "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
                "minecraft:hopper", "minecraft:dispenser", "minecraft:dropper"));
        g.add(new Group("Шалкеры", 0xE040FB,
                "minecraft:shulker_box", "minecraft:white_shulker_box", "minecraft:orange_shulker_box",
                "minecraft:magenta_shulker_box", "minecraft:light_blue_shulker_box", "minecraft:yellow_shulker_box",
                "minecraft:lime_shulker_box", "minecraft:pink_shulker_box", "minecraft:gray_shulker_box",
                "minecraft:light_gray_shulker_box", "minecraft:cyan_shulker_box", "minecraft:purple_shulker_box",
                "minecraft:blue_shulker_box", "minecraft:brown_shulker_box", "minecraft:green_shulker_box",
                "minecraft:red_shulker_box", "minecraft:black_shulker_box"));
        g.add(new Group("Эндер-сундуки", 0x00E5C8, "minecraft:ender_chest"));
        g.add(new Group("Печи и станки", 0xFF6B3D,
                "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
                "minecraft:crafting_table", "minecraft:anvil", "minecraft:enchanting_table",
                "minecraft:brewing_stand"));
        g.add(new Group("Кровати", 0xFF4D6D,
                "minecraft:white_bed", "minecraft:orange_bed", "minecraft:magenta_bed", "minecraft:light_blue_bed",
                "minecraft:yellow_bed", "minecraft:lime_bed", "minecraft:pink_bed", "minecraft:gray_bed",
                "minecraft:light_gray_bed", "minecraft:cyan_bed", "minecraft:purple_bed", "minecraft:blue_bed",
                "minecraft:brown_bed", "minecraft:green_bed", "minecraft:red_bed", "minecraft:black_bed"));
        g.add(new Group("Обсидиан", 0x7C4DFF, "minecraft:obsidian", "minecraft:crying_obsidian"));
        g.add(new Group("Булыжник", 0x9AA5B1, "minecraft:cobblestone"));
        g.add(new Group("Доски", 0x66BB6A,
                "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
                "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
                "minecraft:mangrove_planks", "minecraft:cherry_planks", "minecraft:bamboo_planks",
                "minecraft:crimson_planks", "minecraft:warped_planks"));
        return g;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("portihmod-basefinder.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                BaseFinderConfig c = GSON.fromJson(Files.readString(f), BaseFinderConfig.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            System.err.println("[PortihMod/BaseFinder] Не удалось прочитать конфиг: " + e);
        }
        BaseFinderConfig c = instance;
        if (c.groups == null || c.groups.isEmpty()) c.groups = defaults();
        c.range = clamp(c.range, 16, 256);
        c.scanRadiusChunks = clamp(c.scanRadiusChunks, 2, 32);
        c.maxBoxes = clamp(c.maxBoxes, 20, 1500);
        c.tracers = clamp(c.tracers, 0, 2);
        c.clusterThreshold = clamp(c.clusterThreshold, 1, 4096);
        c.maxPerChunk = clamp(c.maxPerChunk, 64, 65536);
        c.rescanSeconds = clamp(c.rescanSeconds, 0, 600);
        c.scanBudgetMs = Math.max(0.5f, Math.min(10f, c.scanBudgetMs));
        save();
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(instance));
        } catch (Exception e) {
            System.err.println("[PortihMod/BaseFinder] Не удалось сохранить конфиг: " + e);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
