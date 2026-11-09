package com.mineux.visuals.hud;

import com.mineux.visuals.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The main menu's scenes ({@link MenuScene}), each a full-screen picture made of the GUI's
 * rectangles and moving with time: an aurora over a ridge, a nebula of drifting glow, rain,
 * rising bokeh, silk threads, a horizon grid under a striped sun and a picture. Every scene leans on the
 * accent colour and moves its layers a little with the mouse ({@link Frame#px}, {@link Frame#py}).
 */
final class MenuScenes {

    private static final int TEAL = 0xFF3DE0C0;
    private static final int VIOLET = 0xFF9A6BFF;
    private static final int MAGENTA = 0xFFFF3D8A;
    private static final int SKY_BLUE = 0xFF6EA8FF;
    private static final Path BACKGROUND_FILE =
            FabricLoader.getInstance().getConfigDir()
                    .resolve("mineux")
                    .resolve("background")
                    .resolve("background.png");

    private static final Identifier BACKGROUND_ID =
            Identifier.fromNamespaceAndPath("mineux", "background");

    private static DynamicTexture backgroundTexture;
    private static int backgroundWidth;
    private static int backgroundHeight;



    private MenuScenes() {}

    /** One frame's canvas: where to draw, how large, the time in seconds, the accent, and the mouse's pull (-1 to 1). */
    static final class Frame {
        final GuiGraphicsExtractor g;
        final int w;
        final int h;
        final float t;
        final int accent;
        final float px;
        final float py;

        Frame(GuiGraphicsExtractor g, int w, int h, float t, int accent, float px, float py) {
            this.g = g;
            this.w = w;
            this.h = h;
            this.t = t;
            this.accent = accent;
            this.px = px;
            this.py = py;
        }
    }

    // ---- the scenes ---------------------------------------------------------------------------

    static void aurora(Frame f) {
        GuiGraphicsExtractor g = f.g;
        g.fillGradient(0, 0, f.w, f.h, 0xFF04060D, 0xFF0A1020);
        stars(f, 90, 0.7F);
        int[] inks = {f.accent, Theme.mix(f.accent, TEAL, 0.6F), Theme.mix(f.accent, VIOLET, 0.6F)};
        for (int i = 0; i < 3; i++) {
            int ink = inks[i];
            for (int x = 0; x < f.w; x += 3) {
                float phase = x * 0.006F + f.t * 0.25F + i * 1.7F;
                float cy = f.h * (0.26F + 0.07F * i) + (float) Math.sin(phase) * f.h * 0.07F
                    + (float) Math.sin(phase * 2.3F + f.t * 0.4F) * f.h * 0.03F + f.py * 8.0F * (i + 1);
                float tall = f.h * (0.12F + 0.05F * (float) Math.sin(phase * 1.7F + f.t * 0.5F));
                float a = 0.16F + 0.09F * (float) Math.sin(phase * 3.0F + f.t);
                int sx = x + Math.round(f.px * 6.0F * (i + 1));
                g.fillGradient(sx, Math.round(cy - tall), sx + 3, Math.round(cy), Theme.alpha(ink, 0.0F), Theme.alpha(ink, a));
                g.fillGradient(sx, Math.round(cy), sx + 3, Math.round(cy + tall * 0.35F), Theme.alpha(ink, a), Theme.alpha(ink, 0.0F));
            }
        }
        // The ridge the sky sits over.
        for (int x = 0; x < f.w; x += 4) {
            float sx = x + f.px * 14.0F;
            int y = Math.round(f.h * 0.8F + (float) Math.sin(sx * 0.013F) * f.h * 0.04F + (float) Math.sin(sx * 0.031F + 1.0F) * f.h * 0.02F);
            g.fill(x, y, x + 4, f.h, 0xFF03040A);
        }
    }

    static void nebula(Frame f) {
        GuiGraphicsExtractor g = f.g;
        g.fillGradient(0, 0, f.w, f.h, 0xFF07050F, 0xFF0D0716);
        int[] tints = {VIOLET, MAGENTA, SKY_BLUE};
        for (int i = 0; i < 6; i++) {
            float cx = f.w * (0.15F + 0.7F * rand(i, 3)) + (float) Math.sin(f.t * 0.05F + i) * f.w * 0.08F + f.px * (10.0F + i * 6.0F);
            float cy = f.h * (0.15F + 0.7F * rand(i, 5)) + (float) Math.cos(f.t * 0.04F + i * 2.0F) * f.h * 0.06F + f.py * (8.0F + i * 4.0F);
            int r = Math.round(f.h * (0.22F + 0.2F * rand(i, 4)));
            int ink = Theme.mix(f.accent, tints[i % 3], 0.35F + 0.5F * rand(i, 6));
            glow(g, Math.round(cx), Math.round(cy), r, Theme.alpha(ink, 0.035F), 6);
        }
        stars(f, 140, 1.0F);
    }

    static void rain(Frame f) {
        GuiGraphicsExtractor g = f.g;
        g.fillGradient(0, 0, f.w, f.h, 0xFF060A12, 0xFF0B1420);
        glow(g, f.w / 2 + Math.round(f.px * 20.0F), f.h, f.h / 2, Theme.alpha(f.accent, 0.03F), 5);
        for (int i = 0; i < 160; i++) {
            float speed = 0.6F + 0.6F * rand(i, 2);
            float fall = (rand(i, 3) + f.t * speed) % 1.2F - 0.1F;
            int x = Math.round(rand(i, 1) * f.w - f.px * 6.0F * speed);
            int y = Math.round(fall * f.h);
            int length = 6 + Math.round(10.0F * rand(i, 4));
            g.fill(x, y, x + 1, y + length, Theme.alpha(0xFFB0C8FF, 0.16F + 0.25F * rand(i, 5)));
        }
    }

    static void bokeh(Frame f) {
        GuiGraphicsExtractor g = f.g;
        g.fillGradient(0, 0, f.w, f.h, 0xFF0A0810, 0xFF140D08);
        for (int i = 0; i < 34; i++) {
            float size = rand(i, 1);
            int r = 6 + Math.round(34.0F * size * size);
            float rise = (rand(i, 2) + f.t * 0.015F * (0.5F + rand(i, 3))) % 1.2F;
            float cx = rand(i, 4) * f.w + (float) Math.sin(f.t * 0.2F + i) * 20.0F + f.px * r * 0.6F;
            float cy = (1.1F - rise) * f.h + f.py * r * 0.4F;
            int ink = Theme.mix(f.accent, i % 3 == 0 ? 0xFFFFFFFF : 0xFFFF9A3D, 0.3F * rand(i, 5));
            glow(g, Math.round(cx), Math.round(cy), r, Theme.alpha(ink, 0.05F), 3);
        }
    }

    static void silk(Frame f) {
        GuiGraphicsExtractor g = f.g;
        g.fillGradient(0, 0, f.w, f.h, 0xFF050608, 0xFF0B0D12);
        int lines = 20;
        for (int j = 0; j < lines; j++) {
            int ink = Theme.mix(f.accent, SKY_BLUE, j / (float) (lines - 1));
            float a = 0.10F + 0.06F * (float) Math.sin(j + f.t);
            int colour = Theme.alpha(ink, a);
            for (int x = 0; x < f.w; x += 5) {
                float y = f.h * 0.5F + (float) Math.sin(x * 0.004F + f.t * 0.3F + j * 0.12F) * f.h * 0.18F
                    + (float) Math.sin(x * 0.011F - f.t * 0.5F + j * 0.3F) * f.h * 0.06F + (j - (float) lines / 2) * 3.0F + f.py * 10.0F;
                g.fill(x, Math.round(y), x + 5, Math.round(y) + 1, colour);
            }
        }
    }

    static void grid(Frame f) {
        GuiGraphicsExtractor g = f.g;
        int horizon = Math.round(f.h * 0.58F + f.py * 6.0F);
        int haze = Theme.mix(f.accent, MAGENTA, 0.5F);
        g.fillGradient(0, 0, f.w, horizon, 0xFF0A0614, Theme.alpha(Theme.darken(haze, 0.55F), 1.0F));
        stars(f, 60, 0.45F);
        // The sun, striped across its lower half.
        int sunX = f.w / 2 + Math.round(f.px * 10.0F);
        int sunR = Math.round(f.h * 0.16F);
        int sunY = horizon - Math.round(f.h * 0.08F);
        for (int dy = 0 - sunR; dy < sunR; dy++) {
            if (dy > 0 && (dy / 3) % 2 == 1) continue;
            int half = (int) Math.sqrt((double) sunR * sunR - (double) dy * dy);
            int y = sunY + dy;
            if (y >= horizon) break;
            g.fill(sunX - half, y, sunX + half, y + 1, Theme.mix(0xFFFFD36E, MAGENTA, (dy + sunR) / (2.0F * sunR)));
        }
        g.fill(0, horizon, f.w, f.h, 0xFF07040C);
        int line = Theme.alpha(haze, 0.55F);
        float frac = f.t * 0.8F % 1.0F;
        for (int k = 0; k < 18; k++) {
            float z = (k + frac) / 18.0F;
            int y = horizon + Math.round((f.h - horizon) * z * z);
            g.fill(0, y, f.w, y + 1, Theme.alpha(haze, 0.15F + 0.5F * z));
        }
        float vx = f.w / 2.0F + f.px * 20.0F;
        for (int i = 0 - 12; i <= 12; i++) {
            line(g, vx, horizon, f.w / 2.0F + i * f.w / 8.0F, f.h, line);
        }
        g.fillGradient(0, horizon - 2, f.w, horizon + 6, Theme.alpha(haze, 0.5F), Theme.alpha(haze, 0.0F));
    }

    static void picture(Frame f) {
        if (backgroundTexture == null) {
            loadImageBackground();
        }

        imageBackground(f);
    }

    // ---- drawing helpers ------------------------------------------------------------------------

    /** Points of light, twinkling, over the top {@code reach} of the screen. */
    private static void stars(Frame f, int count, float reach) {
        for (int i = 0; i < count; i++) {
            int x = Math.round(rand(i, 11) * f.w - f.px * 4.0F);
            int y = Math.round(rand(i, 12) * f.h * reach - f.py * 3.0F);
            float twinkle = 0.5F + 0.5F * (float) Math.sin(f.t * (1.0F + rand(i, 13) * 2.0F) + i);
            f.g.fill(x, y, x + 1, y + 1, Theme.alpha(0xFFFFFFFF, 0.15F + 0.55F * twinkle * rand(i, 14)));
        }
    }

    /** A soft round glow: {@code layers} discs, each smaller one over the last, in a faint colour. */
    static void glow(GuiGraphicsExtractor g, int cx, int cy, int r, int color, int layers) {
        for (int i = layers; i >= 1; i--) disc(g, cx, cy, r * i / layers, color);
    }

    /** A filled circle, out of horizontal strips. */
    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        int step = Math.max(1, r / 24);
        for (int dy = 0 - r; dy < r; dy += step) {
            double mid = dy + step * 0.5;
            int half = (int) Math.sqrt(Math.max(0.0, (double) r * r - mid * mid));
            if (half > 0) g.fill(cx - half, cy + dy, cx + half, cy + dy + step, color);
        }
    }

    /** A one-pixel line between two points, turned under the pose. */
    private static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.fill(0, 0, Math.round(length), 1, color);
        g.pose().popMatrix();
    }

    /** A number from 0 to 1 that is always the same for one {@code i} and {@code salt}. */
    private static float rand(int i, int salt) {
        int h = i * 0x27D4EB2D + salt * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    static void loadImageBackground() {
        Minecraft mc = Minecraft.getInstance();

        if (!mc.isSameThread()) {
            mc.execute(MenuScenes::loadImageBackground);
            return;
        }

        try {
            Files.createDirectories(BACKGROUND_FILE.getParent());

            if (!Files.isRegularFile(BACKGROUND_FILE)) {
                unloadImageBackground();
                return;
            }

            NativeImage image;
            try (InputStream input = Files.newInputStream(BACKGROUND_FILE)) {
                image = NativeImage.read(input);
            }

            int newWidth = image.getWidth();
            int newHeight = image.getHeight();

            DynamicTexture newTexture =
                    new DynamicTexture(() -> "mineux_background", image);

            // Replace the previously registered texture.
            unloadImageBackground();

            mc.getTextureManager().register(BACKGROUND_ID, newTexture);

            backgroundTexture = newTexture;
            backgroundWidth = newWidth;
            backgroundHeight = newHeight;

            System.out.println("[MineUX] Custom background loaded.");

        } catch (Exception e) {
            System.err.println("[MineUX] Failed to load custom background.");
            e.printStackTrace();
        }
    }

    static void unloadImageBackground() {
        Minecraft mc = Minecraft.getInstance();

        if (backgroundTexture == null) return;

        mc.getTextureManager().release(BACKGROUND_ID);

        backgroundTexture = null;
        backgroundWidth = 0;
        backgroundHeight = 0;
    }

    static void imageBackground(Frame f) {
        if (backgroundTexture == null) {
            f.g.fill(0, 0, f.w, f.h, 0xFF080B12);
            return;
        }

        float imageRatio = (float) backgroundWidth / backgroundHeight;
        float screenRatio = (float) f.w / f.h;

        int srcX = 0;
        int srcY = 0;
        int srcWidth = backgroundWidth;
        int srcHeight = backgroundHeight;

        // Crop the image to match the screen's aspect ratio.
        if (imageRatio > screenRatio) {
            srcWidth = (int) (backgroundHeight * screenRatio);
            srcX = (backgroundWidth - srcWidth) / 2;
        } else {
            srcHeight = (int) (backgroundWidth / screenRatio);
            srcY = (backgroundHeight - srcHeight) / 2;
        }

        f.g.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND_ID,
                0, 0,
                (float) srcX / backgroundWidth,
                (float) srcY / backgroundHeight,
                f.w, f.h,
                srcWidth, srcHeight,
                backgroundWidth,
                backgroundHeight
        );
    }
}
