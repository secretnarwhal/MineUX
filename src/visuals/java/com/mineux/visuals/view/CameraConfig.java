package com.mineux.visuals.view;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Config file: config/mineux/camera.json
 * <p>
 * How your own camera moves and what its picture looks like: freelook, camera tweaks, auto f5,
 * the smooth camera, motion blur, no fov, no bob, no hurt cam and the stretched picture. Its own
 * file rather than more of {@code view.json} because none of it is something on the world -- it
 * is all the one camera, and all of it off by default. Nothing here is ever sent anywhere: the
 * body's look, which is what the server is shown, is never touched by any of it.
 * <p>
 * Every choice is a string, never an enum: the obfuscation a host may apply renames enum constants.
 *
 * @see Freelook
 * @see CameraShape
 * @see SmoothCamera
 * @see MotionBlur
 */
public final class CameraConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/camera");

    private static final ConfigFile<CameraConfig> FILE =
        new ConfigFile<>(CameraConfig.class, "camera.json", CameraConfig::new, LOGGER);

    private static CameraConfig instance;

    public static final String VIEW_BACK = "back";
    public static final String VIEW_FRONT = "front";
    public static final String VIEW_FIRST = "first";

    public static final String QUALITY_LOW = "low";
    public static final String QUALITY_MEDIUM = "medium";
    public static final String QUALITY_HIGH = "high";

    /** Look around without turning: the camera turns, the body and the look the server is shown stay put. */
    public boolean freelook = false;

    /** GLFW's key code, or a mouse button as {@code Keys#MOUSE_BASE - button}; Z by default (left alt is the window manager SUPER key). */
    public int freelookKey = org.lwjgl.glfw.GLFW.GLFW_KEY_Z;

    /** Held rather than pressed on and off. */
    public boolean freelookHold = true;

    /** Where the camera goes while looking around: back, front or first (person). */
    public String freelookView = VIEW_BACK;

    /** Third-person camera distance, and the camera through walls. */
    public boolean cameraTweaks = false;

    /** In blocks at a normal size; vanilla's is 4. Scaled by your size and the camera distance attribute as vanilla's is. */
    public double cameraDistance = 4.0;

    /** Third person while a container is open, back to what it was when it closes. */
    public boolean autoF5 = false;

    /** The camera follows your look a little behind it, eased. */
    public boolean smoothCamera = false;

    /** How fast it catches up, per second; higher is tighter. */
    public double smoothSpeed = 5.0;

    /** Pitch eased as well as yaw. */
    public boolean smoothPitch = true;

    /** In third person the camera's position trails too. */
    public boolean smoothLag = true;

    /** How fast the trailing position catches up. */
    public double smoothLagSpeed = 4.0;

    /** A blur along the way the camera turns. */
    public boolean motionBlur = false;

    /** The longest streak, in hundredths of the screen. */
    public double blurMax = 6.0;

    /** Samples a pixel: low, medium, high. */
    public String blurQuality = QUALITY_MEDIUM;

    /** How strong, 1 to 100: how much of the turn a frame's streak covers. */
    public double blurAmount = 40.0;

    /** No field of view change from sprinting, speed, flying or a drawn bow; the spyglass still zooms. */
    public boolean noFov = false;

    /** No walk bob on the world; the hand keeps its own. */
    public boolean noBob = false;

    /** No shake when you are hurt. */
    public boolean noHurtCam = false;

    /** The picture stretched wider, as a lower resolution stretched to the screen would be. */
    public boolean aspect = false;

    /** How much wider: 1.33 is 4:3 on a 16:9 screen. */
    public double aspectStretch = 1.33;

    public static synchronized CameraConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        if (!Objects.equals(VIEW_BACK, freelookView) && !Objects.equals(VIEW_FRONT, freelookView)
            && !Objects.equals(VIEW_FIRST, freelookView)) freelookView = VIEW_BACK;
        cameraDistance = Math.max(1.0, Math.min(30.0, cameraDistance));
        smoothSpeed = Math.max(0.5, Math.min(30.0, smoothSpeed));
        smoothLagSpeed = Math.max(0.5, Math.min(30.0, smoothLagSpeed));
        blurMax = Math.max(1.0, Math.min(20.0, blurMax));
        if (!Objects.equals(QUALITY_LOW, blurQuality) && !Objects.equals(QUALITY_MEDIUM, blurQuality)
            && !Objects.equals(QUALITY_HIGH, blurQuality)) blurQuality = QUALITY_MEDIUM;
        blurAmount = Math.max(1.0, Math.min(100.0, blurAmount));
        // Narrower than the screen would show past the frustum's edge, where chunks are culled.
        aspectStretch = Math.max(1.0, Math.min(2.0, aspectStretch));
    }

    public void save() {
        FILE.save(this);
    }
}
