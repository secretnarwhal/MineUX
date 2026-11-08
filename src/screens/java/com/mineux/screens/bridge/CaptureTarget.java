package com.mineux.screens.bridge;

/**
 * A monitor or top-level window that can be captured on Windows. Made by the native library
 * (native/src/win_bridge.rs builds it through this exact constructor; keep the two in step).
 * {@code handle} is the HMONITOR or HWND, {@code detail} the device or executable name.
 * {@code width}/{@code height} are physical pixels, {@code scale} the DPI scale of its monitor.
 * {@code blocked} is set for windows of elevated processes, which Windows will not let the game focus.
 */
public record CaptureTarget(int kind, long handle, String title, String detail, int width, int height, float scale, boolean primary, boolean blocked) {

    public static final int KIND_MONITOR = 0;
    public static final int KIND_WINDOW = 1;

    public boolean isMonitor() {
        return kind == KIND_MONITOR;
    }
}
