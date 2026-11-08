package com.mineux.core.ui.term;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The singleplayer worlds, for the main menu's worlds window and the {@code play} command:
 * listed the way vanilla's world list reads them, opened through vanilla's own flow (so its
 * backup and upgrade prompts still come up), and made with vanilla's create-world screen.
 */
final class Worlds {

    private static volatile List<LevelSummary> list = List.of();
    private static volatile boolean loading;
    private static volatile String error;

    private Worlds() {}

    static List<LevelSummary> list() {
        return list;
    }

    static boolean loading() {
        return loading;
    }

    static String error() {
        return error;
    }

    /** Reads the saves folder again, in the background. */
    static void refresh() {
        if (loading) return;
        loading = true;
        error = null;
        try {
            LevelStorageSource source = Minecraft.getInstance().getLevelSource();
            source.loadLevelSummaries(source.findLevelCandidates()).whenComplete((summaries, failure) -> {
                if (failure != null) {
                    error = "could not read the saves folder";
                } else {
                    List<LevelSummary> sorted = new ArrayList<>(summaries);
                    // LevelSummary sorts most recently played first, as vanilla lists them.
                    sorted.sort(null);
                    list = sorted;
                }
                loading = false;
            });
        } catch (Exception e) {
            error = "could not read the saves folder";
            loading = false;
        }
    }

    /** By its number in the list, its name, or its folder. */
    static LevelSummary find(String which) {
        List<LevelSummary> all = list;
        try {
            int index = Integer.parseInt(which) - 1;
            if (index >= 0 && index < all.size()) return all.get(index);
        } catch (NumberFormatException ignored) {
        }
        for (LevelSummary s : all) {
            if (s.getLevelName().equalsIgnoreCase(which) || s.getLevelId().equalsIgnoreCase(which)) return s;
        }
        String lower = which.toLowerCase(Locale.ROOT);
        LevelSummary prefixed = null;
        for (LevelSummary s : all) {
            if (s.getLevelName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                if (prefixed != null) return null;
                prefixed = s;
            }
        }
        return prefixed;
    }

    /**
     * Opens a world, backing out to the title screen if it is cancelled.
     *
     * @return why it cannot be opened from here, or null once it is opening
     */
    static String play(LevelSummary world, Screen title) {
        if (world.isLocked()) return world.getLevelName() + " is open in another game";
        if (world.isDisabled() || world.requiresManualConversion()) {
            return world.getLevelName() + " needs vanilla's world list to open (vanilla singleplayer)";
        }
        Minecraft mc = Minecraft.getInstance();
        mc.createWorldOpenFlows().openWorld(world.getLevelId(), () -> mc.gui.setScreen(title));
        return null;
    }

    /** Vanilla's create-world screen, coming back to the title screen when closed. */
    static void create(Screen title) {
        Minecraft mc = Minecraft.getInstance();
        CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(title));
    }

    /** Survival, creative... and hardcore, for the list. */
    static String mode(LevelSummary world) {
        if (world.isHardcore()) return "hardcore";
        return world.getGameMode().getName();
    }
}
