package com.mineux.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mineux.core.MineUX;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Screens' settings, {@code config/mineux/screens.json}, under the {@code cfg=screens} name of
 * /mineux/screens.txt: per workstation (Extensions.addConfig), so the settings tree may write
 * fields straight in and call {@link #commitLive}. Primitives only (no enums: an obfuscator
 * a host may run renames their constants). Which screens exist, where they are and which are shared
 * is never saved: sharing starts off every time.
 */
public final class ScreensConfig {

    public static final int MIN_PIXELS_PER_BLOCK = 100;
    public static final int MAX_PIXELS_PER_BLOCK = 2000;
    public static final int MAX_CAPTURE_FPS = 120;
    public static final int MAX_WATCH_RANGE = 96;
    public static final int MAX_SHARE_FPS = 10;
    public static final int MAX_SHARE_WIDTH = 1280;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ScreensConfig instance;

    /** Screens drawn and the menu key working; off hides every screen and stops all sharing. */
    public boolean enabled = true;
    /** How many logical pixels of a captured screen fit on one block. */
    public int pixelsPerBlock = 500;
    /** Frames captured per second and screen at most. */
    public int captureFps = 30;
    /** Clicking a screen brings its window to the foreground. */
    public boolean clickable = false;
    /** Clicking a screen picks it up, as long as screens are not clickable. */
    public boolean draggable = true;
    /** How far screens bend around the viewer, 0 (flat) to 100 (half circle). */
    public int curvature = 0;
    /** Friends' shared screens are shown (watched from the relay; friends only, screens on both ways). */
    public boolean watchOthers = true;
    /** How near a friend's shared screen has to be to be watched, blocks. */
    public int watchRange = MAX_WATCH_RANGE;
    /** Frames a shared screen sends per second at most (the relay allows 10). */
    public int shareFps = MAX_SHARE_FPS;
    /** JPEG quality of shared frames, percent. */
    public int shareQuality = 60;
    /** Widest a shared frame is sent, pixels; wider captures are scaled down. */
    public int shareWidth = MAX_SHARE_WIDTH;
    /** Opens the screens menu (a GLFW key, a mouse button as Keys.MOUSE_BASE - b, or none). */
    public int menuKey = org.lwjgl.glfw.GLFW.GLFW_KEY_B;
    /** Flies the freecam to arrange screens. */
    public int freecamKey = org.lwjgl.glfw.GLFW.GLFW_KEY_N;
    /** With Ctrl+Alt, anywhere in Windows: back to the game after a click switched to a window. Keyboard only. */
    public int backKey = org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;

    private transient Path file;
    private transient List<Runnable> listeners = new ArrayList<>();

    public static synchronized ScreensConfig get() {
        if (instance == null) instance = load(com.mineux.core.config.ConfigFile.directory().resolve("screens.json"));
        return instance;
    }

    /** What the workstations and the settings tree call after writing fields: put right, save, tell. */
    public static void commitLive() {
        get().commit();
    }

    static ScreensConfig load(Path file) {
        ScreensConfig config = null;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                config = GSON.fromJson(reader, ScreensConfig.class);
            } catch (IOException | JsonParseException e) {
                Screens.LOGGER.warn("Could not read {}, using the default settings", file, e);
            }
        }
        if (config == null) config = new ScreensConfig();
        config.file = file;
        if (config.listeners == null) config.listeners = new ArrayList<>();
        config.clamp();
        return config;
    }

    private void clamp() {
        pixelsPerBlock = Math.clamp(pixelsPerBlock, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
        captureFps = Math.clamp(captureFps, 1, MAX_CAPTURE_FPS);
        curvature = Math.clamp(curvature, 0, 100);
        watchRange = Math.clamp(watchRange, 8, MAX_WATCH_RANGE);
        shareFps = Math.clamp(shareFps, 1, MAX_SHARE_FPS);
        shareQuality = Math.clamp(shareQuality, 20, 90);
        shareWidth = Math.clamp(shareWidth, 320, MAX_SHARE_WIDTH);
        menuKey = com.mineux.core.input.Keys.sanitize(menuKey);
        freecamKey = com.mineux.core.input.Keys.sanitize(freecamKey);
        backKey = com.mineux.core.input.Keys.isMouse(backKey) ? com.mineux.core.input.Keys.UNBOUND : com.mineux.core.input.Keys.sanitize(backKey);
    }

    /** Puts every value in range, writes the file and tells the listeners. */
    public void commit() {
        clamp();
        save();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Screens.LOGGER.warn("Could not write {}", file, e);
        }
    }

    /** Run {@code listener} after every commit. */
    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    public double arcAngle() {
        return curvature / 100.0 * Math.PI;
    }
}
