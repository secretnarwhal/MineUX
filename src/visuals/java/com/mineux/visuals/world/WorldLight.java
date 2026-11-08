package com.mineux.visuals.world;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The light the world shader lights by this frame, from the sun's angle: where the light comes
 * from (the sun by day, the moon by night), its colour reddening toward dusk, how strong it is,
 * how far into the day it is, the shade's and the fog's colours, and where the sun is on screen.
 */
final class WorldLight {

    private static final float[] DUSK = {1.0F, 0.52F, 0.25F};
    private static final float[] MOON = {0.58F, 0.68F, 1.0F};

    float x;
    float y;
    float z;
    boolean sunUp;
    float day;
    float strength;
    final float[] colour = new float[3];
    final float[] shade = new float[3];
    final float[] fog = new float[3];
    /** The sun on screen, 0 to 1 across and up; how much of it is on screen. */
    float sx = 0.5F;
    float sy = 0.5F;
    float onScreen;

    private WorldLight() {}

    static WorldLight of(ShaderConfig config, int preset, float sunAngle, Matrix4f viewProj) {
        WorldLight light = new WorldLight();
        float sunX = (float) (0.0 - Math.sin(sunAngle));
        float sunY = (float) Math.cos(sunAngle);
        light.sunUp = sunY > 0.0F - 0.06F;
        light.x = light.sunUp ? sunX : 0.0F - sunX;
        light.y = light.sunUp ? sunY : 0.0F - sunY;
        light.z = 0.0F;
        light.day = Shapes.smooth(0.0F - 0.12F, 0.22F, sunY);
        light.strength = light.sunUp ? Shapes.smooth(0.0F - 0.06F, 0.25F, sunY) : 0.38F * Shapes.smooth(0.0F - 0.06F, 0.25F, 0.0F - sunY);

        int[] colours = WorldLook.colours(preset);
        int sunRgb = config.worldNatural ? colours[0] : World.rgb(config.sunColor, colours[0]);
        int fogRgb = config.worldNatural ? colours[1] : World.rgb(config.fogColor, colours[1]);
        float[] sun = SkyLook.rgb(sunRgb);
        float[] shadeRgb = SkyLook.rgb(colours[2]);
        float[] fogBase = SkyLook.rgb(fogRgb);
        float dusk = light.sunUp ? 1.0F - Shapes.smooth(0.0F, 0.42F, sunY) : 0.0F;
        for (int i = 0; i < 3; i++) {
            float c = sun[i] + (DUSK[i] - sun[i]) * dusk * 0.7F;
            light.colour[i] = light.sunUp ? c : MOON[i] * 0.55F;
            light.shade[i] = shadeRgb[i] * (0.45F + 0.55F * light.day) + 0.06F;
            light.fog[i] = fogBase[i] * (0.18F + 0.82F * light.day) + (light.sunUp ? DUSK[i] * dusk * 0.25F : 0.0F);
        }

        Vector4f at = new Vector4f(light.x * 500.0F, light.y * 500.0F, light.z * 500.0F, 1.0F);
        viewProj.transform(at);
        if (at.w > 0.01F) {
            float nx = at.x / at.w;
            float ny = at.y / at.w;
            light.sx = nx * 0.5F + 0.5F;
            light.sy = ny * 0.5F + 0.5F;
            light.onScreen = 1.0F - Shapes.smooth(0.7F, 1.6F, Math.max(Math.abs(nx), Math.abs(ny)));
        }
        return light;
    }
}
