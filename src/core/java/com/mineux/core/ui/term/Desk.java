package com.mineux.core.ui.term;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One workstation's panes: the terminals and readouts open on it, which one has focus, and
 * how the tiled ones are arranged.
 * <p>
 * Tiled panes sit in columns side by side, each column a stack of panes -- or, with
 * {@link #rows} set (SUPER+J), the same thing turned on its side: rows stacked down the desk,
 * each a line of panes. Any column can hold any number, so there is no master pane and
 * nothing stops two panes sharing the left side.
 * <p>
 * Kept for the game session in a static registry keyed by workstation name, so closing the
 * desktop and opening it again -- or leaving a workstation and coming back -- finds every
 * terminal where it was, scrollback and all.
 */
final class Desk {

    /** Where a pane goes relative to another: in its column before or after it, or a new column either side. */
    static final int BEFORE = 0, AFTER = 1, COL_BEFORE = 2, COL_AFTER = 3, SWAP = 4;

    static final class Column {
        final List<Pane> panes = new ArrayList<>();
        float weight = 1f;
    }

    /** A closed pane's frame, shrinking and fading out where it was ({@code windowsOut}). */
    static final class After {
        final float x, y, w, h;
        final long at;

        After(Pane pane, long at) {
            this.x = pane.x;
            this.y = pane.y;
            this.w = pane.w;
            this.h = pane.h;
            this.at = at;
        }
    }

    private static final Map<String, Desk> DESKS = new HashMap<>();

    /** Pinned panes ({@code pin}), on whichever desks they are: drawn over the game with the desktop closed. */
    static final List<Pane> PINS = new ArrayList<>();

    /** Every pane; the floating ones bottom to top. The order of tiled ones is {@link #columns}'. */
    final List<Pane> windows = new ArrayList<>();
    final List<Column> columns = new ArrayList<>();
    /** The window that has the keyboard: the one under the cursor, or the last clicked, keyed to or opened. */
    Pane focused;
    /**
     * The window drawn over the rest: the last one clicked, keyed to or opened -- never one the
     * cursor only passed over, which takes the keyboard ({@link #hover}) and stays where it is.
     */
    Pane top;
    /** Rows stacked top to bottom instead of columns side by side. */
    boolean rows;
    boolean seeded;
    /** The pane that cannot be closed: the main menu's terminal, or the Esc desk's. Null on a workstation. */
    Pane pinned;
    /** The same tiled panes as {@link #columns}, as dwindle's tree of splits. */
    final Dwindle dwindle = new Dwindle();
    final List<After> afters = new ArrayList<>();

    static synchronized Desk of(String workstation) {
        return DESKS.computeIfAbsent(workstation, k -> new Desk());
    }

    /** The desk of that name if it has been made, without making it. */
    static synchronized Desk peek(String name) {
        return DESKS.get(name);
    }

    static synchronized void rename(String from, String to) {
        Desk desk = DESKS.remove(from);
        if (desk != null) DESKS.put(to, desk);
    }

    static synchronized void remove(String workstation) {
        DESKS.remove(workstation);
    }

    static synchronized List<Desk> all() {
        return new ArrayList<>(DESKS.values());
    }

    /** Whether the desks are laid out by dwindle's splits rather than in columns. */
    static boolean dwindleLayout() {
        return java.util.Objects.equals(HyprConf.DWINDLE, HyprConf.get().layout);
    }

    List<Pane> tiled() {
        List<Pane> out = new ArrayList<>();
        if (dwindleLayout()) {
            dwindle.order(out);
            return out;
        }
        for (Column column : columns) out.addAll(column.panes);
        return out;
    }

    /** Floating panes, bottom to top; a group's hidden tabs are not among them. */
    List<Pane> floating() {
        List<Pane> out = new ArrayList<>();
        for (Pane w : windows) if (w.floating && !Group.hidden(w)) out.add(w);
        return out;
    }

    Column columnOf(Pane pane) {
        for (Column column : columns) if (column.panes.contains(pane)) return column;
        return null;
    }

    // ---- tiling ---------------------------------------------------------

    /**
     * Tiles a pane where a new one opened at a point should go: a lone pane gets a neighbour
     * in a new column beside it; otherwise it joins the column under the point, beside the
     * pane there, or the last column when the point is over none. It goes on whichever side
     * of that pane the point is, so it opens under the point -- and focus, following the
     * cursor, stays with it.
     */
    void tileAt(Pane pane, float atX, float atY) {
        Pane under = null;
        for (Pane p : tiled()) if (p != pane && p.contains(atX, atY)) under = p;
        tileColumnsAt(pane, under, atX, atY);
        dwindle.insertAt(pane, under, atX, atY, HyprConf.get().forceSplit);
    }

    private void tileColumnsAt(Pane pane, Pane under, float atX, float atY) {
        if (under == null) {
            tileColumns(pane, null, AFTER);
        } else {
            Column column = columnOf(under);
            boolean lone = columns.size() == 1 && column.panes.size() == 1;
            float fx = (atX - under.x) / Math.max(1f, under.w);
            float fy = (atY - under.y) / Math.max(1f, under.h);
            // Columns run across the desk (down it with rows); panes run along each column.
            float across = rows ? fy : fx;
            float along = rows ? fx : fy;
            if (lone) tileColumns(pane, under, across < 0.5f ? COL_BEFORE : COL_AFTER);
            else tileColumns(pane, under, along < 0.5f ? BEFORE : AFTER);
        }
    }

    /**
     * Tiles a pane next to another (see {@link #BEFORE} and on), or at the end when {@code near}
     * is null -- in dwindle's tree, splitting the focused pane.
     */
    void tile(Pane pane, Pane near, int where) {
        tileColumns(pane, near, where);
        if (near != null) {
            dwindle.insert(pane, near, side(where));
        } else {
            Pane split = focused != null && focused != pane && dwindle.leaf(focused) != null ? focused : null;
            dwindle.insert(pane, split, Dwindle.AUTO);
        }
    }

    /** The side of a pane a drop zone is, on the screen: what the zone means with rows or columns. */
    private int side(int where) {
        boolean inColumn = where == BEFORE || where == AFTER;
        boolean topOrBottom = inColumn != rows;
        boolean before = where == BEFORE || where == COL_BEFORE;
        if (topOrBottom) return before ? Dwindle.TOP : Dwindle.BOTTOM;
        return before ? Dwindle.LEFT : Dwindle.RIGHT;
    }

    private void tileColumns(Pane pane, Pane near, int where) {
        untileColumns(pane);
        pane.floating = false;
        if (!windows.contains(pane)) windows.add(pane);

        Column column = near == null ? null : columnOf(near);
        if (column == null) {
            if (columns.isEmpty() || (columns.size() == 1 && columns.get(0).panes.size() == 1)) {
                newColumn(columns.size(), pane);
            } else {
                Column last = columns.get(columns.size() - 1);
                pane.weight = averageWeight(last);
                last.panes.add(pane);
            }
            return;
        }

        switch (where) {
            case BEFORE, AFTER -> {
                pane.weight = averageWeight(column);
                column.panes.add(column.panes.indexOf(near) + (where == AFTER ? 1 : 0), pane);
            }
            default -> newColumn(columns.indexOf(column) + (where == COL_AFTER ? 1 : 0), pane);
        }
    }

    private void newColumn(int index, Pane pane) {
        Column column = new Column();
        float total = 0;
        for (Column c : columns) total += c.weight;
        column.weight = columns.isEmpty() ? 1f : total / columns.size();
        pane.weight = 1f;
        column.panes.add(pane);
        columns.add(index, column);
    }

    private static float averageWeight(Column column) {
        if (column.panes.isEmpty()) return 1f;
        float total = 0;
        for (Pane p : column.panes) total += p.weight;
        return total / column.panes.size();
    }

    /** Takes a pane out of the tiling, leaving it on the desk; an emptied column goes. */
    void untile(Pane pane) {
        untileColumns(pane);
        dwindle.remove(pane);
    }

    private void untileColumns(Pane pane) {
        Column column = columnOf(pane);
        if (column == null) return;
        column.panes.remove(pane);
        if (column.panes.isEmpty()) columns.remove(column);
    }

    boolean isTiled(Pane pane) {
        return columnOf(pane) != null;
    }

    /** Two tiled panes trade places. */
    void swap(Pane a, Pane b) {
        dwindle.swap(a, b);
        Column ca = columnOf(a), cb = columnOf(b);
        if (ca == null || cb == null) return;
        int ia = ca.panes.indexOf(a), ib = cb.panes.indexOf(b);
        weigh(a, b, a.weight, b.weight);
        ca.panes.set(ia, b);
        cb.panes.set(ib, a);
    }

    /** {@code a} gets {@code b}'s weight and back: the weights as parameters, not a local copy of a field (HoistCheck). */
    private static void weigh(Pane a, Pane b, float wa, float wb) {
        a.weight = wb;
        b.weight = wa;
    }

    /** {@code with}, untiled, takes {@code old}'s place in the tiling, which leaves it. */
    private void replace(Pane old, Pane with) {
        Column column = columnOf(old);
        if (column == null) return;
        untile(with);
        column.panes.set(column.panes.indexOf(old), with);
        with.weight = old.weight;
        dwindle.replace(old, with);
    }

    /**
     * SUPER+SHIFT+arrow in columns: a pane moves to the column that way (a new one at the desk's
     * edge, if its own column has others), or trades places with its neighbour along the column.
     *
     * @param across  towards the next or previous column, rather than along its own
     * @param forward right or down, rather than left or up
     */
    void moveInColumns(Pane pane, boolean across, boolean forward) {
        Column column = columnOf(pane);
        if (column == null) return;
        int ci = columns.indexOf(column);
        if (!across) {
            int pi = column.panes.indexOf(pane);
            int to = pi + (forward ? 1 : -1);
            if (to >= 0 && to < column.panes.size()) swap(pane, column.panes.get(to));
            return;
        }
        int to = ci + (forward ? 1 : -1);
        if (to < 0 || to >= columns.size()) {
            if (column.panes.size() == 1) return;
            untileColumns(pane);
            newColumn(forward ? columns.size() : 0, pane);
            return;
        }
        Column target = columns.get(to);
        float along = column.panes.size() <= 1 ? 1f : column.panes.indexOf(pane) / (float) (column.panes.size() - 1);
        untileColumns(pane);
        pane.weight = averageWeight(target);
        int at = Math.round(along * target.panes.size());
        target.panes.add(Math.max(0, Math.min(target.panes.size(), at)), pane);
    }

    /**
     * {@code resizeactive} in columns: the pane's column wider by {@code across} px and the pane
     * taller in it by {@code along} px, taken from the others in proportion.
     */
    void resizeInColumns(Pane pane, float across, float along) {
        Column column = columnOf(pane);
        if (column == null) return;
        if (across != 0f && columns.size() > 1) {
            float total = 0f, size = 0f;
            for (Column c : columns) total += c.weight;
            for (Pane p : column.panes) size = Math.max(size, rows ? p.h : p.w);
            float share = column.weight / total;
            float whole = size / Math.max(0.01f, share);
            column.weight = Math.max(0.05f * total, column.weight + across / Math.max(1f, whole) * total);
        }
        if (along != 0f && column.panes.size() > 1) {
            float total = 0f;
            for (Pane p : column.panes) total += p.weight;
            float whole = 0f;
            for (Pane p : column.panes) whole += rows ? p.w : p.h;
            pane.weight = Math.max(0.05f * total, pane.weight + along / Math.max(1f, whole) * total);
        }
    }

    /**
     * Whether nothing at all is open on this desk: the one rule for "a window is open", so the
     * desktop's "open a terminal" hint shows only on an empty desk. Every pane counts, whatever it
     * is -- a terminal, a module's settings window, an extension's program, a readout, a list --
     * so a new kind of window counts without being named here. A group's hidden tabs count (the
     * group shows one of them), and so does a pinned pane, which stays on its desk like any other.
     */
    boolean empty() {
        return windows.isEmpty();
    }

    // ---- focus and closing ----------------------------------------------

    /** A click, a key or an opening: the window takes the keyboard and comes to the top. */
    void focus(Pane window) {
        focused = window;
        top = window;
        // A floating window comes to the top of the pile when it takes focus.
        if (window != null && window.floating) {
            windows.remove(window);
            windows.add(window);
        }
    }

    /** The cursor moved onto it: it takes the keyboard, and stays where it is in the pile. */
    void hover(Pane window) {
        focused = window;
    }

    /** The window drawn over the rest, if it is still on show: the last raised, else the focused. */
    Pane top() {
        Pane t = top != null && windows.contains(top) && !Group.hidden(top) ? top : focused;
        return t != null && windows.contains(t) && !Group.hidden(t) ? t : null;
    }

    void close(Pane window) {
        // Its frame shrinks and fades where it was, if it was on show at all. Afters are cleared
        // as they are drawn; on a desk nobody is looking at (a readout closed from the game) the
        // old ones go here instead.
        long now = System.currentTimeMillis();
        afters.removeIf(g -> now - g.at > 5000L);
        if (!Group.hidden(window) && window.w > 8 && window.h > 8) afters.add(new After(window, now));
        PINS.remove(window);
        window.onAll = false;
        take(window);
    }

    /** Takes a pane off this desk, out of its group and the tiling, handing focus on. */
    private void take(Pane window) {
        // Leaving its group first hands the focus of a showing tab on to the tab that shows next.
        if (window.group != null) leaveGroup(window);
        int index = windows.indexOf(window);
        untile(window);
        windows.remove(window);
        if (focused == window) {
            Pane next = null;
            for (int i = Math.min(index, windows.size()) - 1; i >= 0 && next == null; i--) {
                if (!Group.hidden(windows.get(i))) next = windows.get(i);
            }
            for (Pane p : windows) if (next == null && !Group.hidden(p)) next = p;
            focused = next;
        }
        if (top == window) top = focused;
    }

    /**
     * {@code movetoworkspace}: a pane leaves this desk for another, tiled there as a new pane
     * would be (a floating one keeps floating). It leaves its group; pinned, it stays pinned.
     */
    void moveTo(Pane pane, Desk target) {
        if (target == this || !windows.contains(pane)) return;
        take(pane);
        target.windows.add(pane);
        if (!pane.floating) target.tile(pane, null, AFTER);
        target.focus(pane);
    }

    // ---- pinned panes ---------------------------------------------------

    /**
     * SUPER+P: pinned, it is drawn over the game while the desktop is closed, where it was last
     * shown; that is all a pin does. It stays on this desk like any other window -- tiled or
     * floating, in its group, in its place in the pile -- and unpinned, it is as it was.
     */
    void togglePin(Pane pane) {
        pane.onAll = !pane.onAll;
        if (pane.onAll) PINS.add(pane);
        else PINS.remove(pane);
    }

    // ---- groups -----------------------------------------------------------

    /** SUPER+G: a pane becomes a group of its own, or its group breaks up, every tab tiled beside it. */
    void toggleGroup(Pane pane) {
        Group group = pane.group;
        if (group == null) {
            group = new Group();
            group.members.add(pane);
            group.active = pane;
            pane.group = group;
            return;
        }
        Pane active = group.active;
        List<Pane> tabs = new ArrayList<>(group.members);
        for (Pane m : tabs) m.group = null;
        group.members.clear();
        for (Pane m : tabs) {
            if (m == active) continue;
            // A former tab, off the tiling: back in it, beside what was the group.
            placeBeside(m, active);
        }
    }

    /** Puts a pane that is neither tiled nor floating beside another: floating over it, or tiled after it. */
    private void placeBeside(Pane pane, Pane beside) {
        if (beside != null && beside.floating) {
            pane.floating = true;
            pane.fx = beside.fx + 20;
            pane.fy = beside.fy + 20;
            pane.fw = beside.fw;
            pane.fh = beside.fh;
        } else {
            tile(pane, beside != null && isTiled(beside) ? beside : null, AFTER);
        }
    }

    /** A pane joins a group as its showing tab. */
    void joinGroup(Pane pane, Group group) {
        if (pane.group == group || group.active == null) return;
        if (pane.group != null) leaveGroup(pane);
        untile(pane);
        pane.floating = false;
        group.members.add(group.members.indexOf(group.active) + 1, pane);
        pane.group = group;
        show(group, pane);
        focused = pane;
        top = pane;
    }

    /** The tab that shows: it takes the showing one's place, tiled or floating. */
    void show(Group group, Pane pane) {
        showTab(group, pane, group.active);
    }

    /** {@code old}: the tab showing until now, a parameter rather than a local copy of the field (HoistCheck). */
    private void showTab(Group group, Pane pane, Pane old) {
        if (old == pane) return;
        if (old != null && old.floating) {
            pane.floating = true;
            old.floating = false;
            pane.fx = old.fx;
            pane.fy = old.fy;
            pane.fw = old.fw;
            pane.fh = old.fh;
        } else if (old != null) {
            replace(old, pane);
        }
        group.active = pane;
        if (old != null) {
            pane.targetX = old.targetX;
            pane.targetY = old.targetY;
            pane.targetW = old.targetW;
            pane.targetH = old.targetH;
            pane.snap();
        }
        if (focused == old) focused = pane;
        if (top == old) top = pane;
    }

    /** The next tab (or the previous) shows. */
    void cycleGroup(Group group, int step) {
        int n = group.members.size();
        if (n < 2) return;
        int i = group.members.indexOf(group.active);
        show(group, group.members.get(Math.floorMod(i + step, n)));
    }

    /** A pane leaves its group; when it was the showing tab, the next one shows in its place. */
    void leaveGroup(Pane pane) {
        leave(pane, pane.group);
    }

    /** {@code group}: the pane's group, a parameter rather than a local copy of the field (HoistCheck). */
    private void leave(Pane pane, Group group) {
        if (group == null) return;
        // Both decided before show() changes the showing tab.
        boolean showing = group.active == pane;
        boolean others = group.members.size() > 1;
        if (showing && others) {
            int i = group.members.indexOf(pane);
            show(group, group.members.get(i + 1 < group.members.size() ? i + 1 : i - 1));
        }
        group.members.remove(pane);
        pane.group = null;
        if (showing && !others) group.active = null;
    }

    /** SUPER+SHIFT+G: out of its group, and beside it. */
    void moveOutOfGroup(Pane pane) {
        Group group = pane.group;
        if (group == null || group.members.size() < 2) return;
        leaveGroup(pane);
        placeBeside(pane, group.active);
        focus(pane);
    }
}

