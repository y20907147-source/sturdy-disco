package dev.portih.basefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * BaseFinder: ядро. Кэш найденных блоков по чанкам, сканер с бюджетом времени на тик,
 * клавиши и отчёт в чат. Рисованием занимается {@link BaseFinderRender}.
 *
 * ПОТОКИ. Всё работает в главном (клиентском) потоке намеренно: PalettedContainer внутри секций
 * чанка защищён ThreadingDetector, и чтение секции из другого потока, пока главный поток принимает
 * пакеты блоков, может завершиться крашем "Reading/writing from multiple threads". Поэтому вместо
 * воркера используется: (1) быстрый отсев секций через палитру (maybeHas), (2) очередь чанков и
 * жёсткий бюджет мс на тик (BaseFinderConfig.scanBudgetMs).
 */
public final class BaseFinder {
    private BaseFinder() {}

    // ------------------------------------------------------------------ данные

    /** Результат сканирования одного чанка. Хранится в кэше, пока чанк загружен. */
    public static final class ChunkData {
        public final int cx, cz;
        public int[] xyz = new int[96];        // x,y,z подряд
        public byte[] grp = new byte[32];      // индекс группы для каждого блока
        public int size;
        public int[] counts;                   // количество блоков по группам
        public long sumX, sumY, sumZ;          // для центра масс
        public long scannedMs;

        ChunkData(int cx, int cz, int groups) {
            this.cx = cx;
            this.cz = cz;
            this.counts = new int[groups];
        }

        void add(int x, int y, int z, int g) {
            if (size == grp.length) {
                grp = Arrays.copyOf(grp, size * 2);
                xyz = Arrays.copyOf(xyz, size * 6);
            }
            xyz[size * 3] = x;
            xyz[size * 3 + 1] = y;
            xyz[size * 3 + 2] = z;
            grp[size] = (byte) g;
            size++;
            counts[g]++;
            sumX += x;
            sumY += y;
            sumZ += z;
        }
    }

    /** Кэш: ключ = упакованные координаты чанка (как ChunkPos.toLong()). */
    static final Map<Long, ChunkData> CACHE = new HashMap<>();
    private static final LinkedHashSet<Long> QUEUE = new LinkedHashSet<>();

    // быстрый поиск "блок -> индекс группы"; пересобирается при включении
    private static final IdentityHashMap<Block, Integer> GROUP_OF = new IdentityHashMap<>();
    private static final Predicate<BlockState> IS_TARGET = st -> GROUP_OF.containsKey(st.getBlock());
    private static int groupCount;

    private static ClientLevel lastLevel;
    private static int tickCounter;

    public static KeyMapping toggleKey;
    public static KeyMapping reportKey;

    // ------------------------------------------------------------------ инициализация

    /** Вызывается один раз из PortihClient.onInitializeClient(); category - уже созданная категория клавиш мода. */
    public static void init(KeyMapping.Category category) {
        BaseFinderConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portihmod.bf_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, category));
        reportKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portihmod.bf_report", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));

        // Чанк пришёл с сервера -> ставим в очередь на сканирование.
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (!BaseFinderConfig.get().enabled) return;
            QUEUE.add(key(chunkX(chunk), chunkZ(chunk)));
        });
        // Чанк выгружен -> выбрасываем его данные, иначе кэш будет расти бесконечно.
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            long k = key(chunkX(chunk), chunkZ(chunk));
            CACHE.remove(k);
            QUEUE.remove(k);
        });
    }

    // ------------------------------------------------------------------ тик

    public static void tick(Minecraft mc) {
        while (toggleKey.consumeClick()) toggle(mc);
        while (reportKey.consumeClick()) report(mc);

        BaseFinderConfig cfg = BaseFinderConfig.get();
        if (!cfg.enabled || mc.level == null || mc.player == null) {
            if (mc.level == null) lastLevel = null;
            return;
        }

        // смена мира/измерения: старые данные недействительны
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            resetAndEnqueueAll(mc);
        }

        // периодическая перепроверка (кто-то поставил/сломал блок): раз в секунду добавляем 1 устаревший чанк рядом
        if (cfg.rescanSeconds > 0 && ++tickCounter % 20 == 0) enqueueStale(mc, cfg);

        scanQueue(mc, cfg);

        // набор видимых рамок пересобираем 4 раза в секунду, а не каждый кадр
        if (tickCounter % 5 == 0) BaseFinderRender.rebuildVisible(mc, cfg);
    }

    private static void toggle(Minecraft mc) {
        setEnabled(mc, !BaseFinderConfig.get().enabled);
    }

    /** Включает/выключает поиск (клавиша B и переключатель в ClickGUI). */
    public static void setEnabled(Minecraft mc, boolean on) {
        BaseFinderConfig cfg = BaseFinderConfig.get();
        cfg.enabled = on;
        BaseFinderConfig.save();
        if (cfg.enabled) {
            rebuildLookup();
            resetAndEnqueueAll(mc);
        } else {
            CACHE.clear();
            QUEUE.clear();
            BaseFinderRender.clearVisible();
        }
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("BaseFinder: " + (cfg.enabled ? "ВКЛ" : "ВЫКЛ"))
                    .withStyle(cfg.enabled ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        }
    }

    /** Public: GUI может вызвать после смены списка групп/цветов. */
    public static void rebuildLookup() {
        GROUP_OF.clear();
        List<BaseFinderConfig.Group> groups = BaseFinderConfig.get().groups;
        groupCount = groups.size();
        for (int i = 0; i < groups.size(); i++) {
            BaseFinderConfig.Group g = groups.get(i);
            if (!g.enabled || g.blocks == null) continue;
            for (String id : g.blocks) {
                Identifier rl = Identifier.tryParse(id);
                if (rl == null) continue;
                final int gi = i;
                BuiltInRegistries.BLOCK.getOptional(rl).ifPresent(b -> GROUP_OF.put(b, gi));
            }
        }
    }

    private static void resetAndEnqueueAll(Minecraft mc) {
        CACHE.clear();
        QUEUE.clear();
        if (GROUP_OF.isEmpty()) rebuildLookup();
        if (mc.level == null || mc.player == null) return;
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        int r = Math.max(BaseFinderConfig.get().scanRadiusChunks, mc.options.renderDistance().get());
        // ближние чанки первыми: пользователь сразу видит результат рядом с собой
        for (int ring = 0; ring <= r; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    if (mc.level.hasChunk(pcx + dx, pcz + dz)) QUEUE.add(key(pcx + dx, pcz + dz));
                }
            }
        }
    }

    private static void enqueueStale(Minecraft mc, BaseFinderConfig cfg) {
        long now = System.currentTimeMillis();
        long maxAge = cfg.rescanSeconds * 1000L;
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        int r = cfg.scanRadiusChunks;
        for (ChunkData d : CACHE.values()) {
            if (now - d.scannedMs < maxAge) continue;
            if (Math.abs(d.cx - pcx) > r || Math.abs(d.cz - pcz) > r) continue;
            QUEUE.add(key(d.cx, d.cz));
            return;   // по одному за раз: нагрузка размазана во времени
        }
    }

    // ------------------------------------------------------------------ сканер

    private static void scanQueue(Minecraft mc, BaseFinderConfig cfg) {
        if (QUEUE.isEmpty() || mc.level == null) return;
        long deadline = System.nanoTime() + (long) (cfg.scanBudgetMs * 1_000_000.0);
        do {
            Iterator<Long> it = QUEUE.iterator();
            if (!it.hasNext()) break;
            long k = it.next();
            it.remove();
            int cx = keyX(k), cz = keyZ(k);
            if (!mc.level.hasChunk(cx, cz)) continue;
            LevelChunk chunk = mc.level.getChunk(cx, cz);
            ChunkData d = scan(mc.level, chunk, cx, cz, cfg);
            if (d.size == 0) CACHE.remove(k);
            else CACHE.put(k, d);
        } while (!QUEUE.isEmpty() && System.nanoTime() < deadline);
    }

    private static ChunkData scan(ClientLevel level, LevelChunk chunk, int cx, int cz, BaseFinderConfig cfg) {
        ChunkData out = new ChunkData(cx, cz, Math.max(1, groupCount));
        out.scannedMs = System.currentTimeMillis();
        if (GROUP_OF.isEmpty()) return out;

        int minY = level.dimensionType().minY();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = cx << 4, baseZ = cz << 4;

        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection s = sections[si];
            if (s == null || s.hasOnlyAir()) continue;
            // самый дешёвый отсев: проверяем палитру секции, не перебирая 4096 блоков
            if (!s.maybeHas(IS_TARGET)) continue;

            int baseY = minY + (si << 4);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Integer gi = GROUP_OF.get(s.getBlockState(x, y, z).getBlock());
                        if (gi == null) continue;
                        out.add(baseX + x, baseY + y, baseZ + z, gi);
                        if (out.size >= cfg.maxPerChunk) return out;
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ отчёт в чат

    /** Выводит в локальный чат чанки, где целевых блоков больше порога (по убыванию). */
    public static void report(Minecraft mc) {
        if (mc.player == null) return;
        BaseFinderConfig cfg = BaseFinderConfig.get();
        if (!cfg.enabled) {
            mc.player.displayClientMessage(Component.literal("[Базы] Сначала включите подсветку (клавиша B).")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        List<ChunkData> hits = new ArrayList<>();
        for (ChunkData d : CACHE.values()) if (d.size > cfg.clusterThreshold) hits.add(d);
        hits.sort((a, b) -> Integer.compare(b.size, a.size));

        mc.player.displayClientMessage(Component.literal("[Базы] Скоплений (> " + cfg.clusterThreshold
                + " блоков в чанке): " + hits.size()).withStyle(ChatFormatting.GOLD), false);

        double px = mc.player.getX(), pz = mc.player.getZ();
        int shown = Math.min(hits.size(), 10);
        for (int i = 0; i < shown; i++) {
            ChunkData d = hits.get(i);
            int x = (int) Math.round((double) d.sumX / d.size);
            int y = (int) Math.round((double) d.sumY / d.size);
            int z = (int) Math.round((double) d.sumZ / d.size);
            long dist = Math.round(Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz)));
            mc.player.displayClientMessage(Component.literal("#" + (i + 1) + "  X " + x + "  Y " + y + "  Z " + z
                    + "  (" + dist + " м)  " + d.size + " бл.: " + breakdown(d, cfg))
                    .withStyle(ChatFormatting.AQUA), false);
        }
        if (hits.size() > shown) {
            mc.player.displayClientMessage(Component.literal("... и ещё " + (hits.size() - shown))
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** "Контейнеры 12, Печи 6, Кровати 2" - три самые крупные группы. */
    static String breakdown(ChunkData d, BaseFinderConfig cfg) {
        StringBuilder sb = new StringBuilder();
        boolean[] used = new boolean[d.counts.length];
        for (int n = 0; n < 3; n++) {
            int best = -1;
            for (int i = 0; i < d.counts.length; i++) {
                if (!used[i] && d.counts[i] > 0 && (best < 0 || d.counts[i] > d.counts[best])) best = i;
            }
            if (best < 0) break;
            used[best] = true;
            if (sb.length() > 0) sb.append(", ");
            String name = best < cfg.groups.size() ? cfg.groups.get(best).name : "?";
            sb.append(name).append(' ').append(d.counts[best]);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ ключи чанков

    // Совпадает с форматом ChunkPos.toLong(), но не зависит от внутренних полей ChunkPos.
    static long key(int cx, int cz) {
        return (cx & 0xFFFFFFFFL) | ((cz & 0xFFFFFFFFL) << 32);
    }

    static int keyX(long k) {
        return (int) (k & 0xFFFFFFFFL);
    }

    static int keyZ(long k) {
        return (int) (k >>> 32);
    }

    private static int chunkX(LevelChunk c) {
        return keyX(c.getPos().toLong());
    }

    private static int chunkZ(LevelChunk c) {
        return keyZ(c.getPos().toLong());
    }

    static int groupColor(int g) {
        List<BaseFinderConfig.Group> gs = BaseFinderConfig.get().groups;
        return g >= 0 && g < gs.size() ? gs.get(g).color & 0xFFFFFF : 0xFFFFFF;
    }
}

