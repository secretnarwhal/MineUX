package com.mineux.core;

import com.mineux.core.ui.term.Term;

import java.util.List;

/**
 * Where a {@link ShellCommand} writes, and what it may ask of the terminal it was typed in.
 * Implemented by the shell. Every method past {@link #error} has a harmless default, for a
 * place with no terminal behind it (the default {@link #line} is drawn nowhere).
 */
public abstract class CommandOut {

    /** Where {@code ls} puts a switch's state, in GUI px from the line's start: for tables that line up with the shell's. */
    public static final int COL_STATE = 78;
    /** Where {@code ls} puts a description. */
    public static final int COL_DESC = 116;

    public abstract void println(String line);

    public abstract void error(String line);

    /** A line that says something worked: {@code > text}. */
    public void ok(String line) {
        println(line);
    }

    /** A line in one colour (ARGB; {@link Term}'s palette). */
    public void print(String line, int color) {
        println(line);
    }

    /**
     * A new line of rich text at the end of the scrollback: coloured spans, columns
     * ({@link Term.Line#at}), and spans that run a command, fill the prompt or do something when
     * clicked. Build it straight away; it is drawn as it is each frame.
     */
    public Term.Line line() {
        return new Term.Line();
    }

    /** Runs a command line here as a click would: no echo of it, nothing in the history. */
    public void run(String command) {
    }

    /**
     * Runs the shell's own built-in command for {@code words} (its name first), skipping any
     * extension that replaces it ({@link ShellCommand#replacesBuiltIn}): for an extension that
     * adds words to a built-in and hands the rest back.
     */
    public void builtIn(List<String> words) {
    }

    /**
     * {@code words} as the shell takes anything that is not a command: a module's name and what
     * to do to it ({@code rpc server:off}), or {@code key:value} where the prompt is. For a
     * command that shares its name with a module and hands back what is not one of its own words.
     */
    public void module(List<String> words) {
    }

    /**
     * Takes this terminal over with a program ({@link DeskExtensions#addProgram}), handing it
     * {@code arg}; running already, it is handed the new arg.
     *
     * @return false where there is no terminal window to take over (a keybind's shell)
     */
    public boolean launch(String program, String arg) {
        return false;
    }

    /**
     * Opens a window of a kind on the desk, or brings the open one forward: one added through
     * {@link DeskExtensions#addPane}, or one of the desk's own.
     *
     * @return false where there is no desk for it
     */
    public boolean openWindow(String kind) {
        return false;
    }

    /** A yes/no question in a floating window of its own; yes runs {@code onYes}. Nothing where there is no screen to ask on. */
    public void confirm(String question, String detail, String yes, Runnable onYes) {
    }

    /**
     * The {@code tab=} number a module's dir carries in the settings file, or its nearest
     * parent's; -1 for none. Blank or null for the dir the prompt is in. MineUX does nothing with
     * the number: it is the extension's own index (a classic panel's tab, say).
     */
    public int tab(String module) {
        return -1;
    }
}
