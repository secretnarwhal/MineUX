package com.mineux.visuals.self;

import com.mineux.visuals.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Jump effect: a ring on the ground where you jump or land, for your eyes only -- one of the drawn
 * rings, spreading and fading over {@link SelfConfig#ringTime}, or a ring of particles.
 * <p>
 * A jump is leaving the ground going up; a landing is coming back down after more than three ticks
 * in the air, so stepping off a slab is neither. The drawn rings are gizmos, put in each frame for
 * that frame only, like the client's other marks on the world.
 */
final class JumpRings {

    static final List<String> RINGS = Arrays.asList("ripple", "shockwave", "rune", "bloom", "corona", "vortex", "spectrum");
    private static final int RIPPLE = 0, SHOCKWAVE = 1, RUNE = 2, BLOOM = 3, CORONA = 4, VORTEX = 5;

    static final List<String> PARTICLES = Arrays.asList("cloud", "endrod", "crit", "flame", "soul", "happy");
    private static final ParticleOptions[] PARTICLE_TYPES = {
        ParticleTypes.CLOUD, ParticleTypes.END_ROD, ParticleTypes.CRIT, ParticleTypes.FLAME, ParticleTypes.SOUL_FIRE_FLAME,
        ParticleTypes.HAPPY_VILLAGER};

    private static final List<String> TRIGGERS = Arrays.asList("jump", "land", "both");
    private static final int ON_JUMP = 0, ON_LAND = 1;

    /** One ring: where, when (nanos), and a seed so no two turn alike. */
    private record Ring(Vec3 at, long born, float seed) {}

    private static final List<Ring> LIVE = new ArrayList<>();
    private static boolean wasOnGround = true;
    private static int airTicks;
    private static int made;

    private JumpRings() {}

    static int ringIndex(String name) {
        return RINGS.indexOf(name);
    }

    static int particleIndex(String name) {
        return PARTICLES.indexOf(name);
    }

    static int triggerIndex(String name) {
        return TRIGGERS.indexOf(name);
    }

    static void onClientTick(Minecraft mc, LocalPlayer p) {
        SelfConfig config = SelfConfig.get();
        if (!config.jump || p == null || mc.level == null) {
            wasOnGround = true;
            airTicks = 0;
            LIVE.clear();
            return;
        }
        boolean onGround = p.onGround();
        boolean jumped = !onGround && wasOnGround && p.getDeltaMovement().y > 0.0;
        boolean landed = onGround && !wasOnGround && airTicks > 3;
        airTicks = onGround ? 0 : airTicks + 1;
        wasOnGround = onGround;
        int trigger = triggerIndex(config.jumpTrigger);
        boolean fire = jumped && trigger != ON_LAND || landed && trigger != ON_JUMP;
        if (!fire || config.jumpSprintOnly && !p.isSprinting() && !landed) return;

        int particle = particleIndex(config.jumpStyle);
        if (particle >= 0) {
            ParticleOptions type = PARTICLE_TYPES[particle];
            int points = config.jumpPoints;
            double radius = config.jumpRadius;
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2.0 / points * i;
                mc.level.addParticle(type, p.getX() + Math.cos(a) * radius, p.getY() + 0.05, p.getZ() + Math.sin(a) * radius, 0.0, 0.02, 0.0);
            }
            return;
        }
        float seed = (made++ * 0.6180339F) % 1.0F;
        LIVE.add(new Ring(new Vec3(p.getX(), p.getY() + 0.02, p.getZ()), System.nanoTime(), seed));
        if (LIVE.size() > 32) LIVE.remove(0);
    }

    static void onRenderFrame(Minecraft mc) {
        SelfConfig config = SelfConfig.get();
        if (!config.jump || LIVE.isEmpty()) return;
        long now = System.nanoTime();
        double ttl = config.ringTime;
        LIVE.removeIf(r -> (now - r.born()) / 1.0E9 > ttl);
        if (LIVE.isEmpty()) return;
        int style = Math.max(0, ringIndex(config.jumpStyle));
        int c1 = SelfConfig.rgb(config.ringColor, 0x6FC3FF);
        int c2 = SelfConfig.rgb(config.ringColor2, 0xC77DFF);
        float time = (float) ((now / 1.0E9) % 3600.0);
        try (var ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            for (Ring r : LIVE) {
                float prog = (float) Math.min(1.0, (now - r.born()) / 1.0E9 / ttl);
                draw(style, r, prog, time, config.ringSize, c1, c2, config.ringWalls);
            }
        }
    }

    private static void draw(int style, Ring r, float prog, float time, float size, int c1, int c2, boolean walls) {
        float ease = 1.0F - (1.0F - prog) * (1.0F - prog);
        float fade = 1.0F - prog;
        Vec3 at = r.at();
        if (style == RIPPLE) {
            for (int i = 0; i < 3; i++) {
                float p = prog * 1.4F - i * 0.2F;
                if (p <= 0.0F || p >= 1.0F) continue;
                circle(at, size * p, argb(fade * (1.0F - p), Theme.mix(c1, c2, i / 2.0F)), 2.0F, 0, walls);
            }
        } else if (style == SHOCKWAVE) {
            circle(at, size * ease, argb(fade, c1), 3.5F, argb(fade * 0.18F, c2), walls);
            circle(at, size * ease * 0.92F, argb(fade * 0.5F, c2), 1.5F, 0, walls);
        } else if (style == RUNE) {
            float spin = time * 0.8F + r.seed() * 6.283F;
            float rad = size * (0.55F + 0.45F * ease);
            circle(at, rad, argb(fade, c1), 2.0F, 0, walls);
            circle(at, rad * 0.72F, argb(fade * 0.8F, c2), 1.5F, 0, walls);
            star(at, rad * 0.72F, spin, argb(fade, c2), walls);
        } else if (style == BLOOM) {
            circle(at, size * ease, argb(fade * 0.9F, c1), 1.5F, argb(fade * 0.35F, Theme.mix(c1, c2, prog)), walls);
        } else if (style == CORONA) {
            float rad = size * (0.4F + 0.6F * ease);
            circle(at, rad, argb(fade, c1), 2.0F, 0, walls);
            rays(at, rad, rad * (1.25F + 0.2F * (float) Math.sin(time * 6.0F)), time * 0.5F + r.seed() * 6.283F, argb(fade, c2), walls);
        } else if (style == VORTEX) {
            spiral(at, size * ease, time * 3.0F + r.seed() * 6.283F, fade, c1, c2, walls);
        } else {
            spectrum(at, size * ease, time * 0.25F + r.seed(), fade, walls);
        }
    }

    /** A full-strength colour at {@code h} turns round the colour wheel, as 0xRRGGBB. */
    private static int hue(float h) {
        float f = h - (float) Math.floor(h);
        float r = Math.max(0.0F, Math.min(1.0F, Math.abs(f * 6.0F - 3.0F) - 1.0F));
        float g = Math.max(0.0F, Math.min(1.0F, 2.0F - Math.abs(f * 6.0F - 2.0F)));
        float b = Math.max(0.0F, Math.min(1.0F, 2.0F - Math.abs(f * 6.0F - 4.0F)));
        return (Math.round(r * 255.0F) << 16) | (Math.round(g * 255.0F) << 8) | Math.round(b * 255.0F);
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static void circle(Vec3 at, float radius, int stroke, float width, int fill, boolean walls) {
        if (radius <= 0.01F) return;
        GizmoStyle style = (fill >>> 24) > 0 ? GizmoStyle.strokeAndFill(stroke, width, fill) : GizmoStyle.stroke(stroke, width);
        GizmoProperties drawn = Gizmos.circle(at, radius, style);
        if (walls) drawn.setAlwaysOnTop();
    }

    private static void line(Vec3 a, Vec3 b, int color, float width, boolean walls) {
        GizmoProperties drawn = Gizmos.line(a, b, color, width);
        if (walls) drawn.setAlwaysOnTop();
    }

    private static Vec3 around(Vec3 at, float radius, float angle) {
        return at.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
    }

    /** Two triangles over each other, turning. */
    private static void star(Vec3 at, float radius, float spin, int color, boolean walls) {
        for (int i = 0; i < 6; i++) {
            float a = spin + i * 1.0472F;
            line(around(at, radius, a), around(at, radius, a + 2.0944F), color, 1.5F, walls);
        }
    }

    private static void rays(Vec3 at, float from, float to, float spin, int color, boolean walls) {
        for (int i = 0; i < 12; i++) {
            float a = spin + i * 0.5236F;
            line(around(at, from, a), around(at, to, a), color, 2.0F, walls);
        }
    }

    /** Three arms winding in from the rim, the first colour at the rim and the second at the heart. */
    private static void spiral(Vec3 at, float radius, float spin, float fade, int c1, int c2, boolean walls) {
        if (radius <= 0.01F) return;
        for (int arm = 0; arm < 3; arm++) {
            Vec3 last = null;
            for (int i = 0; i <= 12; i++) {
                float t = i / 12.0F;
                Vec3 p = around(at, radius * (1.0F - t * 0.85F), spin + arm * 2.0944F + t * 3.5F);
                if (last != null) line(last, p, argb(fade * (1.0F - t * 0.5F), Theme.mix(c1, c2, t)), 2.0F, walls);
                last = p;
            }
        }
    }

    /** A rim in every hue, the hues turning. */
    private static void spectrum(Vec3 at, float radius, float turn, float fade, boolean walls) {
        if (radius <= 0.01F) return;
        int n = 24;
        for (int i = 0; i < n; i++) {
            float a0 = i * 6.2832F / n;
            float a1 = (i + 1) * 6.2832F / n;
            line(around(at, radius, a0), around(at, radius, a1), argb(fade, hue(turn + i / (float) n)), 3.0F, walls);
        }
    }
}
