package com.mineux.visuals.world;

import net.minecraft.world.phys.Vec3;

/**
 * Totem pop's styles, drawn from {@link Shapes}: each a function of how far through its life the
 * pop is, so it needs nothing kept between frames but when it started.
 */
final class TotemDraw {

    private static final Vec3 X = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 Y = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 Z = new Vec3(0.0, 0.0, 1.0);

    /** This pop's: its feet, its height and size, its colours, its opacity, its seed. */
    private static Vec3 base = Vec3.ZERO;
    private static float height;
    private static float scale;
    private static int c1;
    private static int c2;
    private static float op;
    private static float seed;

    private TotemDraw() {}

    static void draw(int style, Vec3 feet, float tall, float age, float life, float popSeed, float size, int first,
                     int second, float opacity) {
        if (age < 0.0F || age > life) return;
        base = feet;
        height = tall;
        scale = size;
        c1 = first;
        c2 = second;
        op = opacity;
        seed = popSeed;
        float n = age / life;
        if (style == 1) shockwave(n);
        else if (style == 2) halo(age, n);
        else if (style == 3) runes(age, n);
        else if (style == 4) supernova(n);
        else if (style == 5) spiral(age, n);
        else ascension(age, n);
    }

    private static Vec3 at(double dx, double dy, double dz) {
        return base.add(dx, dy, dz);
    }

    /** Rings rising up round them and out of the top, sparks going up with them. */
    private static void ascension(float age, float n) {
        float fade = 1.0F - Shapes.smooth(0.7F, 1.0F, n);
        for (int i = 0; i < 3; i++) {
            float k = Shapes.clamp01(n * 1.6F - i * 0.2F);
            if (k <= 0.0F || k >= 1.0F) continue;
            double y = k * (height + 0.6F) * scale;
            double r = (0.55F - 0.25F * k) * scale;
            float alpha = op * (1.0F - k) * Shapes.smooth(0.0F, 0.1F, k);
            Shapes.ring(at(0.0, y, 0.0), r, X, Z, age * 2.0, 32, i % 2 == 0 ? c1 : c2, alpha, 2.0F, 1.0F);
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0 + seed;
            double r = 0.45 * scale;
            float rise = Shapes.clamp01(n * 1.3F) * (height + 0.4F) * scale;
            Vec3 foot = at(Math.cos(a) * r, 0.0, Math.sin(a) * r);
            Shapes.glowLine(foot, foot.add(0.0, rise, 0.0), c1, op * 0.35F * fade * (1.0F - n), 1.0F, 1.0F);
        }
        sparks(age, n, 14, op * fade);
    }

    /** Rings running out along the ground, one after another, and a flash round the body. */
    private static void shockwave(float n) {
        for (int i = 0; i < 3; i++) {
            float k = Shapes.clamp01((n - i * 0.15F) / 0.7F);
            if (k <= 0.0F || k >= 1.0F) continue;
            float e = 1.0F - (1.0F - k) * (1.0F - k);
            Shapes.groundRing(at(0.0, 0.05, 0.0), (0.3F + 3.4F * e) * scale, i % 2 == 0 ? c1 : c2, op * (1.0F - k), 2.5F, 1.0F);
        }
        float flash = 1.0F - Shapes.smooth(0.0F, 0.35F, n);
        if (flash > 0.0F) {
            double r = (0.5F + 0.6F * (1.0F - flash)) * scale;
            Vec3 mid = at(0.0, height * 0.5F, 0.0);
            Shapes.ring(mid, r, X, Y, 0.0, 28, c1, op * flash, 2.0F, 1.0F);
            Shapes.ring(mid, r, Z, Y, 0.0, 28, c2, op * flash, 2.0F, 1.0F);
        }
    }

    /** A halo over their head grows, then breaks into shards that fly off and fall. */
    private static void halo(float age, float n) {
        float hy = (height + 0.32F) * scale;
        float grow = Shapes.smooth(0.0F, 0.18F, n);
        float broken = Shapes.smooth(0.42F, 0.55F, n);
        double r = (0.4F + 0.1F * grow + 0.5F * broken) * scale;
        float whole = op * grow * (1.0F - broken);
        Shapes.ring(at(0.0, hy + 0.25F * broken * scale, 0.0), r, X, Z, age * 1.5, 32, c1, whole, 2.5F, 1.2F);
        Shapes.ring(at(0.0, hy, 0.0), r * 0.7, X, Z, 0.0 - age * 2.0, 24, c2, whole * 0.6F, 1.5F, 1.0F);
        if (broken <= 0.0F) return;
        float t = Math.max(0.0F, n - 0.42F) * 1.7F;
        float fade = op * (1.0F - Shapes.smooth(0.7F, 1.0F, n));
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2.0 / 16.0 + ItemBody.hash(seed + i) * 0.3;
            double speed = (0.6 + 0.9 * ItemBody.hash(seed * 3.1F + i)) * scale;
            double out = r * 0.9 + speed * t * 2.0;
            double y = hy + 0.25 * scale + (0.8 * ItemBody.hash(seed + i * 1.3F) - 4.5 * t) * t * scale;
            Vec3 shard = at(Math.cos(a) * out, y, Math.sin(a) * out);
            Vec3 tail = shard.add(Math.cos(a + 0.4) * 0.12 * scale, 0.05 * scale, Math.sin(a + 0.4) * 0.12 * scale);
            Shapes.glowLine(tail, shard, i % 2 == 0 ? c1 : c2, fade, 2.0F, 1.0F);
        }
    }

    /** A circle of runes on the ground under them, turning, the star in it drawn and fading out. */
    private static void runes(float age, float n) {
        float in = Shapes.smooth(0.0F, 0.15F, n);
        float out = 1.0F - Shapes.smooth(0.75F, 1.0F, n);
        float alpha = op * in * out;
        double r = 1.3 * scale * (0.6 + 0.4 * in);
        double turn = age * 0.8;
        Vec3 centre = at(0.0, 0.04, 0.0);
        Shapes.ring(centre, r, X, Z, turn, 48, c1, alpha, 2.0F, 1.0F);
        Shapes.ring(centre, r * 0.82, X, Z, 0.0 - turn, 40, c2, alpha * 0.8F, 1.5F, 1.0F);
        // An eight-pointed star, each point joined to the one three on.
        float drawn = Shapes.smooth(0.05F, 0.45F, n);
        for (int i = 0; i < 8; i++) {
            if (i >= drawn * 8.0F) break;
            double a0 = turn + i * Math.PI / 4.0;
            double a1 = turn + (i + 3) * Math.PI / 4.0;
            Vec3 p = centre.add(Math.cos(a0) * r * 0.8, 0.0, Math.sin(a0) * r * 0.8);
            Vec3 q = centre.add(Math.cos(a1) * r * 0.8, 0.0, Math.sin(a1) * r * 0.8);
            Shapes.glowLine(p, q, c1, alpha, 1.5F, 1.0F);
            // A rune mark rising off each point.
            double lift = Shapes.smooth(0.2F, 0.8F, n) * 0.6 * scale;
            Shapes.glowLine(p, p.add(0.0, lift, 0.0), c2, alpha * 0.7F, 1.5F, 1.0F);
        }
    }

    /** A flash in their chest, rays bursting out of it, and a shell running out round them. */
    private static void supernova(float n) {
        Vec3 chest = at(0.0, height * 0.6F, 0.0);
        float e = 1.0F - (1.0F - n) * (1.0F - n);
        float fade = 1.0F - n;
        for (int i = 0; i < 24; i++) {
            double yaw = ItemBody.hash(seed + i * 2.7F) * Math.PI * 2.0;
            double pitch = (ItemBody.hash(seed * 1.9F + i) - 0.5) * Math.PI * 0.9;
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            double reach = (0.3 + 2.6 * e) * scale;
            Vec3 tip = chest.add(dir.scale(reach));
            Vec3 tail = chest.add(dir.scale(reach * Math.max(0.0, 1.0 - 0.6 * (1.0 - n))));
            Shapes.glowLine(tail, tip, i % 3 == 0 ? c2 : c1, op * fade, 2.0F, 1.2F);
        }
        double shell = (0.2 + 1.9 * e) * scale;
        Shapes.ring(chest, shell, X, Y, 0.0, 32, c1, op * fade * 0.8F, 1.5F, 1.0F);
        Shapes.ring(chest, shell, Z, Y, 0.0, 32, c2, op * fade * 0.8F, 1.5F, 1.0F);
        Shapes.ring(chest, shell, X, Z, 0.0, 32, c1, op * fade * 0.8F, 1.5F, 1.0F);
        float core = 1.0F - Shapes.smooth(0.0F, 0.3F, n);
        Shapes.point(chest, World.argb(World.mix(c1, 0xFFFFFF, 0.7F), op * core), 40.0F * core * scale);
    }

    /** Two strands winding up round them, the newest bit of each the brightest. */
    private static void spiral(float age, float n) {
        float fade = op * (1.0F - Shapes.smooth(0.75F, 1.0F, n));
        float head = Shapes.clamp01(n * 1.4F);
        double r = 0.6 * scale;
        double top = (height + 0.5F) * scale;
        for (int strand = 0; strand < 2; strand++) {
            Vec3 last = null;
            int steps = 28;
            for (int i = 0; i <= steps; i++) {
                float k = i / (float) steps * head;
                double a = strand * Math.PI + k * Math.PI * 5.0 + age * 3.0;
                double rr = r * (1.0 - 0.35 * k);
                Vec3 p = at(Math.cos(a) * rr, k * top, Math.sin(a) * rr);
                if (last != null) {
                    float bright = 0.25F + 0.75F * (i / (float) steps);
                    Shapes.glowLine(last, p, strand == 0 ? c1 : c2, fade * bright, 2.0F, 1.0F);
                }
                last = p;
            }
        }
        sparks(age, n, 8, fade);
    }

    /** A few sparks thrown up and out, falling back as they go. */
    private static void sparks(float age, float n, int count, float alpha) {
        for (int i = 0; i < count; i++) {
            float h = ItemBody.hash(seed + i * 1.37F);
            float h2 = ItemBody.hash(seed * 2.1F + i * 0.71F);
            float start = h * 0.35F;
            float t = age - start;
            if (t <= 0.0F) continue;
            double a = h2 * Math.PI * 2.0;
            double out = (0.3 + 0.7 * h) * t * scale;
            double y = (0.4 + height * 0.5 + (2.2 + 1.5 * h2) * t - 2.0 * t * t) * scale;
            if (y < 0.0) continue;
            Vec3 p = at(Math.cos(a) * out, y, Math.sin(a) * out);
            float life = Shapes.clamp01(1.0F - t / 1.2F);
            Shapes.point(p, World.argb(World.mix(i % 2 == 0 ? c1 : c2, 0xFFFFFF, 0.4F), alpha * life), 4.0F * scale);
        }
    }
}
