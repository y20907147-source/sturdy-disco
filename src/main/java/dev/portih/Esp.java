package dev.portih;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * ESP: 2D-рамки игроков сквозь стены + таргет-ESP (броня, яблоки и тотемы в руках цели).
 * Проекция считается вручную по позиции/повороту камеры игрока (вид от первого лица).
 */
public final class Esp {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static LivingEntity lastTarget;
    private static long lastTargetMs;

    private Esp() {}

    public static void render(GuiGraphics g, Minecraft mc, Config c, DeltaTracker delta) {
        if (!(c.esp || c.targetEsp) || mc.level == null || mc.player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        float pt = delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(pt);
        double yaw = Math.toRadians(mc.player.getViewYRot(pt));
        double pitch = Math.toRadians(mc.player.getViewXRot(pt));
        double fov = Math.toRadians(mc.options.fov().get());
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // базис камеры
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double uxx = 0 * fz - rz * fy;
        double uyy = rz * fx - rx * fz;
        double uzz = rx * fy - 0 * fx;
        double[] cam = {eye.x, eye.y, eye.z, fx, fy, fz, rx, 0, rz, uxx, uyy, uzz,
                Math.tan(fov / 2.0), (double) sw / sh, sw, sh};

        LivingEntity target = findTarget(mc);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            if (mc.player.distanceTo(p) > c.espRange) continue;

            float[] box = project(p, pt, cam);
            if (box == null) continue;
            boolean isTarget = p == target;

            if (c.esp) {
                int col = isTarget ? Theme.danger() : Theme.accent();
                drawBox(g, box, col);
                if (c.espInfo) drawNameplate(g, mc, p, box);
            }
            if (c.targetEsp && isTarget) drawTargetInfo(g, mc, c, p, box, sw, sh);
        }
    }

    private static LivingEntity findTarget(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (mc.hitResult instanceof EntityHitResult r && r.getEntity() instanceof LivingEntity le
                && le != mc.player && le.isAlive()) {
            lastTarget = le;
            lastTargetMs = now;
        } else if (HitTracker.lastHitEntity != null && now - HitTracker.lastHitMs < 3000L
                && HitTracker.lastHitEntity.isAlive()) {
            lastTarget = HitTracker.lastHitEntity;
            lastTargetMs = HitTracker.lastHitMs;
        }
        if (lastTarget != null && (now - lastTargetMs > 3000L || !lastTarget.isAlive())) lastTarget = null;
        return lastTarget;
    }

    // ------------------------------------------------------------- проекция

    /** Возвращает {minX, minY, maxX, maxY} рамки сущности на экране или null, если не видна. */
    private static float[] project(LivingEntity e, float pt, double[] cam) {
        Vec3 pos = e.getPosition(pt);
        double hw = e.getBbWidth() / 2.0;
        double h = e.getBbHeight();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double px = pos.x + ((i & 1) == 0 ? -hw : hw);
            double py = pos.y + ((i & 2) == 0 ? 0 : h);
            double pz = pos.z + ((i & 4) == 0 ? -hw : hw);
            double dx = px - cam[0], dy = py - cam[1], dz = pz - cam[2];
            double depth = dx * cam[3] + dy * cam[4] + dz * cam[5];
            if (depth < 0.1) return null;
            double side = dx * cam[6] + dy * cam[7] + dz * cam[8];
            double up = dx * cam[9] + dy * cam[10] + dz * cam[11];
            float sx = (float) (cam[14] / 2.0 + side / depth / (cam[12] * cam[13]) * cam[14] / 2.0);
            float sy = (float) (cam[15] / 2.0 - up / depth / cam[12] * cam[15] / 2.0);
            minX = Math.min(minX, sx);
            minY = Math.min(minY, sy);
            maxX = Math.max(maxX, sx);
            maxY = Math.max(maxY, sy);
        }
        if (maxX - minX < 2 || maxY - minY < 2) return null;
        return new float[]{minX, minY, maxX, maxY};
    }

    // ------------------------------------------------------------- рисование

    private static void drawBox(GuiGraphics g, float[] b, int col) {
        int x0 = Math.round(b[0]), y0 = Math.round(b[1]), x1 = Math.round(b[2]), y1 = Math.round(b[3]);
        int shadow = 0xAA000000;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y0 + 2, shadow);
        g.fill(x0 - 1, y1 - 1, x1 + 1, y1 + 2, shadow);
        g.fill(x0 - 1, y0, x0 + 2, y1, shadow);
        g.fill(x1 - 1, y0, x1 + 2, y1, shadow);
        g.fill(x0, y0, x1, y0 + 1, col);
        g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0, x0 + 1, y1, col);
        g.fill(x1 - 1, y0, x1, y1, col);
    }

    private static void drawNameplate(GuiGraphics g, Minecraft mc, LivingEntity e, float[] b) {
        float hp = e.getHealth();
        float ratio = Draw.clamp01(hp / Math.max(1f, e.getMaxHealth()));
        int x0 = Math.round(b[0]), y0 = Math.round(b[1]), y1 = Math.round(b[3]);
        // вертикальная полоска здоровья слева
        g.fill(x0 - 5, y0, x0 - 3, y1, 0xAA000000);
        int fh = Math.round((y1 - y0) * ratio);
        int col = ratio > 0.5f ? 0xFF4ADE80 : (ratio > 0.25f ? 0xFFFFC857 : 0xFFFF6B6B);
        g.fill(x0 - 5, y1 - fh, x0 - 3, y1, col);

        String s = e.getName().getString() + " " + String.format(Locale.ROOT, "%.1f", hp);
        int tw = mc.font.width(s);
        int cx = Math.round((b[0] + b[2]) / 2f);
        g.fill(cx - tw / 2 - 2, y0 - 12, cx + tw / 2 + 2, y0 - 2, 0x88000000);
        g.drawString(mc.font, s, cx - tw / 2, y0 - 11, 0xFFFFFFFF, false);
    }

    private static int handCount(LivingEntity e, net.minecraft.world.item.Item... items) {
        int n = 0;
        for (ItemStack s : new ItemStack[]{e.getMainHandItem(), e.getOffhandItem()}) {
            for (net.minecraft.world.item.Item it : items) if (s.is(it)) n += s.getCount();
        }
        return n;
    }

    /** Таргет-ESP: броня (с прочностью), яблоки и тотемы в руках цели. */
    private static void drawTargetInfo(GuiGraphics g, Minecraft mc, Config c, LivingEntity e,
                                       float[] b, int sw, int sh) {
        int rows = 0;
        if (c.targetArmor) rows++;
        if (c.targetApples) rows++;
        if (c.targetTotems) rows++;
        if (rows == 0) return;

        int w = 4 * 18 + 10;
        int h = 6 + (c.targetArmor ? 20 : 0) + (c.targetApples ? 12 : 0) + (c.targetTotems ? 12 : 0);
        int x = Math.round(b[2]) + 8;
        int y = Math.round(b[1]);
        if (x + w > sw - 2) x = Math.max(2, Math.round(b[0]) - w - 8);
        y = Math.max(2, Math.min(sh - h - 2, y));

        Draw.panel(g, x, y, w, h, 6, Theme.panel(), 0.8f);
        int cy = y + 4;

        if (c.targetArmor) {
            for (int i = 0; i < ARMOR.length; i++) {
                ItemStack s = e.getItemBySlot(ARMOR[i]);
                int ix = x + 5 + i * 18;
                if (s.isEmpty()) {
                    Draw.roundRect(g, ix + 2, cy + 2, 12, 12, 3, Theme.track());
                } else {
                    g.renderItem(s, ix, cy);
                    g.renderItemDecorations(mc.font, s, ix, cy);
                }
            }
            cy += 20;
        }
        if (c.targetApples) {
            int n = handCount(e, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);
            g.drawString(mc.font, "Яблоки: " + n, x + 6, cy, n > 0 ? 0xFFFFC857 : Theme.subText(), false);
            cy += 12;
        }
        if (c.targetTotems) {
            int n = handCount(e, Items.TOTEM_OF_UNDYING);
            g.drawString(mc.font, "Тотемы: " + n, x + 6, cy, n > 0 ? 0xFFFACC15 : Theme.subText(), false);
        }
    }
}
