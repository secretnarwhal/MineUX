package com.mineux.screens.bridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native library, headless: loaded from the jar's resource path (cache dir by hash), lists
 * the monitors, captures one for a moment and checks the frame is opaque RGBA of the monitor's
 * size. No window, no game; the pixels stay in memory and are thrown away.
 */
@EnabledOnOs(OS.WINDOWS)
class NativeCaptureTest {

    @Test
    void capturesAMonitorAsOpaqueRgba() throws Exception {
        ScreensWinBridge bridge = ScreensWinBridge.start(0L);
        CaptureTarget[] monitors = bridge.getMonitors();
        assertTrue(monitors.length > 0, "a monitor to capture");
        CaptureTarget monitor = monitors[0];
        long source = bridge.startCapture(monitor, 30);
        try {
            CapturedFrame frame = null;
            for (int i = 0; i < 100 && frame == null; i++) {
                Thread.sleep(50);
                frame = bridge.pollFrame(source);
            }
            assertNotNull(frame, "a frame within 5 s (" + bridge.getCaptureError(source) + ")");
            assertEquals(frame.width() * 4, frame.stride());
            assertTrue(frame.width() > 0 && frame.height() > 0);
            boolean rotated = frame.width() == monitor.height() && frame.height() == monitor.width();
            assertTrue(rotated || (frame.width() == monitor.width() && frame.height() == monitor.height()),
                "frame " + frame.width() + "x" + frame.height() + " for monitor " + monitor.width() + "x" + monitor.height());
            ByteBuffer pixels = MemoryUtil.memByteBuffer(frame.ptr(), (int) frame.bytes());
            for (int i = 0; i < 1000; i++) {
                int at = (int) ((long) i * (frame.width() * frame.height() / 1000)) * 4;
                assertEquals(255, pixels.get(at + 3) & 0xFF, "alpha at pixel " + at / 4);
            }
            assertEquals(null, bridge.getCaptureError(source));
        } finally {
            bridge.stopCapture(source);
        }
    }
}
