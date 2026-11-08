package com.mineux.visuals.trail;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Optional;

/**
 * Renders a fading, Bezier-smoothed cursor trail. Mouse movement during
 * normal first-person play steers the camera rather than moving an
 * on-screen pointer, so there's nothing meaningful to trail during
 * gameplay -- the only time a real screen-space cursor exists is while a
 * Screen (inventory, pause menu, chat, ...) is open, so this is driven from
 * {@code Screen.extractRenderState} via {@code CursorTrailMixin} instead of
 * a per-frame gameplay HUD layer. That also fixes a real bug an earlier
 * version had: the vanilla Hud layer stack draws *underneath* an open
 * Screen's own widgets, so a trail attached there was invisible behind the
 * inventory panel. extractRenderState fires after the Screen has queued all
 * of its own widgets, so drawing here lands on top of the menu, and it
 * hands back mouseX/mouseY directly, no manual MouseHandler polling.
 * <p>
 * Each stamp is drawn with a single GuiGraphicsExtractor.blit(...) call,
 * and sprites are loaded through NativeImage + DynamicTexture +
 * TextureManager.
 * <p>
 * A static facade rather than an instance registered anywhere: the visuals have no
 * Fabric API dependency to register a per-screen listener against, so
 * {@code CursorTrailMixin} calls {@link #render} directly out of every
 * Screen's own {@code extractRenderState}.
 */
public final class TrailRenderer {
    private static final int MAX_STAMPS = 512;
    private static final int MAX_STAMPS_PER_FRAME = MAX_STAMPS / 4;
    private static final int PROCEDURAL_SPRITE_SIZE = 64;
    private static final long MAX_SAMPLE_GAP_MS = 100;
    private static final float REFERENCE_PX_PER_MS = 2.0f;

    private static final Identifier TEXTURE_ID =
        Identifier.fromNamespaceAndPath("mineux", "dynamic/cursor_trail_sprite");

    private static final float[] stampX = new float[MAX_STAMPS];
    private static final float[] stampY = new float[MAX_STAMPS];
    private static final long[] stampBirthMs = new long[MAX_STAMPS];
    private static final float[] stampSizeBoost = new float[MAX_STAMPS];
    private static int head = 0;
    private static int count = 0;

    private static final double[] sampleX = new double[3];
    private static final double[] sampleY = new double[3];
    private static int sampleCount = 0;
    private static long lastCallTimeMs = 0;

    private static final SpriteSlot sprite =
        new SpriteSlot(TEXTURE_ID, "mineux cursor trail sprite", PROCEDURAL_SPRITE_SIZE);

    private static RenderPipeline additivePipeline; // built lazily on first use

    private TrailRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        TrailConfig cfg = TrailConfig.get();

        if (!cfg.enabled) {
            resetTrailBuffer();
            return;
        }

        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        if (guiWidth <= 0 || guiHeight <= 0) return;

        long now = System.nanoTime() / 1_000_000L;
        advanceSamples(cfg, mouseX, mouseY, now, lastCallTimeMs, guiWidth, guiHeight);

        if (count == 0) return;

        sprite.ensureLoaded(cfg.spritePath, TrailRenderer::buildProceduralDot);

        RenderPipeline pipeline = cfg.additive()
                ? getOrBuildAdditivePipeline()
                : RenderPipelines.GUI_TEXTURED;

        float baseSize = Math.max(1, cfg.spriteSizePx);
        float tailScale = Mth.clamp(cfg.tailSizeScale, 0.0f, 2.0f);
        float opacity = Mth.clamp(cfg.opacity, 0.0f, 1.0f);
        float colorR = Mth.clamp(cfg.colorR, 0.0f, 1.0f);
        float colorG = Mth.clamp(cfg.colorG, 0.0f, 1.0f);
        float colorB = Mth.clamp(cfg.colorB, 0.0f, 1.0f);
        float tailColorR = Mth.clamp(cfg.tailColorR, 0.0f, 1.0f);
        float tailColorG = Mth.clamp(cfg.tailColorG, 0.0f, 1.0f);
        float tailColorB = Mth.clamp(cfg.tailColorB, 0.0f, 1.0f);
        int lifetimeMs = Math.max(1, cfg.lifetimeMs);

        int liveStamps = 0;
        for (int i = 0; i < count; i++) {
            int ringIdx = Math.floorMod(head - 1 - i, MAX_STAMPS);
            long ageMs = now >= stampBirthMs[ringIdx] ? now - stampBirthMs[ringIdx] : 0;
            if (ageMs >= lifetimeMs) continue;
            liveStamps++;

            float ageFraction = (float) ageMs / (float) lifetimeMs;
            float invAge = 1.0f - ageFraction;
            float alpha = (invAge * invAge) * opacity;
            if (alpha <= 0.001f) continue;

            float sizeScale = (1.0f - ageFraction) + tailScale * ageFraction;
            float diameter = baseSize * sizeScale * stampSizeBoost[ringIdx];
            int size = Math.round(diameter);
            if (size < 1) continue;

            float r = colorR, g = colorG, b = colorB;
            if (cfg.useGradient) {
                r = colorR * (1.0f - ageFraction) + tailColorR * ageFraction;
                g = colorG * (1.0f - ageFraction) + tailColorG * ageFraction;
                b = colorB * (1.0f - ageFraction) + tailColorB * ageFraction;
            }

            int argb = ColorPack.packArgb(alpha, r, g, b);
            int x = Math.round(stampX[ringIdx]) - size / 2;
            int y = Math.round(stampY[ringIdx]) - size / 2;

            graphics.blit(pipeline, TEXTURE_ID, x, y, 0.0f, 0.0f, size, size,
                    sprite.width(), sprite.height(), sprite.width(), sprite.height(), argb);
        }

        if (liveStamps == 0) {
            count = 0;
            head = 0;
        }
    }

    /** {@code lastCall}: {@link #lastCallTimeMs}, a parameter rather than a local copy of the field (HoistCheck). */
    private static void advanceSamples(TrailConfig cfg, double curX, double curY, long now, long lastCall,
                                        int guiWidth, int guiHeight) {
        lastCallTimeMs = now;
        if (lastCall > 0 && now > lastCall && (now - lastCall) > MAX_SAMPLE_GAP_MS) {
            sampleCount = 0;
        }

        sampleX[0] = sampleX[1];
        sampleY[0] = sampleY[1];
        sampleX[1] = sampleX[2];
        sampleY[1] = sampleY[2];
        sampleX[2] = curX;
        sampleY[2] = curY;
        if (sampleCount < 3) sampleCount++;

        if (sampleCount >= 2) {
            double dxT = sampleX[2] - sampleX[1];
            double dyT = sampleY[2] - sampleY[1];
            double distT = Math.sqrt(dxT * dxT + dyT * dyT);
            double screenDiagonal = Math.sqrt((double) guiWidth * guiWidth + (double) guiHeight * guiHeight);
            if (distT > screenDiagonal) {
                sampleCount = 0;
            }
        }

        if (sampleCount < 2) return;

        double s1x = sampleX[1], s1y = sampleY[1];
        double s2x = sampleX[2], s2y = sampleY[2];

        double ctlX, ctlY;
        if (sampleCount >= 3) {
            double s0x = sampleX[0], s0y = sampleY[0];
            ctlX = s1x + (s2x - s0x) * 0.25;
            ctlY = s1y + (s2y - s0y) * 0.25;
        } else {
            ctlX = 0.5 * (s1x + s2x);
            ctlY = 0.5 * (s1y + s2y);
        }

        float sizeBoost = 1.0f;
        if (cfg.useVelocitySize && lastCall > 0 && now > lastCall) {
            double dxV = s2x - s1x, dyV = s2y - s1y;
            double distV = Math.sqrt(dxV * dxV + dyV * dyV);
            float dtMs = (float) (now - lastCall);
            float velocityPxPerMs = (float) (distV / dtMs);
            float velocityFraction = Mth.clamp(velocityPxPerMs / REFERENCE_PX_PER_MS, 0.0f, 1.0f);
            float intensity = Mth.clamp(cfg.velocitySizeIntensity, 0.0f, 1.0f);
            sizeBoost = 1.0f + intensity * velocityFraction;
        }

        emitStampsAlongQuadBezier(s1x, s1y, ctlX, ctlY, s2x, s2y, cfg.stampSpacingPx, now, sizeBoost);
    }

    private static void emitStampsAlongQuadBezier(double p0x, double p0y, double p1x, double p1y,
                                                   double p2x, double p2y, int spacingPx, long now,
                                                   float sizeBoost) {
        double cx = p2x - p0x;
        double cy = p2y - p0y;
        double chord = Math.sqrt(cx * cx + cy * cy);
        double minSpacing = Math.max(1, spacingPx);
        if (chord < minSpacing) return;

        int steps = (int) (chord / minSpacing);
        if (steps < 1) steps = 1;
        if (steps > MAX_STAMPS_PER_FRAME) steps = MAX_STAMPS_PER_FRAME;

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double omt = 1.0 - t;
            double a = omt * omt;
            double b = 2.0 * omt * t;
            double c = t * t;
            double sx = a * p0x + b * p1x + c * p2x;
            double sy = a * p0y + b * p1y + c * p2y;

            stampX[head] = (float) sx;
            stampY[head] = (float) sy;
            stampBirthMs[head] = now;
            stampSizeBoost[head] = sizeBoost;
            head = (head + 1) % MAX_STAMPS;
            if (count < MAX_STAMPS) count++;
        }
    }

    private static void resetTrailBuffer() {
        head = 0;
        count = 0;
        sampleCount = 0;
    }

    private static NativeImage buildProceduralDot() {
        int size = PROCEDURAL_SPRITE_SIZE;
        NativeImage image = new NativeImage(size, size, false);
        float cx = (size - 1) * 0.5f;
        float cy = (size - 1) * 0.5f;
        float radius = (size * 0.5f) - 1.0f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / radius;
                float a = 1.0f - Mth.clamp(d, 0.0f, 1.0f);
                a = a * a;
                int alpha = Math.round(Mth.clamp(a * 255.0f, 0.0f, 255.0f));
                image.setPixelABGR(x, y, (alpha << 24) | (255 << 16) | (255 << 8) | 255);
            }
        }
        return image;
    }

    /**
     * RenderPipelines has no built-in additive GUI pipeline, so this clones
     * every field off the vanilla GUI_TEXTURED pipeline and swaps only the
     * blend function, rather than hand-authoring shaders/vertex layout.
     */
    private static RenderPipeline getOrBuildAdditivePipeline() {
        if (additivePipeline != null) return additivePipeline;

        RenderPipeline base = RenderPipelines.GUI_TEXTURED;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("mineux", "pipeline/gui_textured_additive"))
                .withVertexShader(base.getVertexShader())
                .withFragmentShader(base.getFragmentShader())
                .withPolygonMode(base.getPolygonMode())
                .withCull(base.isCull())
                .withDepthStencilState(Optional.ofNullable(base.getDepthStencilState()))
                .withPrimitiveTopology(base.getPrimitiveTopology())
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE));

        VertexFormat[] bindings = base.getVertexFormatBindings();
        for (int i = 0; i < bindings.length; i++) {
            if (bindings[i] != null) builder.withVertexBinding(i, bindings[i]);
        }
        for (BindGroupLayout layout : base.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }

        additivePipeline = builder.build();
        return additivePipeline;
    }
}
