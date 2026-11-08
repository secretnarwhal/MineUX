package com.mineux.core;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A module of the tests' own, added the way an extension adds one (a module file, a config, a
 * config part, a tray group, a command): what the extension tests read the desktop through.
 * {@link #install} is idempotent and must run before anything builds the settings tree.
 */
public final class TestModule implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/test");
    private static final ConfigFile<TestModule> FILE = new ConfigFile<>(TestModule.class, "testmod.json", TestModule::new, LOGGER);
    private static TestModule instance;
    private static boolean installed;

    public boolean enabled = false;
    public int strength = 3;
    /** A timing pair, drawn with a slider. */
    public int lo = 20;
    public int hi = 35;
    /** A choice, as a string constant (never an enum in a Gson config). */
    public String mode = "soft";
    public Part part = new Part();
    /** A record, not a setting: kept out of workstation switches. */
    public int uses = 0;

    /** An object held in the config, edited through a {@code cfg=testmod.part} sub-dir. */
    public static final class Part {
        public boolean on = false;
        public double range = 2.5;
    }

    public static synchronized TestModule get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        if (part == null) part = new Part();
        strength = Math.max(0, Math.min(10, strength));
        if (!"soft".equals(mode) && !"hard".equals(mode)) mode = "soft";
    }

    public void save() {
        FILE.save(this);
    }

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        Extensions.addModuleFile("/mineux-test/test.txt");
        Extensions.addConfig("testmod", "testmod.json", TestModule::get, () -> { get().ensureDefaults(); get().save(); });
        // What the full edition registers on top of core: the visuals (tray groups, module file,
        // configs, backdrop) and Screens' tray group, module file and config (not its keys or capture).
        com.mineux.visuals.Visuals.install();
        Extensions.addTrayGroup(new com.mineux.screens.Screens.Tray());
        Extensions.addModuleFile("/mineux/screens.txt");
        Extensions.addConfig("screens", "screens.json", com.mineux.screens.ScreensConfig::get, com.mineux.screens.ScreensConfig::commitLive);
        com.mineux.screens.Screens.personalKeys();
        DeskExtensions.addConfigPart("testmod.part", () -> get().part, () -> { get().ensureDefaults(); get().save(); });
        DeskExtensions.addPersonalField("testmod.json", "uses");
        DeskExtensions.addPersonal("testrecord.json", "a log the tests keep: a record, not a loadout");
        DeskExtensions.addStat("teststat", () -> "test stat line");
        Extensions.addTrayGroup(new Group());
        Extensions.addCommand(new Echo());
        DeskExtensions.addProgram(new TestProgramType());
        // The extension points a host that builds core into its own mod uses, with the tests' own names.
        Extensions.addCommand(new Rich());
        Extensions.addCommand(new Sudo());
        DeskExtensions.hideBuiltIn("vim");
        DeskExtensions.addTrayRow("client", 0, new TrayGroup.Row("!testecho tray", "test row in client", "minecraft:stick"));
        DeskExtensions.setTrayBlurb("client", "the tests' blurb for core's group");
        DeskExtensions.addPane(TestWindow.KIND, new TestWindow.Factory());
    }

    /**
     * {@code testrich}, also {@code tr} and {@code testrich2}: rich lines, completion, a nested
     * command after {@code run}, and the word it was typed as.
     */
    public static final class Rich extends ShellCommand {
        public static String typedAs;

        @Override
        public String name() {
            return "testrich";
        }

        @Override
        public String help() {
            return "the tests' rich command";
        }

        @Override
        public List<String> aliases() {
            return List.of("tr", "testrich2");
        }

        @Override
        public void run(String[] args, CommandOut out) {
            run("testrich", args, out);
        }

        @Override
        public void run(String typed, String[] args, CommandOut out) {
            typedAs = typed;
            if (args.length > 0 && args[0].equals("run")) {
                out.run(String.join(" ", java.util.Arrays.asList(args).subList(1, args.length)));
                return;
            }
            if (args.length > 0 && args[0].equals("module")) {
                out.module(java.util.Arrays.asList(args).subList(1, args.length));
                return;
            }
            if (args.length > 0 && args[0].equals("tab")) {
                out.println("tab " + out.tab(args.length > 1 ? args[1] : null));
                return;
            }
            if (args.length > 0 && args[0].equals("window")) {
                out.println("window " + out.openWindow(TestWindow.KIND));
                return;
            }
            out.line().t("rich ", com.mineux.core.ui.term.Term.GOLD).run("click", com.mineux.core.ui.term.Term.CYAN, "testecho clicked", "runs testecho")
                .at(CommandOut.COL_STATE).t("col", com.mineux.core.ui.term.Term.DIM);
            out.ok("rich ok");
        }

        @Override
        public void complete(List<String> typed, String token, CompletionOut out) {
            if (typed.size() == 1) {
                out.add("alpha", "alpha", "the first", com.mineux.core.ui.term.Term.CYAN);
                out.add("beta", "beta", "the second", com.mineux.core.ui.term.Term.CYAN);
                out.add("run", "run", "a command", com.mineux.core.ui.term.Term.CYAN);
                out.add("module", "module", "a module", com.mineux.core.ui.term.Term.CYAN);
            } else if (typed.size() == 2 && typed.get(1).equals("module")) {
                out.modules(false);
            } else if (typed.size() >= 2 && typed.get(1).equals("run")) {
                StringBuilder rest = new StringBuilder();
                for (int i = 2; i < typed.size(); i++) rest.append(typed.get(i)).append(' ');
                out.asCommand(rest.toString());
            }
        }
    }

    /** {@code sudo}, replacing the shell's own: {@code sudo test} is its, anything else is handed back. */
    public static final class Sudo extends ShellCommand {
        @Override
        public String name() {
            return "sudo";
        }

        @Override
        public String help() {
            return "the tests' sudo";
        }

        @Override
        public boolean replacesBuiltIn() {
            return true;
        }

        @Override
        public void run(String[] args, CommandOut out) {
            if (args.length == 1 && args[0].equals("test")) {
                out.ok("sudo replaced");
                return;
            }
            List<String> words = new java.util.ArrayList<>();
            words.add("sudo");
            words.addAll(java.util.Arrays.asList(args));
            out.builtIn(words);
        }
    }

    /** The tests' own desk window. */
    public static final class TestWindow extends com.mineux.core.ui.term.DeskWindow {
        public static final String KIND = "testwindow";
        public static int opened, closed;

        @Override
        public String title() {
            return "test window";
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, long now, int x, int y, int w, int h,
                           int mouseX, int mouseY, boolean focused, TermProgram.Clicks clicks) {
        }

        @Override
        public void closed() {
            closed++;
        }

        static final class Factory extends PaneFactory {
            @Override
            public com.mineux.core.ui.term.DeskWindow open() {
                opened++;
                return new TestWindow();
            }
        }
    }

    private static final class Group extends TrayGroup {
        @Override
        public String name() {
            return "testgroup";
        }

        @Override
        public List<Row> rows() {
            return List.of(new Row("testmod", "test module", "minecraft:stick"), new Row("testmod/part", "test part", "minecraft:feather"));
        }
    }

    /** {@code testprog}: an extension's terminal program. */
    public static final class TestProgramType extends TermProgram.Type {
        public static String lastArg;

        @Override
        public String name() {
            return "testprog";
        }

        @Override
        public String help() {
            return "the tests' own program";
        }

        @Override
        public TermProgram start(String arg, CommandOut out) {
            lastArg = arg;
            return new TermProgram() {
                @Override
                public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, long now,
                                   int x, int y, int w, int h, int mouseX, int mouseY, Clicks clicks) {
                }
            };
        }
    }

    /** {@code testecho a b}: prints its arguments back. */
    public static final class Echo extends ShellCommand {
        @Override
        public String name() {
            return "testecho";
        }

        @Override
        public String help() {
            return "prints its arguments back (the tests' own command)";
        }

        @Override
        public void run(String[] args, CommandOut out) {
            if (args.length == 0) out.error("testecho: nothing to echo");
            else out.println("echo " + String.join(" ", args));
        }
    }
}
