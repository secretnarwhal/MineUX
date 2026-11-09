package com.mineux.core.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/**
 * The desktop's own GUI scale: {@code desk scale} in {@code ui.json} ({@link UiConfig#deskScale}),
 * {@code auto} or 1-4, 2 until set. The desktop (bar, windows, terminals, settings windows, the
 * home and Esc desks, menus, the boot sequence, the greeter, the screens menu) looks the same
 * whatever the player's own GUI scale is, which stays theirs for the HUD and every other screen.
 * <p>
 * How: a {@link DeskScreen} lays itself out at {@link #width()} by {@link #height()} -- the
 * framebuffer in units of the desk scale -- draws through a pose scaled by {@link #factor()},
 * and maps every mouse position it is given back to the desk unit drawn under the cursor
 * ({@link #inX}, {@link #inY}; {@link ScaleMath} has the arithmetic). With the desk scale a whole number, a desk unit is a whole number of pixels, so
 * text is as crisp as at that GUI scale.
 * <p>
 * Read fresh every call, so a change to the setting (or the window) shows on the next frame.
 */
public final class DeskScale {

    private DeskScale() {}

    private static Window window() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.getWindow();
    }

    /** The desk scale in use: the setting's, capped for the window. */
    public static int target() {
        Window w = window();
        int setting = ScaleMath.parse(UiConfig.get().deskScale);
        if (w == null) return setting > 0 ? setting : ScaleMath.DEFAULT;
        return ScaleMath.pick(setting, w.getWidth(), w.getHeight());
    }

    /** The pose scale the desk is drawn through: a desk unit in GUI units. 1 when the two scales agree. */
    public static float factor() {
        Window w = window();
        if (w == null) return 1f;
        return ScaleMath.factor(target(), w.getGuiScale());
    }

    /** The desk's width, in desk units. */
    public static int width() {
        Window w = window();
        return w == null ? 0 : ScaleMath.size(w.getWidth(), target());
    }

    /** The desk's height, in desk units. */
    public static int height() {
        Window w = window();
        return w == null ? 0 : ScaleMath.size(w.getHeight(), target());
    }

    /** A GUI x (a mouse coordinate the game handed over) in desk units. */
    public static double inX(double gui) {
        Window w = window();
        if (w == null) return gui;
        return ScaleMath.toDesk(gui, w.getWidth(), w.getGuiScaledWidth(), target());
    }

    /** A GUI y in desk units. */
    public static double inY(double gui) {
        Window w = window();
        if (w == null) return gui;
        return ScaleMath.toDesk(gui, w.getHeight(), w.getGuiScaledHeight(), target());
    }

    /** A mouse event the game handed over, at its desk position. */
    public static MouseButtonEvent in(MouseButtonEvent event) {
        return new MouseButtonEvent(inX(event.x()), inY(event.y()), event.buttonInfo());
    }

    /**
     * The cursor in desk units, between whole units: for a smooth drag or parallax. The
     * position a frame was given ({@code given}, GUI units) when the two disagree, as for a
     * frame drawn with a made-up cursor.
     */
    public static double cursorX(int given) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null || mc.getWindow() == null) return inX(given);
        double exact = mc.mouseHandler.getScaledXPos(mc.getWindow());
        return inX(Math.abs(exact - given) < 1.5 ? exact : given);
    }

    /** As {@link #cursorX}, down. */
    public static double cursorY(int given) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null || mc.getWindow() == null) return inY(given);
        double exact = mc.mouseHandler.getScaledYPos(mc.getWindow());
        return inY(Math.abs(exact - given) < 1.5 ? exact : given);
    }

    /** The cursor in desk units now, read from the mouse. */
    public static double cursorX() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null || mc.getWindow() == null) return 0;
        return inX(mc.mouseHandler.getScaledXPos(mc.getWindow()));
    }

    /** As {@link #cursorX()}, down. */
    public static double cursorY() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null || mc.getWindow() == null) return 0;
        return inY(mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }

    /**
     * A point of the pose's space (the desk's, with whatever the drawing has translated) in GUI
     * units, rounded: for what the game draws without the pose, the 3D model previews
     * ({@code GuiGraphicsExtractor#skin}, {@code #entity}), which take GUI corners.
     */
    public static int[] onScreen(GuiGraphicsExtractor g, float x, float y) {
        Vector2f v = g.pose().transformPosition(x, y, new Vector2f());
        return new int[] {Math.round(v.x), Math.round(v.y)};
    }

    /** How much the pose scales a length now: the size a model preview's scale is multiplied by. */
    public static float poseScale(GuiGraphicsExtractor g) {
        Matrix3x2f m = g.pose();
        return (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
    }
}
