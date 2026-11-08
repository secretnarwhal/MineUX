package com.mineux.core.rpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineux.core.DeskExtensions;
import com.mineux.core.MineUX;
import com.mineux.core.ui.term.PresenceFacts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;

/**
 * Discord Rich Presence: the card on your Discord profile, in the desktop's own look.
 * <p>
 * It reads like a prompt in the terminal -- the first line is where you are as a path
 * ({@code ~/example.org}, {@code ~/desktop}), the second what is running there -- over the
 * application's art and a badge for what you are doing (the desktop, in game). The clock counts
 * from joining the world, or from launch on the menu. Whatever an extension says it is doing
 * ({@link PresenceSource}) goes over the second line and the badge.
 * <p>
 * <b>Off until there is an application.</b> A presence is shown as a Discord application, whose
 * name is what "Playing" says and whose uploaded art the asset keys below name. {@link #APP_ID}
 * is MineUX's own application's id: it must be an application registered for MineUX itself,
 * never another client's. While it is blank and {@code rpc.json} names no {@code appId}, nothing
 * connects to Discord at all.
 * <p>
 * The client thread works out the card once a second and leaves it for a worker, which owns the
 * Discord connection: it finds Discord when it starts, sends a card only when it changed (and
 * never faster than Discord's limit), and lets go when it closes. Nothing leaves this computer
 * but what Discord shows; the presence goes when the game does, because Discord drops it with
 * the pipe.
 */
public final class Presence {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/rpc");

    /**
     * MineUX's Discord application: its name is what "Playing" says, its art is what the keys
     * below name. It must be an application registered for MineUX; blank keeps the presence off.
     */
    public static final String APP_ID = "";

    /** Art asset keys, as uploaded to the application (tools/discord/art/*.png). */
    static final String ART_LARGE = "mineux";
    static final String BADGE_MENU = "menu";
    static final String BADGE_PLAY = "play";

    private static final String SITE = "https://mineux.com";

    /** Ticks between working out the card. */
    private static final int EVERY = 20;
    /** Discord takes five activity updates in twenty seconds; one in four leaves room. */
    private static final long MIN_SEND_MS = 4000L;
    /** A Discord that is not running is looked for again this often. */
    private static final long RETRY_MS = 15000L;

    private static final long LAUNCHED = System.currentTimeMillis();

    /** The card the worker should be showing ("" for none), and the application to show it as. */
    private static volatile String wanted = "";
    private static volatile String wantedApp = "";
    /** For the settings window. */
    private static volatile String status = "off";

    private static int ticks;
    private static boolean inWorld;
    private static long joined = LAUNCHED;
    private static Thread worker;

    private Presence() {}

    /** Once a tick, in a world or out of one. */
    public static void onClientTick(Minecraft mc) {
        if (ticks++ % EVERY != 0) return;
        boolean world = mc.level != null && mc.player != null;
        if (world != inWorld) {
            inWorld = world;
            joined = world ? System.currentTimeMillis() : LAUNCHED;
        }
        RpcConfig cfg = RpcConfig.get();
        String app = cfg.appId.isEmpty() ? appId() : cfg.appId;
        if (!cfg.enabled || app.isEmpty()) {
            wanted = "";
            wantedApp = "";
            status = cfg.enabled ? "no Discord application set (rpc.json appId)" : "off";
            return;
        }
        try {
            wanted = card(mc, cfg).toString();
        } catch (RuntimeException e) {
            LOGGER.warn("Could not work out the presence", e);
            return;
        }
        wantedApp = app;
        if (worker == null) {
            Thread t = new Thread(new Worker(), "mineux-rpc");
            t.setDaemon(true);
            t.start();
            worker = t;
        }
    }

    /** For the settings window: whether the card is up. */
    public static String stat() {
        return status;
    }

    private static JsonObject card(Minecraft mc, RpcConfig cfg) {
        String where;
        String doing;
        String badge;
        String badgeText;

        if (mc.level == null || mc.player == null) {
            where = "~/desktop";
            doing = "workstation " + PresenceFacts.workstation();
            badge = BADGE_MENU;
            badgeText = "on the desktop";
        } else {
            where = "~/" + place(mc, cfg);
            doing = modules(cfg);
            badge = BADGE_PLAY;
            badgeText = "in game";
        }
        // What an extension is doing goes over it: the last one with something to say.
        // A source with a badge and no line puts only its badge over the desktop's.
        for (PresenceSource source : DeskExtensions.presenceSources()) {
            String said = source.doing();
            boolean saying = said != null && !said.isEmpty();
            if (saying) doing = said;
            if (source.badge() != null && (saying || source.badgeAlone())) {
                badge = source.badge();
                badgeText = source.badgeText() == null ? "" : source.badgeText();
            }
        }
        if (!cfg.customLine.isBlank()) doing = cfg.customLine;

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", cfg.largeImage.isEmpty() ? art() : cfg.largeImage);
        assets.addProperty("large_text", MineUX.ready() ? MineUX.host().discordArtText() : "mineux");
        assets.addProperty("small_image", badge);
        assets.addProperty("small_text", badgeText);

        JsonObject times = new JsonObject();
        times.addProperty("start", joined / 1000L);

        JsonObject activity = new JsonObject();
        activity.addProperty("details", line(where));
        activity.addProperty("state", line(doing));
        activity.add("timestamps", times);
        activity.add("assets", assets);
        activity.addProperty("instance", false);
        if (cfg.button) {
            JsonObject site = new JsonObject();
            String url = site();
            site.addProperty("label", label(url));
            site.addProperty("url", url);
            JsonArray buttons = new JsonArray();
            buttons.add(site);
            activity.add("buttons", buttons);
        }
        return activity;
    }

    /** Where you are: the server as you would type it, singleplayer, or multiplayer when the server is hidden. */
    private static String place(Minecraft mc, RpcConfig cfg) {
        if (mc.isLocalServer()) return "singleplayer";
        ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null || !cfg.showServer) return "multiplayer";
        try {
            ServerAddress address = ServerAddress.parseString(server.ip.trim());
            String host = address.getHost().toLowerCase(Locale.ROOT);
            return address.getPort() == 25565 ? host : host + ":" + address.getPort();
        } catch (RuntimeException e) {
            return server.ip.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static String modules(RpcConfig cfg) {
        int[] m = PresenceFacts.modules();
        String shell = MineUX.shellName();
        if (!cfg.showModules || m[1] == 0) return shell;
        return shell + " > " + m[0] + "/" + m[1] + " modules on";
    }

    /** The host's application ({@link com.mineux.core.Host#discordAppId}), else MineUX's. */
    static String appId() {
        String id = MineUX.ready() ? MineUX.host().discordAppId() : null;
        return id == null || id.isEmpty() ? APP_ID : id;
    }

    /** The host's large art ({@link com.mineux.core.Host#discordArt}), else MineUX's. */
    static String art() {
        String key = MineUX.ready() ? MineUX.host().discordArt() : null;
        return key == null || key.isEmpty() ? ART_LARGE : key;
    }

    /** The host's site ({@link com.mineux.core.Host#site}), else MineUX's. */
    static String site() {
        String url = MineUX.ready() ? MineUX.host().site() : null;
        return url == null || url.isEmpty() ? SITE : url;
    }

    /** What a button says for a site: its address without the scheme or a trailing slash. */
    static String label(String url) {
        String out = url;
        int scheme = out.indexOf("://");
        if (scheme >= 0) out = out.substring(scheme + 3);
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    /** Discord wants two to 128 characters on a line. */
    private static String line(String s) {
        if (s.length() > RpcConfig.MAX_LINE) return s.substring(0, RpcConfig.MAX_LINE - 1) + "…";
        return s.length() < 2 ? s + "  " : s;
    }

    /** Owns the Discord connection, on its own thread: connecting and every write wait on Discord. */
    private static final class Worker implements Runnable {

        private DiscordIpc ipc;
        private String app = "";
        private String sent = "";
        private long sentAt;
        private long retryAt;

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    drop();
                    return;
                }
                step();
            }
        }

        private void step() {
            String card = wanted;
            String want = wantedApp;
            if (card.isEmpty() || want.isEmpty()) {
                drop();
                return;
            }
            if (ipc != null && !want.equals(app)) drop();
            long now = System.currentTimeMillis();
            if (ipc == null) {
                if (now < retryAt) return;
                try {
                    ipc = DiscordIpc.open(want);
                    app = want;
                    sent = "";
                    LOGGER.info("Connected to Discord");
                } catch (IOException e) {
                    retryAt = now + RETRY_MS;
                    status = "Discord not found";
                    return;
                }
            }
            if (card.equals(sent) || now - sentAt < MIN_SEND_MS) return;
            sentAt = now;
            try {
                ipc.setActivity(com.google.gson.JsonParser.parseString(card).getAsJsonObject());
                sent = card;
                status = "showing on Discord";
            } catch (DiscordIpc.RefusedException e) {
                // The card was wrong, not the connection: keep it, and do not send it again until it changes.
                sent = card;
                status = "Discord refused the card";
                LOGGER.warn("Discord refused the presence: {}", e.getMessage());
            } catch (IOException e) {
                LOGGER.info("Lost Discord: {}", e.getMessage());
                status = "Discord not found";
                drop();
                retryAt = now + RETRY_MS;
            }
        }

        private void drop() {
            if (ipc == null) return;
            ipc.close();
            ipc = null;
            app = "";
            sent = "";
            if (wanted.isEmpty()) status = "off";
        }
    }
}
