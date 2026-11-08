package com.mineux.visuals.trail;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * See {@link FakeCursorConfig} for why this hides the real GLFW cursor and draws a sprite
 * rather than swapping the OS cursor image.
 * <p>
 * A static facade, the same as {@link TrailRenderer}: {@code CursorTrailMixin} calls
 * {@link #render} directly out of every Screen's own {@code extractRenderState}, and
 * {@link #onScreenClosed} out of {@code removed}.
 */
public final class FakeCursorRenderer {
    public static final int PROCEDURAL_SIZE = 32;

    /** The arrow's triangle vertices in {@link #PROCEDURAL_SIZE} space, shared with ColorPicker's swatch preview. */
    public static final float[] ARROW_VERTICES_X = {2f, 2f, PROCEDURAL_SIZE - 12f};
    public static final float[] ARROW_VERTICES_Y = {2f, PROCEDURAL_SIZE - 6f, PROCEDURAL_SIZE - 12f};

    private static final Identifier TEXTURE_ID =
        Identifier.fromNamespaceAndPath("mineux", "dynamic/fake_cursor_sprite");

    private static final SpriteSlot sprite =
        new SpriteSlot(TEXTURE_ID, "mineux fake cursor", PROCEDURAL_SIZE);

    private static boolean cursorCurrentlyHidden = false;

    private FakeCursorRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        FakeCursorConfig cfg = FakeCursorConfig.get();

        setRealCursorHidden(cfg.enabled);
        if (!cfg.enabled) return;

        sprite.ensureLoaded(cfg.spritePath, FakeCursorRenderer::buildProceduralArrow);

        int size = Math.max(1, cfg.sizePx);
        float opacity = Mth.clamp(cfg.opacity, 0.0f, 1.0f);
        int argb = ColorPack.packArgb(opacity, Mth.clamp(cfg.colorR, 0.0f, 1.0f), Mth.clamp(cfg.colorG, 0.0f, 1.0f), Mth.clamp(cfg.colorB, 0.0f, 1.0f));

        // Anchored at the sprite's top-left, matching where a real cursor's
        // hotspot usually sits (near the tip of the arrow).
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, mouseX, mouseY, 0.0f, 0.0f, size, size,
                sprite.width(), sprite.height(), sprite.width(), sprite.height(), argb);
    }

    /**
     * Called when a screen closes, in case it closes without another one
     * immediately opening (so {@link #render} won't fire again to naturally
     * re-assert cursor state). Harmless no-op if nothing was hidden, and if
     * gameplay grabs the mouse right after, MouseHandler's own grab logic
     * takes over the cursor mode anyway.
     */
    public static void onScreenClosed() {
        setRealCursorHidden(false);
    }

    public static void setRealCursorHidden(boolean hidden) {
        if (hidden == cursorCurrentlyHidden) return;
        Window window = Minecraft.getInstance().getWindow();
        GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR,
                hidden ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
        cursorCurrentlyHidden = hidden;
    }

    /** A plain filled-triangle arrow silhouette, tip at the top-left corner. */
    private static NativeImage buildProceduralArrow() {
        int size = PROCEDURAL_SIZE;
        NativeImage image = new NativeImage(size, size, false);

        float ax = ARROW_VERTICES_X[0], ay = ARROW_VERTICES_Y[0];
        float bx = ARROW_VERTICES_X[1], by = ARROW_VERTICES_Y[1];
        float cx = ARROW_VERTICES_X[2], cy = ARROW_VERTICES_Y[2];

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean inside = pointInTriangle(x + 0.5f, y + 0.5f, ax, ay, bx, by, cx, cy);
                int alpha = inside ? 255 : 0;
                image.setPixelABGR(x, y, (alpha << 24) | (255 << 16) | (255 << 8) | 255);
            }
        }
        return image;
    }

    private static boolean pointInTriangle(float px, float py, float ax, float ay, float bx, float by, float cx, float cy) {
        float d1 = cross(px, py, ax, ay, bx, by);
        float d2 = cross(px, py, bx, by, cx, cy);
        float d3 = cross(px, py, cx, cy, ax, ay);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    private static float cross(float px, float py, float ax, float ay, float bx, float by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }
}
