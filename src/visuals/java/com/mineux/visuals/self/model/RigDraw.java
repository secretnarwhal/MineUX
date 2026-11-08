package com.mineux.visuals.self.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;

/** A posed {@link Rig} handed to the frame, drawn through the pose it was submitted at. */
public final class RigDraw implements SubmitNodeCollector.CustomGeometryRenderer {

    private final Rig rig;
    private final Rig.Bones bones;
    private final float alpha;
    private final double[] view = new double[12];

    public RigDraw(Rig rig, Rig.Bones bones, float alpha) {
        this.rig = rig;
        this.bones = bones;
        this.alpha = alpha;
    }

    /** The rig's draw into {@code collector}, at {@code pose}. */
    public static void submit(SubmitNodeCollector collector, PoseStack pose, Rig rig, Rig.Bones bones, float alpha, boolean walls)
        throws ReflectiveOperationException {
        collector.submitCustomGeometry(pose, Types.rig(walls), new RigDraw(rig, bones, alpha));
    }

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer vc) {
        Matrix4f pm = pose.pose();
        double[] v = view;
        v[0] = pm.m00();
        v[1] = pm.m10();
        v[2] = pm.m20();
        v[3] = pm.m30();
        v[4] = pm.m01();
        v[5] = pm.m11();
        v[6] = pm.m21();
        v[7] = pm.m31();
        v[8] = pm.m02();
        v[9] = pm.m12();
        v[10] = pm.m22();
        v[11] = pm.m32();
        rig.draw(vc, v, bones, alpha);
    }
}
