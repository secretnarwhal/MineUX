package com.mineux.visuals.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ambient particles: a fall of something round you wherever you go, for your eyes only.
 * <p>
 * Snow, glow and rain are this client's own, kept here and drawn as gizmos each frame: they
 * drift down from above you, settle on what they land on and fade, and are never in the game's
 * particle engine, so they cost it nothing and no setting of its own turns them off. The rest
 * are the game's own particles, a few spawned round you each tick and left to the engine.
 */
public final class Particles {

    private static final List<String> NAMES = Arrays.asList(
        "snow", "glow", "rain", "crit", "flame", "soul", "heart", "cloud", "spark", "lava", "note", "totem", "rod", "snowflake");
    private static final ParticleOptions[] GAME = {
        ParticleTypes.CRIT, ParticleTypes.FLAME, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.HEART, ParticleTypes.CLOUD,
        ParticleTypes.ELECTRIC_SPARK, ParticleTypes.LAVA, ParticleTypes.NOTE, ParticleTypes.TOTEM_OF_UNDYING,
        ParticleTypes.END_ROD, ParticleTypes.SNOWFLAKE};
    /** The first of {@link #NAMES} the game draws. */
    private static final int FIRST_GAME = 3;

    /** Natural colours, two a style: snow, glow, rain. */
    private static final int[] NATURAL = {0xFFFFFF, 0xCFE8FF, 0xFFE9A8, 0xFF9AE6, 0x9DB8D9, 0x6F8FB8};

    private static final int MAX = 1500;
    /** How long a settled flake lies before it is gone, seconds. */
    private static final float SETTLED = 1.2F;

    private static final List<Mote> MOTES = new ArrayList<>();
    private static long lastNanos;
    private static ClientLevel level;

    private Particles() {}

    static int index(String name) {
        return NAMES.indexOf(name);
    }

    // ---- once a tick ----------------------------------------------------------------------------

    static void onClientTick(Minecraft mc) {
        WorldConfig config = WorldConfig.get();
        if (mc.level != level) {
            level = mc.level;
            MOTES.clear();
        }
        if (!config.particles || !World.allowed()) {
            MOTES.clear();
            return;
        }
        int style = Math.max(0, index(config.particle));
        RandomSource random = mc.level.getRandom();
        Vec3 me = mc.player.position();
        for (int i = 0; i < config.particleRate; i++) {
            double x = me.x + (random.nextDouble() - 0.5) * 2.0 * config.particleRadius;
            double z = me.z + (random.nextDouble() - 0.5) * 2.0 * config.particleRadius;
            double y = me.y + random.nextDouble() * config.particleHeight;
            if (style >= FIRST_GAME) {
                mc.level.addParticle(GAME[style - FIRST_GAME], x, y, z, 0.0, 0.0 - config.particleSpeed, 0.0);
            } else if (MOTES.size() < MAX) {
                MOTES.add(new Mote(x, y + 2.0, z, random.nextFloat()));
            }
        }
    }

    // ---- once a frame ---------------------------------------------------------------------------

    static void draw(Minecraft mc) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        if (MOTES.isEmpty()) return;
        WorldConfig config = WorldConfig.get();
        int style = Math.max(0, Math.min(FIRST_GAME - 1, index(config.particle)));
        if (mc.isPaused()) dt = 0.0F;
        int c1 = config.particleNatural ? NATURAL[style * 2] : World.rgb(config.particleColor, 0x8FD3FF);
        int c2 = config.particleNatural ? NATURAL[style * 2 + 1] : World.rgb(config.particleColor2, 0xFF9AE6);
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        float fall = (float) config.particleSpeed * 20.0F * (style == 2 ? 6.0F : 1.0F);
        float size = (float) config.particleSize;
        for (int i = MOTES.size() - 1; i >= 0; i--) {
            Mote mote = MOTES.get(i);
            if (!mote.step(mc, dt, fall, style)) {
                MOTES.remove(i);
                continue;
            }
            mote.draw(eye, style, c1, c2, size, config.particleWalls);
        }
    }

    /** One of this client's own particles. */
    private static final class Mote {
        double x;
        double y;
        double z;
        final float seed;
        float age;
        /** Seconds since it landed; below nothing while it is still falling. */
        float settled = 0.0F - 1.0F;

        Mote(double x, double y, double z, float seed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.seed = seed;
        }

        /** Moves it on by {@code dt}; false once it is gone. */
        boolean step(Minecraft mc, float dt, float fall, int style) {
            age += dt;
            if (settled >= 0.0F) {
                settled += dt;
                return settled < SETTLED;
            }
            if (age > 30.0F) return false;
            // Snow and glow drift as they fall; rain comes straight down.
            float sway = style == 2 ? 0.0F : 0.35F;
            x += Math.sin(age * 1.3F + seed * 40.0F) * sway * dt;
            z += Math.cos(age * 1.1F + seed * 30.0F) * sway * dt;
            y -= fall * (0.7F + 0.6F * seed) * dt;
            BlockPos at = BlockPos.containing(x, y, z);
            if (!mc.level.getBlockState(at).isAir() || !mc.level.getFluidState(at).isEmpty()) {
                if (style == 2) return false;
                settled = 0.0F;
            }
            return true;
        }

        void draw(Vec3 eye, int style, int c1, int c2, float size, boolean walls) {
            float fadeIn = Math.min(1.0F, age / 0.4F);
            float fadeOut = settled < 0.0F ? 1.0F : 1.0F - settled / SETTLED;
            float alpha = Math.min(fadeIn, fadeOut);
            int colour = World.mix(c1, c2, seed);
            Vec3 at = new Vec3(x, y, z);
            double distance = Math.max(1.0, Math.sqrt(at.distanceToSqr(eye)));
            GizmoProperties drawn;
            if (style == 2) {
                drawn = Gizmos.line(at, new Vec3(x, y + 0.45 * size, z), World.argb(colour, alpha * 0.6F), 1.0F);
            } else {
                float pixels = (float) Math.max(1.0, Math.min(12.0, 10.0 * size / distance * 4.0));
                if (style == 1) {
                    // A glow: a soft halo that breathes, round a bright core.
                    float pulse = 0.55F + 0.45F * (float) Math.sin(age * 3.0F + seed * 20.0F);
                    GizmoProperties halo = Gizmos.point(at, World.argb(colour, alpha * 0.25F * pulse), pixels * 2.4F);
                    if (walls) halo.setAlwaysOnTop();
                    drawn = Gizmos.point(at, World.argb(World.mix(colour, 0xFFFFFF, 0.5F), alpha * 0.9F), pixels * 0.8F);
                } else {
                    drawn = Gizmos.point(at, World.argb(colour, alpha * 0.9F), pixels);
                }
            }
            if (walls) drawn.setAlwaysOnTop();
        }
    }
}
