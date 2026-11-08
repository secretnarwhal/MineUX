package com.mineux.core.ui.term;

import com.mineux.core.net.MuxApi;
import com.mineux.core.greeter.AsciiFire;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;

import static com.mineux.core.ui.term.Term.*;

/**
 * What {@code neofetch} prints -- a campfire burning in text ({@link AsciiFire}) and the system
 * lines beside it -- for the terminal to print and for the main menu to draw beside its art.
 */
final class Fetch {

    /** The logo's column: the width the campfire burns in, left of the lines. */
    static final int LOGO_W = 78;
    /** The logs the campfire sits on. */
    private static final String LOGS = "=/\\/\\/\\=";
    private static final int LOG_COLOR = 0xFF8B5A2B;

    /** One fire per size it is drawn at, so two drawn at once do not keep starting each other over. */
    private static final Map<Integer, AsciiFire> FIRES = new HashMap<>();

    /** The swatch under the lines, as neofetch ends with. */
    static final int[] COLORS = {RED, AMBER, GOLD, GREEN, CYAN, BLUE, MAGENTA};

    private Fetch() {}

    /**
     * The lines, as {key, value}: a blank value is a heading (the first, {@code user@host}) or
     * the rule under it.
     */
    static String[][] rows(String user, MenuButtons menu) {
        Minecraft mc = Minecraft.getInstance();
        long minutes = (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) / 60000;
        int on = 0;
        List<Node> toggles = Modules.toggleable();
        for (Node n : toggles) if (PresenceFacts.running(n)) on++;
        String account = MuxApi.signedIn() ? MuxApi.user() : "guest";

        return new String[][] {
            {user + "@" + Shell.brand(), ""},
            {"-----------------", ""},
            {"os", os()},
            {"host", "minecraft " + (mc == null ? "?" : safe(mc::getLaunchedVersion))},
            {"kernel", "fabric " + FabricLoader.getInstance().getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?")},
            {"shell", com.mineux.core.MineUX.shellName()},
            {"wm", menu != null ? "hypr (" + menu.where() + ")" : "workstation " + Workstations.current() + " (" + Workstations.names().size() + ")"},
            {"account", mc == null ? "?" : safe(() -> mc.getUser().getName())},
            {"mineux", account},
            {"modules", on + "/" + toggles.size() + " on"},
            {"mods", String.valueOf(FabricLoader.getInstance().getAllMods().size())},
            {"uptime", minutes + " min"},
        };
    }

    /** The os line: the host's ({@link com.mineux.core.Host#os}), MineUX and its version by default. */
    static String os() {
        if (com.mineux.core.MineUX.ready()) {
            try {
                return com.mineux.core.MineUX.host().os();
            } catch (RuntimeException | LinkageError e) {
                return "MineUX";
            }
        }
        String v = version();
        return v.isEmpty() ? "MineUX" : "MineUX " + v;
    }

    /** MineUX's version, as Fabric has it; blank where MineUX is not its own mod. */
    static String version() {
        try {
            return FabricLoader.getInstance().getModContainer("mineux")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    private static String safe(Supplier<String> s) {
        try {
            String v = s.get();
            return v == null ? "?" : v;
        } catch (RuntimeException e) {
            return "?";
        }
    }

    // ---- drawn ----------------------------------------------------------

    static final int LINE_H = 10;
    static final int INFO_X = 84;
    /** How wide the drawn fetch is, logo to the end of a long value. */
    static final int WIDTH = INFO_X + 46 + 150;

    static int height() {
        return 14 * LINE_H;
    }

    /**
     * The campfire, burning in a {@code w} x {@code h} box, on its logs along the bottom. The
     * fire is drawn in cells a little shorter than a line, so it reads as flame and not text.
     */
    static void campfire(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, List<Hit> hits, TermWindow win) {
        int cols = Math.max(6, w / 6);
        int rows = Math.max(5, (h - 8) / 8);
        int key = cols * 1000 + rows;
        AsciiFire fire = FIRES.computeIfAbsent(key, k -> new AsciiFire(AsciiFire.CAMPFIRE, "classic", 6));
        fire.draw(g, font, x, y + h - 8 - rows * 8, cols, rows, 6, 8, 1f);
        g.text(font, LOGS, x + (cols * 6 - font.width(LOGS)) / 2, y + h - 9, LOG_COLOR, false);

        // A click on it stokes it: the span carries which fire.
        if (hits != null) {
            Span stoke = new Span(String.valueOf(key), FG);
            stoke.hint = "click to stoke the fire";
            hits.add(new Hit(x, y, x + cols * 6, y + h, win, stoke, -1, null, ACT_STOKE));
        }
    }

    /** Stokes the campfire a click landed on, by the key its hit carries. */
    static void stoke(String key) {
        try {
            AsciiFire fire = FIRES.get(Integer.parseInt(key));
            if (fire != null) fire.stoke();
        } catch (NumberFormatException ignored) {
        }
    }

    /**
     * The graphic, with its top left at {@code x, y}. Nothing on it answers a click.
     *
     * @param shown how many lines are showing, for the boot sequence to type it in; -1 for all
     */
    static void draw(GuiGraphicsExtractor g, Font font, int x, int y, String[][] rows, int shown, List<Hit> hits, TermWindow win) {
        int count = rows.length + 1;
        if (shown < 0) shown = count;

        // The campfire burns in the lower part of the logo column, level with the swatch.
        if (shown > 0) campfire(g, font, x, y + 2 * LINE_H, LOGO_W, rows.length * LINE_H - 2 * LINE_H + 12, hits, win);

        for (int i = 0; i < Math.min(shown, rows.length); i++) {
            int ly = y + i * LINE_H;
            String[] row = rows[i];
            if (row[1].isEmpty()) {
                g.text(font, row[0], x + INFO_X, ly, i == 0 ? GREEN : FAINT, true);
            } else {
                g.text(font, row[0], x + INFO_X, ly, GOLD, true);
                g.text(font, row[1], x + INFO_X + 46, ly, FG, true);
            }
        }

        if (shown >= count) {
            int sy = y + rows.length * LINE_H + 4;
            for (int i = 0; i < COLORS.length; i++) {
                int sx = x + INFO_X + i * 14;
                g.fill(sx, sy, sx + 12, sy + 8, COLORS[i]);
            }
        }
    }
}
