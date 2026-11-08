package com.mineux.screens.bridge;

/**
 * A captured frame owned by the native library: tightly packed RGBA8 rows, alpha always 255
 * (the native side swizzles the capture's BGRA). {@code ptr} stays valid until the next poll of
 * the same capture source.
 */
public record CapturedFrame(long ptr, int width, int height, int stride, float scale) {

    public long bytes() {
        return (long) stride * height;
    }
}
