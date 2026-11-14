package dev.portih;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayDeque;
import java.util.Locale;

public final class Hud {
    private static final int K = 24;
    private static final int GAP = 3;
    private static final int TARGET_W = 140;
    private static final int TARGET_H = 40;

    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean wasLeft;
    private static boolean wasRight;

    private static LivingEntity shown;
    private static long shownUntil;

    // перетаскивание: 0 = нет, 1 = кейстроки, 2 = таргет худ
    private static int dragging = 0;
    private static int grabDX;
    private static int grabDY;
    private static boolean wasPressed;

    private Hud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitTracker.update(mc);
        if (mc.options.hideGui) return;

        Config c = Config.get();
        Effects.renderOverlay(g, mc, c);
        if (!c.hudEnabled) return;

        pollClicks(mc);
        boolean edit = mc.screen instanceof ChatScreen;
        updateDrag(mc, c, edit);

        if (c.keystrokes) {
            drawKeystrokes(g, mc, c);
            if (edit) outline(g, keysBounds(mc, c), dragging == 1);
        }
        if (c.targetHud) {
            drawTarget(g, mc, c, edit);
            if (edit) outline(g, targetBounds(mc, c), dragging == 2);
        }
        if (edit) {
            int sw = mc.getWindow().getGuiScaledWidth();
            String hint = "Редактор HUD: перетаскивай панели мышью";
            int tw = mc.font.width(hint);
            int hx = sw / 2 - tw / 2;
            g.fill(hx - 6, 4, hx + tw + 6, 18, Theme.panel());
            g.drawString(mc.font, hint, hx, 8, Theme.text(), false);
        }
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

    // ------------------------------------------------------- перетаскивание

    private static int[] keysBounds(Minecraft mc, Config c) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int w = 3 * K + 2 * GAP;
        int h = 2 * K + GAP;
        if (c.showMouse) h += GAP + (c.showCps ? 28 : K);
        if (c.showSpace) h += GAP + 16;
        return new int[]{(int) (c.keysX * sw), (int) (c.keysY * sh), w, h};
    }

    private static int[] targetBounds(Minecraft mc, Config c) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        return new int[]{(int) (c.targetX * sw) - TARGET_W / 2, (int) (c.targetY * sh), TARGET_W, TARGET_H};
    }

    private static boolean inside(double mx, double my, int[] b) {
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void updateDrag(Minecraft mc, Config c, boolean edit) {
        if (!edit) {
            if (dragging != 0) {
                dragging = 0;
                Config.save();
            }
            wasPressed = false;
            return;
        }

        boolean pressed = mc.mouseHandler.isLeftPressed();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double mx = mc.mouseHandler.xpos() * sw / Math.max(1, mc.getWindow().getScreenWidth());
        double my = mc.mouseHandler.ypos() * sh / Math.max(1, mc.getWindow().getScreenHeight());

        if (pressed && !wasPressed) {
            int[] tb = targetBounds(mc, c);
            int[] kb = keysBounds(mc, c);
            if (c.targetHud && inside(mx, my, tb)) {
                dragging = 2;
                grabDX = (int) (mx - tb[0]);
                grabDY = (int) (my - tb[1]);
            } else if (c.keystrokes && inside(mx, my, kb)) {
                dragging = 1;
                grabDX = (int) (mx - kb[0]);
                grabDY = (int) (my - kb[1]);
            }
        }

        if (dragging != 0 && pressed) {
            if (dragging == 1) {
                int[] b = keysBounds(mc, c);
                c.keysX = clamp((float) ((mx - grabDX) / sw), 0f, Math.max(0f, (sw - b[2]) / (float) sw));
                c.keysY = clamp((float) ((my - grabDY) / sh), 0f, Math.max(0f, (sh - b[3]) / (float) sh));
            } else {
                float half = TARGET_W / 2f / sw;
                c.targetX = clamp((float) ((mx - grabDX + TARGET_W / 2.0) / sw), half, 1f - half);
                c.targetY = clamp((float) ((my - grabDY) / sh), 0f, Math.max(0f, (sh - TARGET_H) / (float) sh));
            }
        }

        if (!pressed && dragging != 0) {
            dragging = 0;
            Config.save();
        }
        wasPressed = pressed;
    }

    private static void outline(GuiGraphics g, int[] b, boolean active) {
        int col = active ? 0xFFFFFFFF : Theme.accent();
        g.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1], col);
        g.fill(b[0] - 1, b[1] + b[3], b[0] + b[2] + 1, b[1] + b[3] + 1, col);
        g.fill(b[0] - 1, b[1], b[0], b[1] + b[3], col);
        g.fill(b[0] + b[2], b[1], b[0] + b[2] + 1, b[1] + b[3], col);
    }

    // ------------------------------------------------------------ кейстроки

    private static void drawKeystrokes(GuiGraphics g, Minecraft mc, Config c) {
        int[] b = keysBounds(mc, c);
        int x0 = b[0];
        int y0 = b[1];
        int total = b[2];

        key(g, mc, x0 + K + GAP, y0, K, K, "W", mc.options.keyUp.isDown());
        int y1 = y0 + K + GAP;
        key(g, mc, x0, y1, K, K, "A", mc.options.keyLeft.isDown());
        key(g, mc, x0 + K + GAP, y1, K, K, "S", mc.options.keyDown.isDown());
        key(g, mc, x0 + 2 * (K + GAP), y1, K, K, "D", mc.options.keyRight.isDown());

        int y = y1 + K + GAP;
        if (c.showMouse) {
            int mw = (total - GAP) / 2;
            int mh = c.showCps ? 28 : K;
            mouseKey(g, mc, c, x0, y, mw, mh, "ЛКМ", mc.options.keyAttack.isDown(), LEFT.size());
            mouseKey(g, mc, c, x0 + mw + GAP, y, mw, mh, "ПКМ", mc.options.keyUse.isDown(), RIGHT.size());
            y += mh + GAP;
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

    private static void drawTarget(GuiGraphics g, Minecraft mc, Config c, boolean edit) {
        long now = System.currentTimeMillis();
        Entity e = (mc.hitResult instanceof EntityHitResult r) ? r.getEntity() : null;
        if (e instanceof LivingEntity le && le != mc.player && le.isAlive()) {
            shown = le;
            shownUntil = now + 2000L;
        }
        boolean real = shown != null && now <= shownUntil && shown.isAlive();
        if (!real && !edit) return;

        String name;
        float hp;
        float max;
        float dist;
        if (real) {
            name = shown.getName().getString();
            hp = shown.getHealth();
            max = Math.max(1f, shown.getMaxHealth());
            dist = mc.player.distanceTo(shown);
        } else {
            name = "Игрок";
            hp = 14f;
            max = 20f;
            dist = 3.5f;
        }

        int[] b = targetBounds(mc, c);
        int x = b[0];
        int y = b[1];
        int w = b[2];
        int h = b[3];

        float ratio = Math.max(0f, Math.min(1f, hp / max));
        int barColor = ratio > 0.5f ? 0xFF22C55E : (ratio > 0.25f ? 0xFFEAB308 : 0xFFEF4444);

        g.fill(x, y, x + w, y + h, Theme.panel());
        g.fill(x, y, x + 2, y + h, Theme.accent());

        if (name.length() > 16) name = name.substring(0, 16);
        g.drawString(mc.font, name, x + 8, y + 5, Theme.text(), false);

        String distText = String.format(Locale.ROOT, "%.1fм", dist);
        g.drawString(mc.font, distText, x + w - 8 - mc.font.width(distText), y + 5, Theme.subText(), false);

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
