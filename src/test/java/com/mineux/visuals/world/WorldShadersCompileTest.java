package com.mineux.visuals.world;

import com.mineux.core.gpu.Glsl;
import com.mineux.core.gpu.ShaderCompile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every sky preset and both world shader passes, rewritten as they are drawn and compiled the way
 * the game's Vulkan backend compiles them (and for OpenGL), with their blocks read back; and the
 * fog noise's flat lattice read the way {@code world_lo.fsh} reads it against a real trilinear read.
 */
class WorldShadersCompileTest {

    @Test
    void everySkyPresetCompiles() throws IOException {
        String vertex = WorldGpu.read("sky/sky.vsh");
        for (int preset = 0; ; preset++) {
            String name;
            try {
                name = SkyLook.file(preset);
            } catch (IndexOutOfBoundsException end) {
                assertTrue(preset > 20, "only " + preset + " presets");
                break;
            }
            Glsl.Program p = Glsl.of(vertex, SkyShader.fragment(name));
            assertEquals(java.util.List.of("uNoise"), p.samplers, name);
            ShaderCompile.check("sky_" + name, p);
        }
        ShaderCompile.check("sky_upscale", Glsl.of(WorldGpu.read("sky/upscale.vsh"), WorldGpu.read("sky/upscale.fsh")));
    }

    @Test
    void bothWorldPassesCompile() throws IOException {
        String vertex = WorldGpu.read("world/world.vsh");
        String common = WorldGpu.read("world/common.glsl");
        Glsl.Program world = Glsl.of(vertex, "#version 330 core\n" + common + WorldGpu.read("world/world.fsh"));
        Glsl.Program low = Glsl.of(vertex, "#version 330 core\n" + common + WorldGpu.read("world/world_lo.fsh"));
        assertTrue(world.samplers.containsAll(java.util.List.of("uScene", "uDepth", "uLoA", "uLoB", "uHeight")), world.samplers.toString());
        assertTrue(low.samplers.containsAll(java.util.List.of("uScene", "uDepth", "uNoise3")), low.samplers.toString());
        ShaderCompile.check("world", world);
        ShaderCompile.check("world_lo", low);
    }

    @Test
    void sameSkyNoiseAsBefore() {
        byte[] bytes = SkyShader.noiseBytes();
        assertEquals(256 * 256 * 4, bytes.length);
        // The first texel, worked by hand from the old upload loop's xorshift.
        int s = 0x9E3779B9;
        s = xorshift(s);
        assertEquals((byte) (s >>> 24), bytes[0]);
    }

    private static int xorshift(int s) {
        int x = s ^ (s << 13);
        x ^= x >>> 17;
        return x ^ (x << 5);
    }

    /** The flat lattice, two bilinear reads and a mix, against a repeating 3D texture's trilinear read. */
    @Test
    void flatNoiseReadsAsTheThreeDimensionalOne() {
        byte[] lattice = Noise3.lattice();
        byte[] atlas = Noise3.atlas(lattice);
        Random random = new Random(7L);
        for (int i = 0; i < 20000; i++) {
            float x = (random.nextFloat() - 0.5F) * 12.0F;
            float y = (random.nextFloat() - 0.5F) * 12.0F;
            float z = (random.nextFloat() - 0.5F) * 12.0F;
            if (i < 64) {
                // Right on texel centres and slice edges too.
                x = (i % 33) / 32.0F + 0.5F / 32.0F;
                z = (i % 32) / 32.0F;
            }
            double want = trilinear(lattice, x, y, z);
            double got = flat(atlas, x, y, z);
            assertEquals(want, got, 1.0E-4, "at " + x + "," + y + "," + z);
        }
    }

    private static double texel3(byte[] lattice, int x, int y, int z) {
        int n = Noise3.N;
        return (lattice[Math.floorMod(x, n) + n * (Math.floorMod(y, n) + n * Math.floorMod(z, n))] & 0xFF) / 255.0;
    }

    private static double trilinear(byte[] lattice, float px, float py, float pz) {
        int n = Noise3.N;
        double u = px * n - 0.5;
        double v = py * n - 0.5;
        double w = pz * n - 0.5;
        int i = (int) Math.floor(u);
        int j = (int) Math.floor(v);
        int k = (int) Math.floor(w);
        double fu = u - i;
        double fv = v - j;
        double fw = w - k;
        double out = 0.0;
        for (int dz = 0; dz < 2; dz++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    double weight = (dx == 0 ? 1 - fu : fu) * (dy == 0 ? 1 - fv : fv) * (dz == 0 ? 1 - fw : fw);
                    out += weight * texel3(lattice, i + dx, j + dy, k + dz);
                }
            }
        }
        return out;
    }

    /** world_lo.fsh's noise3, with the GPU's clamped bilinear read done by hand. */
    private static double flat(byte[] atlas, float px, float py, float pz) {
        float z = pz * 32.0F - 0.5F;
        float zf = (float) Math.floor(z);
        float sx = fract(px) * 32.0F + 1.0F;
        float sy = fract(py) * 32.0F + 1.0F;
        double a = bilinear(atlas, mod(zf, 32.0F) * 34.0F + sx, sy);
        double b = bilinear(atlas, mod(zf + 1.0F, 32.0F) * 34.0F + sx, sy);
        double t = z - zf;
        return a + (b - a) * t;
    }

    private static double bilinear(byte[] atlas, double x, double y) {
        double u = x - 0.5;
        double v = y - 0.5;
        int i = (int) Math.floor(u);
        int j = (int) Math.floor(v);
        double fu = u - i;
        double fv = v - j;
        double out = 0.0;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int cx = Math.max(0, Math.min(Noise3.WIDTH - 1, i + dx));
                int cy = Math.max(0, Math.min(Noise3.TILE - 1, j + dy));
                double weight = (dx == 0 ? 1 - fu : fu) * (dy == 0 ? 1 - fv : fv);
                out += weight * (atlas[cy * Noise3.WIDTH + cx] & 0xFF) / 255.0;
            }
        }
        return out;
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private static float mod(float v, float m) {
        return v - m * (float) Math.floor(v / m);
    }
}
