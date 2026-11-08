package com.mineux.visuals.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Item physics: dropped items fall flat and tumble, instead of hovering and spinning. A flat
 * item (a sword, an ingot) lands on its face; a block on whichever side it rocks onto; a stack
 * shows as a few copies, flat ones in a neat pile.
 * <p>
 * Each item's body ({@link ItemBody}) lives as long as the item does, and is stepped once a
 * frame at the item's extract with the time since its last step. The turn it comes to is kept
 * against that frame's render state -- a new one every frame -- for the draw that follows.
 * Nothing moves where the item really is: the server still has it where it has it.
 */
public final class ItemPhysics {

    private static final Map<ItemEntity, ItemBody> BODIES = new WeakHashMap<>();
    /** This frame's render state -> the turn to draw it at. */
    private static final Map<Object, float[]> TURNS = new WeakHashMap<>();

    private static final Quaternionf TURN = new Quaternionf();
    private static final ItemBody SHOWN = new ItemBody();
    private static final Random RANDOM = new Random();
    /** Vanilla's line between a flat item and a block, in model depth. */
    private static final double FLAT = 0.0625;

    private ItemPhysics() {}

    private static boolean on() {
        return World.allowed() && WorldConfig.get().itemPhysics;
    }

    static void extract(Object entity, Object state) {
        if (!on()) {
            if (!BODIES.isEmpty()) BODIES.clear();
            return;
        }
        if (!ItemEntity.class.isInstance(entity) || !ItemEntityRenderState.class.isInstance(state)) return;
        ItemEntity item = (ItemEntity) entity;
        ItemEntityRenderState drawn = (ItemEntityRenderState) state;
        if (drawn.item.isEmpty()) return;
        WorldConfig config = WorldConfig.get();
        float spin = (float) config.itemSpin;
        boolean flat = drawn.item.getModelBoundingBox().getZsize() <= FLAT;
        boolean fluid = item.isInWater() || item.isInLava();
        boolean onGround = item.onGround() || fluid;
        long now = System.nanoTime();
        ItemBody body = BODIES.get(item);
        if (body == null) {
            body = new ItemBody();
            BODIES.put(item, body);
        }
        if (!body.started) {
            body.start(onGround, flat, (drawn.seed % 100000) * 0.013F + item.getId() * 0.37F, spin);
            body.last = now;
        }
        float dt = Math.min(0.05F, (now - body.last) / 1.0E9F);
        body.last = now;
        if (Minecraft.getInstance().isPaused()) dt = 0.0F;
        float speed = (float) (item.getDeltaMovement().length() * 20.0);
        body.step(dt, flat, onGround, fluid, speed, spin, 0.95F - 0.7F * (float) config.itemBounce);
        TURNS.put(state, new float[] {body.q[0], body.q[1], body.q[2], body.q[3]});
    }

    /** {state, poseStack, submitNodeCollector}: true when the item was drawn here. */
    static boolean draw(Object[] args) {
        if (args.length < 3 || !on()) return false;
        if (!ItemEntityRenderState.class.isInstance(args[0]) || !PoseStack.class.isInstance(args[1])
            || !SubmitNodeCollector.class.isInstance(args[2])) return false;
        float[] turn = TURNS.remove(args[0]);
        if (turn == null) return false;
        ItemEntityRenderState state = (ItemEntityRenderState) args[0];
        if (state.item.isEmpty()) return false;
        PoseStack pose = (PoseStack) args[1];
        SubmitNodeCollector collector = (SubmitNodeCollector) args[2];

        AABB box = state.item.getModelBoundingBox();
        boolean flat = box.getZsize() <= FLAT;
        int copies = Math.max(1, Math.min(5, state.count));
        float depth = (float) box.getZsize();
        float spacing = Math.max(depth * 1.5F, 0.03F);
        float hx = (float) box.getXsize() * 0.5F;
        float hy = (float) box.getYsize() * 0.5F;
        float hz = flat ? depth * 0.5F + (copies - 1) * spacing * 0.5F : depth * 0.5F;
        SHOWN.q[0] = turn[0];
        SHOWN.q[1] = turn[1];
        SHOWN.q[2] = turn[2];
        SHOWN.q[3] = turn[3];
        float lift = SHOWN.support(hx, hy, hz) + 0.004F;
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;

        pose.pushPose();
        pose.translate(0.0F, lift, 0.0F);
        pose.mulPose(TURN.set(turn[0], turn[1], turn[2], turn[3]));
        pose.translate(0.0 - cx, 0.0 - cy, 0.0 - cz);
        RANDOM.setSeed(state.seed);
        for (int k = 0; k < copies; k++) {
            pose.pushPose();
            if (flat) {
                pose.translate(0.0F, 0.0F, (k - (copies - 1) * 0.5F) * spacing);
            } else if (k > 0) {
                pose.translate((RANDOM.nextFloat() * 2.0F - 1.0F) * 0.15F * hx * 2.0F,
                    (RANDOM.nextFloat() * 2.0F - 1.0F) * 0.15F * hy * 2.0F,
                    (RANDOM.nextFloat() * 2.0F - 1.0F) * 0.15F * depth);
            }
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
        pose.popPose();
        return true;
    }
}
