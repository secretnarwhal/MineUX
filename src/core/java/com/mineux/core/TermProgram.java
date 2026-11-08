package com.mineux.core;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A program that takes a terminal window over, the way {@code pipes.sh}, {@code irc} and
 * {@code friends} do, until Ctrl+C or until it says it is {@link #done}. Added through
 * {@link DeskExtensions#addProgram}: typing its {@link Type#name} runs it, and
 * {@code <name> <arg>} hands it {@code arg} (a tab to open on, say).
 * <p>
 * Abstract classes rather than interfaces, so nobody can hand one over as a lambda
 * (an obfuscator a host may run over this code, such as Skidfuscator, breaks those).
 */
public abstract class TermProgram {

    /** What a program is: its name in muxsh, its line in {@code help}, and how one is started. */
    public abstract static class Type {

        /** What is typed to start it, as a muxsh command. */
        public abstract String name();

        /** One line for {@code help}. */
        public abstract String help();

        /**
         * A new run of it, in a terminal of its own. {@code arg} is what was typed after the
         * name, or empty; {@code out} is the terminal's scrollback, for a line on the way out.
         */
        public abstract TermProgram start(String arg, CommandOut out);

        /** Other words that start it, as typed (lower case); see {@link ShellCommand#aliases}. */
        public java.util.List<String> aliases() {
            return java.util.List.of();
        }

        /** Tab completion after its name, as {@link ShellCommand#complete}. */
        public void complete(java.util.List<String> typed, String token, CompletionOut out) {
        }
    }

    /** Somewhere a click lands on, rebuilt every frame, the last one added on top. */
    public abstract static class Clicks {

        /** A rectangle that runs {@code action} when clicked and shows {@code hint} while hovered. */
        public abstract void add(int x0, int y0, int x1, int y1, String hint, Runnable action);

        /** A rectangle that runs a muxsh command in this terminal when clicked. */
        public abstract void command(int x0, int y0, int x1, int y1, String hint, String command);

        /**
         * This frame's hit list itself, for drawing with the terminal's kit
         * ({@link com.mineux.core.ui.term.Sheet}, {@link com.mineux.core.ui.term.Term.Hit#of}): add to it in
         * draw order, the last one added on top. Null where there is none.
         */
        public java.util.List<com.mineux.core.ui.term.Term.Hit> hits() {
            return null;
        }

        /** What the mouse was over last frame among the hits, or null: a span drawn lit when it is {@code hovered().span()}. */
        public com.mineux.core.ui.term.Term.Hit hovered() {
            return null;
        }
    }

    /**
     * True to be the whole window edge to edge, with no glass and no border under it (a picture
     * of something, say). Read every frame. Default false: drawn inside the terminal's border.
     */
    public boolean borderless() {
        return false;
    }

    /** Run again while already running, with a new {@code arg}: change tab, say. */
    public void show(String arg) {
    }

    /** Draws the whole window body, {@code x, y, w, h}. */
    public abstract void render(GuiGraphicsExtractor g, Font font, long now, int x, int y, int w, int h, int mouseX, int mouseY, Clicks clicks);

    /** A client tick while it runs. */
    public void tick() {
    }

    /** A key while it has the keyboard (Ctrl+C is the terminal's: it quits). True when it was taken. */
    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public void charTyped(CharacterEvent event) {
    }

    /** The wheel over it; {@code amount} up is positive. */
    public void scrolled(int mouseX, int mouseY, double amount) {
    }

    /** While true, Escape and the window manager's keys are the program's too (a field being typed). */
    public boolean editing() {
        return false;
    }

    /** True once it has finished on its own: the terminal gets its prompt back. */
    public boolean done() {
        return false;
    }

    /** Ctrl+C or the window closing: let go of whatever it holds. */
    public void quit() {
    }
}
