package dev.portih;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayDeque;
import java.util.Locale;

/** Оповещение сверху экрана: знак опасности и название ивента. */
public final class EventAlert {
    private static final class Alert {
        String name;
        String sub;
        long start;
    }

    private static final ArrayDeque<Alert> QUEUE = new ArrayDeque<>();
    private static Alert current;
    private static String lastName = "";
    private static long lastNameMs = 0L;

    private EventAlert() {}

    // ------------------------------------------------------------ триггеры

    /** Сообщение сервера (чат/экшен-бар). Если в нём есть ключевое слово, показываем оповещение. */
    public static void onMessage(Component message, boolean overlay) {
        Config c = Config.get();
        if (!c.eventAlerts || c.eventKeywords == null || c.eventKeywords.isBlank()) return;
        String raw = message.getString().trim();
        if (raw.isEmpty() || raw.length() > 200) return;

        String low = raw.toLowerCase(Locale.ROOT);
        boolean hit = false;
        for (String k : c.eventKeywords.split(",")) {
            String key = k.trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && low.contains(key)) {
                hit = true;
                break;
            }
        }
        if (!hit) return;
        push(extractName(raw), "ИВЕНТ");
    }

    /** Достаёт название: из кавычек, после двоеточия, иначе берёт текст целиком. */
    private static String extractName(String raw) {
        String s = raw.replaceFirst("^\\[[^\\]]{1,24}\\]\\s*", "").trim();
        String[][] quotes = {{"«", "»"}, {"\"", "\""}, {"“", "”"}};
        for (String[] q : quotes) {
            int a = s.indexOf(q[0]);
            if (a >= 0) {
                int b = s.indexOf(q[1], a + 1);
                if (b > a + 1) return trim(s.substring(a + 1, b).trim());
            }
        }
        int colon = s.indexOf(':');
        if (colon >= 0 && colon < s.length() - 1) {
            String tail = s.substring(colon + 1).trim();
            if (!tail.isEmpty()) return trim(tail);
        }
        return trim(s);
    }

    private static String trim(String s) {
        return s.length() > 34 ? s.substring(0, 33) + ".." : s;
    }

    public static void test() {
        pushForce("Метеоритный дождь", "ИВЕНТ");
    }

    public static void push(String name, String sub) {
        long now = System.currentTimeMillis();
        if (name.equals(lastName) && now - lastNameMs < 8000L) return;
        pushForce(name, sub);
    }

    private static void pushForce(String name, String sub) {
        long now = System.currentTimeMillis();
        lastName = name;
        lastNameMs = now;
        Alert a = new Alert();
        a.name = name;
        a.sub = sub;
        if (current == null) {
            a.start = now;
            current = a;
            sound();
        } else {
            if (QUEUE.size() >= 3) QUEUE.pollFirst();
            QUEUE.addLast(a);
        }
    }

    private static void sound() {
        Config c = Config.get();
        if (!c.eventSound) return;
        Minecraft mc = Minecraft.getInstance();
        SoundEvent ev = SoundEvent.createVariableRangeEvent(
                Identifier.withDefaultNamespace("block.note_block.pling"));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, 0.6f, 0.6f));
    }

    // -------------------------------------------------------------- рендер

    public static void render(GuiGraphics g, Minecraft mc, Config c) {
        long now = System.currentTimeMillis();
        float life = Math.max(2f, c.eventDuration);

        if (current != null && (now - current.start) / 1000f > life) current = null;
        if (current == null && !QUEUE.isEmpty()) {
            current = QUEUE.pollFirst();
            current.start = now;
            sound();
        }
        if (current == null) return;

        float t = (now - current.start) / 1000f;
        float in = Draw.easeOutBack(t / 0.5f);
        float vis = Math.min(Draw.easeOut(t / 0.3f), Draw.clamp01((life - t) / 0.45f));
        if (vis < 0.03f) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        Component title = Component.literal(current.name).withStyle(ChatFormatting.BOLD);
        int nameW = mc.font.width(title);
        int subW = mc.font.width(current.sub);
        int textW = Math.max(nameW, subW);

        int h = 38;
        int w = Math.max(150, 12 + 26 + 10 + textW + 18);
        int x = (sw - w) / 2;
        int y = 10 + Math.round((in - 1f) * 22f);

        Draw.panel(g, x, y, w, h, 14, Draw.fade(0xF0000000 | (Theme.panelSolid() & 0xFFFFFF), vis), vis);

        // знак опасности: мягкая подложка + треугольник с восклицательным знаком
        float pulse = 0.82f + 0.18f * (float) Math.sin(t * 6.0);
        int bx = x + 12;
        int by = y + (h - 26) / 2;
        Draw.roundRect(g, bx, by, 26, 26, 13, Draw.fade(Theme.danger(), 0.16f * vis));
        int tcx = bx + 13;
        Draw.triangle(g, tcx, by + 6, 16, 14, Draw.fade(Theme.danger(), pulse * vis));
        g.drawString(mc.font, "!", tcx - mc.font.width("!") / 2, by + 11, Draw.fade(0xFFFFFFFF, vis), false);

        int tx = bx + 26 + 10;
        g.drawString(mc.font, current.sub, tx, y + 9, Draw.fade(Theme.danger(), vis), false);
        g.drawString(mc.font, title, tx, y + 20, Draw.fade(Theme.text(), vis), false);

        // тонкая полоска оставшегося времени
        int pw = w - 24;
        int left = Math.round(pw * (1f - Draw.clamp01(t / life)));
        Draw.pill(g, x + 12, y + h - 5, pw, 2, Draw.fade(Theme.track(), vis));
        if (left > 1) Draw.pill(g, x + 12, y + h - 5, left, 2, Draw.fade(Theme.danger(), 0.9f * vis));
    }
}
