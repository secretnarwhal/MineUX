package com.mineux.core.ui.term;

import com.mineux.core.TestModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * muxsh against the real configs and an extension's module ({@link TestModule}): every setting
 * it names exists, typing at it changes what the modules read, and an extension's commands,
 * programs and module files are typed like core's own.
 */
class ShellTest {

    @BeforeAll
    static void setUp() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        TestModule.install();
    }

    private static final class NoHost implements Shell.Host {
        String launched;
        int closed;
        String asked;
        public void exit() {}
        public void quit() { closed++; }
        @Override
        public void confirm(String question, String detail, String yes, Runnable onYes) { asked = question; }
        public void art(String arg) {}
        public String artName() { return "art"; }
        public String[] artNames() { return new String[] {"art"}; }
        @Override
        public boolean launches() { return true; }
        public void launch(String app) { launched = app; }
        public void workstationsChanged() {}
    }

    private static Shell shell() {
        return new Shell(new NoHost());
    }

    /** close closes the desktop; quit and shutdown only ask, and nothing stops without a yes. */
    @Test
    void quitAsksAndCloseCloses() {
        NoHost host = new NoHost();
        Shell shell = new Shell(host);

        shell.run("close", false);
        assertEquals(1, host.closed);
        assertEquals(null, host.asked);

        shell.run("quit", false);
        assertEquals(1, host.closed);
        assertEquals("exit minecraft?", host.asked);

        host.asked = null;
        shell.run("shutdown", false);
        assertEquals("exit minecraft?", host.asked);
    }

    @Test
    void everySettingNamesARealField() {
        List<Node> all = new ArrayList<>();
        Modules.root().walk(all);
        for (Node node : all) {
            if (node.toggle != null) {
                assertEquals(Setting.BOOL, node.toggle.kind(), node.path() + " switch");
                assertNotNull(node.toggle.raw(), node.path() + " switch field");
            }
            for (Setting s : node.settings) {
                assertNotNull(s.raw(), node.path() + "/" + s.key + " -> " + s.field);
                assertFalse(s.display().equals("?"), node.path() + "/" + s.key);
            }
        }
    }

    /** Every line of core's file and every extension's (the test module's, the visuals', Screens') made it into the tree: a bad line is only logged in game. */
    @Test
    void moduleFilesLoadCompletely() throws Exception {
        StringBuilder text = new StringBuilder();
        List<String> files = new ArrayList<>();
        files.add("/mineux/modules.txt");
        files.addAll(com.mineux.core.Extensions.moduleFiles());
        for (String file : files) {
            try (var in = Modules.class.getResourceAsStream(file)) {
                text.append(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).append('\n');
            }
        }
        java.util.Set<String> dirs = new java.util.HashSet<>();
        int setLines = 0;
        for (String line : text.toString().split("\n")) {
            if (line.startsWith("dir ")) dirs.add(line.split("\\s+")[1]);
            if (line.startsWith("set ")) setLines++;
        }
        List<Node> all = new ArrayList<>();
        Modules.root().walk(all);
        int settings = 0;
        for (Node node : all) settings += node.settings.size();

        // A dir opened again by a later file is one dir, with the settings of both.
        assertEquals(dirs.size(), all.size(), "dirs: " + dirs);
        assertEquals(setLines, settings);
        assertNotNull(Modules.find("friends").setting("friendly"), "an extension's set line lands in core's dir");
    }

    /** A choice the config does not know would be silently put back by ensureDefaults. */
    @Test
    void everyChoiceIsOneTheConfigKeeps() {
        List<Node> all = new ArrayList<>();
        Modules.root().walk(all);
        for (Node node : all) {
            for (Setting s : node.settings) {
                if (s.options == null) continue;
                String before = s.display();
                for (String option : s.slugs()) {
                    assertEquals(null, s.set(option), node.path() + "/" + s.key);
                    assertEquals(option, s.display(), node.path() + "/" + s.key + " kept " + option);
                }
                s.set(before);
            }
        }
    }

    /** Clickable scrollback names modules by bare name, which only works while names are unique. */
    @Test
    void moduleNamesAreUniqueAndResolveFromAnywhere() {
        List<Node> all = new ArrayList<>();
        Modules.root().walk(all);
        List<String> seen = new ArrayList<>();
        Shell shell = shell();
        shell.run("cd testmod/part");
        for (Node node : all) {
            assertFalse(seen.contains(node.name), "duplicate module name " + node.name);
            seen.add(node.name);
            assertEquals(node, shell.resolve(Shell.rel(node)), node.path());
        }
    }

    @Test
    void settingKeysAreUniquePerModule() {
        List<Node> all = new ArrayList<>();
        Modules.root().walk(all);
        for (Node node : all) {
            List<String> seen = new ArrayList<>();
            for (Setting s : node.settings) {
                assertFalse(seen.contains(s.key), node.path() + " repeats " + s.key);
                seen.add(s.key);
                for (String alias : s.aliases) {
                    assertFalse(seen.contains(alias), node.path() + " repeats " + alias);
                    seen.add(alias);
                }
            }
        }
    }

    @Test
    void typedValuesReachTheConfig() {
        Shell shell = shell();
        TestModule cfg = TestModule.get();

        shell.run("testmod strength:7");
        assertEquals(7, cfg.strength);
        shell.run("testmod strength:99");
        assertTrue(cfg.strength <= 10, "kept in range");

        shell.run("testmod off");
        assertFalse(cfg.enabled);
        shell.run("testmod on");
        assertTrue(cfg.enabled);
        shell.run("toggle testmod");
        assertFalse(cfg.enabled);

        shell.run("testmod mode:hard");
        assertEquals("hard", cfg.mode);

        shell.run("cd testmod/part; range:4.5; on");
        assertEquals(4.5, cfg.part.range, 1e-9);
        assertTrue(cfg.part.on, "a config part's switch");
        assertEquals("~/testmod/part", shell.cwd().path());

        shell.run("testmod window 60-40");
        assertEquals(40, cfg.lo);
        assertEquals(60, cfg.hi);

        shell.run("rpc server:off");
        assertFalse(com.mineux.core.rpc.RpcConfig.get().showServer, "core's own module");
        shell.run("rpc server:on");

        shell.run("cd ..");
        assertEquals("~/testmod", shell.cwd().path());
        shell.run("cd");
        assertEquals("~", shell.cwd().path());
        shell.run("testmod mode:soft");
    }

    @Test
    void completesModulesSettingsCommandsAndPrograms() {
        Shell shell = shell();
        assertTrue(inserts(shell.complete("", "testm")).contains("testmod"));
        assertTrue(inserts(shell.complete("testmod ", "stre")).contains("strength:"));
        assertTrue(inserts(shell.complete("testmod ", "mode:")).contains("mode:hard"));
        assertTrue(inserts(shell.complete("cd ", "testmod/pa")).contains("testmod/part"));
        assertTrue(inserts(shell.complete("", "teste")).contains("testecho"), "an extension's command completes");
        assertTrue(inserts(shell.complete("", "testp")).contains("testprog"), "an extension's program completes");
        shell.run("cd testmod");
        assertTrue(inserts(shell.complete("", "stre")).contains("strength:"));
        assertTrue(inserts(shell.complete("", "o")).contains("on"));
    }

    /** {@link com.mineux.core.Extensions#addCommand}: typed like any command, written through CommandOut. */
    @Test
    void extensionCommandsRunAndAreInHelp() {
        Shell shell = shell();
        shell.run("testecho hello world");
        assertTrue(text(shell).contains("echo hello world"), text(shell));
        shell.run("testecho");
        assertTrue(text(shell).contains("testecho: nothing to echo"));
        shell.run("help");
        assertTrue(text(shell).contains("testecho"), "help lists an extension's command");
        assertTrue(text(shell).contains("testprog"), "help lists an extension's program");
    }

    /** {@link com.mineux.core.DeskExtensions#addProgram}: typing its name launches it in the terminal. */
    @Test
    void extensionProgramsLaunch() {
        NoHost host = new NoHost();
        Shell shell = new Shell(host);
        shell.run("testprog tab2");
        assertEquals(Shell.APP_EXT + "testprog:tab2", host.launched);
    }

    @Test
    void promptAndMotdAreMineUX() {
        Shell shell = shell();
        shell.motd();
        Term.Line prompt = shell.prompt(new Term.Line());
        StringBuilder p = new StringBuilder();
        for (Term.Span span : prompt.spans) p.append(span.text());
        assertTrue(p.toString().endsWith("@mineux:~$ "), p.toString());
        String out = text(shell);
        assertEquals(null, com.mineux.core.BrandWords.foundIn(out), out);
        shell.run("nosuchcommand");
        assertTrue(text(shell).contains("muxsh: command not found"));
    }

    /** An extension's command answers to its aliases too, hears which word was typed, and writes rich lines. */
    @Test
    void aliasesRunTheCommandAndRichLinesPrint() {
        Shell shell = shell();
        shell.run("tr");
        assertEquals("tr", TestModule.Rich.typedAs);
        assertTrue(text(shell).contains("rich click"), text(shell));
        assertTrue(text(shell).contains("> rich ok"), text(shell));
        Term.Span click = null;
        for (Term.Line line : shell.out) for (Term.Span span : line.spans) if ("click".equals(span.text())) click = span;
        assertNotNull(click);
        assertEquals("testecho clicked", click.run, "a span of an extension's line runs its command when clicked");
        shell.run("testrich2 run testecho from inside");
        assertEquals("testrich2", TestModule.Rich.typedAs);
        assertTrue(text(shell).contains("echo from inside"), "CommandOut.run runs a command here");
        shell.run("testrich module testmod strength:4");
        assertEquals(4, TestModule.get().strength, "CommandOut.module hands words to the settings tree");
        shell.run("testrich tab testmod/part");
        assertTrue(text(shell).contains("tab 4"), "CommandOut.tab walks up to the dir with a tab=: " + text(shell));
        shell.run("help");
        assertFalse(text(shell).contains("  tr "), "an alias is not listed in help");
    }

    /** An extension's completion after its name, the nested command after its run, and its aliases at the start. */
    @Test
    void extensionCommandsComplete() {
        Shell shell = shell();
        assertEquals(List.of("alpha"), inserts(shell.complete("testrich ", "al")));
        assertEquals(List.of("beta"), inserts(shell.complete("tr ", "be")), "an alias completes as its command");
        assertTrue(inserts(shell.complete("testrich run ", "teste")).contains("testecho"), "run's rest completes as a command of its own");
        assertTrue(inserts(shell.complete("testrich run testmod ", "stre")).contains("strength:"));
        assertTrue(inserts(shell.complete("", "testrich")).contains("testrich2"), "an alias completes as a first word");
        assertTrue(inserts(shell.complete("testrich module ", "testm")).contains("testmod"), "CompletionOut.modules completes the tree");
    }

    /** A command that replaces a built-in runs for its word, and can hand words back to the built-in. */
    @Test
    void replacedAndHiddenBuiltIns() {
        Shell shell = shell();
        shell.run("sudo test");
        assertTrue(text(shell).contains("sudo replaced"), text(shell));
        shell.run("sudo rm");
        assertTrue(text(shell).contains("not in the sudoers file"), "the built-in still runs what is handed back: " + text(shell));
        shell.run("vim");
        assertTrue(text(shell).contains("command not found: vim"), "a hidden built-in is gone: " + text(shell));
        shell.run("help");
        assertFalse(text(shell).contains("use key:value"), text(shell));
    }

    /** A dir's {@code alias=} is the module too, wherever it is typed. */
    @Test
    void aModuleAnswersToItsAlias() {
        Shell shell = shell();
        assertEquals("testmod", shell.resolve("oldtestmod").name);
        shell.run("oldtestmod strength:6");
        assertEquals(6, TestModule.get().strength);
    }

    /** {@code DeskExtensions.addTrayRow} and {@code setTrayBlurb} reach core's own group. */
    @Test
    void rowsAddedToCoresGroup() {
        Tray.Group client = null;
        for (Tray.Group group : Tray.groups()) if (group.key().equals("client")) client = group;
        assertNotNull(client);
        assertEquals("!testecho tray", client.rows().get(0).path());
        assertEquals("rpc", client.rows().get(1).path());
        assertEquals("the tests' blurb for core's group", client.blurb());
    }

    /** A trace listener hears every step fired and skipped, and every state line. */
    @Test
    void traceListenersHearSteps() {
        List<String> heard = new ArrayList<>();
        Trace.addListener(new Trace.Listener() {
            @Override
            public void note(String id, String text) {
                if (id.equals("\u0000listener")) heard.add(text);
            }
        });
        Trace.steps("\u0000listener", "go");
        Trace.fire("\u0000listener", "go");
        Trace.skip("\u0000listener", "go", "not now");
        Trace.state("\u0000listener", "idle");
        assertEquals(List.of("fire go", "skip go - not now", "idle"), heard);
    }

    /** Without a host, every brand reads MineUX's. */
    @Test
    void brandDefaultsAreMineUX() {
        assertEquals("muxsh", com.mineux.core.MineUX.shellName());
        assertEquals("special", com.mineux.core.MineUX.specialName());
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT, com.mineux.core.MineUX.deskKey());
        assertTrue(Workstations.isSpecial("special"));
        assertTrue(HyprConf.template().contains("exec, muxsh"));
    }

    @Test
    void artsSplitIntoLines() {
        Arts arts = Arts.load();
        assertEquals(arts.names.size(), arts.arts.size());
        assertTrue(arts.names.contains("classic"), "bundled resource read: " + arts.names);
        for (String[] art : arts.arts) assertTrue(art.length >= 1);
    }

    @Test
    void artSettingsAndSelectionByName() {
        Shell shell = shell();
        String palette = ArtView.palette();
        boolean glitch = ArtView.glitch();

        shell.run("art palette rose");
        assertEquals("rose", ArtView.palette());
        shell.run("art palette nonsense");
        assertEquals("rose", ArtView.palette(), "an unknown palette changes nothing");
        shell.run("art glitch off");
        assertEquals(false, ArtView.glitch());
        shell.run("art glitch");
        assertEquals(true, ArtView.glitch(), "on its own it flips");

        shell.run("art blocks");
        assertEquals("blocks", Workstations.state().artName);
        assertEquals(ArtView.names()[ArtView.current()], "blocks");

        shell.run("art palette " + palette);
        shell.run("art glitch " + (glitch ? "on" : "off"));
    }

    @Test
    void programsAndSuperKey() {
        NoHost host = new NoHost();
        Shell shell = new Shell(host);
        shell.run("pipes.sh");
        assertEquals(Shell.APP_PIPES, host.launched);
        shell.run("bonsai");
        assertEquals(Shell.APP_BONSAI, host.launched);

        shell.run("super ctrl");
        assertEquals("ctrl", Shell.superKey());
        shell.run("super nonsense");
        assertEquals("ctrl", Shell.superKey());
        shell.run("super alt");
        assertEquals("alt", Shell.superKey());
    }

    /** A long-running pipes.sh stays at a few hundred rectangles, not thousands. */
    @Test
    void pipesStayCheapToDraw() {
        Pipes pipes = new Pipes();
        pipes.resize(1920, 1080);
        int most = 0;
        for (int i = 0; i < 20 * 60 * 5; i++) {
            pipes.tick();
            most = Math.max(most, pipes.strokeCount());
        }
        assertTrue(most < 400, "strokes: " + most);
    }

    /** SUPER+number runs {@code ws open <n>}: an unused number makes a workstation there. */
    @Test
    void numberedWorkstations() {
        Shell shell = shell();
        String before = Workstations.current();
        if (Workstations.atSlot(9) != null) shell.run("ws rm 9");

        shell.run("ws open 9");
        String made = Workstations.current();
        assertEquals(9, Workstations.slot(made));
        assertEquals(made, Workstations.find("9"));

        shell.run("ws open " + before);
        assertEquals(before, Workstations.current());
        shell.run("ws rm 9");
        assertEquals(null, Workstations.atSlot(9));
    }

    /** An extension's module is per workstation: on in one, off in a new one made after. */
    @Test
    void anExtensionsModuleIsPerWorkstation() {
        Shell shell = shell();
        String before = Workstations.current();
        if (Workstations.atSlot(8) != null) shell.run("ws rm 8");
        shell.run("testmod strength:2");

        shell.run("ws open 8");
        shell.run("testmod strength:9");
        assertEquals(9, TestModule.get().strength);
        shell.run("ws open " + before);
        assertEquals(2, TestModule.get().strength, "the workstation left kept its own value");
        shell.run("ws open 8");
        assertEquals(9, TestModule.get().strength, "and the other one its");
        shell.run("ws open " + before);
        shell.run("ws rm 8");
    }

    @Test
    void tipsOnlyInTheFirstTerminal() {
        Shell first = shell();
        Shell second = shell();
        first.motd();
        second.motd();
        assertEquals(Shell.ART_ROWS, first.out.get(0).artRows);
        assertEquals(Shell.ART_ROWS, second.out.get(0).artRows);
        boolean firstHas = text(first).contains("click anything that glows");
        assertFalse(text(second).contains("click anything that glows"), "only once a session");
        assertTrue(firstHas || !text(first).isEmpty());
    }

    @Test
    void fetchAliases() {
        for (String alias : new String[] {"fetch", "neofetch", "fastfetch"}) {
            Shell shell = shell();
            shell.run(alias);
            assertTrue(text(shell).contains("muxsh"), alias + ": " + text(shell));
            assertFalse(text(shell).contains("command not found"), alias);
        }
    }

    /** The same command straight after itself replaces its last echo and output. */
    @Test
    void repeatedCommandsCollapse() {
        Shell shell = shell();
        shell.run("toggle testmod");
        shell.run("toggle testmod");
        shell.run("toggle testmod");
        String out = text(shell);
        assertEquals(1, count(out, "toggle testmod"), out);
        assertEquals(1, count(out, "testmod is now"), out);
        shell.run("pwd");
        shell.run("toggle testmod");
        assertEquals(2, count(text(shell), "toggle testmod"));
    }

    /** Clicking a switch flips it and prints nothing. */
    @Test
    void quietRunsPrintNothing() {
        Shell shell = shell();
        boolean before = TestModule.get().enabled;
        shell.quiet("toggle testmod");
        assertEquals(!before, TestModule.get().enabled);
        assertEquals(0, shell.out.size());
        shell.quiet("toggle testmod");
    }

    /** Every tray row is a real module of the tree: core's group and an extension's. */
    @Test
    void trayRowsAreModules() {
        boolean ext = false;
        for (Tray.Group group : Tray.groups()) {
            if (group.key().equals("testgroup")) ext = true;
            for (Tray.Row row : group.rows()) {
                if (row.path().startsWith(Tray.COMMAND)) continue;
                assertNotNull(row.node(), group.key() + ": " + row.path());
            }
        }
        assertTrue(ext, "Extensions.addTrayGroup reaches the tray");
    }

    /** Every module with a switch is in the tray, in exactly one group. Friends has none in the tree. */
    @Test
    void everyModuleWithASwitchIsInTheTray() {
        java.util.Map<String, String> rows = new java.util.HashMap<>();
        for (Tray.Group group : Tray.groups()) {
            for (Tray.Row row : group.rows()) {
                if (row.path().startsWith(Tray.COMMAND)) continue;
                String before = rows.put(row.path(), group.key());
                assertTrue(before == null, row.path() + " in two groups: " + before + ", " + group.key());
            }
        }
        List<String> missing = new ArrayList<>();
        for (Node node : Modules.toggleable()) {
            String path = node.path().substring(2);
            if (!rows.containsKey(path)) missing.add(path);
        }
        assertTrue(missing.isEmpty(), "modules with a switch missing from the tray: " + missing);
    }

    /** The module list names only what runs: testmod/part on, but under testmod off it is not listed. */
    @Test
    void onlyModulesThatRunAreListed() {
        Node mod = Modules.find("testmod");
        Node part = Modules.find("part");
        String modWas = mod.toggle.bool() ? "on" : "off", partWas = part.toggle.bool() ? "on" : "off";
        try {
            assertEquals(null, part.toggle.set("on"));
            assertEquals(null, mod.toggle.set("off"));
            assertFalse(PresenceFacts.onNames().contains("part"), "part listed with testmod off");
            assertEquals(null, mod.toggle.set("on"));
            assertTrue(PresenceFacts.onNames().contains("part"), "part not listed with testmod on");
        } finally {
            mod.toggle.set(modWas);
            part.toggle.set(partWas);
        }
    }

    @Test
    void tokenizesQuotes() {
        assertEquals(List.of("testecho", "a:Big Guy", "on"), Shell.tokenize("testecho \"a:Big Guy\" on"));
    }

    @Test
    void keysRoundTrip() {
        for (String name : new String[] {"r", "7", "f5", "mouse4", "rshift", "none", "capslock"}) {
            assertEquals(name, Setting.keyName(Setting.parseKey(name)));
        }
    }

    @Test
    void chainsSplitOutsideQuotesOnly() {
        assertEquals(List.of("a ", " b ", " c"), Shell.chain("a ; b && c"));
        assertEquals(List.of("bind r \"toggle rpc; toggle testmod\"", " ls"), Shell.chain("bind r \"toggle rpc; toggle testmod\"; ls"));
    }

    @Test
    void bindAndUnbind() {
        Shell shell = shell();
        shell.run("unbind f8");
        shell.run("bind F8 \"toggle testmod; testmod strength:4\"");
        Workstations.Bind bound = null;
        for (Workstations.Bind b : Workstations.state().binds) if (b.key.equals("f8")) bound = b;
        assertNotNull(bound, "stored under the key's own name");
        assertEquals("toggle testmod; testmod strength:4", bound.command);

        shell.run("bind f8 toggle rpc");
        int count = 0;
        for (Workstations.Bind b : Workstations.state().binds) if (b.key.equals("f8")) {
            count++;
            assertEquals("toggle rpc", b.command);
        }
        assertEquals(1, count);
        assertTrue(inserts(shell.complete("unbind ", "")).contains("f8"));
        shell.run("unbind f8");
        for (Workstations.Bind b : Workstations.state().binds) assertFalse(b.key.equals("f8"));
        shell.run("bind nosuchkey toggle rpc");
        assertTrue(text(shell).contains("unknown key"));
    }

    @Test
    void timingPairsHaveSlidersThatKeepTheGap() {
        Setting window = Modules.find("testmod").setting("window");
        assertTrue(window.slider);
        Shell shell = shell();
        shell.run("testmod window:100-200");
        Slider.drag(window, 0, 100, Slider.LOW, 1000);
        assertEquals(190, window.low(), 1e-9);
        assertEquals(200, window.high(), 1e-9);
        Slider.drag(window, 0, 100, Slider.HIGH, -50);
        assertEquals(190, window.low(), 1e-9);
        Slider.drag(window, 0, 100, Slider.HIGH, 1000);
        assertEquals(window.max, window.high(), 1e-9);
        assertEquals(0, window.high() % 5, 1e-9, "snapped to the step");
        shell.run("testmod window:20-35");
    }

    @Test
    void anAccountTokenIsNeitherEchoedNorKept() {
        assertEquals("accounts add Steve <token>", AccountCommands.redact("accounts add Steve M.C5_BAY.0.U.abcdefghijklmnop"));
        assertEquals("acc new Steve <token>", AccountCommands.redact("acc new Steve \"M.C5 with a space\""));
        assertEquals("accounts add Steve", AccountCommands.redact("accounts add Steve"), "an offline account has nothing to hide");
        assertEquals("accounts use Steve now", AccountCommands.redact("accounts use Steve now"));
    }

    /** Signed out, the social commands say so rather than failing to connect. */
    @Test
    void aGuestIsToldToSignIn() {
        com.mineux.core.net.MuxApi.signOut();
        Shell shell = shell();
        shell.run("friends list");
        shell.run("notifications");
        shell.run("msg someone hi");
        String out = text(shell);
        assertEquals(3, count(out, Guest.NOTE), out);
    }

    private static String text(Shell shell) {
        StringBuilder out = new StringBuilder();
        for (Term.Line line : shell.out) {
            for (Term.Span span : line.spans) out.append(span.text());
            out.append('\n');
        }
        return out.toString();
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }

    private static List<String> inserts(List<Term.Suggestion> suggestions) {
        List<String> out = new ArrayList<>();
        for (Term.Suggestion s : suggestions) out.add(s.insert());
        return out;
    }
}
