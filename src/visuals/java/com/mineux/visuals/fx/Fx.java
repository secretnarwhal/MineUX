package com.mineux.visuals.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hit and kill effects, for your eyes and ears only: a burst of particles on whoever your hit
 * lands on -- one slot for every hit, one for crits, one for the rest ({@link FxSlots}) -- a
 * sound of your choosing, and on a kill -- someone dying within a few seconds of your
 * last hit on them -- a lightning strike, a totem's burst or a pillar of flame, and a sound.
 * <p>
 * "Your hit" is the server's word for it, not a guess: its damage event names who caused the
 * damage, and only the ones naming you count. The lightning is a bolt this client makes for
 * itself, with an id below every id the server hands out, so it never stands in for a real
 * entity; it sounds and flashes the sky as a real one does and is gone in a second.
 * <p>
 * A crit is told as the game tells it ({@link FxHits}): the server's crit stars following your
 * damage event. The crit slot stands in for those stars: anything but vanilla leaves the game's
 * out for that hit (the hooks ask {@link #onCritPacket} and {@link #onOwnCrit}). Only your
 * own hits count; anybody else's crits you see are drawn as the game draws them. A hit landing
 * inside its target's hurt time gets no damage event from the server, so it is not seen as
 * yours, and its crit stars stay the game's.
 */
public final class Fx {

    /** The bursts, in {@link FxSlots#EFFECTS}'s order after off and vanilla. */
    private static final ParticleOptions[] PARTICLE_TYPES = {
        ParticleTypes.CRIT, ParticleTypes.ENCHANTED_HIT, ParticleTypes.FLAME, ParticleTypes.SOUL_FIRE_FLAME,
        ParticleTypes.HEART, ParticleTypes.TOTEM_OF_UNDYING, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD};

    /** How long after your last hit a death still counts as yours, ms. */
    private static final long KILL_WINDOW_MS = 4000L;

    /** Entity id -> when you last hit it. */
    private static final Map<Integer, Long> HIT_AT = new HashMap<>();
    /** This tick's hits of yours, crit or not. */
    private static final FxHits HITS = new FxHits();
    private static ClientLevel level;
    private static int boltId = 0 - 200000;

    private Fx() {}

    // ---- the server's events -----------------------------------------------------------------

    /** Damage to anyone: yours when its cause is you. */
    public static void onDamage(ClientboundDamageEventPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.level == null || mc.player == null || !com.mineux.visuals.Visuals.on()) return;
        FxConfig config = FxConfig.get();
        // The cosmetics' lock-on and kill effects ride on the same hits and kills.
        boolean cosmetics = com.mineux.visuals.cosmetics.Cosmetics.wantsHits();
        if (!config.enabled && !cosmetics) return;
        if (packet.sourceCauseId() != mc.player.getId() || packet.entityId() == mc.player.getId()) return;
        Entity struck = mc.level.getEntity(packet.entityId());
        if (struck == null) return;
        forgetOld(mc.level);
        HIT_AT.put(struck.getId(), System.currentTimeMillis());

        if (!config.enabled) return;
        // Crit or not is for the packets behind this one to say; every hit is now.
        HITS.hit(struck.getId());
        burst(mc, struck, FxSlots.burst(config.everyHit), config.count);
        sound(mc, config.hitSound, 1.0F);
    }

    /**
     * The server's crit stars or enchanted-hit sparkles on someone, from
     * {@code ClientPacketListener#handleAnimate}: true leaves the game's out. Your hit's crit
     * plays the crit slot here, the first time its stars come.
     */
    public static boolean onCritPacket(Object on, Object particle) {
        Minecraft mc = Minecraft.getInstance();
        if (!Entity.class.isInstance(on) || mc.level == null || !com.mineux.visuals.Visuals.on()) return false;
        FxConfig config = FxConfig.get();
        if (!config.enabled) return false;
        Entity struck = (Entity) on;
        boolean crit;
        if (particle == ParticleTypes.CRIT) {
            int mine = HITS.crit(struck.getId());
            if (mine < 0) return false;
            if (mine == 0) critHit(mc, struck, config);
            crit = true;
        } else if (particle == ParticleTypes.ENCHANTED_HIT) {
            int mine = HITS.magic(struck.getId());
            if (mine < 0) return false;
            crit = mine > 0;
        } else {
            return false;
        }
        return !FxSlots.keepsGame(crit, config.critHit, config.nonCritHit);
    }

    /**
     * The crit stars or sparkles the client draws itself as you swing at a player
     * ({@code LocalPlayer#crit}, {@code #magicCrit}), ahead of the server's: true leaves them
     * out. Always yours; nothing of ours plays here, the server's packets bring that.
     */
    public static boolean onOwnCrit(Object on, Object particle) {
        Minecraft mc = Minecraft.getInstance();
        if (!Entity.class.isInstance(on) || mc.player == null || !com.mineux.visuals.Visuals.on()) return false;
        FxConfig config = FxConfig.get();
        if (!config.enabled) return false;
        int id = ((Entity) on).getId();
        int tick = mc.player.tickCount;
        boolean crit;
        if (particle == ParticleTypes.CRIT) {
            HITS.ownCrit(id, tick);
            crit = true;
        } else if (particle == ParticleTypes.ENCHANTED_HIT) {
            crit = HITS.ownCritOf(id, tick);
        } else {
            return false;
        }
        return !FxSlots.keepsGame(crit, config.critHit, config.nonCritHit);
    }

    /** End of the client tick: this tick's hits with no crit stars were plain ones. */
    public static void onClientTick(Minecraft mc) {
        if (!HITS.any()) return;
        List<Integer> plain = HITS.flush();
        if (mc.level == null) return;
        FxConfig config = FxConfig.get();
        if (!config.enabled) return;
        for (Integer id : plain) {
            Entity struck = mc.level.getEntity(id.intValue());
            if (struck != null) nonCritHit(mc, struck, config);
        }
    }

    private static void critHit(Minecraft mc, Entity struck, FxConfig config) {
        burst(mc, struck, FxSlots.burst(config.critHit), config.count);
        sound(mc, config.critSound, 1.0F);
    }

    private static void nonCritHit(Minecraft mc, Entity struck, FxConfig config) {
        burst(mc, struck, FxSlots.burst(config.nonCritHit), config.count);
        sound(mc, config.nonCritSound, 1.0F);
    }

    /** A death: a kill when you hit them a moment ago. */
    public static void onEntityEvent(ClientboundEntityEventPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.level == null || !com.mineux.visuals.Visuals.on()) return;
        if (packet.getEventId() != EntityEvent.DEATH) return;
        FxConfig config = FxConfig.get();
        boolean cosmetics = com.mineux.visuals.cosmetics.Cosmetics.wantsHits();
        if (!config.enabled && !cosmetics) return;
        Entity dead = packet.getEntity(mc.level);
        if (dead == null || !LivingEntity.class.isInstance(dead)) return;
        forgetOld(mc.level);
        Long at = HIT_AT.remove(dead.getId());
        if (at == null || System.currentTimeMillis() - at > KILL_WINDOW_MS) return;
        if (cosmetics) com.mineux.visuals.cosmetics.Cosmetics.onKill(dead);
        if (config.enabled) kill(mc, dead, config);
    }

    private static void forgetOld(ClientLevel now) {
        if (now == level) {
            if (HIT_AT.size() > 64) HIT_AT.values().removeIf(t -> System.currentTimeMillis() - t > KILL_WINDOW_MS);
            return;
        }
        level = now;
        HIT_AT.clear();
        HITS.clear();
    }

    // ---- what is shown ------------------------------------------------------------------------

    /** {@code count} of burst {@code index} on whoever was hit; nothing for -1 (off, vanilla). */
    private static void burst(Minecraft mc, Entity struck, int index, int count) {
        if (index < 0 || index >= PARTICLE_TYPES.length) return;
        ParticleOptions type = PARTICLE_TYPES[index];
        double w = struck.getBbWidth();
        double h = struck.getBbHeight();
        for (int i = 0; i < count; i++) {
            double x = struck.getX() + (mc.level.getRandom().nextDouble() - 0.5) * w;
            double y = struck.getY() + mc.level.getRandom().nextDouble() * h;
            double z = struck.getZ() + (mc.level.getRandom().nextDouble() - 0.5) * w;
            double vx = (mc.level.getRandom().nextDouble() - 0.5) * 0.4;
            double vy = mc.level.getRandom().nextDouble() * 0.3;
            double vz = (mc.level.getRandom().nextDouble() - 0.5) * 0.4;
            mc.level.addParticle(type, x, y, z, vx, vy, vz);
        }
    }

    private static void kill(Minecraft mc, Entity dead, FxConfig config) {
        int effect = FxSlots.killIndex(config.killEffect);
        Vec3 at = dead.position();
        if (effect == 1) {
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(mc.level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setVisualOnly(true);
                bolt.setId(boltId--);
                bolt.snapTo(at.x, at.y, at.z);
                mc.level.addEntity(bolt);
            }
        } else if (effect == 2) {
            mc.particleEngine.createTrackingEmitter(dead, ParticleTypes.TOTEM_OF_UNDYING, 30);
        } else if (effect == 3 || effect == 4) {
            ParticleOptions type = effect == 3 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME;
            for (int i = 0; i < 60; i++) {
                double angle = i * 0.6;
                double up = i * 0.04;
                mc.level.addParticle(type, at.x + Math.cos(angle) * 0.6, at.y + up, at.z + Math.sin(angle) * 0.6, 0.0, 0.05, 0.0);
            }
        }
        sound(mc, config.killSound, 1.0F);
    }

    private static void sound(Minecraft mc, String name, float pitch) {
        int index = FxSlots.soundIndex(name);
        if (index <= 0) return;
        SimpleSoundInstance sound;
        if (index == 1) sound = SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 0.6F);
        else if (index == 2) sound = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, pitch);
        else if (index == 3) sound = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, pitch);
        else if (index == 4) sound = SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 1.0F);
        else if (index == 5) sound = SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_CRIT, pitch, 0.8F);
        else if (index == 6) sound = SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, pitch, 0.6F);
        else sound = SimpleSoundInstance.forUI(thunder(), pitch, 0.5F);
        mc.getSoundManager().play(sound);
    }

    private static SoundEvent thunder() {
        return SoundEvents.LIGHTNING_BOLT_THUNDER;
    }
}
