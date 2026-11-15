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

import java.util.concurrent.ThreadLocalRandom;

/**
 * ESP: рамки игроков сквозь стены (полная рамка или уголки), здоровье, дистанция, трейсеры,
 * таргет-ESP (броня, яблоки, тотемы), крутящийся маркер на цели и всплывающие цифры урона.
 * Проекция считается вручную по позиции/повороту камеры игрока (вид от первого лица).
 * Все буферы статические: в кадре не создаётся мусор.
 */
public final class Esp {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final long TARGET_KEEP_MS = 3000L;
    private static final long TARGET_FADE_MS = 500L;

    /** Камера: 0-2 глаз, 3-5 вперёд, 6-8 вправо, 9-11 вверх, 12-13 tan/aspect, 14-15 экран, 16-17 масштаб. */
    private static final double[] CAM = new double[18];
    private static final float[] BOX = new float[4];
    private static final float[] PT = new float[2];

    private static LivingEntity lastTarget;
    private static long lastTargetMs;

    // всплывающий урон
    private static final int POP_MAX = 8;
    private static final long POP_LIFE = 1000L;
    private static final double[] POP_POS = new double[POP_MAX * 3];
    private static final float[] POP_DMG = new float[POP_MAX];
    private static final long[] POP_BORN = new long[POP_MAX];
    private static int popNext;
    private static LivingEntity hpEntity;
    private static float hpLast;

    private Esp() {}

    public static void render(GuiGraphics g, Minecraft mc, Config c, DeltaTracker delta) {
        if (!(c.esp || c.targetEsp || c.targetMark || c.damagePops || c.espTracers > 0)) return;
        if (mc.level == null || mc.player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        float pt = delta.getGameTimeDeltaPartialTick(false);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        setupCamera(mc, pt, sw, sh);

        long now = System.currentTimeMillis();
        LivingEntity target = findTarget(mc, now);
        trackDamage(target, now, c);
        float life = target == null ? 0f
                : Draw.clamp01((TARGET_KEEP_MS - (now - lastTargetMs)) / (float) TARGET_FADE_MS);

        double rangeSq = (double) c.espRange * c.espRange;
        boolean wantAll = c.esp || c.espTracers == 2;
        boolean targetOnly = !wantAll;
        Vec3 eye = mc.player.getEyePosition(pt);

        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            boolean isTarget = p == target;
            if (targetOnly && !isTarget) continue;

            double dsq = eye.distanceToSqr(p.getX(), p.getY(), p.getZ());
            if (dsq > rangeSq) continue;
            if (!project(p, pt, sw, sh)) continue;

            float cx = (BOX[0] + BOX[2]) * 0.5f;
            int col = isTarget ? Theme.danger() : Theme.accent();

            // трейсер: от низа экрана к ногам
            if (c.espTracers == 2 || (c.espTracers == 1 && isTarget)) {
                Draw.line(g, sw * 0.5f, sh, cx, BOX[3], 1f, Draw.fade(col, isTarget ? 0.8f : 0.45f));
            }

            if (c.esp) {
                if (c.espFill) {
                    g.fill(Math.round(BOX[0]), Math.round(BOX[1]), Math.round(BOX[2]), Math.round(BOX[3]),
                            Draw.fade(col, isTarget ? 0.12f : 0.06f));
                }
                if (c.espStyle == 0) drawBox(g, BOX, col, c.quality);
                else drawCorners(g, BOX, col, c.quality);
                drawNameplate(g, mc, c, p, BOX, (float) Math.sqrt(dsq));
            }

            if (isTarget) {
                if (c.targetMark) TargetMarker.draw(g, c, p, BOX, CAM, pt, life);
                if (c.targetEsp) drawTargetInfo(g, mc, c, p, BOX, sw, sh);
            }
        }

        if (c.damagePops) renderPops(g, mc, now);
    }

    // ------------------------------------------------------------- цель

    private static LivingEntity findTarget(Minecraft mc, long now) {
        if (mc.hitResult instanceof EntityHitResult r && r.getEntity() instanceof LivingEntity le
                && le != mc.player && le.isAlive()) {
            lastTarget = le;
            lastTargetMs = now;
        } else if (HitTracker.lastHitEntity != null && now - HitTracker.lastHitMs < TARGET_KEEP_MS
                && HitTracker.lastHitEntity.isAlive()) {
            lastTarget = HitTracker.lastHitEntity;
            lastTargetMs = HitTracker.lastHitMs;
        }
        if (lastTarget != null && (now - lastTargetMs > TARGET_KEEP_MS || !lastTarget.isAlive())) lastTarget = null;
        return lastTarget;
    }

    // ------------------------------------------------------------- проекция

    private static void setupCamera(Minecraft mc, float pt, int sw, int sh) {
        Vec3 eye = mc.player.getEyePosition(pt);
        double yaw = Math.toRadians(mc.player.getViewYRot(pt));
        double pitch = Math.toRadians(mc.player.getViewXRot(pt));
        double fov = Math.toRadians(mc.options.fov().get());

        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double tan = Math.tan(fov / 2.0);
        double aspect = (double) sw / sh;

        CAM[0] = eye.x;
        CAM[1] = eye.y;
        CAM[2] = eye.z;
        CAM[3] = fx;
        CAM[4] = fy;
        CAM[5] = fz;
        CAM[6] = rx;
        CAM[7] = 0;
        CAM[8] = rz;
        CAM[9] = -rz * fy;
        CAM[10] = rz * fx - rx * fz;
        CAM[11] = rx * fy;
        CAM[12] = tan;
        CAM[13] = aspect;
        CAM[14] = sw;
        CAM[15] = sh;
        CAM[16] = sw / 2.0 / (tan * aspect);   // пикселей на единицу "side / depth"
        CAM[17] = sh / 2.0 / tan;              // пикселей на единицу "up / depth"
    }

    /**
     * Проекция точки мира в координаты экрана (GUI-пиксели). false, если точка за камерой.
     * Используется и маркером цели (3D-стиль).
     */
    public static boolean projectPoint(double x, double y, double z, double[] cam, float[] out) {
        double dx = x - cam[0], dy = y - cam[1], dz = z - cam[2];
        double depth = dx * cam[3] + dy * cam[4] + dz * cam[5];
        if (depth < 0.1) return false;
        double side = dx * cam[6] + dy * cam[7] + dz * cam[8];
        double up = dx * cam[9] + dy * cam[10] + dz * cam[11];
        out[0] = (float) (cam[14] * 0.5 + side / depth * cam[16]);
        out[1] = (float) (cam[15] * 0.5 - up / depth * cam[17]);
        return true;
    }

    /** Заполняет BOX = {minX, minY, maxX, maxY}. false, если сущность не видна или вне экрана. */
    private static boolean project(LivingEntity e, float pt, int sw, int sh) {
        Vec3 pos = e.getPosition(pt);
        double hw = e.getBbWidth() / 2.0;
        double h = e.getBbHeight();

        // быстрый отсев: центр сильно за спиной
        double cdx = pos.x - CAM[0], cdy = pos.y + h * 0.5 - CAM[1], cdz = pos.z - CAM[2];
        if (cdx * CAM[3] + cdy * CAM[4] + cdz * CAM[5] < -1.5) return false;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double px = pos.x + ((i & 1) == 0 ? -hw : hw);
            double py = pos.y + ((i & 2) == 0 ? 0 : h);
            double pz = pos.z + ((i & 4) == 0 ? -hw : hw);
            if (!projectPoint(px, py, pz, CAM, PT)) return false;
            if (PT[0] < minX) minX = PT[0];
            if (PT[0] > maxX) maxX = PT[0];
            if (PT[1] < minY) minY = PT[1];
            if (PT[1] > maxY) maxY = PT[1];
        }
        if (maxX - minX < 2 || maxY - minY < 2) return false;
        if (maxX < 0 || minX > sw || maxY < 0 || minY > sh) return false;   // целиком вне экрана
        BOX[0] = minX;
        BOX[1] = minY;
        BOX[2] = maxX;
        BOX[3] = maxY;
        return true;
    }

    // ------------------------------------------------------------- рисование

    private static void drawBox(GuiGraphics g, float[] b, int col, int quality) {
        int x0 = Math.round(b[0]), y0 = Math.round(b[1]), x1 = Math.round(b[2]), y1 = Math.round(b[3]);
        if (quality >= 1) {
            int shadow = 0xAA000000;
            g.fill(x0 - 1, y0 - 1, x1 + 1, y0 + 2, shadow);
            g.fill(x0 - 1, y1 - 1, x1 + 1, y1 + 2, shadow);
            g.fill(x0 - 1, y0, x0 + 2, y1, shadow);
            g.fill(x1 - 1, y0, x1 + 2, y1, shadow);
        }
        g.fill(x0, y0, x1, y0 + 1, col);
        g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, col);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, col);
    }

    private static void drawCorners(GuiGraphics g, float[] b, int col, int quality) {
        int x0 = Math.round(b[0]), y0 = Math.round(b[1]), x1 = Math.round(b[2]), y1 = Math.round(b[3]);
        int len = Math.max(3, Math.min(14, Math.round(Math.min(x1 - x0, y1 - y0) * 0.28f)));
        if (quality >= 1) Draw.corners(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, len + 1, 3, 0xAA000000);
        Draw.corners(g, x0, y0, x1, y1, len, 1, col);
    }

    /** "12.5" без String.format (формат в каждом кадре дорогой). */
    private static String num(float v) {
        int t = Math.round(Math.max(0f, v) * 10f);
        return (t / 10) + "." + (t % 10);
    }

    private static void drawNameplate(GuiGraphics g, Minecraft mc, Config c, LivingEntity e, float[] b, float dist) {
        int x0 = Math.round(b[0]), y0 = Math.round(b[1]), y1 = Math.round(b[3]);
        int cx = Math.round((b[0] + b[2]) * 0.5f);

        if (c.espInfo) {
            float hp = e.getHealth();
            float max = Math.max(1f, e.getMaxHealth());
            float ratio = Draw.clamp01(hp / max);
            int barH = Math.max(2, y1 - y0);

            // вертикальная полоска здоровья слева
            g.fill(x0 - 5, y0 - 1, x0 - 3, y1 + 1, 0xAA000000);
            int fh = Math.round(barH * ratio);
            if (fh > 0) g.fill(x0 - 5, y1 - fh, x0 - 3, y1, Draw.hpColor(ratio));

            // золотая полоска поглощения (золотые яблоки)
            float abs = e.getAbsorptionAmount();
            if (abs > 0.05f) {
                int ah = Math.round(barH * Draw.clamp01(abs / max));
                g.fill(x0 - 8, y1 - barH, x0 - 6, y1, 0x66000000);
                g.fill(x0 - 8, y1 - ah, x0 - 6, y1, 0xFFFACC15);
            }

            String s = e.getName().getString() + " " + num(hp);
            int tw = mc.font.width(s);
            Draw.roundRect(g, cx - tw / 2 - 3, y0 - 13, tw + 6, 11, 4, 0x99000000);
            g.drawString(mc.font, s, cx - tw / 2, y0 - 11, 0xFFFFFFFF, false);
        }

        if (c.espDistance) {
            String d = Math.round(dist) + "м";
            int tw = mc.font.width(d);
            g.drawString(mc.font, d, cx - tw / 2, y1 + 3, 0xFFE5E7EB, true);
        }
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
        // тонкая акцентная полоска сверху панели
        Draw.roundRect(g, x + 6, y, w - 12, 2, 1, Draw.fade(Theme.danger(), 0.85f));
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

    // ------------------------------------------------------- цифры урона

    private static void trackDamage(LivingEntity t, long now, Config c) {
        if (t == null) {
            hpEntity = null;
            return;
        }
        float hp = t.getHealth();
        if (t != hpEntity) {
            hpEntity = t;
            hpLast = hp;
            return;
        }
        if (c.damagePops && hp < hpLast - 0.05f) spawnPop(t, hpLast - hp, now);
        hpLast = hp;
    }

    private static void spawnPop(LivingEntity t, float dmg, long now) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int i = popNext;
        popNext = (popNext + 1) % POP_MAX;
        POP_POS[i * 3] = t.getX() + (r.nextDouble() - 0.5) * 0.6;
        POP_POS[i * 3 + 1] = t.getY() + t.getBbHeight() + 0.1;
        POP_POS[i * 3 + 2] = t.getZ() + (r.nextDouble() - 0.5) * 0.6;
        POP_DMG[i] = dmg;
        POP_BORN[i] = now;
    }

    private static void renderPops(GuiGraphics g, Minecraft mc, long now) {
        for (int i = 0; i < POP_MAX; i++) {
            long age = now - POP_BORN[i];
            if (age < 0L || age > POP_LIFE) continue;
            float t = age / (float) POP_LIFE;
            if (!projectPoint(POP_POS[i * 3], POP_POS[i * 3 + 1] + t * 0.9, POP_POS[i * 3 + 2], CAM, PT)) continue;

            String s = "-" + num(POP_DMG[i]);
            float scale = 1f + 0.6f * (1f - Draw.easeOut(age / 200f));
            float a = Draw.clamp01((1f - t) / 0.35f);
            int col = POP_DMG[i] >= 6f ? Theme.danger() : 0xFFFFC857;

            g.pose().pushMatrix();
            g.pose().translate(PT[0], PT[1]);
            g.pose().scale(scale, scale);
            g.drawString(mc.font, s, -mc.font.width(s) / 2, -4, Draw.fade(col, a), true);
            g.pose().popMatrix();
        }
    }
}
