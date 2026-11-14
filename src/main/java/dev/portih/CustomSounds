package dev.portih;

import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Свои звуки: кладёшь .wav в .minecraft/config/portihmod/sounds/ и выбираешь в меню.
 * Поддерживаются MP3 (декодер JLayer, вшит в мод), WAV (PCM 8/16/24/32 бит, float), AIFF и AU.
 * Играет через Java Sound.
 */
public final class CustomSounds {
    private static final long MAX_BYTES = 40L * 1024 * 1024;
    private static final List<String> FILES = new ArrayList<>();
    private static final Map<String, Pcm> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "PortihMod-Sound");
        t.setDaemon(true);
        return t;
    });
    private static boolean warned;

    record Pcm(AudioFormat format, byte[] data) {}

    private CustomSounds() {}

    // ------------------------------------------------------------- список

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("portihmod").resolve("sounds");
    }

    private static boolean supported(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".wave") || n.endsWith(".aif")
                || n.endsWith(".aiff") || n.endsWith(".au");
    }

    public static synchronized void rescan() {
        FILES.clear();
        try {
            Path d = dir();
            Files.createDirectories(d);
            try (Stream<Path> s = Files.list(d)) {
                s.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(CustomSounds::supported)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(FILES::add);
            }
        } catch (Exception e) {
            System.err.println("[PortihMod] Не удалось прочитать папку со звуками: " + e);
        }
    }

    public static synchronized List<String> files() {
        return new ArrayList<>(FILES);
    }

    public static synchronized boolean has(String name) {
        return name != null && !name.isEmpty() && FILES.contains(name);
    }

    public static synchronized String fileAt(int i) {
        return (i >= 0 && i < FILES.size()) ? FILES.get(i) : "";
    }

    /** Встроенные названия + файлы пользователя. */
    public static synchronized String[] names(String[] builtin) {
        String[] out = new String[builtin.length + FILES.size()];
        System.arraycopy(builtin, 0, out, 0, builtin.length);
        for (int i = 0; i < FILES.size(); i++) {
            String n = FILES.get(i);
            if (n.length() > 16) n = n.substring(0, 15) + "..";
            out[builtin.length + i] = "Файл: " + n;
        }
        return out;
    }

    public static synchronized int indexOf(String custom, int builtinIdx, int builtinLen) {
        if (custom != null && !custom.isEmpty()) {
            int i = FILES.indexOf(custom);
            if (i >= 0) return builtinLen + i;
        }
        return Math.floorMod(builtinIdx, builtinLen);
    }

    // ----------------------------------------------------------- файлы

    /** Копирует перетащенные в окно файлы в папку со звуками. Возвращает сколько скопировано. */
    public static int importFiles(List<Path> paths) {
        int n = 0;
        try {
            Files.createDirectories(dir());
            for (Path p : paths) {
                String name = p.getFileName().toString();
                if (!Files.isRegularFile(p) || !supported(name)) continue;
                Files.copy(p, dir().resolve(name), StandardCopyOption.REPLACE_EXISTING);
                n++;
            }
        } catch (IOException e) {
            System.err.println("[PortihMod] Не удалось скопировать звук: " + e);
        }
        rescan();
        return n;
    }

    public static void openFolder() {
        try {
            Files.createDirectories(dir());
            String path = dir().toAbsolutePath().toString();
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("explorer.exe", path);
            else if (os.contains("mac")) pb = new ProcessBuilder("open", path);
            else pb = new ProcessBuilder("xdg-open", path);
            pb.start();
        } catch (Exception e) {
            System.err.println("[PortihMod] Не удалось открыть папку: " + e);
        }
    }

    // ---------------------------------------------------------- проигрывание

    public static void play(String name, float volume, float pitch) {
        POOL.execute(() -> {
            try {
                Pcm pcm = load(name);
                if (pcm == null) return;

                byte[] data = pcm.data();
                if (Math.abs(pitch - 1f) > 0.01f) data = resample(data, pcm.format().getChannels(), pitch);
                if (data.length == 0) return;

                Clip clip = AudioSystem.getClip();
                clip.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.open(pcm.format(), data, 0, data.length);
                applyGain(clip, volume);
                clip.start();
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    System.err.println("[PortihMod] Не удалось проиграть " + name + ": " + t);
                }
            }
        });
    }

    private static void applyGain(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = volume <= 0.0001f ? gain.getMinimum() : (float) (20.0 * Math.log10(volume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }

    /** Читает файл и приводит к PCM 16 бит little-endian. Результат кэшируется. */
    private static Pcm load(String name) throws Exception {
        Path p = dir().resolve(name).normalize();
        if (!p.startsWith(dir().normalize()) || !Files.isRegularFile(p)) return null;
        long size = Files.size(p);
        if (size > MAX_BYTES) throw new IOException("файл больше 40 МБ");

        String key = name + "|" + size + "|" + Files.getLastModifiedTime(p).toMillis();
        Pcm cached = CACHE.get(key);
        if (cached != null) return cached;

        Pcm pcm = decode(name, Files.readAllBytes(p));
        CACHE.keySet().removeIf(k -> k.startsWith(name + "|"));
        CACHE.put(key, pcm);
        return pcm;
    }

    static Pcm decode(String name, byte[] file) throws Exception {
        if (name.toLowerCase(Locale.ROOT).endsWith(".mp3")) return decodeMp3(file);
        return decodeJavaSound(file);
    }

    /** MP3 -> PCM 16 бит little-endian через JLayer. */
    static Pcm decodeMp3(byte[] file) throws Exception {
        Bitstream bs = new Bitstream(new ByteArrayInputStream(file));
        Decoder dec = new Decoder();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rate = 44100;
        int ch = 2;
        try {
            Header h;
            while ((h = bs.readFrame()) != null) {
                SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                rate = sb.getSampleFrequency();
                ch = sb.getChannelCount();
                short[] buf = sb.getBuffer();
                int n = sb.getBufferLength();
                for (int i = 0; i < n; i++) {
                    out.write(buf[i] & 0xFF);
                    out.write((buf[i] >> 8) & 0xFF);
                }
                bs.closeFrame();
                if (out.size() > MAX_BYTES) break; // слишком длинный трек
            }
        } finally {
            bs.close();
        }
        if (out.size() == 0) throw new IOException("не удалось декодировать mp3");
        AudioFormat f = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, ch, ch * 2, rate, false);
        return new Pcm(f, out.toByteArray());
    }

    static Pcm decodeJavaSound(byte[] file) throws Exception {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(new ByteArrayInputStream(file))) {
            AudioFormat f = raw.getFormat();
            AudioInputStream in = raw;
            boolean ok = f.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                    && f.getSampleSizeInBits() == 16 && !f.isBigEndian();
            if (!ok) {
                AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(),
                        16, f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
                in = AudioSystem.getAudioInputStream(target, raw);
            }
            return new Pcm(in.getFormat(), in.readAllBytes());
        }
    }

    /** Изменение тона: линейный ресемплинг 16-битного PCM (меняет и скорость). */
    static byte[] resample(byte[] pcm, int channels, float pitch) {
        int frameSize = channels * 2;
        int frames = pcm.length / frameSize;
        int outFrames = Math.max(1, (int) (frames / pitch));
        byte[] out = new byte[outFrames * frameSize];
        for (int i = 0; i < outFrames; i++) {
            double src = i * (double) pitch;
            int i0 = (int) src;
            int i1 = Math.min(frames - 1, i0 + 1);
            if (i0 >= frames) i0 = frames - 1;
            double frac = src - i0;
            for (int ch = 0; ch < channels; ch++) {
                int a = sample(pcm, i0 * frameSize + ch * 2);
                int b = sample(pcm, i1 * frameSize + ch * 2);
                int v = (int) Math.round(a + (b - a) * frac);
                int o = i * frameSize + ch * 2;
                out[o] = (byte) (v & 0xFF);
                out[o + 1] = (byte) ((v >> 8) & 0xFF);
            }
        }
        return out;
    }

    private static int sample(byte[] d, int i) {
        return (short) ((d[i] & 0xFF) | (d[i + 1] << 8));
    }
}
