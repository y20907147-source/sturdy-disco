package dev.portih;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Locale;

public final class Hud {
    private static final int K = 20;
    private static final int GAP = 2;
    private static final int SPACE_H = 8;
    private static final int TARGET_W = 120;
    private static final int TARGET_H = 28;

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
        boolean chat = mc.screen instanceof ChatScreen;
        boolean edit = chat || mc.screen instanceof HudEditorScreen;
        updateDrag(mc, c, edit);

        if (c.keystrokes) {
            drawKeystrokes(g, mc, c);
            if (edit) outline(g, keysBounds(mc, c), dragging == 1);
        }
        if (c.targetHud) {
            drawTarget(g, mc, c, edit);
            if (edit) outline(g, targetBounds(mc, c), dragging == 2);
        }
        if (chat) {
            int sw = mc.getWindow().getGuiScaledWidth();
            String hint = "Перетаскивай панели мышью";
            int tw = mc.font.width(hint);
            g.drawString(mc.font, hint, sw / 2 - tw / 2, 6, Theme.subText(), false);
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
        if (c.showMouse) h += GAP + K;
        if (c.showSpace) h += GAP + SPACE_H;
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

        // Мышь читаем напрямую из окна: работает в любом экране.
        long handle = GLFW.glfwGetCurrentContext();
        if (handle == 0L) return;

        boolean pressed = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(handle, cx, cy);
        int[] ww = new int[1];
        int[] wh = new int[1];
        GLFW.glfwGetWindowSize(handle, ww, wh);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double mx = cx[0] * sw / Math.max(1, ww[0]);
        double my = cy[0] * sh / Math.max(1, wh[0]);

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
            String l = c.showCps ? "ЛКМ " + LEFT.size() : "ЛКМ";
            String r = c.showCps ? "ПКМ " + RIGHT.size() : "ПКМ";
            key(g, mc, x0, y, mw, K, l, mc.options.keyAttack.isDown());
            key(g, mc, x0 + mw + GAP, y, mw, K, r, mc.options.keyUse.isDown());
            y += K + GAP;
        }
        if (c.showSpace) {
            boolean down = mc.options.keyJump.isDown();
            g.fill(x0, y, x0 + total, y + SPACE_H, down ? Theme.withAlpha(Theme.accent(), 0.92f) : Theme.keyBg());
        }
    }

    private static void key(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, String label, boolean down) {
        int bg = down ? Theme.withAlpha(Theme.accent(), 0.92f) : Theme.keyBg();
        g.fill(x, y, x + w, y + h, bg);
        int fg = down ? 0xFFFFFFFF : Theme.text();
        center(g, mc.font, label, x + w / 2, y + (h - 8) / 2, fg);
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
        if (real) {
            name = shown.getName().getString();
            hp = shown.getHealth();
            max = Math.max(1f, shown.getMaxHealth());
        } else {
            name = "Игрок";
            hp = 14f;
            max = 20f;
        }

        int[] b = targetBounds(mc, c);
        int x = b[0];
        int y = b[1];
        int w = b[2];
        int h = b[3];

        float ratio = Math.max(0f, Math.min(1f, hp / max));
        int barColor = ratio > 0.5f ? 0xFF22C55E : (ratio > 0.25f ? 0xFFEAB308 : 0xFFEF4444);

        g.fill(x, y, x + w, y + h, Theme.panel());

        if (name.length() > 14) name = name.substring(0, 14);
        g.drawString(mc.font, name, x + 6, y + 5, Theme.text(), false);

        String hpText = String.format(Locale.ROOT, "%.1f", hp);
        g.drawString(mc.font, hpText, x + w - 6 - mc.font.width(hpText), y + 5, Theme.subText(), false);

        int bx = x + 6;
        int by = y + 19;
        int bw = w - 12;
        g.fill(bx, by, bx + bw, by + 3, Theme.withAlpha(0x000000, 0.45f));
        g.fill(bx, by, bx + (int) (bw * ratio), by + 3, barColor);
    }

    // ----------------------------------------------------------------- util

    private static void center(GuiGraphics g, Font font, String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }
}
