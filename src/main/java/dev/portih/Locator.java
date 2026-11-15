package dev.portih;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Невидимки: подсветка сквозь стены (ESP) и локатор-стрелки вокруг прицела. */
public final class Locator {
    private static final Set<Integer> GLOWING = new HashSet<>();
    private static final Set<Integer> NOW = new HashSet<>();   // переиспользуется каждый тик

    private Locator() {}

    private static boolean valid(Minecraft mc, AbstractClientPlayer p, Config c) {
        if (p == mc.player || !p.isAlive() || !p.isInvisible() || p.isSpectator()) return false;
        return mc.player.distanceToSqr(p) <= (double) c.invisRange * c.invisRange;
    }

    /** Каждый тик: включаем подсветку на невидимках и снимаем с остальных. */
    public static void tick(Minecraft mc) {
        Config c = Config.get();
        if (mc.level == null || mc.player == null) {
            GLOWING.clear();
            return;
        }
        if (!c.invisEsp && GLOWING.isEmpty()) return;   // нечего делать

        NOW.clear();
        if (c.invisEsp) {
            for (AbstractClientPlayer p : mc.level.players()) {
                if (valid(mc, p, c)) {
                    p.setGlowingTag(true);
                    NOW.add(p.getId());
                }
            }
        }
        if (!GLOWING.isEmpty()) {
            for (Iterator<Integer> it = GLOWING.iterator(); it.hasNext(); ) {
                int id = it.next();
                if (!NOW.contains(id)) {
                    Entity e = mc.level.getEntity(id);
                    if (e != null) e.setGlowingTag(false);
                    it.remove();
                }
            }
        }
        GLOWING.addAll(NOW);
    }

    /** Стрелки-точки вокруг прицела: куда смотреть, чтобы найти невидимку. */
    public static void render(GuiGraphics g, Minecraft mc, Config c) {
        if (!c.invisLocator || mc.level == null || mc.player == null) return;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        int radius = 56;
        float yaw = mc.player.getYRot();

        for (AbstractClientPlayer p : mc.level.players()) {
            if (!valid(mc, p, c)) continue;
            double dx = p.getX() - mc.player.getX();
            double dz = p.getZ() - mc.player.getZ();
            float target = (float) (Mth.atan2(-dx, dz) * 180.0 / Math.PI);
            float rel = Mth.wrapDegrees(target - yaw) * (float) (Math.PI / 180.0);
            int x = cx + Math.round((float) Math.sin(rel) * radius);
            int y = cy - Math.round((float) Math.cos(rel) * radius);

            int dot = Theme.danger();
            Draw.circle(g, x, y, 5, Draw.fade(dot, 0.25f));
            Draw.circle(g, x, y, 3, dot);

            String s = p.getName().getString() + "  " + Math.round(mc.player.distanceTo(p)) + "м";
            int tw = mc.font.width(s) + 12;
            int cxChip = Mth.clamp(x - tw / 2, 4, mc.getWindow().getGuiScaledWidth() - tw - 4);
            int cyChip = y + (y > cy ? 8 : -22);
            Draw.pill(g, cxChip, cyChip, tw, 14, Theme.panel());
            g.drawString(mc.font, s, cxChip + 6, cyChip + 3, Theme.text(), false);
        }
    }
}
