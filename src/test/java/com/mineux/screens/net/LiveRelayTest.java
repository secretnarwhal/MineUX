package com.mineux.screens.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same client against the real relay, as a guest (no account is made or used):
 * {@code ./gradlew test -Dmineux.screens.liveRelay=wss://mineux.com/api/screens}. Screens are
 * friends only, so the relay refuses a guest: one error that says to sign in, a 4001 close, no
 * welcome, no list -- and the client does not reconnect. Sharing between friends needs signed-in
 * accounts and is covered by RelayClientTest against the test relay and by the server's own end-to-end tests.
 */
@EnabledIfSystemProperty(named = "mineux.screens.liveRelay", matches = "wss?://.+")
class LiveRelayTest {

    @Test
    void theRealRelayRefusesGuests() throws Exception {
        URI uri = URI.create(System.getProperty("mineux.screens.liveRelay"));
        String room = Wire.room("mineux-screens-test-" + UUID.randomUUID());
        Recorder sees = new Recorder();
        RelayClient guest = new RelayClient(uri, Wire.hello(null, room, UUID.randomUUID(), "MuxScreensA"), sees);
        try {
            guest.start();
            Recorder.Error e = sees.await(Recorder.Error.class, x -> true, 15);
            assertTrue(e.msg().contains("sign in") && e.msg().contains("friends"), e.msg());
            sees.await(Recorder.Refused.class, x -> true, 10);
            Thread.sleep(2500);
            assertTrue(guest.refused());
            assertFalse(guest.connected());
            assertEquals(0, sees.connects, "never welcomed");
            assertEquals(null, sees.you, "no welcome");
            for (Object event : sees.events) {
                assertFalse(event instanceof Recorder.Screens || event instanceof Recorder.Screen, "a guest is shown nothing");
            }
        } finally {
            guest.stop();
        }
    }
}
