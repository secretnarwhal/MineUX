
package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tiling: columns of any size, panes going beside each other, and nothing left empty. */
class DeskTest {

    private static final class Box extends Pane {
        final String name;

        Box(String name) {
            this.name = name;
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                    List<Hit> hits, Hit hovered) {
        }

        @Override
        void close() {
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static Box add(Desk desk, String name, Pane near, int where) {
        Box box = new Box(name);
        desk.windows.add(box);
        desk.tile(box, near, where);
        return box;
    }

    @Test
    void secondPaneOpensANewColumnThenColumnsGrow() {
        Desk desk = new Desk();
        Box a = add(desk, "a", null, Desk.AFTER);
        Box b = add(desk, "b", null, Desk.AFTER);
        assertEquals(2, desk.columns.size(), "a lone pane gets a neighbour beside it");

        // What was impossible with a master pane: a second one on the left.
        Box c = add(desk, "c", a, Desk.AFTER);
        assertEquals(List.of(a, c), desk.columns.get(0).panes);
        assertEquals(List.of(b), desk.columns.get(1).panes);

        Box d = add(desk, "d", a, Desk.BEFORE);
        assertEquals(List.of(d, a, c), desk.columns.get(0).panes);
    }

    @Test
    void droppingBesideMakesColumnsAndEmptiedColumnsGo() {
        Desk desk = new Desk();
        Box a = add(desk, "a", null, Desk.AFTER);
        Box b = add(desk, "b", null, Desk.AFTER);
        Box c = add(desk, "c", b, Desk.COL_AFTER);
        assertEquals(3, desk.columns.size());

        // c dropped under a: its own column empties and goes.
        desk.tile(c, a, Desk.AFTER);
        assertEquals(2, desk.columns.size());
        assertEquals(List.of(a, c), desk.columns.get(0).panes);

        // b dropped left of a: a new first column.
        desk.tile(b, a, Desk.COL_BEFORE);
        assertEquals(2, desk.columns.size());
        assertEquals(List.of(b), desk.columns.get(0).panes);
        assertEquals(List.of(a, c), desk.columns.get(1).panes);
    }

    @Test
    void swapAndClose() {
        Desk desk = new Desk();
        Box a = add(desk, "a", null, Desk.AFTER);
        Box b = add(desk, "b", null, Desk.AFTER);
        Box c = add(desk, "c", a, Desk.AFTER);

        desk.swap(c, b);
        assertEquals(List.of(a, b), desk.columns.get(0).panes);
        assertEquals(List.of(c), desk.columns.get(1).panes);

        desk.focus(c);
        desk.close(c);
        assertEquals(1, desk.columns.size());
        assertFalse(desk.windows.contains(c));
        assertTrue(desk.focused != c);
    }

    private static void place(Pane p, float x, float y, float w, float h) {
        p.x = x;
        p.y = y;
        p.w = w;
        p.h = h;
    }

    @Test
    void aNewPaneOpensOnTheCursorsSide() {
        Desk desk = new Desk();
        Box a = add(desk, "a", null, Desk.AFTER);
        place(a, 0, 0, 200, 100);

        // A lone pane: the new column goes on the side the cursor is.
        Box b = new Box("b");
        desk.windows.add(b);
        desk.tileAt(b, 40, 50);
        assertEquals(List.of(b), desk.columns.get(0).panes);
        assertEquals(List.of(a), desk.columns.get(1).panes);

        // In a column: above or below the pane there, by which half the cursor is in.
        place(a, 100, 0, 100, 100);
        Box c = new Box("c");
        desk.windows.add(c);
        desk.tileAt(c, 150, 80);
        assertEquals(List.of(a, c), desk.columns.get(1).panes);
        Box d = new Box("d");
        desk.windows.add(d);
        desk.tileAt(d, 150, 10);
        assertEquals(List.of(d, a, c), desk.columns.get(1).panes);
    }

    @Test
    void floatingIsOutOfTheColumns() {
        Desk desk = new Desk();
        Box a = add(desk, "a", null, Desk.AFTER);
        Box b = add(desk, "b", null, Desk.AFTER);

        desk.untile(b);
        b.floating = true;
        assertEquals(List.of(a), desk.tiled());
        assertEquals(List.of(b), desk.floating());
        assertNull(desk.columnOf(b));

        desk.tile(b, a, Desk.AFTER);
        assertFalse(b.floating);
        assertSame(desk.columnOf(a), desk.columnOf(b));
    }

    @Test
    void hoverTakesTheKeyboardAClickRaises() {
        Desk desk = new Desk();
        Box a = add(desk, "a", null, Desk.AFTER);
        Box b = add(desk, "b", null, Desk.AFTER);
        desk.focus(a);
        assertSame(a, desk.top());
        desk.hover(b);
        assertSame(b, desk.focused, "the cursor's window has the keyboard");
        assertSame(a, desk.top(), "and the clicked one stays on top");
        desk.focus(b);
        assertSame(b, desk.top(), "a click raises it");
        desk.close(b);
        assertSame(a, desk.focused);
        assertSame(a, desk.top(), "closing the top hands it on");
    }

    @Test
    void anyWindowAtAllHidesTheTerminalHint() {
        // The hint ("... or click here for a terminal") shows only on an empty desk.
        Desk desk = new Desk();
        assertTrue(desk.empty(), "nothing open: the hint shows");

        // Not a terminal (a module's settings window, an extension's program, a readout...): still a window.
        Box settings = add(desk, "settings", null, Desk.AFTER);
        assertFalse(settings.terminal());
        assertFalse(desk.empty(), "a window that is not a terminal hides it too");

        // A floating one counts the same.
        desk.untile(settings);
        settings.floating = true;
        assertFalse(desk.empty());

        // A group's hidden tab counts: the group shows one of its tabs.
        desk.tile(settings, null, Desk.AFTER);
        settings.floating = false;
        desk.toggleGroup(settings);
        Box tab = new Box("tab");
        desk.windows.add(tab);
        desk.joinGroup(tab, settings.group);
        assertTrue(Group.hidden(settings));
        desk.close(tab);
        assertFalse(desk.empty(), "the tab left behind is still open");

        // A pinned window stays on its desk and counts.
        desk.togglePin(settings);
        try {
            assertFalse(desk.empty(), "a pinned window counts");
        } finally {
            desk.close(settings);
        }
        assertTrue(desk.empty(), "the last window closed: the hint is back");
        assertFalse(Desk.PINS.contains(settings));
    }
}
