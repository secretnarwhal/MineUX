package com.mineux.visuals.cosmetics;

import com.mineux.visuals.cosmetics.kill.KillEffects;
import com.mineux.visuals.cosmetics.kill.KillParams;
import com.mineux.visuals.cosmetics.kill.KillStyles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Where someone you killed fell: a shockwave, the body shattering, a lightning strike, a black
 * hole, a phoenix or an ascent, laid over the ground round them. The kill itself is fx's
 * ({@code fx/Fx}): a death a moment after your hit.
 */
final class KillShow {

    private static final KillEffects EFFECTS = new KillEffects(8);
    /** Column -> its top block's height, for the frame being drawn. */
    private static final Map<Long, Integer> TOPS = new HashMap<>();
    private static ClientLevel level;

    private KillShow() {}

    static int style(CosmeticsConfig config) {
        return KillStyles.NAMES.indexOf(config.kill);
    }

    static void spawn(Entity dead, CosmeticsConfig config) {
        int style = style(config);
        if (style <= 0) return;
        double reach = 5.0;
        double life = style == KillStyles.SHOCKWAVE ? Math.max(0.1, reach / 8.0) : 2.5;
        long nanos = System.nanoTime();
        Vec3 at = dead.position();
        EFFECTS.spawn(new KillEffects.Active(style, at.x, at.y, at.z, dead.getBbWidth(), dead.getBbHeight(),
            nanos / 1.0E9, life, reach, 64, KillEffects.seedFor(dead.getId(), nanos / 1000000L)));
    }

    static void draw(Minecraft mc, CosmeticsConfig config) {
        if (mc.level != level) {
            level = mc.level;
            EFFECTS.clear();
        }
        double now = System.nanoTime() / 1.0E9;
        EFFECTS.prune(now);
        if (EFFECTS.isEmpty()) return;
        TOPS.clear();
        QuadBatch batch = new QuadBatch();
        double scale = config.scale;
        for (KillEffects.Active a : EFFECTS.active()) {
            Attach.Frame mount = Attach.frame(Attach.FEET, Attach.Subject.standing(a.x(), a.y(), a.z(), 0.0, a.height()));
            KillParams params = a.params(now, new Terrain(mc.level, mount, scale));
            int c1 = Cosmetics.color(config);
            int c2 = Cosmetics.color2(config);
            if (config.natural && KillStyles.natural(a.style(), 0) != 0) {
                c1 = KillStyles.natural(a.style(), 0);
                c2 = KillStyles.natural(a.style(), 1);
            }
            Cosmetic effect = KillStyles.create(a.style(), params);
            if (effect == null) continue;
            Builder b = new Builder(batch, c1, c2, config.opacity, params.age(), true);
            Vec3 cam = mc.gameRenderer.mainCamera().position();
            b.view = new double[] {cam.x, cam.y, cam.z};
            b.frame(mount, scale);
            effect.build(b);
        }
        batch.submit(false);
    }

    /** The ground round a body: the top of each column, where it is within two blocks of the feet. */
    private static final class Terrain extends KillParams.Ground {
        private final ClientLevel level;
        private final Attach.Frame mount;
        private final double scale;

        Terrain(ClientLevel level, Attach.Frame mount, double scale) {
            this.level = level;
            this.mount = mount;
            this.scale = scale;
        }

        @Override
        public double at(double u, double w) {
            double[] p = mount.map(u * scale, 0.0, w * scale);
            int bx = (int) Math.floor(p[0]);
            int bz = (int) Math.floor(p[2]);
            long key = (long) bx << 32 ^ bz & 0xFFFFFFFFL;
            Integer top = TOPS.get(key);
            if (top == null) {
                top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                TOPS.put(key, top);
            }
            double feet = mount.oy;
            double y = Math.abs(top.intValue() - feet) > 2.0 ? feet : top.intValue();
            return (y - feet) / scale;
        }
    }
}
