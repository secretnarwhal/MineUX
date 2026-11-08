package com.mineux.visuals.view;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Auto f5: third person while a container is open -- a chest, your inventory, a furnace -- so you
 * see yourself and what is around you, and back to the perspective you had once it closes. A
 * perspective you were already in, third person front or back, is left as it is.
 */
public final class AutoF5 {

    private static boolean forced;
    private static CameraType previous;

    private AutoF5() {}

    /** Once a tick. */
    public static void onClientTick(Minecraft mc) {
        boolean container = CameraConfig.get().autoF5 && mc.player != null
            && AbstractContainerScreen.class.isInstance(mc.gui.screen());
        if (container) {
            if (!forced) {
                previous = mc.options.getCameraType();
                forced = true;
            }
            if (mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            return;
        }
        if (!forced) return;
        forced = false;
        if (previous != null) mc.options.setCameraType(previous);
        previous = null;
    }
}
