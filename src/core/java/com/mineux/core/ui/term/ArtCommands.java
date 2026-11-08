package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Line;
import com.mineux.core.util.Words;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * {@code art}: the word art at the top of every terminal. On its own it shows the next one;
 * {@code art list} opens the art window ({@link ArtPane}), and the rest is what that window's
 * clicks run, so all of it can be typed too.
 * <p>
 * Its own class rather than part of {@link Shell}, to keep that one small.
 */
final class ArtCommands {

    static final String[] SUBS = {"next", "prev", "random", "list", "folder", "reload", "palette", "glitch", "motion"};

    private final Shell shell;

    ArtCommands(Shell shell) {
        this.shell = shell;
    }

    private static final Set<String> W_LIST = Words.of("list ls all panel window gallery");
    private static final Set<String> W_FOLDER = Words.of("folder open dir");
    private static final Set<String> W_RELOAD = Words.of("reload");
    private static final Set<String> W_PALETTE = Words.of("palette colour color colors colours");
    private static final Set<String> W_GLITCH = Words.of("glitch");
    private static final Set<String> W_MOTION = Words.of("motion");

    void run(List<String> args) {
        String sub = args.isEmpty() ? "next" : args.get(0).toLowerCase(Locale.ROOT);
        String value = args.size() > 1 ? args.get(1).toLowerCase(Locale.ROOT) : "";

        if (W_LIST.contains(sub)) {
            if (!shell.openWindow(ArtPane.KIND)) list();
        } else if (W_FOLDER.contains(sub)) {
            openFolder();
        } else if (W_RELOAD.contains(sub)) {
            ArtView.reload();
            shell.ok(ArtView.count() + " arts");
        } else if (W_PALETTE.contains(sub)) {
            palette(value);
        } else if (W_GLITCH.contains(sub)) {
            ArtView.setGlitch(onOff(value, ArtView.glitch()));
            shell.line().t("art glitch ", DIM).t(ArtView.glitch() ? "on" : "off", GOLD);
        } else if (W_MOTION.contains(sub)) {
            ArtView.setMotion(onOff(value, ArtView.motion()));
            shell.line().t("art motion ", DIM).t(ArtView.motion() ? "on" : "off", GOLD);
        } else {
            ArtView.cycle(sub);
            shell.line().t("art ", DIM).t(ArtView.name(), GOLD);
        }
    }

    /** {@code on}, {@code off}, or anything else for the other of what it is now. */
    private static boolean onOff(String value, boolean now) {
        if (ON.contains(value)) return true;
        if (OFF.contains(value)) return false;
        return !now;
    }

    private static final Set<String> ON = Words.of("on 1 true yes");
    private static final Set<String> OFF = Words.of("off 0 false no");

    private void palette(String value) {
        if (value.isEmpty()) {
            Line line = shell.line().t("palette ", DIM);
            for (String p : ArtView.PALETTES) {
                boolean on = Objects.equals(p, ArtView.palette());
                line.run(on ? "[" + p + "]" : p, on ? ArtView.swatch(p) : FG, "art palette " + p, "colour the art " + p).t(" ");
            }
            return;
        }
        if (!ArtView.setPalette(value)) {
            shell.error("art: no palette '" + value + "' - " + String.join(", ", ArtView.PALETTES));
            return;
        }
        shell.line().t("art palette ", DIM).t(value, ArtView.swatch(value));
    }

    /** Where there is no window to open (nowhere, today): every art as a line to click. */
    private void list() {
        int current = ArtView.current();
        for (int i = 0; i < ArtView.count(); i++) {
            String name = ArtView.nameOf(i);
            shell.line().t(i == current ? "> " : "  ", GOLD).run(name, i == current ? GOLD : FG, "art " + (i + 1), "show " + name);
        }
    }

    private void openFolder() {
        Path dir = Arts.folder();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            shell.error("art: could not make " + dir);
            return;
        }
        Util.getPlatform().openPath(dir);
        shell.ok("opened " + dir);
    }
}
