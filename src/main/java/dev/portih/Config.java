package dev.portih;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    // ---- меню ----
    public int lastTab = 0;

    // ---- вид ----
    public boolean lightTheme = false;
    public int accent = 0;
    public float panelOpacity = 0.80f;
    public boolean hudEnabled = true;

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

    // ---- меч ----
    public boolean smallSword = true;
    public float swordScale = 0.60f;
    public float swordY = 0.0f;
    public boolean noSwing = true;       // убрать ванильный взмах меча
    public boolean noDip = true;         // не опускать меч после удара
    public boolean spin = true;
    public int spinMode = 0;             // 0 = по удару, 1 = постоянно
    public int spinTrigger = 0;          // 0 = при попадании, 1 = при клике
    public float spinDuration = 0.45f;
    public int spinTurns = 1;
    public float spinSpeed = 1.0f;
    public int spinAxis = 0;
    public boolean allItems = false;

    // ---- эффекты ----
    public boolean hitParticles = true;
    public int particleType = 0;
    public int particleCount = 8;
    public boolean hitFlash = false;
    public float flashStrength = 0.5f;
    public boolean hitSound = false;
    public int hitSoundIndex = 0;
    public float hitVolume = 1.0f;
    public boolean victoryParticles = true;

    // ---- звук победы ----
    public boolean victorySound = true;
    public int soundIndex = 0;
    public float volume = 1.0f;
    public float pitch = 1.0f;

    public static Config get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("portihmod.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Config c = GSON.fromJson(Files.readString(f), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            System.err.println("[PortihMod] Не удалось прочитать конфиг: " + e);
        }
        save();
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
        save();
    }
}
