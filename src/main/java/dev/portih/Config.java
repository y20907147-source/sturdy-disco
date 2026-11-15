package dev.portih;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 2;
    private static Config instance = new Config();

    /** Версия формата конфига (для миграции старых настроек). */
    public int version = 0;

    // ---- меню ----
    public int lastTab = 0;

    // ---- вид ----
    public boolean lightTheme = false;
    public int accent = 0;
    public float panelOpacity = 0.80f;
    public boolean hudEnabled = true;
    public boolean winLose = true;       // индикатор W/L в таргет худе

    // ---- действия цели и оповещения ----
    public boolean actionFeed = true;    // лента: съел яблоко, кинул зелье...
    public boolean eventAlerts = true;   // плашка "опасность + название ивента"
    public boolean eventSound = true;
    public float eventDuration = 6.0f;
    /** Слова через запятую: если сообщение сервера содержит одно из них, показываем оповещение. */
    public String eventKeywords = "ивент,эвент,event,метеорит,аирдроп,airdrop";

    // ---- бой ----
    public boolean invisEsp = true;       // подсветка невидимых игроков сквозь стены
    public boolean invisLocator = true;   // стрелки-локатор вокруг прицела
    public float invisRange = 64f;
    public boolean autoSwap = true;       // смена предмета в левой руке по кнопке
    public int swapA = 0;                 // индекс в AutoSwap.NAMES
    public int swapB = 1;
    public boolean autoTotem = false;     // сам ставит тотем в левую руку, когда она пуста

        // ---- ESP ----
    public boolean esp = true;            // рамки игроков сквозь стены
    public boolean espInfo = true;        // ник + здоровье
    public float espRange = 96f;
    public boolean targetEsp = true;      // инфо о цели рядом с рамкой
    public boolean targetArmor = true;
    public boolean targetApples = true;
    public boolean targetTotems = true;
    
    // ---- таргет худ ----
    public boolean targetHud = true;
    public float targetX = 0.50f;
    public float targetY = 0.62f;

    // ---- кейстроки ----
    public boolean keystrokes = true;
    public boolean showMouse = true;
    public boolean showSpace = true;
    public boolean showCps = true;
    public float keysX = 0.02f;
    public float keysY = 0.45f;

    // ---- панели (инфо, зелья, клавиши, кулдауны) ----
    public boolean panelInfo = true;
    public boolean panelPotions = true;
    public boolean panelKeybinds = true;
    public boolean panelCooldowns = true;
    public float infoX = 0.40f;
    public float infoY = 0.01f;
    public float potionsX = 0.01f;
    public float potionsY = 0.04f;
    public float keybindsX = 0.01f;
    public float keybindsY = 0.27f;
    public float cooldownsX = 0.70f;
    public float cooldownsY = 0.01f;

    // ---- меч ----
    public boolean smallSword = true;
    public float swordScale = 0.60f;
    public float swordY = 0.0f;
    public boolean noSwing = true;       // убрать ванильный взмах меча
    public boolean noDip = true;         // не опускать меч после удара
    public boolean spin = true;
    public int spinMode = 0;             // 0 = по удару, 1 = постоянно
    public int spinTrigger = 0;          // 0 = при попадании, 1 = при клике
    public float spinDuration = 0.60f;
    public int spinTurns = 1;
    public float spinSpeed = 1.0f;
    public int spinAxis = 1;             // 0 = Y (волчок), 1 = Z (колесо), 2 = X (кувырок)
    public boolean allItems = false;

    // ---- эффекты ----
    public boolean hitParticles = true;
    public int particleType = 0;
    public int particleCount = 8;
    public boolean hitFlash = false;
    public float flashStrength = 0.5f;
    public boolean hitSound = false;
    public int hitSoundIndex = 0;
    public String hitCustom = "";        // имя .wav из config/portihmod/sounds, пусто = встроенный
    public float hitVolume = 1.0f;
    public boolean victoryParticles = true;

    // ---- звук победы ----
    public boolean victorySound = true;
    public int soundIndex = 0;
    public String victoryCustom = "";    // имя .wav из config/portihmod/sounds, пусто = встроенный
    public float volume = 1.0f;
    public float pitch = 1.0f;

    public static Config get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("portihmod.json");
    }

    public static void load() {
        boolean existed = false;
        try {
            Path f = file();
            existed = Files.exists(f);
            if (existed) {
                Config c = GSON.fromJson(Files.readString(f), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            System.err.println("[PortihMod] Не удалось прочитать конфиг: " + e);
        }

        if (!existed) {
            instance.version = CURRENT_VERSION;
        } else if (instance.version < CURRENT_VERSION) {
            migrate(instance);
        }
        if (instance.victoryCustom == null) instance.victoryCustom = "";
        if (instance.hitCustom == null) instance.hitCustom = "";
        if (instance.eventKeywords == null) instance.eventKeywords = "";
        save();
    }

    /** Старые настройки кручения давали "круг не на месте": ставим исправленные значения. */
    private static void migrate(Config c) {
        if (c.version < 2) {
            c.spinAxis = 1;
            c.spinDuration = 0.60f;
            c.noSwing = true;
            c.noDip = true;
        }
        c.version = CURRENT_VERSION;
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(instance));
        } catch (Exception e) {
            System.err.println("[PortihMod] Не удалось сохранить конфиг: " + e);
        }
    }

    public static void reset() {
        instance = new Config();
        instance.version = CURRENT_VERSION;
        save();
    }
}
