package dev.portih.gui;

/** Категории ClickGUI (порядок = порядок в боковой панели). */
public enum Category {
    VISUALS("Визуал"),
    HUD("HUD"),
    PLAYER("Игрок"),
    MISC("Разное"),
    SETTINGS("Настройки");

    public final String title;

    Category(String title) {
        this.title = title;
    }

    public static Category byIndex(int i) {
        Category[] v = values();
        return i >= 0 && i < v.length ? v[i] : v[0];
    }
}
