package com.mineux.core.ui.term;

import com.mineux.core.DeskExtensions;
import com.mineux.core.Extensions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MineUX with nothing added: no extension's command, alias, completion or program reaches a
 * MineUX player. Tagged {@code bare}, which the {@code bareTest} task runs in a JVM of its own,
 * before anything (the other tests' {@code TestModule}) registers an extension.
 */
@Tag("bare")
class BareShellTest {

    @BeforeAll
    static void setUp() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final class NoHost implements Shell.Host {
        public void exit() {}
        public void quit() {}
        public void art(String arg) {}
        public String artName() { return "art"; }
        public String[] artNames() { return new String[] {"art"}; }
        public void launch(String app) {}
        public void workstationsChanged() {}
    }

    @Test
    void nothingIsRegistered() {
        assertTrue(Extensions.commands().isEmpty(), "this JVM must have no extension: " + Extensions.commands());
        assertTrue(DeskExtensions.programs().isEmpty());
        assertTrue(DeskExtensions.hiddenBuiltIns().isEmpty());
        assertTrue(DeskExtensions.panes().isEmpty());
        assertTrue(DeskExtensions.trayRows().isEmpty());
        assertTrue(DeskExtensions.shutdownLayers().isEmpty());
        assertEquals(null, DeskExtensions.rebootHook());
        assertTrue(DeskExtensions.chatRooms().isEmpty());
    }

    /** irc offers MineUX's one room and nothing else. */
    @Test
    void ircHasOnlyThePublicRoom() {
        List<String> rooms = new ArrayList<>();
        for (com.mineux.core.social.Chat.Channel c : com.mineux.core.social.Chat.channels()) rooms.add(c.label());
        assertEquals(List.of(com.mineux.core.social.Chat.PUBLIC), rooms);
    }

    /** {@code help} lists the shell's own commands and nothing else: no "added" section. */
    @Test
    void helpOffersOnlyMineUXsCommands() {
        Shell shell = new Shell(new NoHost());
        shell.run("help");
        Set<String> own = own();
        List<String> listed = new ArrayList<>();
        for (Term.Line line : shell.out) {
            for (Term.Span span : line.spans) {
                // A command row's name puts "<name> " into the prompt.
                if (span.fill != null && span.fill.equals(span.text() + " ") && line.spans.indexOf(span) == 1) listed.add(span.text());
            }
        }
        assertFalse(listed.isEmpty(), "help listed nothing");
        for (String name : listed) assertTrue(own.contains(name), "help offers " + name + ", which is not MineUX's");
        for (String name : own) assertTrue(listed.contains(name), "help leaves out " + name);
        String text = text(shell);
        assertFalse(text.contains("added - commands"), text);
    }

    /** The first word completes to MineUX's commands, its modules and nothing else. */
    @Test
    void completionOffersOnlyMineUXsCommandsAndModules() {
        Shell shell = new Shell(new NoHost());
        Set<String> allowed = own();
        List<Node> nodes = new ArrayList<>();
        Modules.root().walk(nodes);
        for (Node n : nodes) {
            allowed.add(n.name);
            for (Setting s : n.settings) allowed.add(s.key + ":");
        }
        allowed.add("..");
        for (Term.Suggestion s : shell.complete("", "")) {
            assertTrue(allowed.contains(s.insert()), "completion offers " + s.insert() + ", which is not MineUX's");
        }
        // A word nothing knows completes to nothing of anyone else's either.
        for (String cmd : new String[] {"zzq ", "frob "}) assertTrue(shell.complete(cmd, "").isEmpty(), cmd);
    }

    private static Set<String> own() {
        Set<String> out = new HashSet<>();
        for (String[] c : Shell.COMMANDS) out.add(c[0]);
        return out;
    }

    private static String text(Shell shell) {
        StringBuilder out = new StringBuilder();
        for (Term.Line line : shell.out) {
            for (Term.Span span : line.spans) out.append(span.text());
            out.append('\n');
        }
        return out.toString();
    }
}
