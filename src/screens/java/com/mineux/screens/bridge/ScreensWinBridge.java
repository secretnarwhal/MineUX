package com.mineux.screens.bridge;

import org.jspecify.annotations.Nullable;

/* Bridge to the Windows native library (native/, loaded from the jar by NativeLoader)
 *
 * Captures monitors and windows and can bring the captured windows to the foreground, nothing else.
 */
public class ScreensWinBridge {

    // Results of tabInto and focusMinecraft
    public static final int FOCUS_OK = 0;
    public static final int FOCUS_NO_WINDOW = 1;
    public static final int FOCUS_BLOCKED = 2;
    public static final int FOCUS_FAILED = 3;

    // RegisterHotKey modifier flags
    public static final int MOD_ALT = 0x1;
    public static final int MOD_CONTROL = 0x2;
    public static final int MOD_SHIFT = 0x4;

    private long instance;

    static {
        NativeLoader.load();
    }

    private ScreensWinBridge(long instance) {
        this.instance = instance;
    }

    public static ScreensWinBridge start(long minecraftWindow) {
        ScreensWinBridge bridge = new ScreensWinBridge(init(minecraftWindow));

        // Stop capture threads on normal exit
        Runtime.getRuntime().addShutdownHook(new Thread(bridge::shutdownHook));

        return bridge;
    }

    private void shutdownHook() {
        shutdown(instance);
        instance = 0;
    }

    public CaptureTarget[] getMonitors() {
        return monitors(instance);
    }

    // Windows in Z order, excluding Minecraft
    public CaptureTarget[] getWindows() {
        return windows(instance);
    }

    /* Start capturing a monitor or window at up to `rate` frames per second
     * Returns the capture source handle. Throws a RuntimeException with a readable message when capturing isn't possible.
     */
    public long startCapture(CaptureTarget target, int rate) {
        return startCapture(instance, target.kind(), target.handle(), rate);
    }

    public void stopCapture(long source) {
        stopCapture(instance, source);
    }

    public void setCaptureRate(long source, int rate) {
        setCaptureRate(instance, source, rate);
    }

    // Newest frame since the last poll, if any. The frame data is only valid until the next poll of this source.
    public @Nullable CapturedFrame pollFrame(long source) {
        return pollFrame(instance, source);
    }

    // Why the capture stopped (window closed, monitor unplugged, ...) or null while it is running
    public @Nullable String getCaptureError(long source) {
        return captureError(instance, source);
    }

    /* Bring the captured window to the foreground
     * For monitor captures, the window visible at pixel (x, y) of the captured frame is focused.
     * This never sends any input to the window. Returns one of the FOCUS_* results.
     */
    public int tabInto(long source, double x, double y) {
        return tabInto(instance, source, x, y);
    }

    public int focusMinecraft() {
        return focusMinecraft(instance);
    }

    /* Register a system-wide hotkey that brings Minecraft back to the foreground
     * `scancode` is a GLFW scancode, 0 removes the hotkey. Returns false if the combination couldn't be registered.
     */
    public boolean setReturnHotkey(int modifiers, int scancode) {
        return setReturnHotkey(instance, modifiers, scancode);
    }

    private static native long init(long minecraftWindow);
    private static native void shutdown(long instance);

    private static native CaptureTarget[] monitors(long instance);
    private static native CaptureTarget[] windows(long instance);

    private static native long startCapture(long instance, int kind, long handle, int rate);
    private static native void stopCapture(long instance, long source);
    private static native void setCaptureRate(long instance, long source, int rate);
    private static native CapturedFrame pollFrame(long instance, long source);
    private static native String captureError(long instance, long source);

    private static native int tabInto(long instance, long source, double x, double y);
    private static native int focusMinecraft(long instance);
    private static native boolean setReturnHotkey(long instance, int modifiers, int scancode);

}
