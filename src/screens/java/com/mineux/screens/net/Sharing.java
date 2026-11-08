package com.mineux.screens.net;

import com.mineux.core.net.MuxApi;
import com.mineux.screens.Screens;
import com.mineux.screens.ScreensConfig;
import com.mineux.screens.bridge.CapturedFrame;
import com.mineux.screens.capture.CaptureManager;
import com.mineux.screens.displays.Anchor;
import com.mineux.screens.displays.CapturedScreenDisplay;
import com.mineux.screens.math.ViewFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Screens in multiplayer, on the game's side: the relay connection for the server you are on,
 * your shared screens going out, and other players' coming in ({@link Remotes}).
 * <p>
 * <b>Privacy.</b> Every screen starts unshared, and nothing about sharing is saved: a new
 * capture, a new server or a restart starts with nothing shared. Only on a multiplayer server
 * (never singleplayer or LAN), only when signed in to MineUX, and the first whole monitor shared
 * in a session asks to be confirmed. Friends only: the relay passes a screen only to friends on
 * the same server who both have "enable screens" on for each other (their profile in the
 * friends app). Signed out, the relay is not even joined. What travels is the picture and its
 * placement, never a window title or monitor name. A shared screen sends frames only while
 * somebody watches it, and nothing at all while the game is minimised: you cannot see the red
 * LIVE marker then, so you may not remember a screen is out. Menus do not pause it -- you are
 * still looking at the game and its marker there. Leaving the server, switching Screens off
 * ({@code enabled}) or closing the screen ends the share.
 */
public final class Sharing {

    /** At most this many shared screens per account (the relay enforces it too). */
    public static final int MAX_SHARED = 4;
    private static final long RESEND_NANOS = 200_000_000L;

    private final Queue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Sender> senders = new HashMap<>();
    private final Map<UUID, String> sentKeys = new HashMap<>();
    private final Map<UUID, Long> sentAt = new HashMap<>();
    public final Remotes remotes = new Remotes();

    private RelayClient client;
    private String room;
    private UUID me;
    private volatile boolean connected;
    private String lastError;
    private ExecutorService encoder;
    private boolean monitorConfirmed;
    /** MuxApi's generation the relay was joined under: a sign-in or sign-out joins again with the new token. */
    private int joinedAs;
    /** On a server where screens could be shared (multiplayer, not LAN), as the last tick saw it. */
    private boolean onServer;
    /** The relay refused the sign-in joined under {@link #refusedAs} (ended session): wait for a new one. */
    private volatile boolean refused;
    private int refusedAs = -1;

    /** What a signed-out player is told: screens are friends only and need a MineUX sign-in. */
    public static final String SIGNED_OUT = "Screens are shared with friends only: sign in to MineUX to see and share them";

    // ---- the connection ------------------------------------------------

    /** Game thread, every tick: the room for where we are, what changed, what to watch. */
    public void tick(Minecraft mc) {
        Runnable r;
        while ((r = inbox.poll()) != null) r.run();
        String want = roomFor(mc);
        onServer = want != null;
        int generation = MuxApi.generation();
        if (refused && refusedAs != generation) refused = false;
        // Friends only: signed out (or refused under this sign-in) there is nobody to see or show.
        if (!MuxApi.signedIn() || refused) want = null;
        if (want == null || !want.equals(room) || joinedAs != generation) leave();
        if (want != null && client == null) join(mc, want);
        if (client == null) return;
        sendPlacements(mc);
        remotes.tick(mc, client);
    }

    /** The room of the server we are on; null where nothing is shared (singleplayer, LAN, off). */
    static String roomFor(Minecraft mc) {
        if (!ScreensConfig.get().enabled || mc.player == null || mc.level == null || mc.getConnection() == null) return null;
        if (mc.isLocalServer()) return null;
        ServerData server = mc.getCurrentServer();
        if (server == null || server.isLan() || server.ip == null || server.ip.isBlank()) return null;
        return Wire.room(server.ip);
    }

    private void join(Minecraft mc, String newRoom) {
        room = newRoom;
        me = mc.player.getUUID();
        joinedAs = MuxApi.generation();
        String hello = Wire.hello(MuxApi.token(), newRoom, me, mc.player.getName().getString());
        client = new RelayClient(URI.create(relayUrl()), hello, new Inbox());
        client.start();
        Screens.LOGGER.info("Screens relay: joining {} ({})", relayUrl(), newRoom.substring(0, 12));
    }

    /** {@code -Dmineux.screens.relay=ws://...} for a test relay; else the MineUX API's. */
    public static String relayUrl() {
        String override = System.getProperty("mineux.screens.relay");
        if (override != null && !override.isBlank()) return override.trim();
        return MuxApi.wsBase() + "/screens";
    }

    private void leave() {
        if (client == null) return;
        client.stop();
        client = null;
        room = null;
        connected = false;
        CaptureManager manager = Screens.manager();
        if (manager != null) for (CapturedScreenDisplay d : manager.displays) stopSending(d);
        remotes.clear();
    }

    // ---- sharing your screens -------------------------------------------

    /** Why sharing is not possible here, or null when it is. */
    public String unavailable() {
        if (!onServer) return "Sharing works on multiplayer servers (not singleplayer or LAN)";
        if (!MuxApi.signedIn() || refused) return SIGNED_OUT;
        if (client == null) return "Connecting to the screens relay";
        return null;
    }

    /**
     * Shares {@code display}. Null when done; otherwise why not. A whole monitor the first time
     * in a session needs {@code confirm}: without it, the answer starts with "Confirm".
     */
    public String share(CapturedScreenDisplay display, boolean confirm) {
        if (display.shared) return null;
        String why = unavailable();
        if (why != null) return why;
        int count = 0;
        for (CapturedScreenDisplay d : Screens.manager().displays) if (d.shared) count++;
        if (count >= MAX_SHARED) return "At most " + MAX_SHARED + " screens can be shared at once";
        if (display.target.isMonitor() && !monitorConfirmed && !confirm) {
            return "Confirm: this shares your WHOLE monitor " + display.target.title()
                + " -- everything on it, every window you open there -- with your friends here who have screens enabled";
        }
        if (display.target.isMonitor()) monitorConfirmed = true;
        display.shared = true;
        display.viewers = 0;
        senders.put(display.id, new Sender(display.id));
        sentKeys.remove(display.id);
        Screens.LOGGER.info("Screens: sharing {} on this server", display.target.isMonitor() ? "a monitor" : "a window");
        return null;
    }

    public void unshare(CapturedScreenDisplay display) {
        if (!display.shared) return;
        stopSending(display);
        if (client != null) client.unshare(display.id);
    }

    private void stopSending(CapturedScreenDisplay display) {
        display.shared = false;
        display.viewers = 0;
        Sender s = senders.remove(display.id);
        if (s != null) s.close();
        sentKeys.remove(display.id);
        sentAt.remove(display.id);
    }

    private void sendPlacements(Minecraft mc) {
        CaptureManager manager = Screens.manager();
        if (manager == null || mc.player == null || mc.level == null) return;
        long now = System.nanoTime();
        ViewFrame view = ViewFrame.of(mc.player);
        String dim = mc.level.dimension().identifier().toString();
        for (CapturedScreenDisplay d : manager.displays) {
            if (!d.shared || !d.framebuffer.isValid()) continue;
            ShareInfo info = describe(d, dim, view);
            String key = info.key();
            if (key.equals(sentKeys.get(d.id))) continue;
            Long at = sentAt.get(d.id);
            if (at != null && now - at < RESEND_NANOS) continue;
            sentKeys.put(d.id, key);
            sentAt.put(d.id, now);
            client.share(info);
        }
    }

    /** The share message for one of yours: follow view rides on the player, the rest are world spots. */
    static ShareInfo describe(CapturedScreenDisplay d, String dim, ViewFrame view) {
        ShareInfo info = new ShareInfo();
        info.id = d.id;
        info.dim = dim;
        info.anchor = d.anchorMode() == Anchor.FOLLOW_VIEW ? ShareInfo.ANCHOR_PLAYER : ShareInfo.ANCHOR_WORLD;
        Pose.fill(info, d.placement, view);
        info.width = d.worldWidth();
        info.height = d.worldHeight();
        info.curve = (float) (d.arcAngle() / Math.PI);
        int maxWidth = ScreensConfig.get().shareWidth;
        info.pxWidth = Jpeg.scaledWidth(d.framebuffer.width(), maxWidth);
        info.pxHeight = Jpeg.scaledHeight(d.framebuffer.width(), d.framebuffer.height(), maxWidth);
        return info;
    }

    /** Render thread: a new captured frame of a shared screen. */
    public void offer(CapturedScreenDisplay display, CapturedFrame frame) {
        Sender sender = senders.get(display.id);
        if (sender == null || client == null || !connected || display.viewers <= 0 || paused()) return;
        if (encoder == null) encoder = Executors.newSingleThreadExecutor(daemon("mineux-screens-encode"));
        sender.offer(frame.ptr(), frame.width(), frame.height(), client, encoder, ScreensConfig.get().shareWidth);
    }

    /** Minimised: nothing goes out. */
    public boolean paused() {
        return Minecraft.getInstance().getWindow().isMinimized() || Minecraft.getInstance().getWindow().isIconified();
    }

    public boolean connected() {
        return connected;
    }

    public String lastError() {
        return lastError;
    }

    /** Frames sent for a screen so far (for `screens list`). */
    public int sent(CapturedScreenDisplay display) {
        Sender s = senders.get(display.id);
        return s == null ? 0 : s.sent();
    }

    public boolean confirmedThisSession() {
        return monitorConfirmed;
    }

    static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    private Set<UUID> mine() {
        Set<UUID> ids = new HashSet<>();
        CaptureManager manager = Screens.manager();
        if (manager != null) for (CapturedScreenDisplay d : manager.displays) ids.add(d.id);
        return ids;
    }

    private void viewers(UUID id, int n) {
        CaptureManager manager = Screens.manager();
        if (manager == null) return;
        for (CapturedScreenDisplay d : manager.displays) if (d.id.equals(id) && d.shared) d.viewers = Math.max(0, n);
    }

    /** The relay's callbacks, handed to the game thread (frames go straight to the decoder). */
    private final class Inbox extends RelayClient.Listener {
        @Override
        public void onState(boolean up) {
            connected = up;
            inbox.add(() -> {
                if (!up) {
                    CaptureManager manager = Screens.manager();
                    if (manager != null) for (CapturedScreenDisplay d : manager.displays) d.viewers = 0;
                }
                sentKeys.clear();
            });
        }

        @Override
        public void onScreens(List<ShareInfo> list) {
            inbox.add(() -> remotes.list(list, me, mine()));
        }

        @Override
        public void onScreen(ShareInfo screen) {
            inbox.add(() -> remotes.screen(screen, me, mine()));
        }

        @Override
        public void onGone(UUID id) {
            inbox.add(() -> remotes.gone(id));
        }

        @Override
        public void onViewers(UUID id, int n) {
            inbox.add(() -> viewers(id, n));
        }

        @Override
        public void onFrame(Wire.Frame frame) {
            remotes.frame(frame);
        }

        @Override
        public void onRefused() {
            int generation = joinedAs;
            inbox.add(() -> {
                refused = true;
                refusedAs = generation;
                leave();
            });
        }

        @Override
        public void onError(String message) {
            lastError = message;
            Screens.LOGGER.warn("Screens relay: {}", message);
        }
    }
}
