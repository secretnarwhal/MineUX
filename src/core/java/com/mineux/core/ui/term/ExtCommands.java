package com.mineux.core.ui.term;

import com.mineux.core.DeskExtensions;
import com.mineux.core.Extensions;
import com.mineux.core.ShellCommand;
import com.mineux.core.TermProgram;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which word runs what, between the shell's own commands and what extensions added: an
 * extension's command by its name or an alias ({@link ShellCommand#aliases}), the ones that
 * replace a built-in of the same word ({@link ShellCommand#replacesBuiltIn}), and the built-ins
 * an extension took away ({@link DeskExtensions#hideBuiltIn}). With nothing added, every word is
 * the shell's own and nothing here changes anything.
 */
final class ExtCommands {

    private ExtCommands() {}

    /** An extension's command typed as {@code word}, by name or alias; null for none. */
    static ShellCommand command(String word) {
        for (ShellCommand c : Extensions.commands()) {
            if (named(word, c.name(), c.aliases())) return c;
        }
        return null;
    }

    /** An extension's command that runs in place of the built-in {@code word}; null for none. */
    static ShellCommand replacing(String word) {
        ShellCommand c = command(word);
        return c != null && c.replacesBuiltIn() ? c : null;
    }

    /** An extension's program typed as {@code word}, by name or alias; null for none. */
    static TermProgram.Type program(String word) {
        for (TermProgram.Type p : DeskExtensions.programs()) {
            if (named(word, p.name(), p.aliases())) return p;
        }
        return null;
    }

    /** Whether the built-in {@code word} is the shell's to run: neither hidden nor replaced. */
    static boolean builtIn(String word) {
        return !DeskExtensions.hiddenBuiltIns().contains(word.toLowerCase(Locale.ROOT)) && replacing(word) == null;
    }

    /** The shell's own commands as {name, help} rows, without the ones hidden or replaced. */
    static List<String[]> builtInRows() {
        List<String[]> out = new ArrayList<>();
        for (String[] c : Shell.COMMANDS) if (builtIn(c[0])) out.add(c);
        return out;
    }

    /** Every word an extension added: commands' and programs' names and aliases. */
    static List<String> addedWords() {
        List<String> out = new ArrayList<>();
        for (ShellCommand c : Extensions.commands()) {
            out.add(c.name());
            out.addAll(c.aliases());
        }
        for (TermProgram.Type p : DeskExtensions.programs()) {
            out.add(p.name());
            out.addAll(p.aliases());
        }
        return out;
    }

    private static boolean named(String word, String name, List<String> aliases) {
        if (name.equalsIgnoreCase(word)) return true;
        if (aliases == null) return false;
        for (String a : aliases) if (a.equalsIgnoreCase(word)) return true;
        return false;
    }
}
