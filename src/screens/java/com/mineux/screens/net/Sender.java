package com.mineux.screens.net;

import com.mineux.screens.Screens;
import com.mineux.screens.ScreensConfig;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends one shared screen's frames: at most {@code shareFps} a second (10, the relay's cap),
 * only while someone watches, never more than one being encoded. The render thread copies the
 * captured frame out of native memory (a memcpy); scaling to at most {@code shareWidth} and the
 * JPEG encode happen on the encoder thread. When frames get dropped -- by the relay client's
 * budget or the server -- it slows down and lowers the quality, and creeps back up after a few
 * calm seconds.
 */
final class Sender {

    private static final long CALM_NANOS = 3_000_000_000L;

    final UUID id;
    private final AtomicBoolean busy = new AtomicBoolean();
    private ByteBuffer staging;
    private int width;
    private int height;
    private int seq;
    private long lastNanos;
    private double fps = ScreensConfig.MAX_SHARE_FPS;
    private float quality = 0.6f;
    private int seenDrops;
    private long calmSince = System.nanoTime();
    private volatile boolean closed;
    private int sent;

    Sender(UUID id) {
        this.id = id;
    }

    /** Render thread: a new captured frame at {@code ptr}, valid only during this call. */
    void offer(long ptr, int w, int h, RelayClient client, Executor encoder, int maxWidth) {
        ScreensConfig config = ScreensConfig.get();
        long now = System.nanoTime();
        adapt(client, config, now);
        if (closed || busy.get() || now - lastNanos < (long) (1.0e9 / fps)) return;
        lastNanos = now;
        long bytes = (long) w * h * 4;
        if (staging == null || staging.capacity() < bytes) {
            if (staging != null) MemoryUtil.memFree(staging);
            staging = MemoryUtil.memAlloc((int) bytes);
        }
        MemoryUtil.memCopy(ptr, MemoryUtil.memAddress(staging), bytes);
        width = w;
        height = h;
        busy.set(true);
        int frameSeq = seq++;
        float q = quality;
        encoder.execute(() -> encode(client, frameSeq, q, maxWidth));
    }

    private void encode(RelayClient client, int frameSeq, float q, int maxWidth) {
        try {
            if (closed) return;
            byte[] jpeg = Encoders.get().encode(staging, width, height, maxWidth, q);
            if (Wire.HEADER + jpeg.length > Wire.MAX_FRAME) {
                quality = Math.max(0.2f, quality - 0.1f);
                return;
            }
            if (client.sendFrame(id, frameSeq, jpeg)) sent++;
        } catch (IOException | RuntimeException e) {
            Screens.LOGGER.warn("Could not encode a shared frame", e);
        } finally {
            busy.set(false);
            if (closed) free();
        }
    }

    private void adapt(RelayClient client, ScreensConfig config, long now) {
        int drops = client.drops();
        float target = config.shareQuality / 100f;
        if (drops > seenDrops) {
            seenDrops = drops;
            fps = Math.max(2.0, fps * 0.75);
            quality = Math.max(0.3f, Math.min(quality, target) - 0.05f);
            calmSince = now;
        } else if (now - calmSince > CALM_NANOS) {
            fps = Math.min(config.shareFps, fps + 1.0);
            quality = Math.min(target, quality + 0.05f);
            calmSince = now;
        }
        if (fps > config.shareFps) fps = config.shareFps;
        if (quality > target) quality = target;
    }

    double fps() {
        return fps;
    }

    int sent() {
        return sent;
    }

    void close() {
        closed = true;
        if (!busy.get()) free();
    }

    private synchronized void free() {
        if (staging == null) return;
        MemoryUtil.memFree(staging);
        staging = null;
    }

    /** One JPEG encoder per encoding thread. */
    static final class Encoders {
        private static final ThreadLocal<Jpeg.Encoder> LOCAL = ThreadLocal.withInitial(Jpeg.Encoder::new);

        private Encoders() {}

        static Jpeg.Encoder get() {
            return LOCAL.get();
        }
    }
}
