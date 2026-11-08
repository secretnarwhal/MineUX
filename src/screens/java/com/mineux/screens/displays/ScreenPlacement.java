package com.mineux.screens.displays;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/* Where a screen is in the world and which way it is turned
 *
 * Positions on the screen are measured in blocks from its center: `across` towards the right edge and
 * `upward` towards the top edge, as seen by someone looking at the front.
 */
public final class ScreenPlacement {

    private Vec3 center = Vec3.ZERO;

    // Unit vectors: out of the front of the screen, and towards its top edge
    private Vec3 facing = new Vec3(0, 0, 1);
    private Vec3 up = new Vec3(0, 1, 0);

    public Vec3 center() {
        return center;
    }

    public Vec3 facing() {
        return facing;
    }

    public Vec3 up() {
        return up;
    }

    public Vec3 right() {
        return up.cross(facing);
    }

    public void moveTo(Vec3 center) {
        this.center = center;
    }

    // Turn the screen. `up` only has to point roughly upwards, it is straightened against `facing`.
    public void orient(Vec3 facing, Vec3 up) {
        Vec3 front = facing.normalize();
        Vec3 top = up.subtract(front.scale(up.dot(front)));
        if (top.lengthSqr() < 1.0e-12) return;

        this.facing = front;
        this.up = top.normalize();
    }

    // Put the screen `distance` blocks along `look` from `eye`, turned to face back at the eye
    public void faceViewer(Vec3 eye, Vec3 look, Vec3 viewUp, double distance) {
        moveTo(eye.add(look.scale(distance)));
        orient(look.reverse(), viewUp);
    }

    public Vec3 point(double across, double upward) {
        return center.add(right().scale(across)).add(up.scale(upward));
    }

    /* Where a ray meets the plane the screen lies in, null when it runs parallel to it or points away
     * `dir` has to be normalized. The hit is not limited to the area the screen covers.
     */
    public @Nullable Hit raycast(Vec3 from, Vec3 dir) {
        double approach = dir.dot(facing);
        if (Math.abs(approach) < 1.0e-9) return null;

        double distance = center.subtract(from).dot(facing) / approach;
        if (distance < 0) return null;

        Vec3 point = from.add(dir.scale(distance));
        Vec3 offset = point.subtract(center);

        return new Hit(point, offset.dot(right()), offset.dot(up), distance, approach < 0);
    }

    // `front` is false when the ray reached the plane from behind the screen
    public static record Hit(Vec3 point, double across, double upward, double distance, boolean front) {
    }

}
