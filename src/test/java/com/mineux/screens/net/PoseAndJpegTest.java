package com.mineux.screens.net;

import com.mineux.screens.displays.ScreenPlacement;
import com.mineux.screens.math.ViewFrame;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wire's turn of a screen, its placement relative to a player, the room hash, and the JPEG path. */
class PoseAndJpegTest {

    private static void near(Vec3 a, Vec3 b, String what) {
        assertTrue(a.distanceTo(b) < 1.0e-4, what + ": " + a + " vs " + b);
    }

    private static void roundTrip(Vec3 facing, Vec3 up) {
        float[] rot = Pose.encode(facing, up);
        near(Pose.facing(rot[0], rot[1]), facing, "facing");
        near(Pose.up(rot[0], rot[1]), up, "up");
    }

    @Test
    void wallsFloorsCeilingsAndTilts() {
        roundTrip(new Vec3(0, 0, 1), new Vec3(0, 1, 0));
        roundTrip(new Vec3(-1, 0, 0), new Vec3(0, 1, 0));
        roundTrip(new Vec3(0.6, 0, -0.8), new Vec3(0, 1, 0));
        // Floor: facing up, its top edge pointing anywhere flat.
        roundTrip(new Vec3(0, 1, 0), new Vec3(0, 0, 1));
        roundTrip(new Vec3(0, 1, 0), new Vec3(0.8, 0, 0.6));
        // Ceiling: facing down.
        roundTrip(new Vec3(0, -1, 0), new Vec3(-0.6, 0, 0.8));
        // Held up in front of a player looking down 30 degrees.
        Vec3 look = Pose.direction(30, 125);
        roundTrip(look.reverse(), Pose.direction(30 - 90, 125));
    }

    @Test
    void playerAnchoredFollowsTheOwnersView() {
        ViewFrame owner = new ViewFrame(new Vec3(100, 65.62, -40), Pose.direction(10, 45), Pose.direction(10 - 90, 45));
        ScreenPlacement placed = new ScreenPlacement();
        placed.faceViewer(owner.pos(), owner.forward(), owner.up(), 2.5);
        ShareInfo info = new ShareInfo();
        info.anchor = ShareInfo.ANCHOR_PLAYER;
        Pose.fill(info, placed, owner);
        assertEquals(2.5, info.z, 1e-6);
        // The owner turns round and walks off: the screen stays in front of their face.
        ViewFrame moved = new ViewFrame(new Vec3(90, 70, -10), Pose.direction(-20, 200), Pose.direction(-20 - 90, 200));
        ScreenPlacement seen = new ScreenPlacement();
        Pose.place(info, moved, seen);
        near(seen.center(), moved.pos().add(moved.forward().scale(2.5)), "centre");
        near(seen.facing(), moved.forward().reverse(), "facing");
    }

    @Test
    void roomIsHexSha256OfTheLowerCasedAddress() {
        assertEquals(Wire.room("play.example.org"), Wire.room("Play.Example.ORG"));
        assertEquals(64, Wire.room("x").length());
        // sha256("localhost")
        assertEquals("49960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d9763", Wire.room("localhost"));
    }

    @Test
    void jpegScalesToTheCapAndKeepsColours() throws Exception {
        int w = 1920, h = 1080;
        ByteBuffer rgba = ByteBuffer.allocateDirect(w * h * 4);
        for (int i = 0; i < w * h; i++) {
            boolean left = i % w < w / 2;
            rgba.put(i * 4, (byte) (left ? 230 : 20));
            rgba.put(i * 4 + 1, (byte) 30);
            rgba.put(i * 4 + 2, (byte) (left ? 20 : 220));
            rgba.put(i * 4 + 3, (byte) 255);
        }
        byte[] jpeg = new Jpeg.Encoder().encode(rgba, w, h, 1280, 0.6f);
        assertTrue(jpeg.length + Wire.HEADER < Wire.MAX_FRAME, "a 1280 px frame fits the relay's 256 KiB: " + jpeg.length);
        BufferedImage image = Jpeg.decode(jpeg);
        assertNotNull(image);
        assertEquals(1280, image.getWidth());
        assertEquals(720, image.getHeight());
        ByteBuffer back = ByteBuffer.allocate(1280 * 720 * 4);
        Jpeg.toRgba(image, back);
        int at = (360 * 1280 + 200) * 4;
        assertTrue((back.get(at) & 0xFF) > 200 && (back.get(at + 2) & 0xFF) < 60, "left half stays red");
        at = (360 * 1280 + 1100) * 4;
        assertTrue((back.get(at) & 0xFF) < 60 && (back.get(at + 2) & 0xFF) > 190, "right half stays blue");
        assertEquals(255, back.get(at + 3) & 0xFF);
    }
}
