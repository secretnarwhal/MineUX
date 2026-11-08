package com.mineux.visuals.view;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Clear weather: rain and thunder held at nothing on this client, so a storm neither hides the
 * fight nor darkens the sky. The level's rain is only the strength the server last sent, read by
 * the renderer; it is put back to nothing every tick, after any packet that raised it.
 * <p>
 * Ambience's weather ({@link com.mineux.visuals.world.Ambience#weather}) holds it the same way at
 * the weather you chose instead -- clear, rain, or rain with thunder -- and wins over this
 * switch while it is set.
 * <p>
 * What the server last raised it to is kept, and put back when this is switched off: the server
 * only sends the weather when it changes, so without that the sky would stay clear until it did.
 */
public final class Weather {

    private static boolean held;
    /** The weather held last tick, so a change of choice is not taken for the server's. */
    private static int heldAs;
    private static float rain;
    private static float thunder;
    private static ClientLevel level;

    private Weather() {}

    public static void onClientTick(Minecraft mc) {
        ClientLevel now = mc.level;
        if (now != level) {
            level = now;
            held = false;
            rain = 0.0F;
            thunder = 0.0F;
        }
        if (now == null) return;
        int chosen = com.mineux.visuals.world.Ambience.weather();
        if (chosen == 0 && ViewConfig.get().clearWeather) chosen = 1;
        if (chosen > 0) {
            // What the server sent since the last tick: anything other than what is held here.
            float r = now.getRainLevel(1.0F);
            float t = now.getThunderLevel(1.0F);
            float wantRain = chosen >= 2 ? 1.0F : 0.0F;
            float wantThunder = chosen == 3 ? 1.0F : 0.0F;
            boolean same = held && heldAs == chosen;
            if (!held || (same && r != wantRain)) rain = r;
            if (!held || (same && t != wantThunder)) thunder = t;
            now.setRainLevel(wantRain);
            now.setThunderLevel(wantThunder);
            held = true;
            heldAs = chosen;
        } else if (held) {
            held = false;
            now.setRainLevel(rain);
            now.setThunderLevel(thunder);
        }
    }
}
