package com.mineux.visuals.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import java.util.Arrays;
import java.util.List;

/**
 * Swing animations for the main hand: the 1.7 swing and its block-hit poses, held still with no
 * dip as the item is swapped in, and the spring-driven swings of {@link SwingEngine}. Drawn only,
 * on the swing progress the game hands the renderer; the real swing is the game's.
 * <p>
 * old: 1.7's swing, the item turning in the hand. block: the sword held across you as 1.7 held
 * a block, chopping by strength. push: the same block, pushed side to side. down: the blade
 * brought down from overhead. spin: one full turn a swing. The rest are {@link SwingEngine}'s.
 */
final class Swing {

    private static final List<String> MODES = Arrays.asList(
        "old", "block", "push", "down", "spin",
        "smooth", "stab", "slice", "windmill", "combo", "dance", "charge", "momentum", "whirlwind");
    /** The first of {@link #MODES} that {@link SwingEngine} drives. */
    private static final int ENGINE = 5;

    private static long lastFrame;

    private Swing() {}

    static int modeIndex(String name) {
        return MODES.indexOf(name);
    }

    /** Whether the swing animations draw this arm: the main hand's, while they are on. */
    static boolean owns(HandConfig c, AbstractClientPlayer player, HumanoidArm arm) {
        return c.animations && player != null && player.getMainArm() == arm;
    }

    /** The arm's place before a plain swing, without the dip of an item swapped in. */
    static boolean equip(HandConfig c, Object[] a) {
        HumanoidArm arm = (HumanoidArm) a[1];
        if (!owns(c, net.minecraft.client.Minecraft.getInstance().player, arm)) return false;
        PoseStack pose = (PoseStack) a[0];
        float side = arm == HumanoidArm.RIGHT ? 1f : 0f - 1f;
        pose.translate(side * 0.56f, 0f - 0.52f, 0f - 0.72f);
        return true;
    }

    /** In place of the game's swing. */
    static boolean swing(HandConfig c, Object[] a) {
        HumanoidArm arm = (HumanoidArm) a[1];
        if (!owns(c, net.minecraft.client.Minecraft.getInstance().player, arm)) return false;
        int mode = Math.max(0, MODES.indexOf(c.animMode));
        PoseStack pose = (PoseStack) a[0];
        float p = ((Float) a[2]).floatValue();
        float side = arm == HumanoidArm.RIGHT ? 1f : 0f - 1f;
        float strength = Mth.clamp(c.animStrength, 20, 75);
        if (mode >= ENGINE) {
            engine(pose, side, mode - ENGINE, p, (strength - 20f) / 55f);
            return true;
        }
        float g = Mth.sin(Mth.sqrt(p) * (float) Math.PI);
        float anim = (float) Math.sin(p * Math.PI);
        if (mode == 0) {
            pose.mulPose(Axis.YP.rotationDegrees(side * (45f + anim * (0f - 20f))));
            pose.mulPose(Axis.ZP.rotationDegrees(side * g * (0f - 20f)));
            pose.mulPose(Axis.XP.rotationDegrees(g * (0f - 80f)));
            pose.mulPose(Axis.YP.rotationDegrees(side * (0f - 45f)));
        } else if (mode == 1) {
            pose.mulPose(Axis.XP.rotationDegrees(50f));
            pose.mulPose(Axis.YP.rotationDegrees(side * (0f - 60f)));
            pose.mulPose(Axis.ZP.rotationDegrees(side * (110f + strength * g)));
        } else if (mode == 2) {
            pose.mulPose(Axis.XP.rotationDegrees(50f));
            pose.mulPose(Axis.YP.rotationDegrees(side * ((0f - 30f) * (1f - g) - 30f + (strength - 20f) * g)));
            pose.mulPose(Axis.ZP.rotationDegrees(side * 110f));
        } else if (mode == 3) {
            pose.mulPose(Axis.YP.rotationDegrees(side * 90f));
            pose.mulPose(Axis.ZP.rotationDegrees(side * (0f - 30f)));
            pose.mulPose(Axis.XP.rotationDegrees(0f - 90f - strength * anim + 10f));
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(p * (0f - 360f)));
        }
        return true;
    }

    /** One of {@link SwingEngine}'s swings, stepped by the frame's time. */
    private static void engine(PoseStack m, float side, int mode, float p, float strength) {
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0f : (now - lastFrame) / 1.0e9f;
        if (dt > 5.0e-4f || lastFrame == 0L) {
            lastFrame = now;
            SwingEngine.step(mode, p, dt, strength);
        }
        float[] q = SwingEngine.POSE;
        m.translate(side * q[0], q[1], q[2]);
        if (q[6] != 0f) {
            m.translate(0f, 0.12f, 0f);
            m.mulPose(Axis.XP.rotationDegrees(q[6]));
            m.translate(0f, 0f - 0.12f, 0f);
        }
        m.mulPose(Axis.YP.rotationDegrees(side * (45f + q[3])));
        m.mulPose(Axis.ZP.rotationDegrees(side * q[4]));
        m.mulPose(Axis.XP.rotationDegrees(q[5]));
        m.mulPose(Axis.YP.rotationDegrees(side * (0f - 45f)));
    }
}
