package com.mineux.core.ui.term;

import com.mineux.core.TermProgram;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A window of an extension's on the desk, placed by the window manager like every other: made by
 * a {@link com.mineux.core.PaneFactory} added through {@link com.mineux.core.DeskExtensions#addPane},
 * opened by its kind ({@code CommandOut.openWindow(kind)}, or a tray row's {@code !command}). It
 * draws its whole rectangle itself, the desk's border included ({@link #border}), and puts what
 * a click lands on into the frame's {@link TermProgram.Clicks}. An abstract class (no lambdas).
 */
public abstract class DeskWindow {

    /** The desk's pane this window is drawn in, once it is open. */
    ExtPane pane;

    /** What a group's tab calls it. */
    public abstract String title();

    /** The smallest it may be made, in GUI px. */
    public int minW() {
        return Pane.MIN_W;
    }

    public int minH() {
        return Pane.MIN_H;
    }

    /**
     * How tall its title bar is (it draws it), or 0 for none. A window with one is dragged by
     * the bar and resized by its corner ({@link #grip}), as any desktop's windows are.
     */
    public int header() {
        return 0;
    }

    /** Whether the keyboard goes to it while it is focused: a window with a text field. */
    public boolean takesKeys() {
        return false;
    }

    /** Whether it has the keyboard to itself for now (a field being typed into, a key being waited for): not even Escape is anyone else's. */
    public boolean capturing() {
        return false;
    }

    /** Whether what it is capturing is text, so the characters typed are its too. */
    public boolean typing() {
        return false;
    }

    /** Draws it over its rectangle {@code x, y, w, h}; {@code focused} is whether it has the focus. */
    public abstract void render(GuiGraphicsExtractor g, Font font, long now, int x, int y, int w, int h, int mouseX, int mouseY,
                                boolean focused, TermProgram.Clicks clicks);

    /** A key while it is focused and {@link #takesKeys}: true when it was taken. */
    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    /** The wheel over it; {@code amount} up is positive. */
    public void scrolled(double mouseY, double amount) {
    }

    /** A client tick while it is open. */
    public void tick() {
    }

    /** It was closed: let go of what it holds. */
    public void closed() {
    }

    /** Closes it, as its own close button would. */
    public final void close() {
        if (pane != null) pane.close();
    }

    /** The window manager's border around {@code x, y, w, h}, lit while focused: the one every window on the desk has. */
    public final void border(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused) {
        if (pane != null) Decor.border(g, pane, x, y, w, h, focused, 0);
    }

    /** A yes/no question in a floating window over the desk; yes runs {@code onYes}. */
    public final void confirm(String question, String detail, String yes, Runnable onYes) {
        if (pane != null) pane.confirm(question, detail, yes, onYes);
    }

    /** The resize corner's dots, lit while the mouse is on it: for a window with a {@link #header}. */
    public final void grip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (pane != null) pane.drawGrip(g, pane.onGrip(mouseX, mouseY));
    }
}
