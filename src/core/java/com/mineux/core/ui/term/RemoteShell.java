package com.mineux.core.ui.term;

/**
 * The door a friend's remote command comes through, and the only one.
 * <p>
 * The terminal's internals are package-private, so {@code friend.Remote} cannot reach a
 * {@link Shell} even if it wanted to. That is deliberate: the one place in this client where a
 * command can arrive from another person is a thing that should be reachable through exactly one
 * named method, so that the question "what can a remote command do" has one answer and it is
 * "whatever {@link Binds#headless} does".
 * <p>
 * The permission check is <b>not</b> here. It is in {@code friend.Remote}, twice over, before
 * anything reaches this class -- see its javadoc. This is the plumbing, not the gate.
 */
public final class RemoteShell {

    private RemoteShell() {}

    /**
     * Runs a terminal command in the shell nobody is looking at. Client thread only.
     *
     * @return the last line it printed, or null
     */
    public static String run(String command) {
        return Binds.headless(command);
    }
}
