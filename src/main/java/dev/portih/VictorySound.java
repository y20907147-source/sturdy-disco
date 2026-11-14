package dev.portih;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

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

    private static LivingEntity target;
    private static long targetTime;
    private static float lastDist;

    private VictorySound() {}

    /** Вызывается каждый клиентский тик. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            target = null;
            return;
        }
        long now = System.currentTimeMillis();

        if (mc.options.keyAttack.isDown()
                && mc.hitResult instanceof EntityHitResult r
                && r.getEntity() instanceof LivingEntity le
                && le != mc.player) {
            target = le;
            targetTime = now;
        }

        if (target == null) return;
        if (now - targetTime > 5000L) {
            target = null;
            return;
        }
        if (target.isAlive()) {
            lastDist = mc.player.distanceTo(target);
            return;
        }

        boolean nearby = lastDist < 16f;
        target = null;
        if (nearby && Config.get().victorySound) play();
    }

    public static void play() {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        int i = Math.floorMod(c.soundIndex, IDS.length);
        SoundEvent ev = SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(IDS[i]));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, c.pitch, c.volume));
    }
}
