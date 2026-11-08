package com.mineux.screens.render;

import com.mineux.screens.Screens;
import com.mineux.screens.capture.CaptureManager;
import com.mineux.screens.displays.CapturedScreenDisplay;
import com.mineux.screens.displays.Surface;
import com.mineux.screens.net.RemoteScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Everything Screens puts in the world each frame: your screens, other players' shared ones,
 * the outline of the hovered or held screen, and the badges -- a red "LIVE" with the viewer
 * count over each of your shared screens, the owner's name over each of theirs.
 */
public final class WorldDraw {

    private static final int LIVE_RED = 0xFFE8343E;

    private WorldDraw() {}

    public static void submit(SubmitNodeCollector collector, Vec3 camera) {
        ScreenRenderer.prepare();
        PoseStack pose = new PoseStack();
        CaptureManager manager = Screens.manager();
        if (manager != null) {
            for (CapturedScreenDisplay d : manager.displays) {
                d.render(pose, collector, camera);
                if (d.shared) {
                    d.outline(pose, collector, camera, LIVE_RED, 3.0f);
                    badge(collector, camera, d, "LIVE  " + d.viewers + " watching", 0xFFFFFFFF, 0xE0C0141E);
                }
            }
            if (manager.pointer.hovered != null) {
                manager.pointer.hovered.target().outline(pose, collector, camera, ARGB.color(255, 255, 255, 255), 2.0f);
            } else if (manager.pointer.grabbed != null) {
                manager.pointer.grabbed.outline(pose, collector, camera, ARGB.color(255, 255, 200, 0), 2.0f);
            }
        }
        for (RemoteScreen r : Screens.sharing().remotes.all()) {
            if (!r.visible || !r.texture().isValid()) continue;
            r.render(pose, collector, camera);
            badge(collector, camera, r, r.info.ownerName.isEmpty() ? "shared screen" : r.info.ownerName, 0xFFF2F2F7, 0xA0101018);
        }
    }

    /** A line of text just over the top left corner, lying on the screen's plane, facing out of its front. */
    private static void badge(SubmitNodeCollector collector, Vec3 camera, Surface s, String text, int color, int background) {
        if (!s.texture().isValid()) return;
        Vec3 right = s.placement.right();
        Vec3 up = s.placement.up();
        Vec3 facing = s.placement.facing();
        double scale = Math.clamp(s.worldHeight() / 180.0, 0.004, 0.03);
        Vec3 corner = s.surfacePoint(0, 0).add(up.scale(scale * 3.0)).add(facing.scale(0.02)).subtract(camera);
        PoseStack pose = new PoseStack();
        pose.translate(corner.x, corner.y, corner.z);
        Matrix3f basis = new Matrix3f(
            (float) right.x, (float) right.y, (float) right.z,
            (float) up.x, (float) up.y, (float) up.z,
            (float) facing.x, (float) facing.y, (float) facing.z);
        pose.mulPose(new Quaternionf().setFromNormalized(basis));
        float f = (float) scale;
        pose.scale(f, 0f - f, f);
        collector.submitText(pose, 0f, -9f, Component.literal(text).getVisualOrderText(), false,
            Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, color, background, 0);
    }
}
