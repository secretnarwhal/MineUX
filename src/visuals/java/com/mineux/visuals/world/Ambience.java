package com.mineux.visuals.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.List;

/**
 * Ambience: the world's time of day, fog, sky and light as you would have them, on this client
 * only. Each is written over the game's own numbers at the point it still has them as numbers:
 * <ul>
 *   <li>the time of day on the client's clocks, so the sun, the moon, the stars, the sky's
 *       colour and the daylight all move together as they would at that hour -- the day count,
 *       and with it the moon's phase, is the server's;</li>
 *   <li>the fog's colour and distance as the frame's fog is set up;</li>
 *   <li>the sky's colour once it is extracted, faded to a second colour as you look down;</li>
 *   <li>the lightmap's colours, multiplied by a tint, each time it is rebuilt.</li>
 * </ul>
 * The weather is {@link com.mineux.visuals.view.Weather}'s: it already holds the rain at nothing
 * for clear weather, and holds it at full for rain or thunder the same way.
 */
public final class Ambience {

    private static final List<String> WEATHER = Arrays.asList("off", "clear", "rain", "thunder");
    private static final long DAY = 24000L;

    private Ambience() {}

    static int weatherIndex(String name) {
        return WEATHER.indexOf(name);
    }

    private static boolean on() {
        return World.allowed() && WorldConfig.get().ambience;
    }

    /**
     * The weather ambience asks for: 0 for none (the server's, or clear weather's own switch),
     * 1 clear, 2 rain, 3 thunder.
     */
    public static int weather() {
        if (!on()) return 0;
        return Math.max(0, weatherIndex(WorldConfig.get().weather));
    }

    /** A clock's total ticks, moved to the chosen hour of the same day. */
    static Object clock(Object value) {
        if (!Long.class.isInstance(value) || !on()) return value;
        WorldConfig config = WorldConfig.get();
        if (!config.time) return value;
        long ticks = ((Long) value).longValue();
        long day = Math.floorDiv(ticks, DAY);
        return Long.valueOf(day * DAY + config.timeOfDay);
    }

    static void fog(FogData fog) {
        if (!on()) return;
        WorldConfig config = WorldConfig.get();
        if (config.fogColour) {
            int rgb = World.rgb(config.fogColor, 0xC0D8FF);
            fog.color.set(World.red(rgb), World.green(rgb), World.blue(rgb), 1.0F);
        }
        if (config.fogDistance) {
            float end = config.fogChunks * 16.0F;
            // Only ever closer than the game's own environmental fog: water, lava, powder snow,
            // blindness and darkness keep theirs, so this never lets you see further through them.
            fog.environmentalStart = Math.min(fog.environmentalStart, 0.0F);
            fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
            fog.renderDistanceStart = 0.0F;
            fog.renderDistanceEnd = end;
            fog.skyEnd = end;
            fog.cloudEnd = end;
        }
    }

    static void sky(SkyRenderState state) {
        if (!on()) return;
        WorldConfig config = WorldConfig.get();
        if (!config.skyColour) return;
        int top = World.rgb(config.skyColor, 0x78A7FF);
        if (!config.skyGradient) {
            state.skyColor = 0xFF000000 | top;
            return;
        }
        int bottom = World.rgb(config.skyColor2, 0x1A2A6C);
        // Straight ahead is the sky colour; straight down the second one.
        float pitch = Minecraft.getInstance().gameRenderer.mainCamera().xRot();
        float t = Math.max(0.0F, Math.min(1.0F, pitch / 90.0F));
        state.skyColor = 0xFF000000 | World.mix(top, bottom, t);
    }

    /** Called from the lightmap's extract, after fullbright has had its say. */
    public static void lightmap(LightmapRenderState state) {
        if (!on()) return;
        WorldConfig config = WorldConfig.get();
        if (!config.lightmap) return;
        int rgb = World.rgb(config.lightmapColor, 0xFFFFFF);
        if (rgb == 0xFFFFFF) return;
        float r = World.red(rgb);
        float g = World.green(rgb);
        float b = World.blue(rgb);
        // New vectors: the state's are often the extractor's shared WHITE, which must stay white.
        state.blockLightTint = tinted(state.blockLightTint, r, g, b);
        state.skyLightColor = tinted(state.skyLightColor, r, g, b);
        state.ambientColor = tinted(state.ambientColor, r, g, b);
    }

    private static Vector3fc tinted(Vector3fc colour, float r, float g, float b) {
        if (colour == null) return null;
        return new Vector3f(colour.x() * r, colour.y() * g, colour.z() * b);
    }
}
