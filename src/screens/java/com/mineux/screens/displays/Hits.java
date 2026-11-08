package com.mineux.screens.displays;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Ray tests against a captured screen, flat or curved. */
final class Hits {

    private Hits() {}

    static CapturedScreenDisplay.@Nullable CaptureHit intersect(CapturedScreenDisplay screen, Vec3 pos, Vec3 dir) {
        if (!screen.framebuffer.isValid()) return null;
        int width = screen.framebuffer.width();
        int height = screen.framebuffer.height();
        if (width <= 0 || height <= 0) return null;
        double arc = screen.arcAngle();
        if (arc >= Surface.MIN_ARC) return curved(screen, pos, dir, arc);
        ScreenPlacement.Hit hit = screen.placement.raycast(pos, dir);
        if (hit == null) return null;
        double x = (hit.across() / screen.worldWidth() + 0.5) * width;
        double y = (0.5 - hit.upward() / screen.worldHeight()) * height;
        if (x < 0 || y < 0 || x > width || y > height) return null;
        return new CapturedScreenDisplay.CaptureHit(screen, hit.point(), x, y, hit.front() ? hit.distance() : 0.0 - hit.distance());
    }

    /** Ray-cylinder intersection, limited to the section of the cylinder the screen covers. */
    private static CapturedScreenDisplay.@Nullable CaptureHit curved(CapturedScreenDisplay screen, Vec3 pos, Vec3 dir, double arc) {
        int width = screen.framebuffer.width();
        int height = screen.framebuffer.height();
        ScreenPlacement placement = screen.placement;
        Vec3 right = placement.right();
        Vec3 facing = placement.facing();
        double radius = screen.worldWidth() / arc;
        // The cylinder axis runs along up. Work in the plane orthogonal to it, the axis as origin:
        // x along right, z along facing.
        Vec3 rel = pos.subtract(placement.center().add(facing.scale(radius)));
        double ox = rel.dot(right);
        double oz = rel.dot(facing);
        double dx = dir.dot(right);
        double dz = dir.dot(facing);
        double a = dx * dx + dz * dz;
        if (a < 1.0e-9) return null;
        double b = 2.0 * (ox * dx + oz * dz);
        double c = ox * ox + oz * oz - radius * radius;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0) return null;
        double root = Math.sqrt(discriminant);
        double first = (0.0 - b - root) / (2.0 * a);
        double second = (0.0 - b + root) / (2.0 * a);
        for (int i = 0; i < 2; i++) {
            double t = i == 0 ? first : second;
            if (t < 0) continue;
            double px = ox + dx * t;
            double pz = oz + dz * t;
            double angle = Math.atan2(px, 0.0 - pz);
            if (Math.abs(angle) > arc / 2) continue;
            Vec3 hit = pos.add(dir.scale(t));
            double x = (angle / arc + 0.5) * width;
            double y = (0.5 - hit.subtract(placement.center()).dot(placement.up()) / screen.worldHeight()) * height;
            if (y < 0 || y > height) continue;
            // The image is on the inside of the cylinder, which a ray hits moving away from the axis.
            boolean front = px * dx + pz * dz > 0;
            return new CapturedScreenDisplay.CaptureHit(screen, hit, x, y, front ? t : 0.0 - t);
        }
        return null;
    }
}
