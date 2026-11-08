package com.mineux.core.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineux.core.DeskExtensions;
import com.mineux.core.net.MuxApi;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An extension's chatroom ({@link ChatRoomSource}) against a local server speaking the chat wire:
 * its tab, its own stream with its own Authorization, only its room's frames taken, a line said
 * to its server and nowhere else, hidden and emptied when the source says so, and opened again
 * under a new sign-in. Offline.
 */
class ChatRoomsTest {

    private static HttpServer server;
    private static final List<String> STREAM_AUTH = new CopyOnWriteArrayList<>();
    private static final List<String> SENT = new CopyOnWriteArrayList<>();

    private static volatile boolean shown = true;
    private static volatile String auth = "Bearer room-token";
    private static String base;

    private static final ChatRoomSource SOURCE = new ChatRoomSource() {
        @Override public String room() { return "lounge"; }
        @Override public String base() { return base; }
        @Override public String authorization() { return auth; }
        @Override public boolean shown() { return shown; }
    };

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat/stream", ChatRoomsTest::stream);
        server.createContext("/api/chat/send", ChatRoomsTest::send);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "chat-rooms-test");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
        MuxApi.signOut();
        DeskExtensions.addChatRoom(SOURCE);
    }

    @AfterAll
    static void stop() {
        shown = false;
        ChatNet.tick();
        server.stop(0);
    }

    private static void stream(HttpExchange exchange) throws IOException {
        STREAM_AUTH.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        frame(out, "hello", "{\"nick\":\"me-there\"}");
        frame(out, "history", "{\"room\":\"lounge\",\"messages\":[{\"id\":1,\"nick\":\"ann\",\"body\":\"earlier\",\"at\":\"2026-11-06T10:00:00Z\"}],"
            + "\"users\":[{\"nick\":\"ann\"},{\"nick\":\"me-there\"}]}");
        // Another room on the same stream: MineUX already has its own, so this one is left alone.
        frame(out, "history", "{\"room\":\"public\",\"messages\":[{\"id\":2,\"nick\":\"bob\",\"body\":\"not here\"}],\"users\":[]}");
        frame(out, "msg", "{\"room\":\"lounge\",\"id\":3,\"nick\":\"ann\",\"body\":\"now\"}");
        try {
            for (int i = 0; i < 300; i++) {
                Thread.sleep(100);
                out.write(":\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (InterruptedException | IOException e) {
            // The client went.
        } finally {
            exchange.close();
        }
    }

    private static void frame(OutputStream out, String event, String data) throws IOException {
        out.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void send(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        SENT.add(exchange.getRequestHeaders().getFirst("Authorization") + " " + body);
        byte[] answer = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, answer.length);
        exchange.getResponseBody().write(answer);
        exchange.close();
    }

    private static void await(String what, BooleanSupplier done) throws InterruptedException {
        long until = System.currentTimeMillis() + 10_000;
        while (!done.getAsBoolean()) {
            if (System.currentTimeMillis() > until) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(25);
        }
    }

    private static boolean saidIn(Chat.Channel channel, String body) {
        return Chat.lines(channel).stream().anyMatch(l -> l.body.equals(body));
    }

    @Test
    void anExtensionRoomStreamsSaysHidesAndFollowsItsSignIn() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> DeskExtensions.addChatRoom(named("public")), "MineUX's own room");
        assertThrows(IllegalArgumentException.class, () -> DeskExtensions.addChatRoom(named("Lounge")), "already added");
        assertThrows(IllegalArgumentException.class, () -> DeskExtensions.addChatRoom(named("#lounge")), "not a plain word");

        Chat.Channel room = Chat.room("#lounge");
        assertTrue(room != null && room.source == SOURCE, "the tab is there while shown");
        assertEquals(Chat.PUBLIC, Chat.channels().get(0).name, "MineUX's room first");

        ChatNet.connect();
        await("the room's history", () -> saidIn(room, "earlier") && saidIn(room, "now"));
        assertEquals("Bearer room-token", STREAM_AUTH.get(0), "the stream is opened with the source's sign-in");
        assertFalse(saidIn(Chat.room(Chat.PUBLIC), "not here"), "another room on that stream is not MineUX's");
        assertEquals("me-there", ChatNet.nick(room));
        assertFalse(ChatNet.nick().equals("me-there"), "MineUX's nick is its own stream's");
        assertTrue(Chat.users(room).contains("ann"));
        assertTrue(ChatNet.state(room).startsWith("connected"), ChatNet.state(room));
        assertEquals("127.0.0.1", ChatNet.site(room));
        assertFalse(ChatNet.running(), "signed out of MineUX: its own stream stays shut");

        // MineUX's own stream cannot fill an extension's room, whatever its frames say.
        ChatNet.dispatch("msg", "{\"room\":\"lounge\",\"id\":9,\"nick\":\"eve\",\"body\":\"from mineux\"}");
        assertFalse(saidIn(room, "from mineux"));

        ChatNet.say(room, "hello there", false);
        await("the line to reach the room's server", () -> !SENT.isEmpty());
        JsonObject sent = JsonParser.parseString(SENT.get(0).substring(SENT.get(0).indexOf('{'))).getAsJsonObject();
        assertTrue(SENT.get(0).startsWith("Bearer room-token "), SENT.get(0));
        assertEquals("lounge", sent.get("room").getAsString());
        assertEquals("hello there", sent.get("body").getAsString());

        shown = false;
        ChatNet.tick();
        assertNull(Chat.room("#lounge"), "hidden: no tab");
        assertFalse(Chat.channels().contains(room));
        assertTrue(Chat.lines(room).isEmpty(), "hidden: what it said is dropped");
        assertEquals("closed", ChatNet.state(room));

        shown = true;
        auth = "Bearer other-account";
        ChatNet.tick();
        await("the stream under the new sign-in", () -> STREAM_AUTH.contains("Bearer other-account"));
        await("the history again", () -> saidIn(room, "earlier"));

        auth = "Bearer third-account";
        ChatNet.tick();
        await("another account's stream", () -> STREAM_AUTH.contains("Bearer third-account"));
    }

    private static ChatRoomSource named(String name) {
        return new ChatRoomSource() {
            @Override public String room() { return name; }
            @Override public String base() { return base; }
            @Override public String authorization() { return ""; }
        };
    }
}
