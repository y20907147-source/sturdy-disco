package dev.portih;

public final class Theme {
    public static final int[] ACCENTS = {
            0xFF3B82F6, 0xFF22C55E, 0xFFEF4444, 0xFFA855F7, 0xFFF97316, 0xFFEAB308
    };
    public static final String[] ACCENT_NAMES = {
            "Синий", "Зелёный", "Красный", "Фиолетовый", "Оранжевый", "Жёлтый"
    };

    private Theme() {}

    public static int accent() {
        return ACCENTS[Math.floorMod(Config.get().accent, ACCENTS.length)];
    }

    public static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Полупрозрачная панель худа. */
    public static int panel() {
        return withAlpha(Config.get().lightTheme ? 0xF1F5F9 : 0x0F131A, Config.get().panelOpacity);
    }

    /** Непрозрачная панель меню. */
    public static int panelSolid() {
        return 0xFF000000 | (Config.get().lightTheme ? 0xF1F5F9 : 0x12161D);
    }

    public static int sidebar() {
        return 0xFF000000 | (Config.get().lightTheme ? 0xE2E8F0 : 0x0C0F14);
    }

    public static int keyBg() {
        return withAlpha(Config.get().lightTheme ? 0xE2E8F0 : 0x1F2937, Math.max(0.55f, Config.get().panelOpacity));
    }

    public static int text() {
        return Config.get().lightTheme ? 0xFF111827 : 0xFFF9FAFB;
    }

    public static int subText() {
        return Config.get().lightTheme ? 0xFF4B5563 : 0xFF9CA3AF;
    }
}
