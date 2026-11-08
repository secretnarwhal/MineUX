package com.mineux.screens.net;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** An RGBA8 frame in native memory, freed by hand: frames come too often to leave to the GC. */
final class Pixels {

    final ByteBuffer buffer;
    final int width;
    final int height;

    Pixels(int width, int height) {
        this.width = width;
        this.height = height;
        this.buffer = MemoryUtil.memAlloc(width * height * 4);
    }

    boolean fits(int w, int h) {
        return w == width && h == height;
    }

    void free() {
        MemoryUtil.memFree(buffer);
    }
}
