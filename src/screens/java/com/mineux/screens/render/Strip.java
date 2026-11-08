package com.mineux.screens.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * The quads of one side of a screen. {@code normal} is given only for the game's entity render
 * types (shader packs, or our pipeline failing), whose vertices need overlay, light and normal too.
 */
final class Strip implements SubmitNodeCollector.CustomGeometryRenderer {

    private final Vec3[] top;
    private final Vec3[] bottom;
    private final int color;
    private final boolean flipped;
    private final Vec3 normal;

    Strip(Vec3[] top, Vec3[] bottom, int color, boolean flipped, Vec3 normal) {
        this.top = top;
        this.bottom = bottom;
        this.color = color;
        this.flipped = flipped;
        this.normal = normal;
    }

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer buffer) {
        int quads = top.length - 1;
        for (int i = 0; i < quads; i++) {
            float left = (float) i / quads;
            float right = (float) (i + 1) / quads;
            if (!flipped) {
                corner(pose, buffer, top[i], left, 0.0f);
                corner(pose, buffer, bottom[i], left, 1.0f);
                corner(pose, buffer, bottom[i + 1], right, 1.0f);
                corner(pose, buffer, top[i + 1], right, 0.0f);
            } else {
                corner(pose, buffer, top[i + 1], right, 0.0f);
                corner(pose, buffer, bottom[i + 1], right, 1.0f);
                corner(pose, buffer, bottom[i], left, 1.0f);
                corner(pose, buffer, top[i], left, 0.0f);
            }
        }
    }

    private void corner(PoseStack.Pose pose, VertexConsumer buffer, Vec3 position, float u, float v) {
        VertexConsumer vertex = buffer.addVertex(pose, position.toVector3f()).setColor(color).setUv(u, v);
        if (normal == null) return;
        vertex.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, normal.toVector3f());
    }
}
