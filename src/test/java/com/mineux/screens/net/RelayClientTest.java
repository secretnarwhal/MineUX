package com.mineux.screens.net;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two headless relay clients -- the classes the game uses, no game -- against the test relay:
 * one shares a screen and sends JPEG frames, the other sees it, watches it and receives them.
 * Friends only: Alice and Bob have screens on both ways ({@link TestRelay#screens}) unless a
 * test says otherwise; a guest is refused; a non-friend, or one side switching off, sees nothing.
 */
class RelayClientTest {

    private static final String ROOM = Wire.room("Play.Example.org");
    private TestRelay relay;
    private RelayClient alice;
    private RelayClient bob;
    private final Recorder aliceSees = new Recorder();
    private final Recorder bobSees = new Recorder();
    private final UUID alicePlayer = UUID.randomUUID();
    private final UUID bobPlayer = UUID.randomUUID();

    @BeforeEach
    void up() throws Exception {
        relay = new TestRelay(0);
        alice = new RelayClient(URI.create(relay.url()), Wire.hello("token-alice", ROOM, alicePlayer, "Alice"), aliceSees);
        bob = new RelayClient(URI.create(relay.url()), Wire.hello("token-bob", ROOM, bobPlayer, "Bob"), bobSees);
        relay.screens("token-alice", "token-bob", true);
        alice.start();
        bob.start();
        aliceSees.waitConnected(alice, 5);
        bobSees.waitConnected(bob, 5);
    }

    @AfterEach
    void down() throws Exception {
        alice.stop();
        bob.stop();
        relay.close();
    }

    private static ShareInfo share(UUID id) {
        ShareInfo s = new ShareInfo();
        s.id = id;
        s.dim = "minecraft:overworld";
        s.anchor = ShareInfo.ANCHOR_WORLD;
        s.x = 10.5;
        s.y = 70;
        s.z = -3.25;
        s.yaw = 90;
        s.pitch = 0;
        s.width = 3.84;
        s.height = 2.16;
        s.curve = 0.25f;
        s.pxWidth = 320;
        s.pxHeight = 180;
        return s;
    }

    private static byte[] jpeg(int seed) throws Exception {
        int w = 640, h = 360;
        ByteBuffer rgba = ByteBuffer.allocate(w * h * 4);
        for (int i = 0; i < w * h; i++) {
            rgba.put(i * 4, (byte) (i % w * 255 / w));
            rgba.put(i * 4 + 1, (byte) seed);
            rgba.put(i * 4 + 2, (byte) 40);
            rgba.put(i * 4 + 3, (byte) 255);
        }
        return new Jpeg.Encoder().encode(rgba, w, h, 320, 0.6f);
    }

    @Test
    void shareWatchAndReceiveFrames() throws Exception {
        assertEquals(alicePlayer, aliceSees.you);
        UUID id = UUID.randomUUID();
        alice.share(share(id));
        Recorder.Screen seen = bobSees.await(Recorder.Screen.class, s -> s.info().id.equals(id), 5);
        assertEquals(alicePlayer, seen.info().owner);
        assertEquals("Alice", seen.info().ownerName);
        assertEquals(3.84, seen.info().width, 1e-9);
        assertEquals(0.25f, seen.info().curve, 1e-6);

        bob.watch(Set.of(id));
        aliceSees.await(Recorder.Viewers.class, v -> v.id().equals(id) && v.n() == 1, 5);

        int received = 0;
        for (int seq = 0; seq < 5; seq++) {
            byte[] frame = jpeg(seq * 40);
            assertTrue(alice.sendFrame(id, seq, frame) || seq > 0, "the first frame goes out");
            Wire.Frame got = bobSees.await(Wire.Frame.class, f -> f.id().equals(id), 5);
            assertEquals(Wire.CODEC_JPEG, got.codec());
            assertArrayEquals(frame, got.payload());
            BufferedImage image = Jpeg.decode(got.payload());
            assertNotNull(image);
            assertEquals(320, image.getWidth());
            assertEquals(180, image.getHeight());
            received++;
            Thread.sleep(120);
        }
        assertEquals(5, received);

        bob.watch(Set.of());
        aliceSees.await(Recorder.Viewers.class, v -> v.id().equals(id) && v.n() == 0, 5);
        alice.unshare(id);
        bobSees.await(Recorder.Gone.class, g -> g.id().equals(id), 5);
    }

    @Test
    void guestsAreRefusedAndDoNotRetry() throws Exception {
        Recorder guestSees = new Recorder();
        RelayClient guest = new RelayClient(URI.create(relay.url()), Wire.hello(null, ROOM, UUID.randomUUID(), "Guest"), guestSees);
        try {
            UUID id = UUID.randomUUID();
            alice.share(share(id));
            guest.start();
            Recorder.Error e = guestSees.await(Recorder.Error.class, x -> true, 5);
            assertTrue(e.msg().contains("sign in"), e.msg());
            guestSees.await(Recorder.Refused.class, x -> true, 5);
            Thread.sleep(1500);
            assertTrue(guest.refused());
            assertFalse(guest.connected());
            assertEquals(0, guestSees.connects, "never welcomed");
            for (Object event : guestSees.events) {
                assertFalse(event instanceof Recorder.Screens || event instanceof Recorder.Screen, "a guest is shown nothing");
            }
        } finally {
            guest.stop();
        }
    }

    @Test
    void screensOffOnEitherSideShowsNothingAndSwitchingOffIsLive() throws Exception {
        relay.screens("token-alice", "token-bob", false);
        UUID id = UUID.randomUUID();
        alice.share(share(id));
        bob.watch(Set.of(id));
        Thread.sleep(400);
        assertTrue(bobSees.events.stream().noneMatch(e -> e instanceof Recorder.Screen), "off: Bob sees nothing");
        assertTrue(aliceSees.events.stream().noneMatch(e -> e instanceof Recorder.Viewers), "off: Bob's watch counts nothing");

        relay.screens("token-alice", "token-bob", true);
        bobSees.await(Recorder.Screen.class, s -> s.info().id.equals(id), 5);
        aliceSees.await(Recorder.Viewers.class, v -> v.id().equals(id) && v.n() == 1, 5);
        assertTrue(alice.sendFrame(id, 1, jpeg(1)));
        bobSees.await(Wire.Frame.class, f -> f.seq() == 1, 5);

        relay.screens("token-alice", "token-bob", false);
        bobSees.await(Recorder.Gone.class, g -> g.id().equals(id), 5);
        aliceSees.await(Recorder.Viewers.class, v -> v.id().equals(id) && v.n() == 0, 5);
        Thread.sleep(150);
        alice.sendFrame(id, 2, jpeg(2));
        Thread.sleep(300);
        assertTrue(bobSees.events.stream().noneMatch(e -> e instanceof Wire.Frame f && f.seq() == 2), "no frame after off");
    }

    @Test
    void aNonFriendSeesNothing() throws Exception {
        Recorder carolSees = new Recorder();
        RelayClient carol = new RelayClient(URI.create(relay.url()), Wire.hello("token-carol", ROOM, UUID.randomUUID(), "Carol"), carolSees);
        try {
            carol.start();
            carolSees.waitConnected(carol, 5);
            UUID id = UUID.randomUUID();
            alice.share(share(id));
            bobSees.await(Recorder.Screen.class, s -> s.info().id.equals(id), 5);
            carol.watch(Set.of(id));
            Thread.sleep(300);
            alice.sendFrame(id, 5, jpeg(5));
            Thread.sleep(300);
            assertTrue(carolSees.events.stream().noneMatch(e -> e instanceof Recorder.Screen || e instanceof Wire.Frame), "Carol sees nothing");
            assertTrue(aliceSees.events.stream().noneMatch(e -> e instanceof Recorder.Viewers v && v.n() > 0), "Carol counts nothing");
        } finally {
            carol.stop();
        }
    }

    @Test
    void lateJoinerGetsTheListAndOtherRoomsSeeNothing() throws Exception {
        UUID id = UUID.randomUUID();
        alice.share(share(id));
        bobSees.await(Recorder.Screen.class, s -> s.info().id.equals(id), 5);
        Recorder carolSees = new Recorder();
        RelayClient carol = new RelayClient(URI.create(relay.url()), Wire.hello("token-carol", ROOM, UUID.randomUUID(), "Carol"), carolSees);
        Recorder daveSees = new Recorder();
        RelayClient dave = new RelayClient(URI.create(relay.url()), Wire.hello("token-dave", Wire.room("other.server"), UUID.randomUUID(), "Dave"), daveSees);
        relay.screens("token-alice", "token-carol", true);
        relay.screens("token-alice", "token-dave", true);
        try {
            carol.start();
            dave.start();
            Recorder.Screens list = carolSees.await(Recorder.Screens.class, l -> true, 5);
            assertEquals(1, list.list().size());
            assertEquals(id, list.list().get(0).id);
            Recorder.Screens other = daveSees.await(Recorder.Screens.class, l -> true, 5);
            assertTrue(other.list().isEmpty());
        } finally {
            carol.stop();
            dave.stop();
        }
    }

    @Test
    void reconnectsAndSharesAgain() throws Exception {
        UUID id = UUID.randomUUID();
        alice.share(share(id));
        bob.watch(Set.of(id));
        aliceSees.await(Recorder.Viewers.class, v -> v.n() == 1, 5);
        relay.kickAll();
        // Both come back by themselves, say again what they share and watch, and it works again.
        aliceSees.await(Recorder.Viewers.class, v -> v.id().equals(id) && v.n() == 1, 15);
        assertTrue(aliceSees.connects >= 2);
        assertTrue(alice.sendFrame(id, 99, jpeg(7)));
        Wire.Frame got = bobSees.await(Wire.Frame.class, f -> f.seq() == 99, 5);
        assertEquals(id, got.id());
    }

    @Test
    void framesNeverQueueBehindTheirOwnScreen() throws Exception {
        UUID id = UUID.randomUUID();
        alice.share(share(id));
        bob.watch(Set.of(id));
        aliceSees.await(Recorder.Viewers.class, v -> v.n() == 1, 5);
        byte[] frame = new byte[200 * 1024];
        new java.util.Random(1).nextBytes(frame);
        relay.stalled = true;
        int queued = 0;
        for (int i = 0; i < 50; i++) if (alice.sendFrame(id, i, frame)) queued++;
        assertTrue(queued < 50, "a burst is thinned, not queued");
        assertTrue(alice.drops() > 0);
        assertFalse(alice.sendFrame(id, 0, new byte[Wire.MAX_FRAME]), "over 256 KiB is refused");
        relay.stalled = false;
    }

    @Test
    void listParsing() {
        ShareInfo s = share(UUID.randomUUID());
        ShareInfo back = ShareInfo.fromJson(s.toJson());
        assertNotNull(back);
        assertEquals(s.key(), back.key());
        assertEquals(List.of(), List.of());
    }
}
