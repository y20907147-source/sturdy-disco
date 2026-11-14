package dev.portih;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;

public final class VictorySound {
    public static final String[] IDS = {
            "entity.player.levelup",
            "ui.toast.challenge_complete",
            "entity.experience_orb.pickup",
            "item.totem.use",
            "block.anvil.land",
            "entity.firework_rocket.blast",
            "entity.ender_dragon.growl",
            "block.bell.use"
    };
    public static final String[] NAMES = {
            "Левел-ап", "Достижение", "Опыт", "Тотем",
            "Наковальня", "Фейерверк", "Рык дракона", "Колокол"
    };

    private static float lastDist = 99f;

    private VictorySound() {}

    /** Вызывается каждый клиентский тик. Победа = умерла цель, по которой ты попал. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        LivingEntity t = HitTracker.lastHitEntity;
        if (t == null) return;

        long now = System.currentTimeMillis();
        if (now - HitTracker.lastHitMs > 5000L) {
            HitTracker.lastHitEntity = null;
            return;
        }

        if (!t.isDeadOrDying() && !t.isRemoved()) {
            lastDist = mc.player.distanceTo(t);
            return;
        }

        boolean died = t.isDeadOrDying() || lastDist < 8f;
        HitTracker.lastHitEntity = null;
        if (!died) return;
        if (mc.player.distanceTo(t) > 24f) return;

        Config c = Config.get();
        if (c.victorySound) play();
        if (c.victoryParticles) {
            Effects.spawn(mc, ParticleTypes.TOTEM_OF_UNDYING,
                    t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 30, 1.0, 0.6);
        }
    }

    public static void play() {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        if (CustomSounds.has(c.victoryCustom)) {
            CustomSounds.play(c.victoryCustom, c.volume, c.pitch);
            return;
        }
        int i = Math.floorMod(c.soundIndex, IDS.length);
        SoundEvent ev = SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(IDS[i]));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, c.pitch, c.volume));
    }
}
