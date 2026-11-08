package com.mineux.screens.displays;

import com.mineux.screens.render.FrameTexture;
import com.mineux.screens.render.ScreenRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * A screen in the world, whoever's it is: its place, its size and bend, its latest frame, and
 * how it is drawn. Your own captures ({@link CapturedScreenDisplay}) and other players' shared
 * screens ({@code net.RemoteScreen}) are both one.
 * <p>
 * A curved screen is a section of a cylinder. Its width is kept as arc length and the centre
 * column stays on the placement's plane, the left and right edges coming forward.
 */
public abstract class Surface {

    /** Arc angles below this are treated as flat. */
    public static final double MIN_ARC = 1.0e-3;

    /** Flat strips a curved screen is drawn with. */
    private static final int CURVE_STRIPS = 48;
    /** Line segments per curved edge of an outline. */
    private static final int OUTLINE_SEGMENTS = 24;

    public final ScreenPlacement placement = new ScreenPlacement();

    /** 1 is fully opaque, lower values make the screen see-through. */
    public double opacity = 1.0;

    public abstract FrameTexture texture();

    /** Size in blocks, 0 before the first frame arrived. */
    public abstract double worldWidth();

    public abstract double worldHeight();

    /** Angle in radians the screen bends around the viewer, 0 when flat. */
    public abstract double arcAngle();

    /** World position of pixel (x, y) of the frame, following the curvature. */
    public Vec3 surfacePoint(double x, double y) {
        int width = texture().width();
        int height = texture().height();
        if (width <= 0 || height <= 0) return placement.center();
        double upward = (0.5 - y / height) * worldHeight();
        double arc = arcAngle();
        if (arc < MIN_ARC) return placement.point((x / width - 0.5) * worldWidth(), upward);
        double radius = worldWidth() / arc;
        double angle = (x / width - 0.5) * arc;
        return placement.point(radius * Math.sin(angle), upward)
            .add(placement.facing().scale(radius * (1.0 - Math.cos(angle))));
    }

    public void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera) {
        FrameTexture texture = texture();
        if (!texture.isValid()) return;
        int width = texture.width();
        int height = texture.height();
        int strips = arcAngle() < MIN_ARC ? 1 : CURVE_STRIPS;
        Vec3[] top = new Vec3[strips + 1];
        Vec3[] bottom = new Vec3[strips + 1];
        for (int i = 0; i <= strips; i++) {
            double x = (double) width * i / strips;
            top[i] = surfacePoint(x, 0).subtract(camera);
            bottom[i] = surfacePoint(x, height).subtract(camera);
        }
        ScreenRenderer.submit(poseStack, collector, texture.location(), top, bottom, placement.facing(), (float) opacity);
    }

    /** The screen's edge as a closed line relative to the camera, {@code lift} blocks in front of it. */
    public Vec3[] outline(Vec3 camera, double lift) {
        int w = texture().width();
        int h = texture().height();
        Vec3 offset = placement.facing().scale(lift).subtract(camera);
        int segments = arcAngle() < MIN_ARC ? 1 : OUTLINE_SEGMENTS;
        Vec3[] points = new Vec3[segments * 2 + 3];
        for (int i = 0; i <= segments; i++) {
            double x = (double) w * i / segments;
            points[i] = surfacePoint(x, 0).add(offset);
            points[segments * 2 + 1 - i] = surfacePoint(x, h).add(offset);
        }
        points[segments * 2 + 2] = points[0];
        return points;
    }

    public void outline(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, int color, float width) {
        if (!texture().isValid()) return;
        ScreenRenderer.outline(poseStack, collector, outline(camera, 0.01), color, width);
    }
}
