package com.mineux.screens.net;

import com.mineux.screens.displays.ScreenPlacement;
import com.mineux.screens.math.ViewFrame;
import net.minecraft.world.phys.Vec3;

/**
 * A screen's turn as the relay's {@code rot: [yaw, pitch]}, in the game's angles: the facing is
 * {@code directionFromRotation(pitch, yaw)} and the top edge points along
 * {@code directionFromRotation(pitch - 90, yaw)}. That covers every placement the menu makes
 * (walls, floors, ceilings, held in front of you, lined up with another), none of which roll;
 * a rolled screen comes out with its roll taken away. For a floor or a ceiling the yaw is read
 * from the top edge, the facing being straight up or down.
 */
public final class Pose {

    private Pose() {}

    /** [yaw, pitch] of a facing and a top edge, both unit vectors. */
    public static float[] encode(Vec3 facing, Vec3 up) {
        double pitch = 0.0 - Math.toDegrees(Math.asin(Math.clamp(facing.y, -1.0, 1.0)));
        double flat = Math.sqrt(facing.x * facing.x + facing.z * facing.z);
        double yaw;
        if (flat > 1.0e-3) {
            yaw = Math.toDegrees(Math.atan2(0.0 - facing.x, facing.z));
        } else {
            // Facing straight up (pitch -90) or down (+90): the top edge carries the yaw.
            double s = Math.signum(Math.sin(Math.toRadians(pitch)));
            yaw = Math.toDegrees(Math.atan2(0.0 - up.x * s, up.z * s));
        }
        return new float[] {(float) yaw, (float) pitch};
    }

    public static Vec3 facing(float yaw, float pitch) {
        return direction(pitch, yaw);
    }

    public static Vec3 up(float yaw, float pitch) {
        return direction(pitch - 90.0f, yaw);
    }

    /** The game's {@code Vec3.directionFromRotation}, in doubles. */
    static Vec3 direction(double pitch, double yaw) {
        double p = Math.toRadians(pitch);
        double y = Math.toRadians(yaw);
        double flat = Math.cos(p);
        return new Vec3((0.0 - Math.sin(y)) * flat, 0.0 - Math.sin(p), Math.cos(y) * flat);
    }

    /** The share's place in the world: a world anchor as it is, a player anchor around {@code owner}. */
    public static void place(ShareInfo info, ViewFrame owner, ScreenPlacement out) {
        Vec3 facing = facing(info.yaw, info.pitch);
        Vec3 up = up(info.yaw, info.pitch);
        Vec3 center = new Vec3(info.x, info.y, info.z);
        if (info.player()) {
            center = owner.pos().add(owner.toWorld(center));
            facing = owner.toWorld(facing);
            up = owner.toWorld(up);
        }
        out.moveTo(center);
        out.orient(facing, up);
    }

    /** Fills pos and rot from a placement: world coordinates, or relative to {@code owner} for a player anchor. */
    public static void fill(ShareInfo info, ScreenPlacement placement, ViewFrame owner) {
        Vec3 center = placement.center();
        Vec3 facing = placement.facing();
        Vec3 up = placement.up();
        if (info.player()) {
            center = owner.toLocal(center.subtract(owner.pos()));
            facing = owner.toLocal(facing);
            up = owner.toLocal(up);
        }
        float[] rot = encode(facing, up);
        info.x = center.x;
        info.y = center.y;
        info.z = center.z;
        info.yaw = rot[0];
        info.pitch = rot[1];
    }
}
