package com.mineux.screens.displays;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Placement aids while a screen is being moved: sticking it to blocks and lining it up with other screens
public final class ScreenSnapping {

    // How far away a block can be to stick a screen to it
    private static final double BLOCK_REACH = 40.0;

    // Distance kept from the block face, so the screen doesn't flicker against it
    private static final double BLOCK_GAP = 1.0 / 32.0;

    // Free space between two screens lined up next to each other
    private static final double NEIGHBOR_GAP = 1.0 / 16.0;

    // How far outside the edge of a screen one can aim and still line up with it
    private static final double NEIGHBOR_REACH = 0.75;

    private ScreenSnapping() {
    }

    /* Lay the screen flat onto the block face the viewer is aiming at
     * With `grid` the screen also moves to the nearest half block and turns in steps of 90 degrees.
     */
    public static void ontoBlock(ScreenPlacement placement, Vec3 eye, Vec3 look, float yaw, boolean grid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        Vec3 end = eye.add(look.scale(BLOCK_REACH));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        if (hit.getType() != HitResult.Type.BLOCK) return;

        Direction face = hit.getDirection();
        Vec3 out = face.getUnitVec3();
        Vec3 spot = hit.getLocation();

        if (grid) {
            spot = new Vec3(
                    face.getAxis() == Direction.Axis.X ? spot.x : halfBlock(spot.x),
                    face.getAxis() == Direction.Axis.Y ? spot.y : halfBlock(spot.y),
                    face.getAxis() == Direction.Axis.Z ? spot.z : halfBlock(spot.z));
        }

        // On walls the top edge points to the sky. On floors it points away from the viewer and on ceilings towards them.
        Vec3 top = new Vec3(0, 1, 0);
        if (face.getAxis() == Direction.Axis.Y) {
            Vec3 ahead = grid
                    ? Direction.fromYRot(yaw).getUnitVec3()
                    : new Vec3(0f - Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
            top = face == Direction.UP ? ahead : ahead.reverse();
        }

        placement.moveTo(spot.add(out.scale(BLOCK_GAP)));
        placement.orient(out, top);
    }

    private static double halfBlock(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    /* Line the screen up with another screen the viewer is aiming at the edge of
     * It ends up next to that edge, or diagonally off a corner, turned the same way as the other screen.
     */
    public static void besideNeighbor(Surface moving, List<? extends Surface> all, Vec3 eye, Vec3 look) {
        double width = moving.worldWidth();
        double height = moving.worldHeight();

        for (Surface other : all) {
            if (other == moving || !other.texture().isValid()) continue;

            ScreenPlacement.Hit hit = other.placement.raycast(eye, look);
            if (hit == null) continue;

            double halfWidth = other.worldWidth() / 2;
            double halfHeight = other.worldHeight() / 2;

            // Positive outside of the other screen, negative inside
            double pastSide = Math.abs(hit.across()) - halfWidth;
            double pastTop = Math.abs(hit.upward()) - halfHeight;

            // Aiming just inside an edge counts as well
            double inner = -0.4 * Math.min(halfWidth, halfHeight);
            boolean atSide = pastSide > inner && pastSide < NEIGHBOR_REACH;
            boolean atTop = pastTop > inner && pastTop < NEIGHBOR_REACH;

            double across = 0;
            double upward = 0;
            if (atSide && (atTop || pastTop <= 0)) across = Math.signum(hit.across()) * (halfWidth + NEIGHBOR_GAP + width / 2);
            if (atTop && (atSide || pastSide <= 0)) upward = Math.signum(hit.upward()) * (halfHeight + NEIGHBOR_GAP + height / 2);
            if (across == 0 && upward == 0) continue;

            moving.placement.orient(other.placement.facing(), other.placement.up());
            moving.placement.moveTo(other.placement.point(across, upward));
            return;
        }
    }

}
