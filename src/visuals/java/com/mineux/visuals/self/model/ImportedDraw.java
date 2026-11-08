package com.mineux.visuals.self.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * An imported model's pieces handed to the frame, one draw a texture. The pose stack is already at
 * the model's feet, turned to its facing and scaled to its height; each corner is only moved by its
 * bone, turned about the bone's pivot, on its way in.
 */
public final class ImportedDraw implements SubmitNodeCollector.CustomGeometryRenderer {

    private final Imported m;
    private final Piece p;
    /** Each bone's rotation, row major, nine to a bone; null for a model drawn still. */
    private final float[] rig;
    private final int alpha;
    private final int light;
    private final boolean lit;

    private ImportedDraw(Imported m, Piece p, float[] rig, int alpha, int light, boolean lit) {
        this.m = m;
        this.p = p;
        this.rig = rig;
        this.alpha = alpha;
        this.light = light;
        this.lit = lit;
    }

    /** The model's pieces into {@code collector}, at {@code pose}. */
    public static void submit(SubmitNodeCollector collector, PoseStack pose, Imported m, float[] rig, float alpha, int light, boolean lit,
                              boolean walls) throws ReflectiveOperationException {
        List<Identifier> textures = ModelFiles.textures(m);
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        float[] bones = rig != null && m.rigged ? rig : null;
        for (int i = 0; i < m.parts.size(); i++) {
            Piece piece = m.parts.get(i);
            if (piece.count == 0 || i >= textures.size()) continue;
            RenderType type = Types.entity(textures.get(i), walls);
            collector.submitCustomGeometry(pose, type, new ImportedDraw(m, piece, bones, a, light, lit));
        }
    }

    /**
     * The rig's rotations for a walk phase and stride, how far in the air, a crouch (0 or 1), the
     * head's turn and tilt and a swing, nine floats to a bone.
     */
    public static float[] pose(float phase, float amp, float air, float crouch, float headYaw, float headPitch, float attack) {
        float[] out = new float[54];
        float s = (float) Math.sin(phase) * amp;
        float legSwing = 0.75F * s;
        float armSwing = 0.6F * s;
        float swing = attack > 0.0F ? (float) Math.sin(attack * Math.PI) : 0.0F;
        euler(out, 9, headPitch + 0.15F * crouch, 0.0F - headYaw, 0.0F);
        euler(out, 18, armSwing, 0.0F, 0.45F * air);
        euler(out, 27, 0.0F - armSwing - 1.4F * swing, 0.0F, 0.0F - 0.45F * air);
        euler(out, 36, 0.0F - legSwing - 0.4F * air, 0.0F, 0.0F);
        euler(out, 45, legSwing + 0.4F * air, 0.0F, 0.0F);
        return out;
    }

    /** Rotation about z, then x, then y, row major, into {@code m} at {@code o}. */
    private static void euler(float[] m, int o, float rx, float ry, float rz) {
        float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx);
        float cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
        float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);
        float a01 = 0.0F - sz * cx, a02 = sz * sx;
        float a11 = cz * cx, a12 = 0.0F - cz * sx;
        float a21 = sx, a22 = cx;
        m[o] = cy * cz;
        m[o + 1] = cy * a01 + sy * a21;
        m[o + 2] = cy * a02 + sy * a22;
        m[o + 3] = sz;
        m[o + 4] = a11;
        m[o + 5] = a12;
        m[o + 6] = 0.0F - sy * cz;
        m[o + 7] = cy * a21 - sy * a01;
        m[o + 8] = cy * a22 - sy * a02;
    }

    private static int mulColor(int x, int y) {
        int a = (x >>> 24) * (y >>> 24) / 255;
        int r = (x >> 16 & 0xFF) * (y >> 16 & 0xFF) / 255;
        int g = (x >> 8 & 0xFF) * (y >> 8 & 0xFF) / 255;
        int b = (x & 0xFF) * (y & 0xFF) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer vc) {
        float[] pos = p.pos, nrm = p.nrm, uv = p.uv, pv = m.pivot;
        int[] vcol = p.vcol;
        byte[] bone = p.bone;
        for (int t = 0; t + 2 < p.count; t += 3) {
            // Each triangle as a quad with its last corner twice.
            for (int k = 0; k < 4; k++) {
                int i = t + Math.min(k, 2);
                float x = pos[i * 3], y = pos[i * 3 + 1], z = pos[i * 3 + 2];
                float nx = nrm[i * 3], ny = nrm[i * 3 + 1], nz = nrm[i * 3 + 2];
                int b = rig != null && bone != null ? bone[i] : 0;
                if (b != 0) {
                    int r = b * 9;
                    float dx = x - pv[b * 3], dy = y - pv[b * 3 + 1], dz = z - pv[b * 3 + 2];
                    x = rig[r] * dx + rig[r + 1] * dy + rig[r + 2] * dz + pv[b * 3];
                    y = rig[r + 3] * dx + rig[r + 4] * dy + rig[r + 5] * dz + pv[b * 3 + 1];
                    z = rig[r + 6] * dx + rig[r + 7] * dy + rig[r + 8] * dz + pv[b * 3 + 2];
                    float ra = rig[r] * nx + rig[r + 1] * ny + rig[r + 2] * nz;
                    float rb = rig[r + 3] * nx + rig[r + 4] * ny + rig[r + 5] * nz;
                    float rc = rig[r + 6] * nx + rig[r + 7] * ny + rig[r + 8] * nz;
                    nx = ra;
                    ny = rb;
                    nz = rc;
                }
                if (!lit) {
                    nx = 0.0F;
                    ny = 1.0F;
                    nz = 0.0F;
                }
                int col = vcol != null ? mulColor(vcol[i], p.color) : p.color;
                col = (col >>> 24) * alpha / 255 << 24 | col & 0xFFFFFF;
                vc.addVertex(pose, x, y, z)
                    .setColor(col)
                    .setUv(uv[i * 2], uv[i * 2 + 1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
            }
        }
    }
}
