package com.mineux.visuals.world;

import com.mineux.visuals.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Totem pop: something to see when someone pops a totem -- rings rising round them, a
 * shockwave, a halo that breaks, a rune circle, a burst of rays or a spiral -- following them
 * as they move, for as long as the style lasts. Yours and everyone else's, either or both; the
 * game's own cloud of totem particles can be left out.
 * <p>
 * A pop is what the visuals' entity-event mixin hands over: the entity event the server plays for
 * a totem saving a player. Your own is drawn fainter in first person, so it does not hide the
 * fight.
 */
public final class TotemPop {

    private static final List<String> STYLES = Arrays.asList("ascension", "shockwave", "halo", "runes", "supernova", "spiral");
    private static final List<String> COLOR_MODES = Arrays.asList("totem", "theme", "custom");
    /** Each style's length, seconds. */
    private static final float[] LIFE = {1.9F, 1.3F, 1.7F, 2.2F, 1.4F, 2.0F};
    private static final int MAX = 24;

    private static final List<Pop> POPS = new ArrayList<>();

    private TotemPop() {}

    static int styleIndex(String name) {
        return STYLES.indexOf(name);
    }

    static int colorModeIndex(String name) {
        return COLOR_MODES.indexOf(name);
    }

    private static boolean on() {
        return World.allowed() && TotemConfig.get().enabled;
    }

    static boolean hidesVanilla() {
        return on() && TotemConfig.get().hideVanilla;
    }

    /** From {@code VisualBridge}'s entity events: {@code entity} has just popped a totem. */
    public static void onPop(Entity entity) {
        if (!on() || entity == null || World.failed(World.TOTEMS)) return;
        Minecraft mc = Minecraft.getInstance();
        TotemConfig config = TotemConfig.get();
        boolean self = entity == mc.player;
        if (self ? !config.self : !config.others) return;
        if (POPS.size() >= MAX) POPS.remove(0);
        POPS.add(new Pop(entity, System.nanoTime(), mc.level.getRandom().nextFloat() * 100.0F, self));
    }

    static void draw(Minecraft mc, float partialTick) {
        if (POPS.isEmpty()) return;
        if (!on()) {
            POPS.clear();
            return;
        }
        TotemConfig config = TotemConfig.get();
        int style = Math.max(0, styleIndex(config.style));
        float stretch = (float) config.duration;
        float life = LIFE[style] * stretch;
        long now = System.nanoTime();
        POPS.removeIf(pop -> (now - pop.start) / 1.0E9F > life || pop.who.level() != mc.level);
        if (POPS.isEmpty()) return;
        int[] colours = colours(config, now);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        // Never through walls: a pop behind a wall would show where its player is.
        Shapes.walls(false);
        for (Pop pop : POPS) {
            Entity who = pop.who;
            if (who.isAlive()) {
                pop.x = Mth.lerp(partialTick, who.xOld, who.getX());
                pop.y = Mth.lerp(partialTick, who.yOld, who.getY());
                pop.z = Mth.lerp(partialTick, who.zOld, who.getZ());
            }
            float age = (now - pop.start) / 1.0E9F / stretch;
            float opacity = (float) config.opacity * (pop.self && firstPerson ? 0.45F : 1.0F);
            TotemDraw.draw(style, new Vec3(pop.x, pop.y, pop.z), who.getBbHeight(), age, LIFE[style], pop.seed,
                (float) config.size, colours[0], colours[1], opacity);
        }
    }

    private static int[] colours(TotemConfig config, long now) {
        int mode = colorModeIndex(config.colorMode);
        if (mode == 1) return new int[] {Theme.GOLD & 0xFFFFFF, Theme.GREEN & 0xFFFFFF};
        if (mode == 2) return new int[] {World.rgb(config.color, 0xFFD34D), World.rgb(config.color2, 0x6EE06A)};
        return new int[] {0xFFD34D, 0x6EE06A};
    }

    /** One pop, and where its popper was last seen. */
    private static final class Pop {
        final Entity who;
        final long start;
        final float seed;
        final boolean self;
        double x;
        double y;
        double z;

        Pop(Entity who, long start, float seed, boolean self) {
            this.who = who;
            this.start = start;
            this.seed = seed;
            this.self = self;
            this.x = who.getX();
            this.y = who.getY();
            this.z = who.getZ();
        }
    }
}
