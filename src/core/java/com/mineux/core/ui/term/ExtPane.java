package com.mineux.core.ui.term;

import com.mineux.core.MineUX;
import com.mineux.core.TermProgram;
import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * An extension's {@link DeskWindow} as the desk places it. A window that throws is closed and
 * logged; the desktop goes on.
 */
final class ExtPane extends Pane {

    final String kind;
    final DeskWindow window;
    private Desktop screen;
    private boolean broken;

    ExtPane(String kind, DeskWindow window, Desktop screen) {
        this.kind = kind;
        this.window = window;
        this.screen = screen;
        this.w = 1;
        this.h = 1;
        window.pane = this;
    }

    @Override
    int minW() {
        return window.minW();
    }

    @Override
    int minH() {
        return window.minH();
    }

    @Override
    int header() {
        return window.header();
    }

    @Override
    boolean takesKeys() {
        return window.takesKeys();
    }

    @Override
    boolean capturing() {
        return window.capturing();
    }

    @Override
    boolean typing() {
        return window.typing();
    }

    @Override
    String title() {
        return window.title();
    }

    @Override
    void attach(Desktop screen) {
        this.screen = screen;
    }

    @Override
    void close() {
        if (screen != null) screen.closeWindow(this);
        else closed();
    }

    private boolean closedOnce;

    /** Off the desk, however it went (its button, SUPER+Q, the desk's close): the window is told once. */
    void closed() {
        if (closedOnce) return;
        closedOnce = true;
        try {
            window.closed();
        } catch (RuntimeException | LinkageError e) {
            MineUX.LOGGER.warn("The {} window failed to close", kind, e);
        }
    }

    @Override
    void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused, List<Hit> hits, Hit hovered) {
        if (broken) return;
        try {
            window.render(g, font, now, ix(), iy(), iw(), ih(), mouseX, mouseY, focused, new Clicks(hits, hovered));
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    @Override
    void tick(boolean settled) {
        if (broken) return;
        try {
            window.tick();
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    @Override
    boolean keyPressed(KeyEvent event) {
        if (broken) return false;
        try {
            return window.keyPressed(event);
        } catch (RuntimeException | LinkageError e) {
            failed(e);
            return true;
        }
    }

    @Override
    boolean charTyped(CharacterEvent event) {
        if (broken) return false;
        try {
            return window.charTyped(event);
        } catch (RuntimeException | LinkageError e) {
            failed(e);
            return true;
        }
    }

    @Override
    void scrolled(double mouseY, double amount) {
        if (broken) return;
        try {
            window.scrolled(mouseY, amount);
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    void confirm(String question, String detail, String yes, Runnable onYes) {
        if (screen != null) screen.confirm(question, detail, yes, onYes);
    }

    private void failed(Throwable e) {
        if (broken) return;
        broken = true;
        MineUX.LOGGER.warn("The {} window failed and is closed", kind, e);
        if (screen != null) screen.closeWindow(this);
    }

    /** Where the window puts what a click lands on: the desk's hits. */
    private static final class Clicks extends TermProgram.Clicks {
        private final List<Hit> hits;
        private final Hit hovered;

        Clicks(List<Hit> hits, Hit hovered) {
            this.hits = hits;
            this.hovered = hovered;
        }

        @Override
        public void add(int x0, int y0, int x1, int y1, String hint, Runnable action) {
            Span span = new Span("", FG);
            span.hint = hint;
            span.action = action;
            hits.add(new Hit(x0, y0, x1, y1, null, span, -1, null, ACT_NONE));
        }

        @Override
        public void command(int x0, int y0, int x1, int y1, String hint, String command) {
            Span span = new Span("", FG);
            span.hint = hint;
            span.run = command;
            hits.add(new Hit(x0, y0, x1, y1, null, span, -1, null, ACT_NONE));
        }

        @Override
        public List<Hit> hits() {
            return hits;
        }

        @Override
        public Hit hovered() {
            return hovered;
        }
    }
}
