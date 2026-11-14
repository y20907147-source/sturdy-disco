package dev.portih;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

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
    public boolean spin = true;
    public float spinSpeed = 1.0f;
    public int spinAxis = 0;
    public boolean allItems = false;

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
