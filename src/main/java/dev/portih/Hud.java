package dev.portih;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayDeque;
import java.util.Locale;

public final class Hud {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean wasLeft;
    private static boolean wasRight;

    private static LivingEntity shown;
    private static long shownUntil;

    private Hud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !c.hudEnabled) return;

        pollClicks(mc);
        if (c.keystrokes) drawKeystrokes(g, mc, c);
        if (c.targetHud) drawTarget(g, mc, c);
    }

    // ------------------------------------------------------------------ CPS

    private static void pollClicks(Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean l = mc.screen == null && mc.options.keyAttack.isDown();
        boolean r = mc.screen == null && mc.options.keyUse.isDown();
        if (l && !wasLeft) LEFT.addLast(now);
        if (r && !wasRight) RIGHT.addLast(now);
        wasLeft = l;
        wasRight = r;
        trim(LEFT, now);
        trim(RIGHT, now);
    }

    private static void trim(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.pollFirst();
    }

    // ------------------------------------------------------------ кейстроки

    private static void drawKeystrokes(GuiGraphics g, Minecraft mc, Config c) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int k = 24;
        int gap = 3;
        int x0 = (int) (c.keysX * sw);
        int y0 = (int) (c.keysY * sh);
        int total = 3 * k + 2 * gap;

        key(g, mc, x0 + k + gap, y0, k, k, "W", mc.options.keyUp.isDown());
        int y1 = y0 + k + gap;
        key(g, mc, x0, y1, k, k, "A", mc.options.keyLeft.isDown());
        key(g, mc, x0 + k + gap, y1, k, k, "S", mc.options.keyDown.isDown());
        key(g, mc, x0 + 2 * (k + gap), y1, k, k, "D", mc.options.keyRight.isDown());

        int y = y1 + k + gap;
        if (c.showMouse) {
            int mw = (total - gap) / 2;
            int mh = c.showCps ? 28 : k;
            mouseKey(g, mc, c, x0, y, mw, mh, "ЛКМ", mc.options.keyAttack.isDown(), LEFT.size());
            mouseKey(g, mc, c, x0 + mw + gap, y, mw, mh, "ПКМ", mc.options.keyUse.isDown(), RIGHT.size());
            y += mh + gap;
        }
        if (c.showSpace) {
            key(g, mc, x0, y, total, 16, "ПРЫЖОК", mc.options.keyJump.isDown());
        }
    }

    private static void key(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, String label, boolean down) {
        int bg = down ? Theme.withAlpha(Theme.accent(), 0.92f) : Theme.keyBg();
        g.fill(x, y, x + w, y + h, bg);
        int fg = down ? 0xFFFFFFFF : Theme.text();
        center(g, mc.font, label, x + w / 2, y + (h - 8) / 2, fg);
    }

    private static void mouseKey(GuiGraphics g, Minecraft mc, Config c, int x, int y, int w, int h,
                                 String label, boolean down, int cps) {
        int bg = down ? Theme.withAlpha(Theme.accent(), 0.92f) : Theme.keyBg();
        g.fill(x, y, x + w, y + h, bg);
        int fg = down ? 0xFFFFFFFF : Theme.text();
        int sub = down ? 0xFFFFFFFF : Theme.subText();
        if (c.showCps) {
            center(g, mc.font, label, x + w / 2, y + 4, fg);
            center(g, mc.font, cps + " CPS", x + w / 2, y + h - 12, sub);
        } else {
            center(g, mc.font, label, x + w / 2, y + (h - 8) / 2, fg);
        }
    }

    // ------------------------------------------------------------ таргет худ

    private static void drawTarget(GuiGraphics g, Minecraft mc, Config c) {
        long now = System.currentTimeMillis();
        Entity e = (mc.hitResult instanceof EntityHitResult r) ? r.getEntity() : null;
        if (e instanceof LivingEntity le && le != mc.player && le.isAlive()) {
            shown = le;
            shownUntil = now + 2000L;
        }
        if (shown == null || now > shownUntil || !shown.isAlive()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int w = 140;
        int h = 40;
        int x = (int) (c.targetX * sw) - w / 2;
        int y = (int) (c.targetY * sh);

        float hp = shown.getHealth();
        float max = Math.max(1f, shown.getMaxHealth());
        float ratio = Math.max(0f, Math.min(1f, hp / max));
        int barColor = ratio > 0.5f ? 0xFF22C55E : (ratio > 0.25f ? 0xFFEAB308 : 0xFFEF4444);

        g.fill(x, y, x + w, y + h, Theme.panel());
        g.fill(x, y, x + 2, y + h, Theme.accent());

        String name = shown.getName().getString();
        if (name.length() > 16) name = name.substring(0, 16);
        g.drawString(mc.font, name, x + 8, y + 5, Theme.text(), false);

        String dist = String.format(Locale.ROOT, "%.1fм", mc.player.distanceTo(shown));
        g.drawString(mc.font, dist, x + w - 8 - mc.font.width(dist), y + 5, Theme.subText(), false);

        int bx = x + 8;
        int by = y + 18;
        int bw = w - 16;
        g.fill(bx, by, bx + bw, by + 6, Theme.withAlpha(0x000000, 0.45f));
        g.fill(bx, by, bx + (int) (bw * ratio), by + 6, barColor);

        String hpText = String.format(Locale.ROOT, "%.1f / %.1f HP", hp, max);
        g.drawString(mc.font, hpText, bx, y + 28, Theme.subText(), false);
    }

    // ----------------------------------------------------------------- util

    private static void center(GuiGraphics g, Font font, String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }
}
