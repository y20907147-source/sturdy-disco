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
        return 0xFF000000 | (Config.get().lightTheme ? 0xF8FAFC : 0x111418);
    }

    public static int border() {
        return Config.get().lightTheme ? 0xFFCBD5E1 : 0xFF2A2F3A;
    }

    /** Выключенный тумблер / пустая часть слайдера. */
    public static int muted() {
        return Config.get().lightTheme ? 0xFFCBD5E1 : 0xFF374151;
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

    /** Боковая панель меню. */
    public static int sidebar() {
        return Config.get().lightTheme ? 0xFFEEF2F7 : 0xFF0C0F13;
    }

    /** Карточка настройки. */
    public static int card() {
        return Config.get().lightTheme ? 0xFFEFF3F8 : 0xFF181C23;
    }

    public static int cardHover() {
        return Config.get().lightTheme ? 0xFFE2E8F0 : 0xFF212631;
    }

    public static int chip() {
        return Config.get().lightTheme ? 0xFFDDE3EC : 0xFF262C38;
    }
}
