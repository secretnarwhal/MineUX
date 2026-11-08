package com.mineux.screens;

import com.mineux.core.Extensions;
import com.mineux.core.TrayGroup;
import com.mineux.screens.capture.CaptureManager;
import com.mineux.screens.net.Sharing;
import com.mineux.screens.shell.ScreensCommand;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Screens in MineUX: your monitors and windows as screens in the world, shared live with the
 * other MineUX players on your server when you choose. {@link #install} registers it with core
 * once, before the desktop first opens; MineUX's Fabric entry calls it, and a host mod that builds
 * Screens into itself can too. The game reaches it through {@link ScreensHooks}.
 * <p>
 * Registrations: tray group {@code screens}, module file {@code /mineux/screens.txt}, config
 * {@code cfg=screens} ({@code screens.json}, per workstation), muxsh command {@code screens}.
 */
public final class Screens {

    public static final Logger LOGGER = LoggerFactory.getLogger("MineUX/screens");
    public static final String CFG = "screens";

    private static Keys keys;
    private static CaptureManager manager;
    private static final Sharing SHARING = new Sharing();
    private static boolean installed;

    private Screens() {}

    public static void install() {
        if (installed) return;
        installed = true;
        keys = new Keys();
        Extensions.addTrayGroup(new Tray());
        Extensions.addModuleFile("/mineux/screens.txt");
        Extensions.addConfig(CFG, "screens.json", ScreensConfig::get, ScreensConfig::commitLive);
        personalKeys();
        Extensions.addCommand(new ScreensCommand());
        if (Platform.get() == Platform.WINDOWS) manager = new CaptureManager();
        else LOGGER.warn("Screens captures monitors and windows through Windows APIs; here it only shows friends' screens");
    }

    /** The keys are the person's hands, not a loadout: one set in every workstation. */
    public static void personalKeys() {
        com.mineux.core.DeskExtensions.addPersonalField("screens.json", "menuKey");
        com.mineux.core.DeskExtensions.addPersonalField("screens.json", "freecamKey");
        com.mineux.core.DeskExtensions.addPersonalField("screens.json", "backKey");
    }

    public static Keys keys() {
        return keys;
    }

    /** Your captures; null off Windows, where nothing can be captured. */
    public static @Nullable CaptureManager manager() {
        return manager;
    }

    public static Sharing sharing() {
        return SHARING;
    }

    /** The tray group: your screens' switch and sharing, a page each in its Glass settings window. */
    public static final class Tray extends TrayGroup {
        private static final List<Row> ROWS = List.of(
            new Row("screens", "screens", "minecraft:black_stained_glass_pane"),
            new Row("screens/share", "friends' screens", "minecraft:ender_eye"));

        @Override
        public String name() {
            return "screens";
        }

        @Override
        public String icon() {
            return "minecraft:black_stained_glass_pane";
        }

        @Override
        public String blurb() {
            return "your monitors and windows as screens in the world, and the ones friends share";
        }

        @Override
        public List<Row> rows() {
            return ROWS;
        }
    }
}
