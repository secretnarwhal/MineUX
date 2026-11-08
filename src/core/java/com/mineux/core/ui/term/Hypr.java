package com.mineux.core.ui.term;

import com.mineux.core.util.Words;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Hyprland's dispatchers, for the desktop: what a {@code bind} line in {@code hyprland.conf}
 * runs, and what {@code hyprctl dispatch} runs from a terminal. Each works on the desk that has
 * the keyboard -- the special workspace while it is down -- and its focused pane.
 */
final class Hypr {

    private static final Set<String> KNOWN = Words.of("exec killactive togglefloating windowmode pin opacity togglesplit cyclenext resizeactive "
        + "movefocus movewindow workspace movetoworkspace movetoworkspacesilent togglespecialworkspace togglegroup "
        + "changegroupactive moveintogroup moveoutofgroup");

    /** Whether a bind can name this dispatcher. */
    static boolean known(String dispatcher) {
        return KNOWN.contains(dispatcher);
    }

    static List<String> names() {
        List<String> out = new ArrayList<>(KNOWN);
        out.sort(null);
        return out;
    }

    private final TerminalScreen screen;

    Hypr(TerminalScreen screen) {
        this.screen = screen;
    }

    /** Runs a dispatcher; null when it ran (or had nothing to do), or why it could not. */
    String dispatch(String dispatcher, String arg) {
        String d = dispatcher.toLowerCase(Locale.ROOT);
        String a = arg == null ? "" : arg.trim();
        if (!known(d)) return "unknown dispatcher '" + dispatcher + "'";
        // Those that need no focused window.
        if (Objects.equals("windowmode", d)) return WindowMode.request(a);
        if (Objects.equals("exec", d)) return exec(a);
        if (Objects.equals("workspace", d)) return workspace(a);
        if (Objects.equals("togglespecialworkspace", d)) return toggleSpecial();
        if (Objects.equals("movetoworkspace", d)) return moveToWorkspace(a, true);
        if (Objects.equals("movetoworkspacesilent", d)) return moveToWorkspace(a, false);
        if (Objects.equals("togglesplit", d)) return toggleSplit();
        if (Objects.equals("cyclenext", d)) return cycleNext();
        if (Objects.equals("movefocus", d)) return moveFocus(a);

        // The rest act on the focused window, and do nothing without one.
        Desk desk = screen.desk();
        Pane f = focused();
        if (f == null) return null;
        if (Objects.equals("killactive", d)) {
            screen.closePane(f);
        } else if (Objects.equals("togglefloating", d)) {
            screen.toggleFloat(f);
        } else if (Objects.equals("pin", d)) {
            return pin(desk, f);
        } else if (Objects.equals("opacity", d)) {
            return opacity(f, a);
        } else if (Objects.equals("movewindow", d)) {
            moveWindow(desk, f, dir(a));
        } else if (Objects.equals("resizeactive", d)) {
            return resize(desk, f, a);
        } else if (Objects.equals("togglegroup", d)) {
            desk.toggleGroup(f);
        } else if (Objects.equals("changegroupactive", d)) {
            if (f.group != null) desk.cycleGroup(f.group, dir(a) == 'b' ? -1 : 1);
        } else if (Objects.equals("moveintogroup", d)) {
            moveIntoGroup(desk, f, dir(a));
        } else if (Objects.equals("moveoutofgroup", d)) {
            desk.moveOutOfGroup(f);
        }
        screen.layout();
        return null;
    }

    private static final Set<String> TERMINALS = Words.of("muxsh kitty alacritty foot wezterm terminal term xterm");

    /** {@code exec}: a terminal for a terminal's name, anything else run as a muxsh command. */
    private String exec(String arg) {
        if (arg.isEmpty()) return "exec: what? (" + com.mineux.core.MineUX.shellName() + " for a terminal, or a command)";
        String first = arg.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (TERMINALS.contains(first) || first.equalsIgnoreCase(com.mineux.core.MineUX.shellName())) {
            screen.spawnAtCursor();
            return null;
        }
        screen.command(arg);
        return null;
    }

    private Pane focused() {
        Desk desk = screen.desk();
        Pane f = desk.focused;
        return f != null && desk.windows.contains(f) && !Group.hidden(f) ? f : null;
    }

    private String pin(Desk desk, Pane f) {
        if (screen.onMenu() || f == desk.pinned || ListPane.class.isInstance(f)) return "pin: this window stays where it is";
        desk.togglePin(f);
        screen.layout();
        return null;
    }

    /** SUPER+K and SUPER+L: the focused window less or more see-through, pinned over the game too. */
    static String opacity(Pane f, String arg) {
        float to = opacityAfter(f.opacity, arg);
        if (Float.isNaN(to)) return "opacity: a step like -0.1 or +0.1, or a level like 0.6 or 60%";
        f.opacity = to;
        f.opacityAt = System.currentTimeMillis();
        return null;
    }

    /**
     * Opacity {@code now} after {@code arg}: a step ({@code -0.1}, {@code +10%}) or a level
     * ({@code 0.6}, {@code 60%}), kept between {@link Pane#MIN_OPACITY} and 1; NaN for nonsense.
     */
    static float opacityAfter(float now, String arg) {
        String a = arg == null ? "" : arg.trim();
        if (a.isEmpty()) return 1f;
        boolean step = a.startsWith("+") || a.startsWith("-");
        boolean percent = a.endsWith("%");
        String number = percent ? a.substring(0, a.length() - 1) : a;
        float v;
        try {
            v = Float.parseFloat(number);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
        if (percent || !step && v > 1f) v = v / 100f;
        float to = step ? now + v : v;
        return Math.max(Pane.MIN_OPACITY, Math.min(1f, to));
    }

    /** SUPER+J: in dwindle the focused pane's split turns; in columns the columns become rows. */
    private String toggleSplit() {
        Desk desk = screen.desk();
        if (Desk.dwindleLayout()) {
            Pane f = focused();
            if (f != null) desk.dwindle.toggleSplit(f);
        } else {
            desk.rows = !desk.rows;
        }
        screen.layout();
        return null;
    }

    private String cycleNext() {
        Desk desk = screen.desk();
        List<Pane> order = visible(desk);
        if (!order.isEmpty()) desk.focus(order.get((order.indexOf(focused()) + 1) % order.size()));
        return null;
    }

    private String moveFocus(String a) {
        Desk desk = screen.desk();
        List<Pane> order = visible(desk);
        Pane f = focused();
        if (f == null) {
            if (!order.isEmpty()) desk.focus(order.get(0));
            return null;
        }
        Pane to = neighbour(order, f, dir(a));
        if (to != null) desk.focus(to);
        return null;
    }

    /**
     * {@code resizeactive dx dy}, in GUI px: a floating window's size; tiled, the share of its
     * column (or row) across and of its column along -- in dwindle, the ratios of the splits it
     * is part of.
     */
    private String resize(Desk desk, Pane f, String a) {
        String[] parts = a.trim().split("[\\s,]+");
        float dx, dy;
        try {
            dx = Float.parseFloat(parts[0]);
            dy = parts.length > 1 ? Float.parseFloat(parts[1]) : 0f;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return "resizeactive <dx> <dy>, e.g. resizeactive 40 0";
        }
        if (f.floating) {
            f.fw += dx;
            f.fh += dy;
        } else if (Desk.dwindleLayout()) {
            grow(desk.dwindle.splitAbove(f, false), f, dx);
            grow(desk.dwindle.splitAbove(f, true), f, dy);
        } else {
            desk.resizeInColumns(f, desk.rows ? dy : dx, desk.rows ? dx : dy);
        }
        screen.layout();
        return null;
    }

    /** A split grown by {@code by} px for the pane: its half gets bigger, whichever half it is in. */
    private static void grow(Dwindle.Node split, Pane pane, float by) {
        if (split == null || by == 0f) return;
        float size = split.stacked ? split.h : split.w;
        float step = 2f * by / Math.max(1f, size);
        split.ratio = Math.max(0.1f, Math.min(1.9f, split.ratio + (Dwindle.inFirst(split, pane) ? step : 0f - step)));
    }

    /** SUPER+CTRL+arrow: into the group that way, which the pane there becomes if it is not one yet. */
    private static void moveIntoGroup(Desk desk, Pane f, char dir) {
        Pane to = neighbour(visible(desk), f, dir);
        if (to == null || to == desk.pinned) return;
        if (to.group == null) desk.toggleGroup(to);
        desk.joinGroup(f, to.group);
    }

    /** Every pane that is showing, tiled then floating: what focus can move between. */
    static List<Pane> visible(Desk desk) {
        List<Pane> out = new ArrayList<>(desk.tiled());
        out.addAll(desk.floating());
        return out;
    }

    private static char dir(String a) {
        return a.isEmpty() ? 'r' : Character.toLowerCase(a.charAt(0));
    }

    /**
     * {@code movewindow}: floating, it steps an eighth of the desk that way; tiled in columns,
     * it moves into the column that way or along its own; tiled in dwindle, it swaps with the
     * pane that way.
     */
    private void moveWindow(Desk desk, Pane f, char dir) {
        if (f.floating) {
            float step = Math.max(screen.width, screen.height) / 8f;
            if (dir == 'l') f.fx -= step;
            else if (dir == 'r') f.fx += step;
            else if (dir == 'u') f.fy -= step;
            else if (dir == 'd') f.fy += step;
        } else if (Desk.dwindleLayout()) {
            Pane to = neighbour(desk.tiled(), f, dir);
            if (to != null) desk.swap(f, to);
        } else {
            boolean sideways = dir == 'l' || dir == 'r';
            desk.moveInColumns(f, sideways != desk.rows, dir == 'r' || dir == 'd');
        }
        screen.layout();
    }

    /**
     * The pane nearest {@code from} in a direction: the closest whose edge is past {@code from}'s
     * that way, those level with it first; centres decide when edges overlap (floating panes).
     */
    static Pane neighbour(List<Pane> panes, Pane from, char dir) {
        Pane best = null;
        float bestScore = Float.MAX_VALUE;
        float fcx = from.x + from.w / 2f, fcy = from.y + from.h / 2f;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (Pane p : panes) {
                if (p == from) continue;
                float pcx = p.x + p.w / 2f, pcy = p.y + p.h / 2f;
                float gap, offset, overlap;
                if (dir == 'l' || dir == 'r') {
                    gap = dir == 'l' ? from.x - (p.x + p.w) : p.x - (from.x + from.w);
                    if (pass == 1) gap = dir == 'l' ? fcx - pcx : pcx - fcx;
                    overlap = Math.min(from.y + from.h, p.y + p.h) - Math.max(from.y, p.y);
                    offset = Math.abs(pcy - fcy);
                } else {
                    gap = dir == 'u' ? from.y - (p.y + p.h) : p.y - (from.y + from.h);
                    if (pass == 1) gap = dir == 'u' ? fcy - pcy : pcy - fcy;
                    overlap = Math.min(from.x + from.w, p.x + p.w) - Math.max(from.x, p.x);
                    offset = Math.abs(pcx - fcx);
                }
                if (pass == 0 ? gap < -4f : gap <= 0f) continue;
                float score = Math.max(0f, gap) + (overlap > 0f ? offset * 0.01f : 10000f + offset);
                if (score < bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        return best;
    }

    // ---- workspaces -----------------------------------------------------

    private String toggleSpecial() {
        screen.toggleSpecial();
        return null;
    }

    /** {@code special}, or Hyprland's named form {@code special:magic}: here, the special workspace. */
    private static boolean special(String a) {
        String s = a.toLowerCase(Locale.ROOT);
        return Objects.equals("special", s) || s.startsWith("special:");
    }

    private String workspace(String a) {
        // From the home desk too: the main menu opens workstations like the Esc desk does.
        if (special(a)) {
            screen.toggleSpecial();
            return null;
        }
        String target = target(a, true);
        if (target == null) return null;
        if (Objects.equals(TerminalScreen.ESC_DESK, target)) screen.showEscDesk();
        else screen.goTo(target);
        return null;
    }

    /**
     * The workstation an argument names: its number or name, or {@code e+1} / {@code e-1} for
     * the next or previous in the bar, the Esc desk counting as the first in a world. A free
     * number is made (without opening it) when {@code make} is off; with it, {@code ws open}
     * makes it. Null for none.
     */
    private String target(String a, boolean make) {
        String arg = a.toLowerCase(Locale.ROOT);
        if (Objects.equals("previous", arg)) return Workstations.previous();
        // e+1 (Hyprland's "open workspaces"), r+1 and m+1 (its other relative forms) and +1 all
        // step through the bar; here every workstation in it is open.
        boolean prefixed = arg.startsWith("e") || arg.startsWith("r") || arg.startsWith("m");
        String rel = prefixed ? arg.substring(1) : arg;
        if (rel.startsWith("+") || rel.startsWith("-")) {
            int step;
            try {
                step = Integer.parseInt(rel.replace("+", ""));
            } catch (NumberFormatException e) {
                return null;
            }
            List<String> names = Workstations.names();
            int at = screen.onEsc() ? -1 : names.indexOf(Workstations.current());
            int to = at + step;
            if (to == -1 && screen.inWorldDesk()) return TerminalScreen.ESC_DESK;
            return to >= 0 && to < names.size() ? names.get(to) : null;
        }
        String found = Workstations.find(a);
        if (found != null) return found;
        int slot = Workstations.parseSlot(a);
        if (slot <= 0) return null;
        return make ? String.valueOf(slot) : Workstations.createQuiet(slot);
    }

    private String moveToWorkspace(String a, boolean follow) {
        if (screen.onMenu()) return null;
        Desk desk = screen.desk();
        Pane f = focused();
        if (f == null) return null;
        if (f == desk.pinned) return "movetoworkspace: this terminal stays on its desk";
        if (special(a)) {
            // Made, without being opened, if this is the first it is heard of.
            String made = Workstations.makeSpecial();
            if (made != null) return made;
            desk.moveTo(f, Desk.of(Workstations.SPECIAL));
            if (follow) screen.goTo(Workstations.SPECIAL);
            else screen.layout();
            return null;
        }
        String target = target(a, false);
        if (target == null || Objects.equals(TerminalScreen.ESC_DESK, target)) return null;
        desk.moveTo(f, Desk.of(target));
        if (follow) screen.goTo(target);
        else screen.layout();
        return null;
    }
}
