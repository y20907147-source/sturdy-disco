package dev.portih;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

public final class Effects {
    public static final ParticleOptions[] PARTICLES = {
            ParticleTypes.CRIT,
            ParticleTypes.ENCHANTED_HIT,
            ParticleTypes.FLAME,
            ParticleTypes.END_ROD,
            ParticleTypes.HEART,
            ParticleTypes.SMOKE
    };
    public static final String[] PARTICLE_NAMES = {
            "Криты", "Магия", "Огонь", "Звёздочки", "Сердечки", "Дым"
    };

    public static final String[] HIT_SOUND_IDS = {
            "entity.arrow.hit_player",
            "block.note_block.pling",
            "entity.player.attack.crit",
            "block.amethyst_block.chime",
            "entity.experience_orb.pickup",
            "entity.player.attack.knockback"
    };
    public static final String[] HIT_SOUND_NAMES = {
            "Лук", "Пилинг", "Крит", "Аметист", "Опыт", "Нокбэк"
    };

    private Effects() {}

    /** Вызывается в момент реального попадания. */
    public static void onHit(Minecraft mc, LivingEntity target) {
        Config c = Config.get();
        if (mc.level == null) return;

        if (c.hitParticles) {
            int i = Math.floorMod(c.particleType, PARTICLES.length);
            spawn(mc, PARTICLES[i],
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    Math.max(1, c.particleCount), 0.7, 0.35);
        }
        if (c.hitSound) playHit();
    }

    /** Звук удара (встроенный или свой .wav). Используется и кнопкой "Проверить". */
    public static void playHit() {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        if (CustomSounds.has(c.hitCustom)) {
            CustomSounds.play(c.hitCustom, c.hitVolume, 1.0f);
            return;
        }
        int i = Math.floorMod(c.hitSoundIndex, HIT_SOUND_IDS.length);
        SoundEvent ev = SoundEvent.createVariableRangeEvent(
                Identifier.withDefaultNamespace(HIT_SOUND_IDS[i]));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, 1.0f, c.hitVolume));
    }

    public static void spawn(Minecraft mc, ParticleOptions type, double x, double y, double z,
                             int count, double spread, double speed) {
        if (mc.level == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            mc.level.addParticle(type,
                    x + (r.nextDouble() - 0.5) * spread,
                    y + (r.nextDouble() - 0.5) * spread,
                    z + (r.nextDouble() - 0.5) * spread,
                    (r.nextDouble() - 0.5) * speed,
                    r.nextDouble() * speed,
                    (r.nextDouble() - 0.5) * speed);
        }
    }

    /** Вспышка экрана при попадании. */
    public static void renderOverlay(GuiGraphics g, Minecraft mc, Config c) {
        if (!c.hitFlash || HitTracker.lastHitMs == 0L) return;
        long dt = System.currentTimeMillis() - HitTracker.lastHitMs;
        if (dt >= 200L) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float a = (1f - dt / 200f) * c.flashStrength * 0.35f;
        if (a > 0.02f) g.fill(0, 0, sw, sh, Theme.withAlpha(Theme.accent(), a));
    }
}
