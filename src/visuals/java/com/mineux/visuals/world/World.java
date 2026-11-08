package com.mineux.visuals.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mineux.visuals.VisualOps.*;

/**
 * The world and screen visuals of this package, run from one place: the loader's world hooks
 * (ops 70 to 79) come in here, and the parts are ticked once a tick and drawn once a frame as
 * gizmos from {@code LevelRenderer#render}, as sight's marks are.
 * <p>
 * Each part fails on its own: one that throws is logged and turned off until the game restarts,
 * and the game goes on drawing that piece as vanilla does.
 */
public final class World {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/world");

    static final int AMBIENCE = 0;
    static final int NO_RENDER = 1;
    static final int PARTICLES = 2;
    static final int ITEMS = 3;
    /** 4 is unused. */
    static final int UNUSED = 4;
    static final int TOTEMS = 5;
    static final int SKY = 6;
    static final int SHADER = 7;

    private static final String[] NAMES = {"ambience", "no render", "particles", "item physics", "unused", "totem pop", "sky shaders", "world shaders"};
    private static final boolean[] FAILED = new boolean[NAMES.length];

    private World() {}

    static boolean allowed() {
        return com.mineux.visuals.Visuals.on();
    }

    static boolean failed(int part) {
        return FAILED[part];
    }

    static void fail(int part, Throwable e) {
        if (FAILED[part]) return;
        FAILED[part] = true;
        LOGGER.error("world's {} failed and is off until the game restarts", NAMES[part], e);
    }

    // ---- the loader's hooks ---------------------------------------------------------------------

    public static void fog(Object fog) {
        if (!FogData.class.isInstance(fog)) return;
        if (!FAILED[AMBIENCE]) {
            try {
                Ambience.fog((FogData) fog);
            } catch (RuntimeException | LinkageError e) {
                fail(AMBIENCE, e);
            }
        }
        if (!FAILED[SKY]) {
            try {
                SkyShader.fog((FogData) fog);
            } catch (RuntimeException | LinkageError e) {
                fail(SKY, e);
            }
        }
    }

    public static void sky(Object state) {
        if (!SkyRenderState.class.isInstance(state)) return;
        // The sun the world shader lights by: the sky's own, after the clocks have been moved.
        WorldShader.sunAngle(((SkyRenderState) state).sunAngle);
        if (FAILED[AMBIENCE]) return;
        try {
            Ambience.sky((SkyRenderState) state);
        } catch (RuntimeException | LinkageError e) {
            fail(AMBIENCE, e);
        }
    }

    public static Object clock(Object ticks) {
        if (FAILED[AMBIENCE]) return ticks;
        try {
            return Ambience.clock(ticks);
        } catch (RuntimeException | LinkageError e) {
            fail(AMBIENCE, e);
            return ticks;
        }
    }

    /** {@code OP_WORLD_HIDE}: whether to leave the piece {@code code} names undrawn. */
    public static boolean hide(Object code) {
        if (!Integer.class.isInstance(code)) return false;
        int piece = ((Integer) code).intValue();
        if (piece >= HIDE_CLOUDS && piece <= HIDE_SUN_MOON) return sky(piece);
        if (piece == HIDE_TOTEM_PARTICLES) return !FAILED[TOTEMS] && TotemPop.hidesVanilla();
        if (FAILED[NO_RENDER]) return false;
        try {
            return NoRender.hides(piece);
        } catch (RuntimeException | LinkageError e) {
            fail(NO_RENDER, e);
            return false;
        }
    }

    private static boolean sky(int piece) {
        if (FAILED[SKY]) return false;
        try {
            return SkyShader.hides(piece);
        } catch (RuntimeException | LinkageError e) {
            fail(SKY, e);
            return false;
        }
    }

    /** {@code OP_WORLD_HIDE_AT}: an explosion's particles at a point (no render's explosions). */
    public static boolean hideAt(Object thing) {
        try {
            if (Vec3.class.isInstance(thing)) {
                return !FAILED[NO_RENDER] && NoRender.hides(HIDE_EXPLOSIONS);
            }
        } catch (RuntimeException | LinkageError e) {
            fail(NO_RENDER, e);
        }
        return false;
    }

    public static void item(Object entity, Object state) {
        if (FAILED[ITEMS]) return;
        try {
            ItemPhysics.extract(entity, state);
        } catch (RuntimeException | LinkageError e) {
            fail(ITEMS, e);
        }
    }

    public static boolean itemDraw(Object args) {
        if (FAILED[ITEMS] || !Object[].class.isInstance(args)) return false;
        try {
            return ItemPhysics.draw((Object[]) args);
        } catch (RuntimeException | LinkageError e) {
            fail(ITEMS, e);
            return false;
        }
    }

    public static void frame(Object view, Object camera) {
        if (FAILED[SKY] || !Matrix4fc.class.isInstance(view) || !CameraRenderState.class.isInstance(camera)) return;
        try {
            SkyShader.beginFrame((Matrix4fc) view, (CameraRenderState) camera);
        } catch (RuntimeException | LinkageError e) {
            fail(SKY, e);
        }
    }

    public static void frameEnd() {
        if (FAILED[SHADER]) return;
        try {
            WorldShader.apply();
        } catch (RuntimeException | LinkageError e) {
            fail(SHADER, e);
        }
    }

    // ---- once a tick, once a frame --------------------------------------------------------------

    public static void onClientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (!FAILED[PARTICLES]) {
            try {
                Particles.onClientTick(mc);
            } catch (RuntimeException | LinkageError e) {
                fail(PARTICLES, e);
            }
        }
    }

    public static void onRenderFrame(Minecraft mc, float partialTick) {
        if (!allowed() || mc.level == null || mc.player == null) return;
        try (var ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            for (int part = PARTICLES; part <= TOTEMS; part++) {
                if (FAILED[part] || part == ITEMS) continue;
                try {
                    draw(part, mc, partialTick);
                } catch (RuntimeException | LinkageError e) {
                    fail(part, e);
                }
            }
        }
    }

    private static void draw(int part, Minecraft mc, float partialTick) {
        if (part == PARTICLES) Particles.draw(mc);
        else if (part == TOTEMS) TotemPop.draw(mc, partialTick);
    }

    // ---- colours ----------------------------------------------------------------------------

    /** {@code #rrggbb} (or {@code rrggbb}) as 0xRRGGBB; anything else is the fallback. */
    static int rgb(String hex, int fallback) {
        if (hex == null) return fallback;
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() != 6) return fallback;
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }

    /** {@code a} to {@code b} by {@code t}, both 0xRRGGBB; the alpha byte is dropped. */
    static int mix(int a, int b, float t) {
        float u = Math.max(0.0F, Math.min(1.0F, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * u);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * u);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * u);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * 0xRRGGBB at {@code alpha} (0 to 1) as ARGB, the alpha rounded to sixteen steps: gizmos are
     * batched by colour, and a fade through every alpha would be a batch for every one.
     */
    static int argb(int rgb, float alpha) {
        float a = Math.max(0.0F, Math.min(1.0F, alpha));
        int step = Math.round(a * 15.0F);
        return ((step * 17) << 24) | (rgb & 0xFFFFFF);
    }

    /** A hue round the wheel, for chroma colours: 0xRRGGBB. */
    static int hue(float h, float saturation, float brightness) {
        float f = (h - (float) Math.floor(h)) * 6.0F;
        int sector = Math.min(5, (int) f);
        float part = f - sector;
        float p = brightness * (1.0F - saturation);
        float q = brightness * (1.0F - saturation * part);
        float t = brightness * (1.0F - saturation * (1.0F - part));
        float r = sector == 0 || sector == 5 ? brightness : sector == 1 ? q : sector == 4 ? t : p;
        float g = sector == 1 || sector == 2 ? brightness : sector == 0 ? t : sector == 3 ? q : p;
        float b = sector == 3 || sector == 4 ? brightness : sector == 2 ? t : sector == 5 ? q : p;
        return (Math.round(r * 255.0F) << 16) | (Math.round(g * 255.0F) << 8) | Math.round(b * 255.0F);
    }
}
