package com.mineux.core.gpu;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Which of the game's two graphics backends is drawing: OpenGL or Vulkan. Read once from the live
 * device ({@code RenderSystem}'s, its {@code DeviceInfo#backendName}), never from options.txt,
 * which says only what the player asked for at the last start. The game cannot change backend
 * without a restart, so the first answer read from a device is kept; before there is a device the
 * answer is "not Vulkan" and nothing is kept.
 * <p>
 * Everything this client draws goes through the game's own render API and runs on both. What
 * still cannot is marked {@code vulkan=no} in {@code modules.txt} and hidden from the settings
 * tree when this says Vulkan ({@code ui.term.Modules}).
 */
public final class Backend {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/gpu");

    public static final String OPENGL = "OpenGL";
    public static final String VULKAN = "Vulkan";

    /** The backend's name as the device gave it; null until a device has been read. */
    private static String name;
    private static boolean logged;

    private Backend() {}

    /** Whether the game is drawing with Vulkan. False before the device exists. */
    public static boolean vulkan() {
        return isVulkan(name());
    }

    /** The backend's name ({@link #OPENGL}, {@link #VULKAN}); "unknown" before the device exists. */
    public static String name() {
        String known = name;
        if (known != null) return known;
        String read = read();
        if (read == null) return "unknown";
        name = read;
        return read;
    }

    /** Whether a backend name is the Vulkan one: the device's own name, any case. */
    public static boolean isVulkan(String backendName) {
        return backendName != null && backendName.toLowerCase(Locale.ROOT).contains("vulkan");
    }

    /** Logs the backend once, and what is hidden on it; the settings tree calls this once built. */
    public static void logOnce(int hidden) {
        if (logged || name == null && read() == null) return;
        logged = true;
        if (vulkan() && hidden > 0) {
            LOGGER.info("Graphics backend: {}. {} client settings need OpenGL and are hidden; switch the graphics backend to OpenGL in video settings to use them.", name(), hidden);
        } else {
            LOGGER.info("Graphics backend: {}. Every client visual runs on it.", name());
        }
    }

    private static String read() {
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device == null) return null;
            return device.getDeviceInfo().backendName();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
