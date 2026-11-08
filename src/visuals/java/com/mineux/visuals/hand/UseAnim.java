package com.mineux.visuals.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

/**
 * The hand view's own use animations: the arm brings food to the mouth, a shield across, a bow
 * drawn back, a trident or spear wound up, a charged crossbow levelled. Each eases in and out by
 * a weight of its own per hand, so letting go of the use key does not snap the arm back.
 */
final class UseAnim {

    private static final int NONE = 0, EAT = 1, DRINK = 2, BLOCK = 3, BOW = 4, SPEAR = 5,
        CROSSBOW = 6, SPYGLASS = 7, HORN = 8, BRUSH = 9;

    /** Per hand, main then off: how far in the animation is, which it was and how long it had run. */
    private static final float[] WEIGHT = new float[2];
    private static final int[] LAST = new int[2];
    private static final float[] LAST_TICKS = new float[2];
    private static final long[] LAST_NANOS = new long[2];

    private UseAnim() {}

    private static int kind(ItemUseAnimation a) {
        if (a == ItemUseAnimation.EAT) return EAT;
        if (a == ItemUseAnimation.DRINK) return DRINK;
        if (a == ItemUseAnimation.BLOCK) return BLOCK;
        if (a == ItemUseAnimation.BOW) return BOW;
        if (a == ItemUseAnimation.TRIDENT || a == ItemUseAnimation.SPEAR) return SPEAR;
        if (a == ItemUseAnimation.CROSSBOW) return CROSSBOW;
        if (a == ItemUseAnimation.SPYGLASS) return SPYGLASS;
        if (a == ItemUseAnimation.TOOT_HORN) return HORN;
        if (a == ItemUseAnimation.BRUSH) return BRUSH;
        return NONE;
    }

    static void apply(PoseStack pose, AbstractClientPlayer player, InteractionHand hand, ItemStack item,
                      float s, boolean using, float speed) {
        int h = hand == InteractionHand.MAIN_HAND ? 0 : 1;
        long now = System.nanoTime();
        float dt = LAST_NANOS[h] == 0L ? 0f : Math.min(0.1f, (now - LAST_NANOS[h]) / 1.0e9f);
        LAST_NANOS[h] = now;

        int anim = using ? kind(item.getUseAnimation()) : NONE;
        float ticks = 0f;
        if (using) ticks = player.getTicksUsingItem() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean aiming = !using && item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item);
        if (aiming) anim = CROSSBOW;

        boolean active = anim != NONE;
        float rate = (active ? 9f : 7f) * speed;
        WEIGHT[h] += ((active ? 1f : 0f) - WEIGHT[h]) * (1f - (float) Math.exp((0f - rate) * dt));
        if (active) {
            LAST[h] = anim;
            LAST_TICKS[h] = using ? ticks : 0f;
        }

        float w = smooth(WEIGHT[h]);
        if (w < 0.002f || LAST[h] == NONE) return;
        int a = active ? anim : LAST[h];
        float t = active ? ticks : LAST_TICKS[h];
        float time = (float) (now / 1.0e9 % 3600.0);
        float ns = 0f - s;
        if (a == EAT || a == DRINK) {
            eat(pose, a == DRINK, active, s, ns, t, w);
        } else if (a == BLOCK) {
            pose.translate(ns * 0.18f * w, 0.1f * w, 0.06f * w);
            pose.mulPose(Axis.YP.rotationDegrees(ns * 28f * w));
            pose.mulPose(Axis.XP.rotationDegrees((0f - 8f) * w));
        } else if (a == BOW) {
            bow(pose, active, ns, t, w, time);
        } else if (a == CROSSBOW) {
            crossbow(pose, aiming, using, s, ns, t, w, time);
        } else if (a == SPEAR) {
            float wind = Math.min(1f, t / 10f);
            pose.translate(0f, (0.08f + 0.18f * wind) * w, (0.1f + 0.22f * wind) * w);
            pose.mulPose(Axis.XP.rotationDegrees((0f - 20f - 45f * wind) * w));
            pose.mulPose(Axis.YP.rotationDegrees(ns * 10f * wind * w));
            if (wind >= 1f && active) {
                pose.translate((float) Math.sin(time * 39f) * 0.004f, (float) Math.sin(time * 33f) * 0.004f, 0f);
            }
        } else if (a == SPYGLASS) {
            pose.translate(ns * 0.42f * w, 0.24f * w, 0.32f * w);
            pose.mulPose(Axis.YP.rotationDegrees(s * 30f * w));
        } else if (a == HORN) {
            pose.translate(ns * 0.4f * w, 0.2f * w, 0.18f * w);
            pose.mulPose(Axis.YP.rotationDegrees(s * 34f * w));
            pose.mulPose(Axis.XP.rotationDegrees((0f - 22f) * w));
        } else if (a == BRUSH) {
            pose.translate(ns * 0.12f * w, (0f - 0.02f) * w, 0f);
            pose.mulPose(Axis.ZP.rotationDegrees(s * 22f * (float) Math.sin(t * 0.55f) * w));
            pose.mulPose(Axis.XP.rotationDegrees(14f * w));
        }
    }

    private static void eat(PoseStack pose, boolean drink, boolean active, float s, float ns, float t, float w) {
        pose.translate(ns * 0.46f * w, (0.16f + (drink ? 0.06f : 0f)) * w, 0.12f * w);
        pose.mulPose(Axis.YP.rotationDegrees(s * 38f * w));
        pose.mulPose(Axis.XP.rotationDegrees((drink ? 0f - 38f : 0f - 16f) * w));
        pose.mulPose(Axis.ZP.rotationDegrees(s * (drink ? 18f : 10f) * w));
        if (!active || t <= 4f) return;
        float bob = Math.abs((float) Math.cos(t / 4f * Math.PI)) * (drink ? 0.025f : 0.055f);
        pose.translate(0f, bob * w, 0f);
        if (drink) pose.mulPose(Axis.XP.rotationDegrees((0f - 6f) * (float) Math.sin(t * 0.8f) * w));
    }

    private static void bow(PoseStack pose, boolean active, float ns, float t, float w, float time) {
        float pull = t / 20f;
        pull = Math.min(1f, (pull * pull + pull * 2f) / 3f);
        float s = 0f - ns;
        pose.translate(ns * 0.3f * w, (0.06f + 0.02f * pull) * w, (0.1f * pull - 0.06f) * w);
        pose.mulPose(Axis.YP.rotationDegrees(s * (12f - 8f * pull) * w));
        pose.mulPose(Axis.ZP.rotationDegrees(s * (0f - 14f - 6f * pull) * w));
        if (pull > 0.9f && active) {
            float k = (pull - 0.9f) * 10f;
            pose.translate((float) Math.sin(time * 41f) * 0.004f * k, (float) Math.sin(time * 37f + 1.3f) * 0.004f * k, 0f);
        }
    }

    private static void crossbow(PoseStack pose, boolean aiming, boolean using, float s, float ns, float t, float w, float time) {
        if (!aiming && using) {
            float load = Math.min(1f, t / 25f);
            pose.translate(ns * 0.12f * w, (0f - 0.04f) * w, 0.1f * w);
            pose.mulPose(Axis.XP.rotationDegrees((20f + 10f * load) * w));
            pose.mulPose(Axis.ZP.rotationDegrees(s * 8f * (float) Math.sin(t * 1.6f) * load * w));
            pose.translate(0f, (float) Math.sin(time * 34f) * 0.003f * load * w, 0f);
        } else {
            pose.translate(ns * 0.26f * w, 0.08f * w, 0.04f * w);
            pose.mulPose(Axis.YP.rotationDegrees(ns * 6f * w));
            pose.mulPose(Axis.XP.rotationDegrees((0f - 4f) * w));
        }
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
}
