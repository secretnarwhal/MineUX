package com.mineux.screens.net;

import com.mineux.screens.ScreensConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Other players' shared screens in this server's room. Only those in your dimension and within
 * the watch range (96 blocks at most) are watched -- asked for frames -- and drawn; the relay
 * sends nothing for the rest. List changes come on the game thread (Sharing drains the relay's
 * callbacks there); frames come on the socket's thread straight to the decoder.
 */
public final class Remotes {

    private final Map<UUID, RemoteScreen> screens = new ConcurrentHashMap<>();
    private volatile Set<UUID> watched = Set.of();
    private ExecutorService decoder;

    public Collection<RemoteScreen> all() {
        return screens.values();
    }

    void list(List<ShareInfo> list, UUID me, Set<UUID> mine) {
        Set<UUID> keep = new HashSet<>();
        for (ShareInfo s : list) {
            if (isMine(s, me, mine)) continue;
            keep.add(s.id);
            screen(s, me, mine);
        }
        for (UUID id : new ArrayList<>(screens.keySet())) {
            if (!keep.contains(id)) gone(id);
        }
    }

    void screen(ShareInfo s, UUID me, Set<UUID> mine) {
        if (isMine(s, me, mine)) return;
        RemoteScreen have = screens.get(s.id);
        if (have != null) have.update(s);
        else screens.put(s.id, new RemoteScreen(s));
    }

    void gone(UUID id) {
        RemoteScreen r = screens.remove(id);
        if (r != null) r.dispose();
    }

    private static boolean isMine(ShareInfo s, UUID me, Set<UUID> mine) {
        return mine.contains(s.id) || (me != null && me.equals(s.owner));
    }

    /** Socket thread: a frame for a screen we watch goes to the decoder; anything else is ignored. */
    void frame(Wire.Frame frame) {
        if (frame.codec() != Wire.CODEC_JPEG || !watched.contains(frame.id())) return;
        RemoteScreen r = screens.get(frame.id());
        if (r == null) return;
        if (decoder == null) decoder = Executors.newSingleThreadExecutor(Sharing.daemon("mineux-screens-decode"));
        r.offer(frame.payload(), decoder);
    }

    /** Game thread: which screens to watch now; tells the relay when that changes. */
    void tick(Minecraft mc, RelayClient client) {
        Set<UUID> want = new HashSet<>();
        ScreensConfig config = ScreensConfig.get();
        ClientLevel level = mc.level;
        if (config.watchOthers && level != null && mc.player != null) {
            String dim = level.dimension().identifier().toString();
            Vec3 eye = mc.player.getEyePosition();
            double range = config.watchRange;
            for (RemoteScreen r : screens.values()) {
                if (!r.info.dim.equals(dim)) continue;
                Vec3 at = r.where(level);
                if (at != null && at.distanceToSqr(eye) <= range * range) want.add(r.info.id);
            }
        }
        if (want.equals(watched)) return;
        watched = Set.copyOf(want);
        if (client != null) client.watch(want);
    }

    /** Render thread, once a frame: new frames up, every watched screen placed. */
    public void frame(Minecraft mc, float partialTick) {
        ClientLevel level = mc.level;
        for (RemoteScreen r : screens.values()) {
            boolean watching = watched.contains(r.info.id);
            r.visible = watching && level != null && r.place(level, partialTick);
            if (watching) r.upload();
        }
    }

    public boolean watching(UUID id) {
        return watched.contains(id);
    }

    void clear() {
        for (RemoteScreen r : screens.values()) r.dispose();
        screens.clear();
        watched = Set.of();
    }
}
