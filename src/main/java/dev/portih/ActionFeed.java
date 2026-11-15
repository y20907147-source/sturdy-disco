package dev.portih;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;

/** Лента действий цели под таргет худом: "съел яблоко", "кинул зелье", "потерял тотем"... */
public final class ActionFeed {
    private static final class Ev {
        String text;
        int color;
        long born;
    }

    private static final float LIFE = 3.4f;
    private static final int MAX = 4;

    private static final ArrayList<Ev> EVENTS = new ArrayList<>();
    private static LivingEntity tracked;

    private static boolean usingPrev;
    private static Item useItem;
    private static boolean useFood;
    private static int useRemain;

    private static boolean swingPrev;
    private static Item mainPrev;
    private static boolean blockPrev;
    private static int totemsPrev;
    private static long lastHurtMs;
    private static long lastShieldMs;

    /** Время последнего события, чтобы худ не пропадал, пока что-то происходит. */
    public static long lastEventMs = 0L;

    private ActionFeed() {}

    private static int totems(LivingEntity e) {
        int n = 0;
        ItemStack a = e.getMainHandItem();
        ItemStack b = e.getOffhandItem();
        if (a.is(Items.TOTEM_OF_UNDYING)) n += a.getCount();
        if (b.is(Items.TOTEM_OF_UNDYING)) n += b.getCount();
        return n;
    }

    private static void reset(LivingEntity le) {
        tracked = le;
        EVENTS.clear();
        usingPrev = le.isUsingItem();
        useItem = null;
        useFood = false;
        useRemain = 99;
        swingPrev = le.swinging;
        mainPrev = le.getMainHandItem().getItem();
        blockPrev = le.isBlocking();
        totemsPrev = totems(le);
        lastHurtMs = 0L;
    }

    /** Вызывается каждый кадр для показываемой цели. */
    public static void update(LivingEntity le) {
        if (!Config.get().actionFeed) return;
        if (le != tracked) {
            reset(le);
            return;
        }
        long now = System.currentTimeMillis();

        // еда и зелья: событие, когда предмет реально доеден/допит
        boolean using = le.isUsingItem();
        if (using) {
            ItemStack s = le.getUseItem();
            useItem = s.getItem();
            useFood = s.has(DataComponents.FOOD);
            useRemain = le.getUseItemRemainingTicks();
        } else if (usingPrev && useItem != null && useRemain <= 6) {
            finishUse(useItem, useFood);
        }
        usingPrev = using;

        // броски: начало взмаха руки с зельем / жемчугом / зарядом ветра
        boolean swing = le.swinging;
        Item mainNow = le.getMainHandItem().getItem();
        if (swing && !swingPrev) {
            Item it = isThrowable(mainPrev) ? mainPrev : mainNow;
            if (it == Items.SPLASH_POTION || it == Items.LINGERING_POTION) push("Кинул зелье", 0xFFF472B6);
            else if (it == Items.ENDER_PEARL) push("Кинул жемчуг", 0xFF38BDF8);
            else if (it == Items.WIND_CHARGE) push("Заряд ветра", 0xFF67E8F9);
        }
        if (!swing) mainPrev = mainNow;
        swingPrev = swing;

        // щит
        boolean block = le.isBlocking();
        if (block && !blockPrev && now - lastShieldMs > 1500L) {
            lastShieldMs = now;
            push("Поднял щит", 0xFF94A3B8);
        }
        blockPrev = block;

        // тотем: стало меньше тотемов в руках сразу после урона
        if (le.hurtTime > 0) lastHurtMs = now;
        int t = totems(le);
        if (t < totemsPrev && now - lastHurtMs < 1200L && le.isAlive()) {
            push("Потерял тотем", 0xFFFACC15);
        }
        totemsPrev = t;
    }

    private static boolean isThrowable(Item i) {
        return i == Items.SPLASH_POTION || i == Items.LINGERING_POTION
                || i == Items.ENDER_PEARL || i == Items.WIND_CHARGE;
    }

    private static void finishUse(Item it, boolean food) {
        if (it == Items.GOLDEN_APPLE) push("Съел яблоко", 0xFFFFC857);
        else if (it == Items.ENCHANTED_GOLDEN_APPLE) push("Съел зач. яблоко", 0xFFFFA94D);
        else if (it == Items.CHORUS_FRUIT) push("Съел хорус", 0xFFC084FC);
        else if (it == Items.POTION) push("Выпил зелье", 0xFFA78BFA);
        else if (food) push("Поел", 0xFF86EFAC);
    }

    private static void push(String text, int color) {
        long now = System.currentTimeMillis();
        if (!EVENTS.isEmpty()) {
            Ev last = EVENTS.get(EVENTS.size() - 1);
            if (last.text.equals(text) && now - last.born < 600L) return;
        }
        Ev e = new Ev();
        e.text = text;
        e.color = color;
        e.born = now;
        EVENTS.add(e);
        while (EVENTS.size() > MAX) EVENTS.remove(0);
        lastEventMs = now;
    }

    public static void clear() {
        EVENTS.clear();
        tracked = null;
    }

    /**
     * Рисует ленту под таргет худом. cx - центр по горизонтали, y - верх первой плашки.
     * sample = true: показать пример (режим редактора).
     */
    public static void render(GuiGraphics g, Minecraft mc, int cx, int y, float vis, boolean sample) {
        if (!Config.get().actionFeed || vis < 0.03f) return;
        long now = System.currentTimeMillis();

        for (Iterator<Ev> it = EVENTS.iterator(); it.hasNext(); ) {
            if ((now - it.next().born) / 1000f > LIFE) it.remove();
        }

        int rowY = y;
        if (EVENTS.isEmpty() && sample) {
            pill(g, mc, cx, rowY, "Съел яблоко", 0xFFFFC857, 1f, 0f, vis);
            rowY += 20;
            pill(g, mc, cx, rowY, "Кинул зелье", 0xFFF472B6, 1f, 0f, vis);
            return;
        }

        // новые сверху
        for (int i = EVENTS.size() - 1; i >= 0; i--) {
            Ev e = EVENTS.get(i);
            float age = (now - e.born) / 1000f;
            float in = Draw.easeOutBack(age / 0.28f);
            float out = Draw.clamp01((LIFE - age) / 0.45f);
            float a = Math.min(Draw.clamp01(age / 0.18f), out) * vis;
            float slide = (1f - Math.min(1f, in)) * -6f;
            pill(g, mc, cx, rowY, e.text, e.color, a, slide, 1f);
            rowY += 20;
        }
    }

    private static void pill(GuiGraphics g, Minecraft mc, int cx, int y, String text, int dot,
                             float a, float slide, float extra) {
        a *= extra;
        if (a < 0.04f) return;
        int tw = mc.font.width(text);
        int w = 10 + 8 + 6 + tw + 8;
        int h = 17;
        int x = cx - w / 2;
        int yy = y + Math.round(slide);
        Draw.panel(g, x, yy, w, h, h / 2, Draw.fade(Theme.panel(), a), a * 0.7f);
        Draw.circle(g, x + 11, yy + h / 2, 3, Draw.fade(dot | 0xFF000000, a));
        g.drawString(mc.font, text, x + 20, yy + (h - 8) / 2, Draw.fade(Theme.text(), a), false);
    }
}
