package com.mineux.screens.fabric;

import com.mineux.screens.Screens;
import net.fabricmc.api.ClientModInitializer;

/**
 * MineUX's Fabric entry for Screens (fabric.mod.json, after MineUXMod): registers it with core.
 * The game reaches it through the mixins beside this class (mineux.screens.mixins.json).
 */
public final class ScreensMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Screens.install();
    }
}
