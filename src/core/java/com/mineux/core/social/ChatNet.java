package com.mineux.core.social;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mineux.core.DeskExtensions;
import com.mineux.core.net.MuxApi;
import com.mineux.core.social.FriendList;
import com.mineux.core.social.FriendOps;
import com.mineux.core.social.FriendTalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The chat's side of the wire. MineUX's room is the site's own ({@code /chat} on the MineUX API,
 * the chatroom on mineux.com): one server-sent-events stream down, signed in with the client's
 * session, and a POST a line up. An extension's rooms ({@link ChatRoomSource}) are the same wire
 * against a server of its own, each with a stream of its own, its own sign-in and its own
 * reconnect; only that room's frames are read off it. A line said here comes back on the stream
 * like anybody's, so nothing is echoed by hand. Conversations with friends go through the friends
 * service ({@link FriendTalk}), which already carries them.
 * <p>
 * The streams are opened the first time {@code irc} is, and held for the rest of the session so
 * the tabs count what was said while they were not looked at; {@link #tick} keeps each extension
 * room's stream following its source (shown or not, signed in or not, which account). One daemon
 * thread owns each stream; a call is a thread of its own. Nothing here throws at a caller: a
 * failure is a line in the channel.
 */
public final class ChatNet {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/chat");

    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private static final long BACKOFF_MIN_MS = 3_000L, BACKOFF_MAX_MS = 60_000L;

    /** MineUX's own stream. */
    private static final Stream OWN = new Stream(null, null);
    /** One per extension room, made as they are first looked at. Under ChatNet's lock. */
    private static final List<Stream> EXTRA = new ArrayList<>();
    /** irc has been opened: the streams are wanted for the rest of the session. */
    private static volatile boolean wanted;

    private ChatNet() {}

    /** Opens the streams that are not open. Returns at once. */
    public static synchronized void connect() {
        wanted = true;
        tick();
    }

    /** Closes MineUX's stream (a sign-out, another account); {@link #connect} opens it again. Extension rooms follow their own sign-in. */
    public static synchronized void stop() {
        OWN.stop();
    }

    /** Whether MineUX's stream is open or trying to be. */
    static boolean running() {
        return OWN.running;
    }

    /**
     * Once a client tick: each extension room's stream opened while irc is wanted, its source is
     * shown and signed in; closed, and its tab emptied, when that stops or the sign-in changes.
     */
    public static synchronized void tick() {
        // irc open as a guest (for an extension's room), then a MineUX sign-in: its room comes too.
        if (wanted && !OWN.running && MuxApi.signedIn()) OWN.start("");
        ensure();
        for (Stream each : EXTRA) follow(each);
    }

    /** A stream for every extension room added. */
    private static void ensure() {
        List<ChatRoomSource> sources = DeskExtensions.chatRooms();
        if (EXTRA.size() >= sources.size()) return;
        for (ChatRoomSource source : sources) if (streamOf(source) == null) EXTRA.add(new Stream(source, Chat.extraRoom(source)));
    }

    private static void follow(Stream stream) {
        String auth = wanted && stream.may() ? stream.auth() : "";
        if (stream.running && !auth.equals(stream.openedWith)) {
            stream.stop();
            if (stream.room != null) Chat.empty(stream.room);
        }
        if (!auth.isEmpty() && !stream.running) stream.start(auth);
    }

    private static Stream streamOf(ChatRoomSource source) {
        for (Stream each : EXTRA) if (each.source == source) return each;
        return null;
    }

    private static synchronized Stream streamFor(Chat.Channel channel) {
        if (channel.source == null) return OWN;
        ensure();
        return streamOf(channel.source);
    }

    /** The name MineUX's server has you under, once it has said. */
    public static String nick() {
        return OWN.nick;
    }

    /** The name a channel's server has you under, once it has said. */
    public static String nick(Chat.Channel channel) {
        Stream stream = streamFor(channel);
        return stream == null ? "" : stream.nick;
    }

    /** One line on how MineUX's stream is: connected and as whom, or why not. */
    public static String state() {
        return OWN.state();
    }

    /** One line on how the stream a channel comes on is. */
    public static String state(Chat.Channel channel) {
        Stream stream = streamFor(channel);
        return stream == null ? "closed" : stream.state();
    }

    /** Where a channel is, for irc's header and its tab's hover. */
    public static String site(Chat.Channel channel) {
        ChatRoomSource source = channel.source;
        if (source == null) return "mineux.com";
        try {
            return source.site();
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---- one stream -----------------------------------------------------------

    /** One server's stream: MineUX's ({@code source} null) or an extension room's. */
    private static final class Stream {

        final ChatRoomSource source;
        /** The extension room it fills; null for MineUX's, whose frames say their room. */
        final Chat.Channel room;

        volatile boolean running;
        volatile Thread worker;
        /** The open stream's body, closed by {@link #stop} so a blocked read ends at once. */
        volatile InputStream open;
        volatile boolean live;
        volatile String nick = "";
        volatile String trouble = "";
        /** The authorization it was opened with: another one, and it is opened again. */
        volatile String openedWith = "";

        Stream(ChatRoomSource source, Chat.Channel room) {
            this.source = source;
            this.room = room;
        }

        /** Whether it may be open: signed in (MineUX), or shown with a sign-in (an extension's). */
        boolean may() {
            if (source == null) return MuxApi.signedIn();
            try {
                return source.shown() && !auth().isEmpty();
            } catch (RuntimeException e) {
                return false;
            }
        }

        /** The Authorization header's value now; empty for none. */
        String auth() {
            String value;
            try {
                value = source == null ? bearer(MuxApi.token()) : source.authorization();
            } catch (RuntimeException e) {
                value = "";
            }
            return value == null ? "" : value;
        }

        String base() {
            return source == null ? MuxApi.base() : source.base();
        }

        synchronized void start(String auth) {
            if (running) return;
            running = true;
            openedWith = auth;
            Thread thread = new Thread(this::loop, source == null ? "mineux-chat" : "mineux-chat-room");
            thread.setDaemon(true);
            worker = thread;
            thread.start();
        }

        synchronized void stop() {
            running = false;
            live = false;
            nick = "";
            trouble = "";
            openedWith = "";
            dropWorker(worker);
            dropBody(open);
        }

        // The fields come in as parameters, never local copies: the obfuscator would read the
        // field again after it is cleared (HoistCheck).
        private void dropWorker(Thread thread) {
            worker = null;
            if (thread != null) thread.interrupt();
        }

        private void dropBody(InputStream body) {
            open = null;
            if (body == null) return;
            try {
                body.close();
            } catch (IOException ignored) {
                // Closing it was all that was wanted.
            }
        }

        /** Whether the calling thread is this stream's current one. */
        private boolean mine() {
            return running && worker == Thread.currentThread();
        }

        String state() {
            if (!may()) return source == null ? "not signed in" : "closed";
            if (live) return nick.isEmpty() ? "connected" : "connected as " + nick;
            String why = trouble;
            return why.isEmpty() ? "connecting..." : why;
        }

        private void loop() {
            long backoff = BACKOFF_MIN_MS;
            for (int tries = 0; tries < 100_000 && mine() && may(); tries++) {
                boolean opened = false;
                try {
                    opened = stream();
                } catch (IOException e) {
                    LOGGER.debug("chat stream ended", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (RuntimeException e) {
                    LOGGER.warn("chat stream failed", e);
                }
                if (!mine()) return;
                live = false;
                if (trouble.isEmpty()) trouble = "disconnected, trying again";
                backoff = opened ? BACKOFF_MIN_MS : Math.min(BACKOFF_MAX_MS, backoff * 2L);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            ended();
        }

        /** The loop gave up by itself: the next connect starts afresh, unless another stream has started. */
        private synchronized void ended() {
            if (worker != Thread.currentThread()) return;
            worker = null;
            running = false;
            live = false;
        }

        private boolean stream() throws IOException, InterruptedException {
            String auth = auth();
            if (auth.isEmpty()) {
                trouble = "not signed in";
                return false;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/chat/stream"))
                .header("Accept", "text/event-stream")
                .header("Authorization", auth)
                .GET()
                .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                trouble = "the server refused the chat (" + response.statusCode() + ")";
                response.body().close();
                return false;
            }

            open = response.body();
            if (!mine()) {
                response.body().close();
                return false;
            }
            live = true;
            trouble = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String event = "";
                StringBuilder data = new StringBuilder();
                String line = reader.readLine();
                while (line != null && mine()) {
                    if (line.isEmpty()) {
                        // Not once it is closed: its room may already be emptied.
                        if (!event.isEmpty() && data.length() > 0 && mine()) dispatch(event, data.toString());
                        event = "";
                        data.setLength(0);
                    } else if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (data.length() > 0) data.append('\n');
                        data.append(line.substring(5).trim());
                    }
                    line = reader.readLine();
                }
            }
            return true;
        }

        void dispatch(String event, String data) {
            try {
                JsonObject json = GSON.fromJson(data, JsonObject.class);
                if (json != null) handle(event, json);
            } catch (RuntimeException e) {
                // One bad frame must not take the stream down with it.
                LOGGER.warn("chat frame {} failed", event, e);
            }
        }

        private void handle(String event, JsonObject json) {
            if (Objects.equals("hello", event)) {
                nick = string(json, "nick");
                return;
            }
            Chat.Channel channel = channel(string(json, "room"));
            if (channel != null) frame(channel, event, json);
        }

        /** The channel a frame's room is, as this stream may fill it: MineUX's own rooms, or the one extension room. */
        private Chat.Channel channel(String name) {
            if (source == null) return Chat.ownRoom("#" + name);
            if (room == null || !room.name.equalsIgnoreCase("#" + name)) return null;
            return room;
        }
    }

    /** Feeds one frame of MineUX's own stream through, as the stream does (tests read a live stream with it). */
    static void dispatch(String event, String data) {
        OWN.dispatch(event, data);
    }

    private static void frame(Chat.Channel channel, String event, JsonObject json) {
        if (Objects.equals("history", event)) {
            Chat.clear(channel);
            for (JsonElement each : array(json, "messages")) {
                if (each.isJsonObject()) said(channel, each.getAsJsonObject());
            }
            List<String> users = new ArrayList<>();
            for (JsonElement each : array(json, "users")) {
                if (each.isJsonObject()) users.add(string(each.getAsJsonObject(), "nick"));
            }
            Chat.users(channel, users);
            // What was there before you came is not news.
            Chat.watchQuietly(channel);
        } else if (Objects.equals("msg", event)) {
            said(channel, json);
        } else if (Objects.equals("join", event)) {
            Chat.user(channel, string(json, "nick"), true);
        } else if (Objects.equals("part", event)) {
            Chat.user(channel, string(json, "nick"), false);
        } else if (Objects.equals("del", event)) {
            Chat.remove(channel, json.has("id") && json.get("id").isJsonPrimitive() ? json.get("id").getAsLong() : 0L);
        }
    }

    private static void said(Chat.Channel channel, JsonObject row) {
        long id = row.has("id") && row.get("id").isJsonPrimitive() ? row.get("id").getAsLong() : 0L;
        boolean action = Objects.equals("action", string(row, "kind"));
        Chat.add(channel, id, string(row, "at"), string(row, "nick"), string(row, "body"), action ? Chat.ACTION : Chat.MSG);
    }

    private static String bearer(String token) {
        return token == null || token.isEmpty() ? "" : "Bearer " + token;
    }

    // ---- saying ------------------------------------------------------------

    /** Says a line in a channel, off the caller's thread; what goes wrong is noted in the channel. */
    public static void say(Chat.Channel channel, String text, boolean action) {
        Thread thread = new Thread(() -> send(channel, text, action), "mineux-chat-call");
        thread.setDaemon(true);
        thread.start();
    }

    private static void send(Chat.Channel channel, String text, boolean action) {
        if (channel.dm) {
            JsonObject body = FriendTalk.who(channel.name);
            body.addProperty("text", action ? "* " + text : text);
            JsonObject answer = FriendTalk.post(FriendOps.DM, body);
            if (answer == null) {
                String why = FriendList.callError();
                Chat.note(channel, "not sent: " + (why.isEmpty() ? "failed" : why));
            } else {
                Chat.add(channel, 0L, string(answer, "at"), FriendList.selfUser(), action ? "* " + text : text, Chat.MINE);
            }
            return;
        }

        Stream stream = streamFor(channel);
        if (stream == null || !stream.may()) {
            Chat.note(channel, stream == null || stream.source == null ? "not sent: not signed in" : "not sent: this room is closed");
            return;
        }
        String auth = stream.auth();
        JsonObject body = new JsonObject();
        body.addProperty("room", channel.name.substring(1));
        body.addProperty("body", text);
        body.addProperty("action", action);
        HttpRequest request = HttpRequest.newBuilder(URI.create(stream.base() + "/chat/send"))
            .header("Accept", "application/json")
            .header("Authorization", auth)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) return;
            String message = "";
            try {
                message = string(GSON.fromJson(response.body(), JsonObject.class), "message");
            } catch (RuntimeException ignored) {
                // Not json: the status says enough.
            }
            Chat.note(channel, "not sent: " + (message.isEmpty() ? "the server refused it (" + response.statusCode() + ")" : message));
        } catch (IOException e) {
            Chat.note(channel, "not sent: could not reach the server");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            Chat.note(channel, "not sent: " + e.getMessage());
        }
    }

    /** Reads a conversation's history from the server, once, off the caller's thread. */
    public static void load(Chat.Channel channel) {
        if (!channel.dm || !Chat.ask(channel)) return;
        Thread thread = new Thread(() -> history(channel), "mineux-chat-call");
        thread.setDaemon(true);
        thread.start();
    }

    private static void history(Chat.Channel channel) {
        JsonObject answer = FriendTalk.conversation(channel.name);
        if (answer == null) {
            String why = FriendList.callError();
            Chat.note(channel, "could not read the conversation: " + (why.isEmpty() ? "failed" : why));
            return;
        }
        Chat.clear(channel);
        String me = FriendList.selfUser();
        for (JsonElement each : array(answer, "messages")) {
            if (!each.isJsonObject()) continue;
            JsonObject row = each.getAsJsonObject();
            boolean mine = row.has("mine") && row.get("mine").isJsonPrimitive() && row.get("mine").getAsBoolean();
            Chat.add(channel, 0L, string(row, "at"), mine ? me : channel.name, string(row, "body"), mine ? Chat.MINE : Chat.MSG);
        }
        Chat.watchQuietly(channel);
    }

    // ---- json --------------------------------------------------------------

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static JsonArray array(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) return new JsonArray();
        return object.get(key).getAsJsonArray();
    }
}
