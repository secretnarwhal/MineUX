package com.mineux.screens.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A line through points relative to the camera, in the game's line render type. */
final class Line implements SubmitNodeCollector.CustomGeometryRenderer {

    private final Vec3[] points;
    private final int color;
    private final float width;

    Line(Vec3[] points, int color, float width) {
        this.points = points;
        this.color = color;
        this.width = width;
    }

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer buffer) {
        for (int i = 0; i + 1 < points.length; i++) {
            Vector3f from = points[i].toVector3f();
            Vector3f to = points[i + 1].toVector3f();
            Vector3f along = new Vector3f(to).sub(from);
            buffer.addVertex(pose, from).setColor(color).setNormal(pose, along).setLineWidth(width);
            buffer.addVertex(pose, to).setColor(color).setNormal(pose, along).setLineWidth(width);
        }
    }
}
