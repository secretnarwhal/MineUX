package com.mineux.screens.net;

import com.mineux.screens.displays.Surface;
import com.mineux.screens.math.ViewFrame;
import com.mineux.screens.render.FrameTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Another player's shared screen: drawn like your own, never clickable or draggable. A world
 * anchored one glides to where its owner last put it; a player anchored one follows its owner's
 * player while that player is loaded and is hidden while not. Frames are decoded on the
 * decoder thread and go up to the GPU here, on the render thread.
 */
public final class RemoteScreen extends Surface {

    public ShareInfo info;
    public boolean visible;

    private final FrameTexture texture = new FrameTexture();
    private final AtomicReference<byte[]> incoming = new AtomicReference<>();
    private final AtomicBoolean decoding = new AtomicBoolean();
    private final AtomicReference<Pixels> ready = new AtomicReference<>();
    private final AtomicReference<Pixels> spare = new AtomicReference<>();
    private volatile boolean disposed;
    private boolean placed;

    RemoteScreen(ShareInfo info) {
        this.info = info;
    }

    @Override
    public FrameTexture texture() {
        return texture;
    }

    @Override
    public double worldWidth() {
        return info.width;
    }

    @Override
    public double worldHeight() {
        return info.height;
    }

    @Override
    public double arcAngle() {
        return info.curve * Math.PI;
    }

    void update(ShareInfo next) {
        if (!next.anchor.equals(info.anchor)) placed = false;
        info = next;
    }

    /** Where it is this frame; false when it cannot be shown (its owner's player is not loaded). */
    boolean place(ClientLevel level, float partialTick) {
        if (info.player()) {
            Player owner = info.owner == null ? null : level.getPlayerByUUID(info.owner);
            if (owner == null) return false;
            Pose.place(info, ViewFrame.of(owner), placement);
            placed = true;
            return true;
        }
        Vec3 from = placement.center();
        Vec3 facing = placement.facing();
        Vec3 up = placement.up();
        Pose.place(info, null, placement);
        if (placed && from.distanceToSqr(placement.center()) < 16.0) {
            // A screen its owner carries (follow position) is resent a few times a second: glide.
            placement.moveTo(from.lerp(placement.center(), 0.35));
            placement.orient(facing.lerp(placement.facing(), 0.35), up.lerp(placement.up(), 0.35));
        }
        placed = true;
        return true;
    }

    /** Where it would be for the watch range: its spot, or its owner's eyes. Null when unknown. */
    Vec3 where(ClientLevel level) {
        if (!info.player()) return new Vec3(info.x, info.y, info.z);
        Player owner = info.owner == null ? null : level.getPlayerByUUID(info.owner);
        return owner == null ? null : owner.getEyePosition();
    }

    // ---- frames ---------------------------------------------------------

    /** From the socket's thread: keep the newest, start decoding if idle. */
    void offer(byte[] jpeg, java.util.concurrent.Executor decoder) {
        if (disposed) return;
        incoming.set(jpeg);
        if (decoding.compareAndSet(false, true)) decoder.execute(this::decodeAll);
    }

    private void decodeAll() {
        try {
            byte[] next;
            while ((next = incoming.getAndSet(null)) != null && !disposed) decode(next);
        } finally {
            decoding.set(false);
        }
        if (incoming.get() != null && !disposed && decoding.compareAndSet(false, true)) decodeAll();
    }

    private void decode(byte[] jpeg) {
        BufferedImage image = Jpeg.decode(jpeg);
        if (image == null || image.getWidth() > 4096 || image.getHeight() > 4096) return;
        Pixels into = spare.getAndSet(null);
        if (into != null && !into.fits(image.getWidth(), image.getHeight())) {
            into.free();
            into = null;
        }
        if (into == null) into = new Pixels(image.getWidth(), image.getHeight());
        Jpeg.toRgba(image, into.buffer);
        if (disposed) {
            into.free();
            return;
        }
        recycle(ready.getAndSet(into));
    }

    private void recycle(Pixels done) {
        if (done == null) return;
        Pixels old = spare.getAndSet(done);
        if (old != null) old.free();
    }

    /** Render thread: the newest decoded frame to the GPU. */
    void upload() {
        Pixels frame = ready.getAndSet(null);
        if (frame == null) return;
        texture.upload(frame.buffer, frame.width, frame.height, 1.0f);
        recycle(frame);
    }

    /** Render thread: its texture freed; buffers freed as the decoder lets go of them. */
    void dispose() {
        disposed = true;
        texture.destroy();
        Pixels a = ready.getAndSet(null);
        if (a != null) a.free();
        if (!decoding.get()) {
            Pixels b = spare.getAndSet(null);
            if (b != null) b.free();
        }
    }
}
