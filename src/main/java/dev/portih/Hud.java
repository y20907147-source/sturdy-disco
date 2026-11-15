package dev.portih;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;

public final class Hud {
    private static final int K = 24;
    private static final int GAP = 4;
    private static final int MOUSE_H = 20;
    private static final int SPACE_H = 14;
    private static final int CPS_H = 16;
    private static final int TARGET_W = 172;
    private static final int TARGET_H = 48;
    private static final int RADIUS = 12;
    private static final int KEY_R = 9;

    private static final HashMap<String, Float> ANIM = new HashMap<>();
    private static long lastNs;
    private static float dt = 0.016f;

    // плавная полоска здоровья цели
    private static LivingEntity animFor;
    private static float barRatio = 1f;
    private static float ghostRatio = 1f;

    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean wasLeft;
    private static boolean wasRight;

    private static LivingEntity shown;
    private static long shownUntil;
    private static float targetVis = 0f;
    private static long feedSeen = 0L;

    // перетаскивание: 0 = нет, 1 = кейстроки, 2 = таргет худ, 10+ = панели (Panels)
    private static int dragging = 0;
    private static int grabDX;
    private static int grabDY;
    private static boolean wasPressed;

    private Hud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long ns = System.nanoTime();
        dt = lastNs == 0L ? 0.016f : Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;

        HitTracker.update(mc);
        if (mc.options.hideGui) return;

        Config c = Config.get();
        Effects.renderOverlay(g, mc, c);
        EventAlert.render(g, mc, c);
        Locator.render(g, mc, c);
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
        Panels.render(g, mc, c, edit);
        if (edit) {
            for (int i = 0; i < Panels.COUNT; i++) {
                if (Panels.enabled(c, i)) outline(g, Panels.bounds(mc, c, i), dragging == 10 + i);
            }
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
        if (c.showMouse) h += GAP + MOUSE_H;
        if (c.showSpace) h += GAP + SPACE_H;
        if (c.showCps) h += GAP + CPS_H;
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
            int panel = -1;
            for (int i = Panels.COUNT - 1; i >= 0 && panel < 0; i--) {
                if (Panels.enabled(c, i) && inside(mx, my, Panels.bounds(mc, c, i))) panel = i;
            }
            if (panel >= 0) {
                int[] pb = Panels.bounds(mc, c, panel);
                dragging = 10 + panel;
                grabDX = (int) (mx - pb[0]);
                grabDY = (int) (my - pb[1]);
            } else if (c.targetHud && inside(mx, my, tb)) {
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
            if (dragging >= 10) {
                int pi = dragging - 10;
                int[] b = Panels.bounds(mc, c, pi);
                Panels.setPos(c, pi,
                        clamp((float) ((mx - grabDX) / sw), 0f, Math.max(0f, (sw - b[2]) / (float) sw)),
                        clamp((float) ((my - grabDY) / sh), 0f, Math.max(0f, (sh - b[3]) / (float) sh)));
            } else if (dragging == 1) {
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
        Draw.border(g, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6, 12, Draw.fade(col, active ? 0.9f : 0.45f));
    }

    // ------------------------------------------------------------ анимации

    /** Плавное значение 0..1 для нажатия/включения. */
    private static float anim(String id, boolean on) {
        float a = ANIM.getOrDefault(id, 0f);
        float t = on ? 1f : 0f;
        a += (t - a) * Math.min(1f, dt * 18f);
        if (Math.abs(t - a) < 0.01f) a = t;
        ANIM.put(id, a);
        return a;
    }

    // ------------------------------------------------------------ кейстроки

    private static void drawKeystrokes(GuiGraphics g, Minecraft mc, Config c) {
        int[] b = keysBounds(mc, c);
        int x0 = b[0];
        int y0 = b[1];
        int total = b[2];

        key(g, mc, "w", x0 + K + GAP, y0, K, K, KEY_R, "W", mc.options.keyUp.isDown());
        int y1 = y0 + K + GAP;
        key(g, mc, "a", x0, y1, K, K, KEY_R, "A", mc.options.keyLeft.isDown());
        key(g, mc, "s", x0 + K + GAP, y1, K, K, KEY_R, "S", mc.options.keyDown.isDown());
        key(g, mc, "d", x0 + 2 * (K + GAP), y1, K, K, KEY_R, "D", mc.options.keyRight.isDown());

        int y = y1 + K + GAP;
        if (c.showMouse) {
            int mw = (total - GAP) / 2;
            key(g, mc, "lmb", x0, y, mw, MOUSE_H, MOUSE_H / 2, "ЛКМ", mc.options.keyAttack.isDown());
            key(g, mc, "rmb", x0 + mw + GAP, y, total - mw - GAP, MOUSE_H, MOUSE_H / 2, "ПКМ", mc.options.keyUse.isDown());
            y += MOUSE_H + GAP;
        }
        if (c.showSpace) {
            float a = anim("space", mc.options.keyJump.isDown());
            int in = Math.round(a * 1.5f);
            Draw.pill(g, x0 + in, y + in / 2, total - in * 2, SPACE_H - in,
                    Draw.lerpColor(Theme.keyBg(), Theme.withAlpha(Theme.accent(), 0.95f), a));
            Draw.pill(g, x0 + total / 2 - 9, y + SPACE_H / 2 - 1, 18, 2,
                    Draw.lerpColor(Theme.subText(), 0xFFFFFFFF, a));
            y += SPACE_H + GAP;
        }
        if (c.showCps) {
            Draw.pill(g, x0, y, total, CPS_H, Theme.keyBg());
            String s = LEFT.size() + "  |  " + RIGHT.size() + "  CPS";
            center(g, mc.font, s, x0 + total / 2, y + (CPS_H - 8) / 2, Theme.subText());
        }
    }

    private static void key(GuiGraphics g, Minecraft mc, String id, int x, int y, int w, int h, int r,
                            String label, boolean down) {
        float a = anim(id, down);
        int in = Math.round(a * 1.5f);   // клавиша мягко "вдавливается"
        int bg = Draw.lerpColor(Theme.keyBg(), Theme.withAlpha(Theme.accent(), 0.95f), a);
        Draw.roundRect(g, x + in, y + in, w - in * 2, h - in * 2, r - in, bg);
        center(g, mc.font, label, x + w / 2, y + (h - 8) / 2, Draw.lerpColor(Theme.text(), 0xFFFFFFFF, a));
    }

    // ------------------------------------------------------------ таргет худ

    private static int hpColor(float ratio) {
        if (ratio > 0.5f) return Draw.lerpColor(0xFFFFC857, 0xFF4ADE80, (ratio - 0.5f) * 2f);
        return Draw.lerpColor(0xFFFF6B6B, 0xFFFFC857, ratio * 2f);
    }

    private static void drawTarget(GuiGraphics g, Minecraft mc, Config c, boolean edit) {
        long now = System.currentTimeMillis();
        Entity e = (mc.hitResult instanceof EntityHitResult r) ? r.getEntity() : null;
        if (e instanceof LivingEntity le && le != mc.player && le.isAlive()) {
            if (le != shown) ActionFeed.clear();
            shown = le;
            shownUntil = now + 2000L;
        }
        boolean real = shown != null && now <= shownUntil && shown.isAlive();

        // пока цель что-то делает, худ не гаснет
        if (real && ActionFeed.lastEventMs != feedSeen) {
            feedSeen = ActionFeed.lastEventMs;
            shownUntil = Math.max(shownUntil, now + 2200L);
        }
        if (real) ActionFeed.update(shown);

        targetVis = Draw.approach(targetVis, (real || edit) ? 1f : 0f, dt, 12f);
        float vis = targetVis;
        if (vis < 0.03f) return;

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

        float ratio = Math.max(0f, Math.min(1f, hp / max));
        if (real && shown != animFor) {
            animFor = shown;
            barRatio = ratio;
            ghostRatio = ratio;
        }
        barRatio += (ratio - barRatio) * Math.min(1f, dt * 10f);
        if (ratio > ghostRatio) ghostRatio = ratio;
        else ghostRatio += (ratio - ghostRatio) * Math.min(1f, dt * 1.8f);

        int[] b = targetBounds(mc, c);
        int slide = Math.round((1f - Draw.easeOut(vis)) * 8f);
        int x = b[0];
        int y = b[1] + slide;
        int w = b[2];
        int h = b[3];

        Draw.panel(g, x, y, w, h, RADIUS, Draw.fade(Theme.panel(), vis), vis);

        // голова в мягкой акцентной подложке
        int hs = 30;
        int hx = x + 10;
        int hy = y + (h - hs) / 2;
        Draw.roundRect(g, hx - 2, hy - 2, hs + 4, hs + 4, 8, Draw.fade(Theme.accent(), 0.85f * vis));
        drawHead(g, mc, real ? shown : mc.player, hx, hy, hs);

        int tx = hx + hs + 12;
        if (name.length() > 13) name = name.substring(0, 13);
        g.drawString(mc.font, name, tx, y + 10, Draw.fade(Theme.text(), vis), false);

        String hpText = String.format(Locale.ROOT, "%.1f", hp);
        int hpX = x + w - 12 - mc.font.width(hpText);
        g.drawString(mc.font, hpText, hpX, y + 10, Draw.fade(Theme.subText(), vis), false);

        if (c.winLose && mc.player != null) {
            float diff = real ? mc.player.getHealth() - hp : 6f;
            String wl = diff > 0.05f ? "W" : (diff < -0.05f ? "L" : "=");
            int col = diff > 0.05f ? 0xFF4ADE80 : (diff < -0.05f ? 0xFFFF6B6B : Theme.subText());
            int cw = mc.font.width(wl) + 10;
            int chipX = hpX - 6 - cw;
            Draw.pill(g, chipX, y + 8, cw, 12, Draw.fade(Theme.withAlpha(col & 0xFFFFFF, 0.2f), vis));
            g.drawString(mc.font, wl, chipX + 5, y + 10, Draw.fade(col | 0xFF000000, vis), false);
        }

        // тонкая капсула здоровья без бликов и градиентов
        int bx = tx;
        int by = y + h - 17;
        int bw = x + w - 12 - bx;
        int bh = 6;
        Draw.pill(g, bx, by, bw, bh, Draw.fade(Theme.track(), vis));
        int gw = (int) (bw * ghostRatio);
        if (gw > 1) Draw.pill(g, bx, by, Math.max(bh, gw), bh, Draw.fade(0x40FFFFFF, vis));
        int fw = (int) (bw * barRatio);
        if (fw > 1) Draw.pill(g, bx, by, Math.max(bh, fw), bh, Draw.fade(hpColor(barRatio) | 0xFF000000, vis));

        // действия цели
        ActionFeed.render(g, mc, x + w / 2, y + h + 7, vis, edit && !real);
    }

    private static void drawHead(GuiGraphics g, Minecraft mc, LivingEntity e, int x, int y, int s) {
        if (e instanceof AbstractClientPlayer p) {
            PlayerFaceRenderer.draw(g, p.getSkin(), x, y, s);
        } else {
            Draw.roundRect(g, x, y, s, s, 4, Theme.chip());
            center(g, mc.font, "?", x + s / 2, y + (s - 8) / 2, Theme.subText());
        }
    }

    // ----------------------------------------------------------------- util

    private static void center(GuiGraphics g, Font font, String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }
}
