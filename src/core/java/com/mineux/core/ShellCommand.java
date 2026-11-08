package com.mineux.core;

import java.util.List;

/**
 * A command added to muxsh through {@link Extensions#addCommand}. An abstract class, not an
 * interface, so nobody can hand one over as a lambda (an obfuscator a host may run over this code, such as Skidfuscator, breaks those).
 * <p>
 * Only {@link #name}, {@link #help} and {@link #run} must be written. The rest have defaults that
 * keep a command what it was before they existed: no other names, no completion, and the shell's
 * own commands first.
 */
public abstract class ShellCommand {

    /** What is typed: {@code screens}. */
    public abstract String name();

    /** One line for {@code help}. */
    public abstract String help();

    /** Run it. {@code args} excludes the command's own name. */
    public abstract void run(String[] args, CommandOut out);

    /**
     * Other words that run it, as typed (lower case). Listed nowhere but completion; a command
     * hears which word was typed only if it needs to through {@link #run(String, String[], CommandOut)}.
     */
    public List<String> aliases() {
        return List.of();
    }

    /**
     * Run it, knowing which of its words was typed ({@link #name} or one of {@link #aliases}).
     * By default the same as {@link #run(String[], CommandOut)}.
     */
    public void run(String typed, String[] args, CommandOut out) {
        run(args, out);
    }

    /**
     * Tab completion after its name. {@code typed} is every word before the one being typed, its
     * own name (or alias, as typed) first; {@code token} is that word so far. Add candidates to
     * {@code out}; it filters them by {@code token}.
     */
    public void complete(List<String> typed, String token, CompletionOut out) {
    }

    /**
     * True when its name and aliases run it in place of the shell's own command of the same
     * word (an extension's {@code gui} for core's). It can still hand words back to the
     * built-in through {@link CommandOut#builtIn}. Default false: a built-in of the same word
     * wins and this command is never reached by that word.
     */
    public boolean replacesBuiltIn() {
        return false;
    }
}
