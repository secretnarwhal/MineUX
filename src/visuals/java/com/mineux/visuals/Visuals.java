package com.mineux.visuals;

import com.mineux.core.DeskExtensions;
import com.mineux.core.Extensions;
import com.mineux.core.MineUX;
import com.mineux.visuals.hud.MenuBackdrop;
import com.mineux.visuals.self.SkinBrowse;

/**
 * The full edition's visual modules: the HUD skin and theme, the world and sky, the camera and
 * hand, and your own player and cosmetics. Every one of them only changes what this client
 * draws; nothing here touches an input the server sees, a packet, or anyone else's game.
 * <p>
 * {@link #install} is called once by MineUX's entry point when the jar is the full one: it adds
 * the four tray groups, the module file, the per-workstation configs and the menu scene (the
 * home desk's backdrop) to core. The visuals'
 * mixins ({@code mineux.visuals.mixins.json}, listed by the full jar only) call in through
 * {@link VisualBridge}, which does nothing until {@link #on}.
 */
public final class Visuals {

    public static final String MODULE_FILE = "/mineux/visuals.txt";

    public static final VisualGroup HUD = new VisualGroup("hud", "minecraft:compass",
        "your screen's overlay: crosshair, hotbar, scoreboard, chat, the main menu and the theme",
        VisualRows.HUD, "crosshair", "hotbar", "scoreboard", "chat", "menu");
    public static final VisualGroup WORLD = new VisualGroup("world", "minecraft:sunflower",
        "the world on your screen: time, sky, fog, particles, pops, hit effects, shaders",
        VisualRows.WORLD, "world", "fx");
    public static final VisualGroup CAMERA = new VisualGroup("camera", "minecraft:spyglass",
        "your camera and your hand: zoom, look around, smoothing, blur, shake, fov, the item you hold",
        VisualRows.CAMERA, "view", "camera", "hand");
    public static final VisualGroup SELF = new VisualGroup("self", "minecraft:armor_stand",
        "your own player as you see it: body, walk, model, skin, armour, cosmetics, the cursor",
        VisualRows.SELF, "self", "cosmetics", "trail", "cursor");

    private static volatile boolean installed;

    private Visuals() {}

    public static void install() {
        if (installed) return;
        Extensions.addTrayGroup(HUD);
        Extensions.addTrayGroup(WORLD);
        Extensions.addTrayGroup(CAMERA);
        Extensions.addTrayGroup(SELF);
        Extensions.addModuleFile(MODULE_FILE);
        VisualConfigs.register();
        DeskExtensions.addBackdrop(new MenuBackdrop());
        // The skin changer's window: its browse button (act=skinbrowse) and how that went (stat=skin).
        DeskExtensions.addAct("skinbrowse", "browse files...", "choose a skin png in your file manager; it is copied into config/mineux/skins",
            SkinBrowse::open);
        DeskExtensions.addStat("skin", SkinBrowse::stat);
        installed = true;
        MineUX.LOGGER.info("MineUX visuals up: {} configs from {}", VisualConfigs.all().size(), MODULE_FILE);
    }

    /** Whether the visuals are running: installed, on a host that is up. */
    public static boolean on() {
        return installed && MineUX.ready();
    }
}
