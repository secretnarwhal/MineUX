package com.mineux.core;

/**
 * Where an extension's command or program puts its tab completions
 * ({@link ShellCommand#complete}, {@link TermProgram.Type#complete}). Implemented by the shell;
 * the shell filters what is added against the word being typed, so add every candidate.
 */
public abstract class CompletionOut {

    /**
     * A completion: what replaces the word being typed, how it is listed, a dim note beside it,
     * and its colour (ARGB; the terminal's palette is {@code com.mineux.core.ui.term.Term}'s).
     */
    public abstract void add(String insert, String label, String note, int color);

    /**
     * The rest of the line from here is a command of its own ({@code run <command>}): the word
     * being typed is completed as it would be at the start of {@code line}, and what was added
     * here is dropped. {@code line} is what is typed of that command before the word.
     */
    public abstract void asCommand(String line);

    /** The keys {@code bind} offers: the ones already bound, then the usual ones. */
    public abstract void keys();

    /** The settings tree's modules, as {@code cd} completes them ({@code switchesOnly}: as {@code toggle} does, those with a switch). */
    public abstract void modules(boolean switchesOnly);
}
