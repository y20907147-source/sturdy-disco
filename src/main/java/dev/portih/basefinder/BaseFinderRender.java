package dev.portih.basefinder;

import dev.portih.Draw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Map;

/**
 * Рендер подсветки: проекция 3D-блоков на экран (тот же подход, что и Esp.java мода: рисуем в HUD,
 * поэтому рамки видны сквозь стены). Два этапа:
 *  1) rebuildVisible() - 4 раза в секунду из тика: выбирает ближайшие maxBoxes блоков и скопления;
 *  2) render() - каждый кадр: только проекция и отрисовка готовых массивов (без аллокаций).
 *
 * Ограничение 2D-проекции: она не знает про покачивание камеры (view bobbing) и динамический FOV
 * (спринт/зелья), поэтому при беге рамки могут слегка "плавать". В покое совпадают точно.
 */
public final class BaseFinderRender {
    private BaseFinderRender() {}

    private static final double NEAR = 0.05;

    // ---- видимые блоки (заполняются в rebuildVisible) ----
    private static int[] vx = new int[256], vy = new int[256], vz = new int[256];
    private static int[] vcol = new int[256];
    private static float[] vdist = new float[256];
    private static int visCount;

    // ---- скопления ----
    private static final int MAX_CLUSTERS = 64;
    private static final double[] clX = new double[MAX_CLUSTERS], clY = new double[MAX_CLUSTERS],
            clZ = new double[MAX_CLUSTERS];
    private static final int[] clCol = new int[MAX_CLUSTERS];
    private static final String[] clLabel = new String[MAX_CLUSTERS];
    private static int clCount;

    // ---- камера: 0-2 глаз, 3-5 вперёд, 6-8 вправо, 9-11 вверх, 12 экранная ширина, 13 высота, 14-15 масштаб ----
    private static final double[] CAM = new double[16];
    private static final double[] CS = new double[24];   // 8 углов: depth, side, up
    private static final float[] PX = new float[8], PY = new float[8];

    // временные массивы сортировки
    private static long[] sortKeys = new long[1024];
    private static int[] candX = new int[1024], candY = new int[1024], candZ = new int[1024], candG = new int[1024];

    // рёбра куба по индексам углов (бит0=x, бит1=y, бит2=z)
    private static final int[] EDGES = {
            0, 1, 2, 3, 4, 5, 6, 7,
            0, 2, 1, 3, 4, 6, 5, 7,
            0, 4, 1, 5, 2, 6, 3, 7};

    public static void clearVisible() {
        visCount = 0;
        clCount = 0;
    }

    // ------------------------------------------------------------------ этап 1: выбор

    static void rebuildVisible(Minecraft mc, BaseFinderConfig cfg) {
        if (mc.player == null) return;
        double px = mc.player.getX(), py = mc.player.getY() + 0.5, pz = mc.player.getZ();
        double rangeSq = (double) cfg.range * cfg.range;
        double chunkRange = cfg.range + 24.0;   // запас на размер чанка
        int n = 0;
        clCount = 0;

        // --- кандидаты: все блоки в радиусе ---
        for (Map.Entry<Long, BaseFinder.ChunkData> e : BaseFinder.CACHE.entrySet()) {
            BaseFinder.ChunkData d = e.getValue();
            double ccx = (d.cx << 4) + 8 - px, ccz = (d.cz << 4) + 8 - pz;

            // скопление: добавляем независимо от дальности подсветки (трейсер ведёт к далёким базам)
            if (d.size > cfg.clusterThreshold && clCount < MAX_CLUSTERS) {
                int i = clCount++;
                clX[i] = (double) d.sumX / d.size + 0.5;
                clY[i] = (double) d.sumY / d.size + 0.5;
                clZ[i] = (double) d.sumZ / d.size + 0.5;
                int top = 0;
                for (int g = 1; g < d.counts.length; g++) if (d.counts[g] > d.counts[top]) top = g;
                clCol[i] = BaseFinder.groupColor(top);
                long dist = Math.round(Math.sqrt((clX[i] - px) * (clX[i] - px) + (clZ[i] - pz) * (clZ[i] - pz)));
                clLabel[i] = d.size + " бл. - " + dist + " м";
            }

            if (ccx * ccx + ccz * ccz > chunkRange * chunkRange) continue;
            if (n + d.size > candX.length) growCandidates(n + d.size);
            for (int b = 0; b < d.size; b++) {
                double dx = d.xyz[b * 3] + 0.5 - px, dy = d.xyz[b * 3 + 1] + 0.5 - py, dz = d.xyz[b * 3 + 2] + 0.5 - pz;
                double dsq = dx * dx + dy * dy + dz * dz;
                if (dsq > rangeSq) continue;
                candX[n] = d.xyz[b * 3];
                candY[n] = d.xyz[b * 3 + 1];
                candZ[n] = d.xyz[b * 3 + 2];
                candG[n] = d.grp[b];
                sortKeys[n] = ((long) (int) dsq << 32) | n;   // dsq <= 256^2 влезает в int
                n++;
            }
        }

        // --- ближайшие maxBoxes ---
        Arrays.sort(sortKeys, 0, n);
        int take = Math.min(n, cfg.maxBoxes);
        if (take > vx.length) {
            vx = new int[take];
            vy = new int[take];
            vz = new int[take];
            vcol = new int[take];
            vdist = new float[take];
        }
        for (int i = 0; i < take; i++) {
            int idx = (int) (sortKeys[i] & 0xFFFFFFFFL);
            vx[i] = candX[idx];
            vy[i] = candY[idx];
            vz[i] = candZ[idx];
            vcol[i] = 0xFF000000 | BaseFinder.groupColor(candG[idx]);
            vdist[i] = (float) Math.sqrt((double) (sortKeys[i] >>> 32));
        }
        visCount = take;
    }

    private static void growCandidates(int need) {
        int cap = Math.max(need, candX.length * 2);
        candX = Arrays.copyOf(candX, cap);
        candY = Arrays.copyOf(candY, cap);
        candZ = Arrays.copyOf(candZ, cap);
        candG = Arrays.copyOf(candG, cap);
        sortKeys = Arrays.copyOf(sortKeys, cap);
    }

    // ------------------------------------------------------------------ этап 2: рисование

    public static void render(GuiGraphics g, Minecraft mc, DeltaTracker delta) {
        BaseFinderConfig cfg = BaseFinderConfig.get();
        if (!cfg.enabled || mc.level == null || mc.player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;   // проекция рассчитана на вид от первого лица
        if (visCount == 0 && clCount == 0) return;

        float pt = delta.getGameTimeDeltaPartialTick(false);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        setupCamera(mc, pt, sw, sh);

        // рамки
        float range = Math.max(1f, cfg.range);
        for (int i = 0; i < visCount; i++) {
            float a = 1f - 0.55f * Math.min(1f, vdist[i] / range);
            drawBox(g, vx[i], vy[i], vz[i], Draw.fade(vcol[i], a), sw, sh);
        }

        // трейсеры и подписи
        if (cfg.tracers > 0) {
            float ox = sw * 0.5f;
            float oy = cfg.tracerFromCrosshair ? sh * 0.5f : sh;
            for (int i = 0; i < clCount; i++) {
                drawTracer(g, mc, clX[i], clY[i], clZ[i], 0xFF000000 | clCol[i], clLabel[i], ox, oy, sw, sh);
            }
            if (cfg.tracers == 2) {
                int lim = Math.min(visCount, 48);   // трейсеры ко всем рамкам - только к ближайшим
                for (int i = 0; i < lim; i++) {
                    drawTracer(g, mc, vx[i] + 0.5, vy[i] + 0.5, vz[i] + 0.5, Draw.fade(vcol[i], 0.5f), null,
                            ox, oy, sw, sh);
                }
            }
        }
    }

    private static void setupCamera(Minecraft mc, float pt, int sw, int sh) {
        Vec3 eye = mc.player.getEyePosition(pt);
        double yaw = Math.toRadians(mc.player.getViewYRot(pt));
        double pitch = Math.toRadians(mc.player.getViewXRot(pt));
        double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = (double) sw / sh;

        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);

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
        CAM[12] = sw / 2.0 / (tan * aspect);   // пикселей на единицу side/depth
        CAM[13] = sh / 2.0 / tan;              // пикселей на единицу up/depth
        CAM[14] = sw;
        CAM[15] = sh;
    }

    private static void drawBox(GuiGraphics g, int bx, int by, int bz, int col, int sw, int sh) {
        double minD = Double.MAX_VALUE, maxD = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double dx = bx + (i & 1) - CAM[0], dy = by + ((i >> 1) & 1) - CAM[1], dz = bz + ((i >> 2) & 1) - CAM[2];
            double d = dx * CAM[3] + dy * CAM[4] + dz * CAM[5];
            CS[i * 3] = d;
            CS[i * 3 + 1] = dx * CAM[6] + dz * CAM[8];
            CS[i * 3 + 2] = dx * CAM[9] + dy * CAM[10] + dz * CAM[11];
            if (d < minD) minD = d;
            if (d > maxD) maxD = d;
        }
        if (maxD < NEAR) return;   // куб целиком за спиной

        if (minD >= NEAR) {
            // все углы перед камерой: считаем экранный прямоугольник для выбора уровня детализации
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                float sx = (float) (CAM[14] * 0.5 + CS[i * 3 + 1] / CS[i * 3] * CAM[12]);
                float sy = (float) (CAM[15] * 0.5 - CS[i * 3 + 2] / CS[i * 3] * CAM[13]);
                PX[i] = sx;
                PY[i] = sy;
                if (sx < x0) x0 = sx;
                if (sx > x1) x1 = sx;
                if (sy < y0) y0 = sy;
                if (sy > y1) y1 = sy;
            }
            if (x1 < 0 || x0 > sw || y1 < 0 || y0 > sh) return;   // за пределами экрана
            float size = Math.max(x1 - x0, y1 - y0);
            int ix0 = Math.round(x0), iy0 = Math.round(y0), ix1 = Math.round(x1), iy1 = Math.round(y1);
            if (size < 3f) {                       // далеко: точка
                g.fill(ix0 - 1, iy0 - 1, ix0 + 2, iy0 + 2, col);
                return;
            }
            if (size < 16f) {                      // средне: плоская рамка (4 fill без поворотов)
                g.fill(ix0, iy0, ix1, iy0 + 1, col);
                g.fill(ix0, iy1 - 1, ix1, iy1, col);
                g.fill(ix0, iy0 + 1, ix0 + 1, iy1 - 1, col);
                g.fill(ix1 - 1, iy0 + 1, ix1, iy1 - 1, col);
                return;
            }
            // близко: полный каркас, углы уже спроецированы
            for (int e = 0; e < 12; e++) {
                int a = EDGES[e * 2], b = EDGES[e * 2 + 1];
                if (offscreen(PX[a], PY[a], PX[b], PY[b], sw, sh)) continue;
                Draw.line(g, PX[a], PY[a], PX[b], PY[b], 1f, col);
            }
            return;
        }

        // куб пересекает ближнюю плоскость: каждое ребро отсекаем по depth = NEAR
        for (int e = 0; e < 12; e++) {
            int a = EDGES[e * 2] * 3, b = EDGES[e * 2 + 1] * 3;
            double da = CS[a], db = CS[b];
            if (da < NEAR && db < NEAR) continue;
            double sa = CS[a + 1], ua = CS[a + 2], sb = CS[b + 1], ub = CS[b + 2];
            if (da < NEAR) {
                double t = (NEAR - da) / (db - da);
                sa += (sb - sa) * t;
                ua += (ub - ua) * t;
                da = NEAR;
            } else if (db < NEAR) {
                double t = (NEAR - db) / (da - db);
                sb += (sa - sb) * t;
                ub += (ua - ub) * t;
                db = NEAR;
            }
            float x1 = (float) (CAM[14] * 0.5 + sa / da * CAM[12]);
            float y1 = (float) (CAM[15] * 0.5 - ua / da * CAM[13]);
            float x2 = (float) (CAM[14] * 0.5 + sb / db * CAM[12]);
            float y2 = (float) (CAM[15] * 0.5 - ub / db * CAM[13]);
            if (offscreen(x1, y1, x2, y2, sw, sh)) continue;
            Draw.line(g, clampF(x1), clampF(y1), clampF(x2), clampF(y2), 1f, col);
        }
    }

    /** Отрезок целиком по одну сторону от экрана - рисовать нет смысла. */
    private static boolean offscreen(float x1, float y1, float x2, float y2, int sw, int sh) {
        return (x1 < 0 && x2 < 0) || (x1 > sw && x2 > sw) || (y1 < 0 && y2 < 0) || (y1 > sh && y2 > sh);
    }

    /** Защита от гигантских координат у самой ближней плоскости. */
    private static float clampF(float v) {
        return Math.max(-4000f, Math.min(4000f, v));
    }

    /**
     * Линия от прицела (или низа экрана) к точке мира. Если точка за спиной - линия указывает
     * направление, в котором нужно повернуться.
     */
    private static void drawTracer(GuiGraphics g, Minecraft mc, double wx, double wy, double wz, int col,
                                   String label, float ox, float oy, int sw, int sh) {
        double dx = wx - CAM[0], dy = wy - CAM[1], dz = wz - CAM[2];
        double depth = dx * CAM[3] + dy * CAM[4] + dz * CAM[5];
        double side = dx * CAM[6] + dz * CAM[8];
        double up = dx * CAM[9] + dy * CAM[10] + dz * CAM[11];

        float ex, ey;
        boolean front = depth >= 0.1;
        if (front) {
            ex = (float) (CAM[14] * 0.5 + side / depth * CAM[12]);
            ey = (float) (CAM[15] * 0.5 - up / depth * CAM[13]);
        } else {
            // за спиной: направление на экране = (side, -up), конец на фиксированном радиусе
            double len = Math.max(1e-6, Math.sqrt(side * side + up * up));
            float r = Math.min(sw, sh) * 0.42f;
            ex = (float) (sw * 0.5 + side / len * r);
            ey = (float) (sh * 0.5 - up / len * r);
        }
        // ограничиваем длину, чтобы линия не уходила в миллионы пикселей
        float tx = ex - ox, ty = ey - oy;
        float l = (float) Math.sqrt(tx * tx + ty * ty);
        float max = Math.max(sw, sh) * 1.2f;
        if (l > max) {
            ex = ox + tx / l * max;
            ey = oy + ty / l * max;
        }
        Draw.line(g, ox, oy, ex, ey, 1f, Draw.fade(col, 0.75f));

        if (label != null) {
            int tw = mc.font.width(label);
            int lx = Math.round(Math.max(4, Math.min(sw - tw - 4, ex - tw / 2f)));
            int ly = Math.round(Math.max(4, Math.min(sh - 14, ey - 12)));
            g.drawString(mc.font, label, lx, ly, col, true);
        }
    }
}
