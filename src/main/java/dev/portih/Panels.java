package dev.portih;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Панели как в читклиентах: инфо-плашка, Potions, Keybinds, Cooldowns. Все двигаются мышью. */
public final class Panels {
    public static final int INFO = 0;
    public static final int POTIONS = 1;
    public static final int KEYBINDS = 2;
    public static final int COOLDOWNS = 3;
    public static final int COUNT = 4;

    private static final int RADIUS = 11;
    private static final int HEADER_H = 22;
    private static final int ROW_H = 14;
    private static final int PAD = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final int[] W = new int[COUNT];
    private static final int[] H = new int[COUNT];

    private static final ItemStack[] CD_ITEMS = {
            new ItemStack(Items.ENDER_PEARL),
            new ItemStack(Items.CHORUS_FRUIT),
            new ItemStack(Items.GOLDEN_APPLE),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.WIND_CHARGE),
            new ItemStack(Items.TRIDENT),
            new ItemStack(Items.MACE)
    };

    private static long lastNs;
    private static float fps = 60f;

    private Panels() {}

    // ------------------------------------------------------------ положение

    public static boolean enabled(Config c, int i) {
        return switch (i) {
            case INFO -> c.panelInfo;
            case POTIONS -> c.panelPotions;
            case KEYBINDS -> c.panelKeybinds;
            default -> c.panelCooldowns;
        };
    }

    public static float getX(Config c, int i) {
        return switch (i) {
            case INFO -> c.infoX;
            case POTIONS -> c.potionsX;
            case KEYBINDS -> c.keybindsX;
            default -> c.cooldownsX;
        };
    }

    public static float getY(Config c, int i) {
        return switch (i) {
            case INFO -> c.infoY;
            case POTIONS -> c.potionsY;
            case KEYBINDS -> c.keybindsY;
            default -> c.cooldownsY;
        };
    }

    public static void setPos(Config c, int i, float x, float y) {
        switch (i) {
            case INFO -> { c.infoX = x; c.infoY = y; }
            case POTIONS -> { c.potionsX = x; c.potionsY = y; }
            case KEYBINDS -> { c.keybindsX = x; c.keybindsY = y; }
            default -> { c.cooldownsX = x; c.cooldownsY = y; }
        }
    }

    /** {x, y, w, h} панели по размеру, посчитанному в последнем кадре. */
    public static int[] bounds(Minecraft mc, Config c, int i) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int w = Math.max(40, W[i]);
        int h = Math.max(HEADER_H, H[i]);
        return new int[]{(int) (getX(c, i) * sw), (int) (getY(c, i) * sh), w, h};
    }

    // ------------------------------------------------------------- рендер

    public static void render(GuiGraphics g, Minecraft mc, Config c, boolean edit) {
        long ns = System.nanoTime();
        if (lastNs != 0L) {
            float d = Math.max(0.0001f, (ns - lastNs) / 1_000_000_000f);
            fps += (1f / d - fps) * 0.05f;
        }
        lastNs = ns;

        if (c.panelInfo) drawInfo(g, mc, c);
        if (c.panelPotions) drawPotions(g, mc, c, edit);
        if (c.panelKeybinds) drawKeybinds(g, mc, c);
        if (c.panelCooldowns) drawCooldowns(g, mc, c, edit);
    }

    private static int[] frame(GuiGraphics g, Minecraft mc, Config c, int i, int w, int h) {
        W[i] = w;
        H[i] = h;
        int[] b = bounds(mc, c, i);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        // не даём панели уехать за экран, если она выросла
        b[0] = Math.max(0, Math.min(b[0], sw - w));
        b[1] = Math.max(0, Math.min(b[1], sh - h));
        Draw.panel(g, b[0], b[1], w, h, Math.min(RADIUS, h / 2), Theme.panel());
        return b;
    }

    private static void header(GuiGraphics g, Minecraft mc, int[] b, int w, String title, String right) {
        g.drawString(mc.font, title, b[0] + PAD, b[1] + 8, Theme.text(), false);
        if (right != null) {
            int cw = mc.font.width(right) + 10;
            int chipX = b[0] + w - PAD - cw;
            Draw.pill(g, chipX, b[1] + 6, cw, 12, Theme.withAlpha(Theme.accent(), 0.18f));
            g.drawString(mc.font, right, chipX + 5, b[1] + 8, Theme.accent(), false);
        }
    }

    // ------------------------------------------------------- инфо-плашка

    private static void drawInfo(GuiGraphics g, Minecraft mc, Config c) {
        String name = mc.player != null ? mc.player.getName().getString() : "player";
        String[] parts = {"PortihMod", name, Math.round(fps) + " fps", LocalTime.now().format(TIME)};
        int gap = 12;
        int tw = 0;
        for (int k = 0; k < parts.length; k++) {
            tw += mc.font.width(parts[k]);
            if (k < parts.length - 1) tw += gap;
        }
        int w = tw + PAD * 2;
        int h = 22;
        int[] b = frame(g, mc, c, INFO, w, h);
        int x = b[0] + PAD;
        int y = b[1] + (h - 8) / 2;
        for (int k = 0; k < parts.length; k++) {
            g.drawString(mc.font, parts[k], x, y, k == 0 ? Theme.accent() : Theme.text(), false);
            x += mc.font.width(parts[k]);
            if (k < parts.length - 1) {
                Draw.circle(g, x + gap / 2, b[1] + h / 2, 1, Theme.withAlpha(Theme.subText() & 0xFFFFFF, 0.6f));
                x += gap;
            }
        }
    }

    // ------------------------------------------------------------ Potions

    private record Row(String left, String right, int color) {}

    private static String formatTime(int ticks) {
        int s = Math.max(0, ticks / 20);
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static void drawPotions(GuiGraphics g, Minecraft mc, Config c, boolean edit) {
        List<Row> rows = new ArrayList<>();
        if (mc.player != null) {
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                String name = e.getEffect().value().getDisplayName().getString();
                String lvl = e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "";
                String time = e.isInfiniteDuration() ? "inf" : formatTime(e.getDuration());
                rows.add(new Row(name + lvl, time, 0xFF000000 | e.getEffect().value().getColor()));
            }
        }
        if (rows.isEmpty() && edit) {
            rows.add(new Row("Скорость 2", "15s", 0xFF7CAFC6));
            rows.add(new Row("Регенерация", "32s", 0xFFCD5CAB));
        }
        if (rows.isEmpty()) {
            W[POTIONS] = 0;
            H[POTIONS] = 0;
            return;
        }
        drawList(g, mc, c, POTIONS, "Potions", String.valueOf(rows.size()), rows, 100);
    }

    private static void drawList(GuiGraphics g, Minecraft mc, Config c, int id, String title, String count,
                                 List<Row> rows, int minW) {
        int w = Math.max(minW, mc.font.width(title) + mc.font.width(count) + PAD * 3 + 8);
        for (Row r : rows) {
            w = Math.max(w, PAD * 2 + 10 + mc.font.width(r.left()) + 14 + mc.font.width(r.right()));
        }
        int h = HEADER_H + rows.size() * ROW_H + 6;
        int[] b = frame(g, mc, c, id, w, h);
        header(g, mc, b, w, title, count);
        int y = b[1] + HEADER_H;
        for (Row r : rows) {
            Draw.circle(g, b[0] + PAD + 2, y + 4, 2, r.color());
            g.drawString(mc.font, r.left(), b[0] + PAD + 10, y, Theme.text(), false);
            g.drawString(mc.font, r.right(), b[0] + w - PAD - mc.font.width(r.right()), y, Theme.subText(), false);
            y += ROW_H;
        }
    }

    // ----------------------------------------------------------- Keybinds

    private static void drawKeybinds(GuiGraphics g, Minecraft mc, Config c) {
        KeyMapping[] maps = {PortihClient.openMenu, PortihClient.toggleHud, PortihClient.toggleSpin};
        List<Row> rows = new ArrayList<>();
        for (KeyMapping km : maps) {
            if (km == null || km.isUnbound()) continue;
            rows.add(new Row(Component.translatable(km.getName()).getString(),
                    km.getTranslatedKeyMessage().getString(), Theme.accent()));
        }
        if (rows.isEmpty()) rows.add(new Row("Нет назначенных клавиш", "", Theme.muted()));
        drawList(g, mc, c, KEYBINDS, "Keybinds", String.valueOf(rows.size()), rows, 110);
    }

    // ---------------------------------------------------------- Cooldowns

    private static void drawCooldowns(GuiGraphics g, Minecraft mc, Config c, boolean edit) {
        List<ItemStack> stacks = new ArrayList<>();
        List<Float> pcts = new ArrayList<>();
        if (mc.player != null) {
            for (ItemStack s : CD_ITEMS) {
                float p = mc.player.getCooldowns().getCooldownPercent(s, 0f);
                if (p > 0f) {
                    stacks.add(s);
                    pcts.add(p);
                }
            }
        }
        if (stacks.isEmpty() && edit) {
            stacks.add(CD_ITEMS[0]);
            pcts.add(0.6f);
        }

        int rowH = 20;
        int w = 130;
        int h = HEADER_H + stacks.size() * rowH + (stacks.isEmpty() ? 0 : 4);
        int[] b = frame(g, mc, c, COOLDOWNS, w, h);
        header(g, mc, b, w, "Cooldowns", stacks.isEmpty() ? null : String.valueOf(stacks.size()));

        int y = b[1] + HEADER_H;
        for (int k = 0; k < stacks.size(); k++) {
            ItemStack s = stacks.get(k);
            float p = pcts.get(k);
            g.renderItem(s, b[0] + PAD - 2, y);
            String name = s.getHoverName().getString();
            if (name.length() > 12) name = name.substring(0, 11) + "..";
            g.drawString(mc.font, name, b[0] + PAD + 16, y + 1, Theme.text(), false);
            int bx = b[0] + PAD + 16;
            int bw = w - PAD * 2 - 16;
            Draw.pill(g, bx, y + 12, bw, 3, Theme.track());
            int fw = (int) (bw * p);
            if (fw > 0) Draw.pill(g, bx, y + 12, Math.max(3, fw), 3, Theme.accent());
            y += rowH;
        }
    }
}
