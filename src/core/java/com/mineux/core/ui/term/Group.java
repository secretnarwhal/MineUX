package com.mineux.core.ui.term;

import java.util.ArrayList;
import java.util.List;

/**
 * Hyprland's group: several panes stacked as tabs in one tile, one showing at a time, with a
 * bar of their names across the top. SUPER+G makes the focused pane one (or breaks its group
 * up), SUPER+CTRL+arrow moves a pane into the group that way, SUPER+[ and ] and a click on a
 * tab change which is showing.
 * <p>
 * Only the showing tab ({@link #active}) is in the desk's tiling, or floats; the others stay on
 * the desk, laid over it and hidden, and trade places with it in the tiling when picked.
 */
final class Group {

    /** How tall the tab bar over a group is, in GUI px. */
    static final int BAR_H = 11;

    final List<Pane> members = new ArrayList<>();
    Pane active;

    /** Whether a pane is a tab of a group without being the one showing. */
    static boolean hidden(Pane pane) {
        return pane.group != null && pane.group.active != pane;
    }
}
