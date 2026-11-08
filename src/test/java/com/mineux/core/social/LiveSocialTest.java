package com.mineux.core.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineux.core.net.MuxApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The social client's calls against the real MineUX API ({@link MuxApi#DEFAULT_BASE}, or
 * {@code MINEUX_LIVE_BASE}) that need no account: the irc stream read as a guest (its frames fed
 * through the client's own parser), the public changelog, and the friends stream refusing a
 * caller with no session. Off unless {@code MINEUX_LIVE=1}:
 * <pre>MINEUX_LIVE=1 ./gradlew test --tests '*LiveSocialTest*'</pre>
 * Nothing is posted and no account is made.
 */
@EnabledIfEnvironmentVariable(named = "MINEUX_LIVE", matches = "1|true")
class LiveSocialTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeEach
    void start() {
        String base = System.getenv("MINEUX_LIVE_BASE");
        MuxApi.setBase(base == null || base.isBlank() ? MuxApi.DEFAULT_BASE : base);
        MuxApi.signOut();
    }

    @AfterEach
    void stop() {
        MuxApi.signOut();
    }

    @Test
    void aGuestReadsThePublicRoom() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MuxApi.base() + "/chat/stream"))
            .header("Accept", "text/event-stream").timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode(), "GET /chat/stream as a guest");
        boolean history = false;
        long until = System.currentTimeMillis() + 15_000;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String event = "";
            StringBuilder data = new StringBuilder();
            for (String line = reader.readLine(); line != null && System.currentTimeMillis() < until; line = reader.readLine()) {
                if (line.isEmpty()) {
                    if (!event.isEmpty() && data.length() > 0) {
                        ChatNet.dispatch(event, data.toString());
                        if (event.equals("history") && data.indexOf("\"public\"") >= 0) history = true;
                    }
                    if (history) break;
                    event = "";
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                }
            }
        }
        assertTrue(ChatNet.nick().startsWith("guest-"), "a guest's nick: " + ChatNet.nick());
        assertTrue(history, "the public room's history came");
        List<String> users = Chat.users(Chat.room(Chat.PUBLIC));
        assertTrue(users.contains(ChatNet.nick()), "the guest is listed in #public: " + users);
    }

    @Test
    void theChangelogIsPublic() throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(MuxApi.base() + "/changelog"))
            .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "GET /changelog");
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertFalse(json.entrySet().isEmpty(), "the changelog answer has something in it: " + response.body());
    }

    @Test
    void theFriendsStreamWantsASession() throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(MuxApi.base() + "/friends/stream"))
            .header("Accept", "text/event-stream").timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode(), "GET /friends/stream with no session");
        FriendNet.start();
        assertFalse(FriendNet.started(), "signed out, the client does not open the friends stream");
    }
}
