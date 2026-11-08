package com.mineux.screens.math;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/* Position and orientation of a viewer, used to place things relative to the player
 *
 * `forward` and `up` have to be normalized and orthogonal.
 */
public record ViewFrame(Vec3 pos, Vec3 forward, Vec3 up) {

    // Eye position and look direction of an entity, interpolated for the current frame
    public static ViewFrame of(Entity entity) {
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        return new ViewFrame(entity.getEyePosition(partialTicks), entity.getViewVector(partialTicks), entity.getUpVector(partialTicks));
    }

    public Vec3 right() {
        return forward.cross(up);
    }

    // Express a world space direction in this frame's (right, up, forward) basis
    public Vec3 toLocal(Vec3 world) {
        return new Vec3(world.dot(right()), world.dot(up), world.dot(forward));
    }

    // Inverse of toLocal
    public Vec3 toWorld(Vec3 local) {
        return right().scale(local.x).add(up.scale(local.y)).add(forward.scale(local.z));
    }

}
