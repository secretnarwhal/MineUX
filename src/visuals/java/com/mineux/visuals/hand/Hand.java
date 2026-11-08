package com.mineux.visuals.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The first-person hand and the item in it: the hand hooks land here
 * ({@link com.mineux.visuals.VisualOps#OP_HAND_ATTACK} and the four after it) and go out to the view model (below),
 * the swing animations ({@link Swing}), the hand view ({@link Grip}) and the inspect animation
 * ({@link Inspect}). The glint ({@link Glint}) and the hand shaders ({@link HandShaders}) have
 * hooks of their own.
 * <p>
 * Drawn only, every one of them. The swing progress changed here is the one the hands are drawn
 * with this frame, never the player's: the swing, the attack, its timing and its packet are the
 * game's own, so the server and its anticheat see a vanilla swing whatever is on your screen.
 * <p>
 * A hook that throws is logged once and the hand is drawn as vanilla draws it from then on.
 */
public final class Hand {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/hand");

    private static boolean broken;

    /** The visual swing's own clock: when it started, and what the player's swing was last frame. */
    private static long swingStart;
    private static boolean wasSwinging;
    private static int lastSwingTime;

    private Hand() {}

    // ---- the swing progress --------------------------------------------------------------------

    /** The swing progress the hands are drawn with this frame: none, re-timed, or the game's. */
    public static Object attack(Object value) {
        if (broken || !Float.class.isInstance(value) || !com.mineux.visuals.Visuals.on()) return value;
        HandConfig config = HandConfig.get();
        if (config.noSwing) return Float.valueOf(0f);
        LocalPlayer player = Minecraft.getInstance().player;
        if (!config.swingSpeed || player == null) return value;
        return Float.valueOf(paced(player, config.swingTicks));
    }

    /**
     * The swing as it would look over {@code ticks} ticks: started when the player's swing starts
     * or starts again, and run to its end on the frame clock whatever the game's swing does.
     */
    private static float paced(LocalPlayer player, int ticks) {
        long now = System.nanoTime();
        boolean swinging = player.swinging;
        int time = player.swingTime;
        if (swinging && (!wasSwinging || time < lastSwingTime)) swingStart = now;
        wasSwinging = swinging;
        lastSwingTime = time;
        if (swingStart == 0L) return 0f;
        float progress = (float) ((now - swingStart) / (ticks * 50.0e6));
        if (progress >= 1f) {
            swingStart = 0L;
            return 0f;
        }
        return progress;
    }

    // ---- the arm hooks -------------------------------------------------------------------------

    /** {@code query(OP_HAND_*, Object[])}: true when the visuals took that part of the drawing over. */
    public static boolean query(int op, Object arg) {
        if (broken || !Object[].class.isInstance(arg) || !com.mineux.visuals.Visuals.on()) return false;
        Object[] a = (Object[]) arg;
        try {
            if (op == OP_HAND_BEGIN) return begin(a);
            if (op == OP_HAND_EQUIP) return Swing.equip(HandConfig.get(), a);
            if (op == OP_HAND_SWING) return Swing.swing(HandConfig.get(), a);
            if (op == OP_HAND_ITEM) return Grip.item(HandConfig.get(), a);
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            LOGGER.error("Hand visuals failed; the hand is vanilla's from here on", e);
        }
        return false;
    }

    /** The head of each arm, on a pose of its own: the inspect hand move and the view model. True skips the arm. */
    private static boolean begin(Object[] a) {
        if (!AbstractClientPlayer.class.isInstance(a[0]) || !PoseStack.class.isInstance(a[3])) return false;
        AbstractClientPlayer player = (AbstractClientPlayer) a[0];
        boolean main = a[1] == InteractionHand.MAIN_HAND;
        PoseStack pose = (PoseStack) a[3];
        float equip = ((Float) a[5]).floatValue();
        HandConfig config = HandConfig.get();

        if (main) Inspect.frame(config, player, (ItemStack) a[2]);
        if (!main && Inspect.hidesOffHand(config)) return true;

        HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1f : 0f - 1f;
        // An old-style swing holds the item still: no dip as it is swapped in.
        float shown = Swing.owns(config, player, arm) ? 0f : equip;
        if (main) Inspect.applyHand(config, pose, side, shown);
        if (config.viewModel) viewModel(config, pose, main, side, shown);
        Grip.remember(pose);
        return false;
    }

    /** Where this hand sits: moved, turned and scaled about the hand's own place. */
    private static void viewModel(HandConfig c, PoseStack pose, boolean main, float side, float equip) {
        boolean off = !main && !c.vmMirror;
        double x = off ? c.vmOffX : c.vmMainX;
        double y = off ? c.vmOffY : c.vmMainY;
        double z = off ? c.vmOffZ : c.vmMainZ;
        double rx = off ? c.vmOffRotX : c.vmMainRotX;
        double ry = off ? c.vmOffRotY : c.vmMainRotY;
        double rz = off ? c.vmOffRotZ : c.vmMainRotZ;
        float scale = (float) (off ? c.vmOffScale : c.vmMainScale);
        if (!main && c.vmMirror) {
            // The main hand's numbers, mirrored across the middle of the screen.
            x = 0.0 - x;
            ry = 0.0 - ry;
            rz = 0.0 - rz;
        }
        pose.translate((float) x, (float) y, (float) z);
        pose.mulPose(Axis.XP.rotationDegrees((float) rx));
        pose.mulPose(Axis.YP.rotationDegrees((float) ry));
        pose.mulPose(Axis.ZP.rotationDegrees((float) rz));
        if (scale == 1f) return;
        float ax = side * 0.56f;
        float ay = 0f - 0.52f - equip * 0.6f;
        pose.translate(ax, ay, 0f - 0.72f);
        pose.scale(scale, scale, scale);
        pose.translate(0f - ax, 0f - ay, 0.72f);
    }
}
