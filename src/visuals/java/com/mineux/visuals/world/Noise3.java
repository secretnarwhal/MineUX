package com.mineux.visuals.world;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mineux.core.gpu.Texture;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * A 32 x 32 x 32 lattice of random bytes the world shader's fog reads one octave of noise from
 * with each fetch. The same lattice every time, from a fixed seed.
 * <p>
 * The game's render API has no 3D textures, so the lattice is laid out flat: its 32 slices side
 * by side, each 34 x 34 with a one-texel border copied from the slice's far edge, so a bilinear
 * read inside a slice wraps as a repeating 3D texture's would; {@code world_lo.fsh}'s
 * {@code noise3} mixes the two slices either side of a point, which is the trilinear read.
 */
final class Noise3 {

    static final int N = 32;
    static final int TILE = N + 2;
    static final int WIDTH = N * TILE;

    private static final Texture TEXTURE = new Texture("mineux fog noise", Texture.TABLE);

    private Noise3() {}

    static GpuTextureView view() {
        if (TEXTURE.is(GpuFormat.R8_UNORM, WIDTH, TILE)) return TEXTURE.view();
        GpuTextureView view = TEXTURE.ensure(GpuFormat.R8_UNORM, WIDTH, TILE);
        byte[] atlas = atlas(lattice());
        ByteBuffer bytes = MemoryUtil.memAlloc(atlas.length);
        try {
            bytes.put(atlas).flip();
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(TEXTURE.texture(), bytes, 0, 0, 0, 0, WIDTH, TILE);
        } finally {
            MemoryUtil.memFree(bytes);
        }
        return view;
    }

    /** The lattice, x fastest then y then z, as it always was. */
    static byte[] lattice() {
        byte[] out = new byte[N * N * N];
        Random random = new Random(1337L);
        for (int i = 0; i < out.length; i++) out[i] = (byte) random.nextInt(256);
        return out;
    }

    /** The lattice's slices side by side, each with its wrapped border: row by row, WIDTH x TILE. */
    static byte[] atlas(byte[] lattice) {
        byte[] out = new byte[WIDTH * TILE];
        for (int y = 0; y < TILE; y++) {
            int ly = Math.floorMod(y - 1, N);
            for (int x = 0; x < WIDTH; x++) {
                int z = x / TILE;
                int lx = Math.floorMod(x % TILE - 1, N);
                out[y * WIDTH + x] = lattice[lx + N * (ly + N * z)];
            }
        }
        return out;
    }
}
