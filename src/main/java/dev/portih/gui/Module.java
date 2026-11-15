package dev.portih.gui;

import dev.portih.Draw;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Карточка модуля: заголовок (название + описание + переключатель) и раскрывающийся список настроек.
 * Состояние модуля не хранится здесь: геттеры/сеттеры ведут в Config, поэтому сохранение остаётся прежним.
 */
public final class Module {
    public static final int HEADER = 40;

    public final Category category;
    public final String name;
    private final String alias;          // английское название для поиска
    private final String desc;
    private final BooleanSupplier getOn;   // null = у модуля нет главного переключателя
    private final Consumer<Boolean> setOn;
    private final List<GuiComponent> settings = new ArrayList<>();

    private boolean expanded;
    private float expand;
    private float hover;
    private float onAnim = -1f;
    private float x, y, w;
    private String descCache = "";
    private float descCacheW = -1f;

    public Module(Category category, String name, String alias, String desc,
                  BooleanSupplier getOn, Consumer<Boolean> setOn) {
        this.category = category;
        this.name = name;
        this.alias = alias;
        this.desc = desc;
        this.getOn = getOn;
        this.setOn = setOn;
    }

    public Module add(GuiComponent s) {
        settings.add(s);
        return this;
    }

    /** Поиск по названию, английскому названию и описанию (q уже в нижнем регистре). */
    public boolean matches(String q) {
        return name.toLowerCase(Locale.ROOT).contains(q)
                || alias.toLowerCase(Locale.ROOT).contains(q)
                || desc.toLowerCase(Locale.ROOT).contains(q);
    }

    private float contentHeight() {
        float h = 8;
        for (GuiComponent s : settings) h += s.height() + 4;
        return h;
    }

    public float height() {
        return HEADER + (settings.isEmpty() ? 0f : contentHeight() * expand);
    }

    public float top() { return y; }

    public boolean contains(int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + height();
    }

    /** Анимации, влияющие на раскладку (вызывается каждый кадр для всех карточек). */
    public void update(float dt) {
        expand = AnimationUtil.approach(expand, expanded ? 1f : 0f, dt, 14f);
        float target = getOn != null && getOn.getAsBoolean() ? 1f : 0f;
        onAnim = onAnim < 0f ? target : AnimationUtil.approach(onAnim, target, dt, 14f);
    }

    public void layout(float x, float y, float w) {
        this.x = x;
        this.y = y;
        this.w = w;
        float cy = y + HEADER + 4;
        for (GuiComponent s : settings) {
            s.x = x + 12;
            s.y = cy;
            s.w = w - 24;
            cy += s.height() + 4;
        }
    }

    private String fitDesc(float maxW) {
        if (maxW != descCacheW) {
            descCacheW = maxW;
            String s = desc;
            if (RenderUtil.width(s) * 0.75f > maxW) {
                while (s.length() > 1 && RenderUtil.width(s + "...") * 0.75f > maxW) s = s.substring(0, s.length() - 1);
                s = s + "...";
            }
            descCache = s;
        }
        return descCache;
    }

    public void draw(int mx, int my, float dt) {
        float h = height();
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h && RenderUtil.inClip(my);
        hover = AnimationUtil.approach(hover, hov ? 1f : 0f, dt, 14f);
        int acc = RenderUtil.accent();

        // карточка: #121216 90%, контур #24242C, при включении/наведении контур теплеет к акценту
        RenderUtil.rect(x, y, w, h, 10, RenderUtil.bg(Draw.lerpColor(RenderUtil.BG_CARD, RenderUtil.BG_CARD_HOVER, hover), 0.90f));
        RenderUtil.outline(x, y, w, h, 10,
                Draw.lerpColor(RenderUtil.OUTLINE, acc, Math.min(1f, onAnim * 0.55f + hover * 0.25f)));

        // заголовок
        RenderUtil.text(name, x + 12, y + 9, Draw.lerpColor(RenderUtil.TEXT, acc, onAnim * 0.25f));
        RenderUtil.small(fitDesc(w - 24), x + 12, y + 24, RenderUtil.MUTED);

        if (getOn != null) RenderUtil.toggle(x + w - 46, y + 10, 34, 18, onAnim);

        if (!settings.isEmpty()) {          // три точки = есть настройки; подсвечиваются при раскрытии
            float dx = x + w - 27, dy = getOn != null ? y + 34 : y + 16;
            int dc = Draw.lerpColor(RenderUtil.MUTED, acc, expand);
            RenderUtil.rect(dx - 6, dy, 3, 3, 1, dc);
            RenderUtil.rect(dx - 1, dy, 3, 3, 1, dc);
            RenderUtil.rect(dx + 4, dy, 3, 3, 1, dc);
        }

        if (expand > 0.01f && !settings.isEmpty()) {
            boolean clip = expand < 0.995f;
            if (clip) RenderUtil.g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
            float old = RenderUtil.alpha;
            RenderUtil.alpha = old * expand;
            RenderUtil.fill(x + 12, y + HEADER, x + w - 12, y + HEADER + 1, RenderUtil.OUTLINE);
            for (GuiComponent s : settings) s.draw(mx, my, dt);
            RenderUtil.alpha = old;
            if (clip) RenderUtil.g.disableScissor();
        }
    }

    /**
     * Нажатие внутри карточки. Возвращает компонент, захвативший мышь (слайдер, цвет, ввод), либо null.
     * Клик по переключателю включает модуль; клик по остальной шапке раскрывает настройки.
     */
    public GuiComponent mouseDown(int mx, int my, int button) {
        if (!contains(mx, my)) return null;
        if (my < y + HEADER) {
            boolean onSwitch = getOn != null && button == 0 && mx >= x + w - 52 && my < y + 32;
            if (onSwitch || (settings.isEmpty() && getOn != null && button == 0)) {
                setOn.accept(!getOn.getAsBoolean());
            } else if (!settings.isEmpty()) {
                expanded = !expanded;
            }
            return null;
        }
        if (expand > 0.9f) {
            for (GuiComponent s : settings) {
                if (s.contains(mx, my)) return s.mouseDown(mx, my, button) ? s : null;
            }
        }
        return null;
    }
}
