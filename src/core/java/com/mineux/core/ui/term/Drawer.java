package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Line;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lines that fold open inside the scrollback: the main menu's {@code ls} keeps one for the
 * worlds and one for the servers under its buttons, and clicking Singleplayer or Multiplayer
 * there opens that one -- the rows fade in one after another, and fold away again on the next
 * click. Typing the command still opens a window; this is the list without leaving the
 * terminal.
 * <p>
 * The two of one listing are partners: opening one folds the other.
 */
final class Drawer {

    private static final long ANIM_MS = 220;
    /** How long a built copy of the rows is kept, so a hovered row is the same object next frame. */
    private static final long REBUILD_MS = 400;

    private final Supplier<List<Line>> content;
    private final Runnable onOpen;
    Drawer partner;

    private boolean open;
    private long changedAt;
    private List<Line> built = List.of();
    private long builtAt;

    Drawer(Supplier<List<Line>> content, Runnable onOpen) {
        this.content = content;
        this.onOpen = onOpen;
    }

    void toggle() {
        set(!open);
        if (open && partner != null) partner.set(false);
    }

    private void set(boolean open) {
        if (this.open == open) return;
        long now = System.currentTimeMillis();
        // Reversed partway through, it turns round from where it has got to.
        float at = progress(now);
        this.open = open;
        changedAt = now - (long) ((open ? at : 1f - at) * ANIM_MS);
        if (open && onOpen != null) onOpen.run();
    }

    boolean isOpen() {
        return open;
    }

    /** 0 folded away, 1 all the way open. */
    private float progress(long now) {
        float t = Math.min(1f, (now - changedAt) / (float) ANIM_MS);
        return open ? t : 1f - t;
    }

    /** The rows showing now, each faded by how far open the drawer is; none when folded away. */
    List<Line> rows(long now) {
        float p = progress(now);
        if (p <= 0f) return List.of();
        if (now - builtAt > REBUILD_MS || built.isEmpty()) {
            built = new ArrayList<>(content.get());
            built.add(new Line());
            builtAt = now;
        }
        int shown = (int) Math.ceil(p * built.size());
        List<Line> out = new ArrayList<>(built.subList(0, Math.min(shown, built.size())));
        for (Line line : out) line.fade = p * p * (3 - 2 * p);
        return out;
    }
}
