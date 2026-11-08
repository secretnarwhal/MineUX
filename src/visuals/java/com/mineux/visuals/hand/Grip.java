package com.mineux.visuals.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hand view: your own arm in first person, with the item in its hand the way it is held in third
 * person -- the arm swings, and the item goes with it. grip draws arm and item together, eating,
 * drinking, blocking and drawing a bow included ({@link UseAnim}); overlay leaves vanilla's item
 * where it is and draws the bare arm under it.
 * <p>
 * The arm is placed from the pose this hand had before vanilla placed it ({@link #remember}),
 * which is the view model's and the inspect animation's.
 */
final class Grip {

    /** This hand's pose before vanilla placed the arm: the view model's, the inspect's. */
    private static final PoseStack BASE = new PoseStack();

    private Grip() {}

    static void remember(PoseStack pose) {
        BASE.last().set(pose.last());
    }

    /** In place of vanilla drawing the item: true when it was drawn here, with the arm. */
    static boolean item(HandConfig c, Object[] a) {
        if (!ItemInHandRenderer.class.isInstance(a[0]) || !AbstractClientPlayer.class.isInstance(a[1])) return false;
        PoseStack pose = (PoseStack) a[4];
        InteractionHand hand = (InteractionHand) a[7];
        if (!c.handView || !draw(c, a)) {
            Inspect.applyItem(c, pose, hand == InteractionHand.MAIN_HAND);
            return false;
        }
        return true;
    }

    private static boolean draw(HandConfig c, Object[] a) {
        AbstractClientPlayer player = (AbstractClientPlayer) a[1];
        ItemStack item = (ItemStack) a[2];
        PoseStack pose = (PoseStack) a[4];
        SubmitNodeCollector collector = (SubmitNodeCollector) a[5];
        int light = ((Integer) a[6]).intValue();
        InteractionHand hand = (InteractionHand) a[7];
        if (item.isEmpty() || player.isInvisible()) return false;
        if (hand == InteractionHand.OFF_HAND && !c.hvOffHand) return false;

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        boolean using = player.isUsingItem() && player.getUsedItemHand() == hand;
        boolean special = item.is(Items.CROSSBOW) || item.is(Items.BOW) || item.is(Items.TRIDENT);
        String style = c.hvStyle;
        boolean grip = (style == null || !style.equals("overlay")) && (c.hvUseAnims || !using && !special);
        if (!grip) {
            overlay(c, player, right, pose, collector, light);
            return false;
        }

        float s = right ? 1f : 0f - 1f;
        float swing = ((Float) a[8]).floatValue();
        float equip = ((Float) a[9]).floatValue();
        pose.pushPose();
        try {
            pose.last().set(BASE.last());
            pose.translate(s * (float) c.hvOffsetX, (float) c.hvOffsetY, (float) c.hvOffsetZ);
            if (c.hvUseAnims) {
                UseAnim.apply(pose, player, hand, item, s, using, (float) c.hvUseSpeed);
                if (using) {
                    swing = 0f;
                    equip = 0f;
                }
            }
            swingArm(pose, s, swing, equip);
            if (c.hvRotation != 0.0) pose.mulPose(Axis.YP.rotationDegrees(s * (float) c.hvRotation));
            toShoulder(pose, s);
            AvatarRenderer<AbstractClientPlayer> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
            drawArm(renderer, player, right, c.hvSleeve, pose, collector, light);
            PlayerModel model = renderer.getModel();
            (right ? model.rightArm : model.leftArm).translateAndRotate(pose);
            pose.mulPose(Axis.XP.rotationDegrees(0f - 90f));
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            pose.translate(s / 16f, 0.125f, 0f - 0.625f);
            if (hand == InteractionHand.MAIN_HAND && Inspect.playing(c)) {
                pose.mulPose(Axis.XP.rotationDegrees(90f));
                Inspect.applyItem(c, pose, true);
                pose.mulPose(Axis.XP.rotationDegrees(0f - 90f));
            }
            ItemDisplayContext context = right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            ((ItemInHandRenderer) a[0]).renderItem(player, item, context, pose, collector, light);
        } finally {
            pose.popPose();
        }
        return true;
    }

    /** Vanilla's empty-hand swing and swap dip, applied to the arm. */
    private static void swingArm(PoseStack pose, float s, float swing, float equip) {
        float g = (float) Math.sqrt(swing);
        float h = (0f - 0.3f) * (float) Math.sin(g * Math.PI);
        float i = 0.4f * (float) Math.sin(g * Math.PI * 2.0);
        float j = (0f - 0.4f) * (float) Math.sin(swing * Math.PI);
        pose.translate(s * (h + 0.64000005f), i - 0.6f + equip * (0f - 0.6f), j - 0.71999997f);
        pose.mulPose(Axis.YP.rotationDegrees(s * 45f));
        float k = (float) Math.sin(swing * swing * Math.PI);
        float l = (float) Math.sin(g * Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(s * l * 70f));
        pose.mulPose(Axis.ZP.rotationDegrees(s * k * (0f - 20f)));
    }

    /** From the hand's place to the model's shoulder, as vanilla draws an empty hand. */
    private static void toShoulder(PoseStack pose, float s) {
        pose.translate(0f - s, 3.6f, 3.5f);
        pose.mulPose(Axis.ZP.rotationDegrees(s * 120f));
        pose.mulPose(Axis.XP.rotationDegrees(200f));
        pose.mulPose(Axis.YP.rotationDegrees(s * (0f - 135f)));
        pose.translate(s * 5.6f, 0f, 0f);
    }

    /** overlay: the bare arm under vanilla's item, which is left as it is. */
    private static void overlay(HandConfig c, AbstractClientPlayer player, boolean right, PoseStack pose,
                                SubmitNodeCollector collector, int light) {
        float s = right ? 1f : 0f - 1f;
        pose.pushPose();
        try {
            pose.translate(s * (0.08f + (float) c.hvOffsetX), (float) c.hvOffsetY - 0.08f, (float) c.hvOffsetZ);
            if (c.hvRotation != 0.0) pose.mulPose(Axis.YP.rotationDegrees(s * (float) c.hvRotation));
            pose.mulPose(Axis.YP.rotationDegrees(s * 45f));
            toShoulder(pose, s);
            AvatarRenderer<AbstractClientPlayer> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
            drawArm(renderer, player, right, c.hvSleeve, pose, collector, light);
        } finally {
            pose.popPose();
        }
    }

    private static void drawArm(AvatarRenderer<AbstractClientPlayer> renderer, AbstractClientPlayer player, boolean right,
                                boolean sleeve, PoseStack pose, SubmitNodeCollector collector, int light) {
        Identifier skin = player.getSkin().body().texturePath();
        if (right) {
            renderer.renderRightHand(pose, collector, light, skin, sleeve && player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
        } else {
            renderer.renderLeftHand(pose, collector, light, skin, sleeve && player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
        }
    }
}
