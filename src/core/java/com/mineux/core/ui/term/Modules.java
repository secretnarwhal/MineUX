package com.mineux.core.ui.term;

import com.mineux.core.DeskExtensions;
import com.mineux.core.Extensions;
import com.mineux.core.gpu.Backend;
import com.mineux.core.util.Words;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The terminal's filesystem: every module as a directory, every setting it is safe to type
 * as an entry in it.
 * <p>
 * The tree itself is data, {@code mineux/modules.txt} and after it every file added through
 * {@link Extensions#addModuleFile}, read once. Written as builder
 * calls it came to a few hundred string literals, and the obfuscator turns each one into
 * code -- enough to push the builder past the JVM's 64KB method limit on some builds and not
 * others. What stays here is the one thing a text file cannot say: which live config object
 * each {@code cfg=} name reads and writes, and how it saves ({@link #binding}).
 * <p>
 * What the files list is what reads well as {@code key:value}. A {@code dir} line naming a dir
 * an earlier file made opens that dir again, so an extension can add settings to one of core's.
 * <p>
 * A line marked {@code vulkan=no} is a module or setting that needs the OpenGL backend. On Vulkan
 * ({@link Backend}) it is left out of the tree -- and with it out of the tray, the settings
 * windows and the terminal -- while its config value is left as it was;
 * {@link #vulkanHidden} counts what was left out, for the one line that tells the player why.
 */
final class Modules {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/terminal");
    private static final String RESOURCE = "/mineux/modules.txt";

    private static Node root;
    /** How many modules and settings the tree left out for needing OpenGL; 0 on OpenGL. */
    private static int vulkanHidden;

    private Modules() {}

    static synchronized Node root() {
        if (root == null) {
            boolean vulkan = Backend.vulkan();
            String text = readResource();
            root = build(text, vulkan);
            vulkanHidden = vulkan ? openGlOnly(text) : 0;
            Backend.logOnce(vulkanHidden);
        }
        return root;
    }

    /** How many modules and settings are hidden because the game is on Vulkan; 0 on OpenGL. */
    static synchronized int vulkanHidden() {
        root();
        return vulkanHidden;
    }

    /** The line that tells a Vulkan player why some settings are missing; null when nothing is. */
    static String vulkanNote() {
        int hidden = vulkanHidden();
        if (hidden <= 0) return null;
        return "some visuals need OpenGL; you're on Vulkan, so " + hidden + (hidden == 1 ? " setting is" : " settings are")
            + " hidden. Switch the graphics backend to OpenGL in video settings to use them.";
    }

    /** How many lines of the file are marked {@code vulkan=no}. */
    static int openGlOnly(String text) {
        int count = 0;
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (needsOpenGl(line)) count++;
        }
        return count;
    }

    /** Whether a line carries {@code vulkan=no} among its options (before the description). */
    static boolean needsOpenGl(String line) {
        int bar = line.indexOf(" | ");
        String head = bar < 0 ? line : line.substring(0, bar);
        for (String token : head.strip().split("\\s+")) {
            if (token.equals("vulkan=no")) return true;
        }
        return false;
    }

    /** A module anywhere in the tree by its own name, or null. */
    static Node find(String name) {
        List<Node> all = new ArrayList<>();
        root().walk(all);
        for (Node node : all) if (node.name.equals(name)) return node;
        return null;
    }

    /** The directory at a path from the root ({@code clicker/reach}), or null. */
    static Node at(String path) {
        Node node = root();
        for (String part : path.split("/")) {
            if (part.isEmpty()) continue;
            node = node.child(part);
            if (node == null) return null;
        }
        return node;
    }

    /** Every directory with an on/off switch, in tree order. */
    static List<Node> toggleable() {
        List<Node> all = new ArrayList<>();
        root().walk(all);
        all.removeIf(n -> n.toggle == null);
        return all;
    }

    /** Core's file, then every extension's, as one text. */
    private static String readResource() {
        StringBuilder text = new StringBuilder(read(RESOURCE));
        for (String extra : Extensions.moduleFiles()) text.append("\n").append(read(extra));
        return text.toString();
    }

    private static String read(String resource) {
        try (InputStream in = Modules.class.getResourceAsStream(resource)) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not read {}", resource, e);
        }
        LOGGER.warn("{} is missing; its modules are not in the tree", resource);
        return "";
    }

    // ---- the file -------------------------------------------------------

    private static final Set<String> W_DIR = Words.of("dir");
    private static final Set<String> W_SET = Words.of("set");

    /** Builds the tree from the file's text as OpenGL shows it. */
    static Node build(String text) {
        return build(text, false);
    }

    /**
     * Builds the tree from the file's text. A line it cannot make sense of is logged and skipped.
     * On {@code vulkan} a line marked {@code vulkan=no} is left out, a dir with everything under it.
     */
    static Node build(String text, boolean vulkan) {
        Node home = new Node("~", "home", null, null, null);
        Map<String, Node> dirs = new HashMap<>();
        Set<String> dropped = new HashSet<>();
        Node current = null;
        boolean skipping = false;
        int number = 0;

        for (String raw : text.split("\n")) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;

            try {
                int bar = line.indexOf(" | ");
                String desc = bar < 0 ? "" : line.substring(bar + 3).strip();
                String[] tokens = (bar < 0 ? line : line.substring(0, bar)).strip().split("\\s+");

                String word = tokens[0];
                if (W_DIR.contains(word)) {
                    skipping = vulkan && (needsOpenGl(line) || under(dropped, tokens[1]));
                    if (skipping) {
                        dropped.add(tokens[1]);
                        current = null;
                    } else {
                        current = dir(home, dirs, tokens, desc);
                    }
                } else if (W_SET.contains(word)) {
                    if (skipping || vulkan && needsOpenGl(line)) continue;
                    if (current == null) throw new IllegalStateException("set before any dir");
                    set(current, tokens, desc);
                } else {
                    throw new IllegalStateException("unknown line kind " + tokens[0]);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("{} line {}: {} ({})", RESOURCE, number, e.getMessage(), line);
            }
        }

        return home;
    }

    /** Whether a dir's path is inside one already left out. */
    private static boolean under(Set<String> dropped, String path) {
        int slash = path.lastIndexOf('/');
        while (slash > 0) {
            if (dropped.contains(path.substring(0, slash))) return true;
            slash = path.lastIndexOf('/', slash - 1);
        }
        return false;
    }

    private static Node dir(Node home, Map<String, Node> dirs, String[] tokens, String desc) {
        String path = tokens[1];
        Node again = dirs.get(path);
        if (again != null) return again;
        int slash = path.lastIndexOf('/');
        Node parent = slash < 0 ? home : dirs.get(path.substring(0, slash));
        if (parent == null) throw new IllegalStateException("no parent for " + path);

        Map<String, String> opts = options(tokens, 2);
        Binding binding = binding(opts.getOrDefault("cfg", "none"));
        if (binding == null) throw new IllegalStateException("unknown cfg " + opts.get("cfg"));

        int tab = Integer.parseInt(opts.getOrDefault("tab", "-1"));
        Node node = parent.dir(path.substring(slash + 1), desc, tab, binding.owner(), binding.commit());
        if (opts.containsKey("toggle")) node.toggle(opts.get("toggle"));
        if (opts.containsKey("fold")) node.follows = true;
        if (opts.containsKey("alias")) node.aliases = opts.get("alias").split(",");
        if (opts.containsKey("stat")) {
            node.stat = stats().get(opts.get("stat"));
            if (node.stat == null) throw new IllegalStateException("unknown stat " + opts.get("stat"));
        }
        if (opts.containsKey("act")) {
            node.act = acts().get(opts.get("act"));
            if (node.act == null) throw new IllegalStateException("unknown act " + opts.get("act"));
        }

        dirs.put(path, node);
        return node;
    }

    private static void set(Node node, String[] tokens, String desc) {
        Setting setting = node.add(tokens[1], tokens[2], desc);
        Map<String, String> opts = options(tokens, 3);

        if (opts.containsKey("alias")) setting.alias(opts.get("alias").split(","));
        if (opts.containsKey("label")) setting.label = opts.get("label").replace('_', ' ');
        if (opts.containsKey("min") || opts.containsKey("max")) {
            setting.range(Double.parseDouble(opts.get("min")), Double.parseDouble(opts.get("max")));
        }
        if (opts.containsKey("pair")) setting.pair(opts.get("pair"));
        if (opts.containsKey("choices")) setting.choices(opts.get("choices").replace('_', ' ').split(";"));
        if (opts.containsKey("hidden")) setting.hidden(opts.get("hidden").replace('_', ' ').split(";"));
        if (opts.containsKey("key")) setting.keybind();
        if (opts.containsKey("advanced")) setting.advanced = true;
        if (opts.containsKey("slider")) {
            if (Double.isNaN(setting.min)) throw new IllegalStateException("slider without min/max");
            setting.slider = true;
            if (opts.containsKey("gap")) setting.gap = Double.parseDouble(opts.get("gap"));
            if (opts.containsKey("step")) setting.step = Double.parseDouble(opts.get("step"));
        }
        if (opts.containsKey("confirm")) {
            setting.warning = stats().get(opts.get("confirm"));
            if (setting.warning == null) throw new IllegalStateException("unknown confirm " + opts.get("confirm"));
        }
        if (opts.containsKey("scale")) setting.scale = Double.parseDouble(opts.get("scale"));
        if (opts.containsKey("over")) setting.over = Double.parseDouble(opts.get("over"));
    }

    /** {@code name=value} tokens, and bare flags as {@code name=""}. */
    private static Map<String, String> options(String[] tokens, int from) {
        Map<String, String> out = new HashMap<>();
        for (int i = from; i < tokens.length; i++) {
            int eq = tokens[i].indexOf('=');
            if (eq < 0) out.put(tokens[i], "");
            else out.put(tokens[i].substring(0, eq), tokens[i].substring(eq + 1));
        }
        return out;
    }

    // ---- what each cfg= name reads and writes ---------------------------

    record Binding(Supplier<Object> owner, Runnable commit) {}

    /** The live config behind a {@code cfg=} name, and how it is put right and saved after a write. */
    static Binding binding(String name) {
        return name == null ? null : bindings().get(name);
    }

    /**
     * Every {@code cfg=} name, built on first use: the top-level ones straight from
     * {@link Configs} -- the one list workstations read too, so a config the settings tree can
     * reach is per-workstation unless {@link Configs#personal} says otherwise -- and the sub-dirs
     * that edit an object held in one of them. A table, not a switch on the name: see
     * {@link com.mineux.core.util.Words} for why this code has none.
     * <p>
     * A new module's config goes in through {@link Extensions#addConfig}, never straight in here.
     */
    private static Map<String, Binding> bindings() {
        if (BINDINGS == null) {
            Map<String, Binding> map = new HashMap<>();
            map.put("none", new Binding(null, null));
            for (Configs.Entry e : Configs.all()) map.put(e.cfg(), new Binding(e.live(), e.commit()));
            for (Map.Entry<String, DeskExtensions.Part> e : DeskExtensions.configParts().entrySet()) {
                map.put(e.getKey(), new Binding(e.getValue().part(), e.getValue().commit()));
            }
            BINDINGS = map;
        }
        return BINDINGS;
    }

    private static Map<String, Binding> BINDINGS;

    /**
     * Every {@code stat=} name: a line a settings window shows under a module's rows. And every
     * {@code confirm=} name: what a switch that is asked about first says under its question.
     */
    private static Map<String, Supplier<String>> stats() {
        if (STATS == null) {
            Map<String, Supplier<String>> map = new HashMap<>();
            map.put("rpc", com.mineux.core.rpc.Presence::stat);
            map.putAll(DeskExtensions.stats());
            STATS = map;
        }
        return STATS;
    }

    private static Map<String, Supplier<String>> STATS;

    /** Every {@code act=} name: a button row a settings window shows under a module's rows. */
    private static Map<String, Node.Act> acts() {
        if (ACTS == null) {
            Map<String, Node.Act> map = new HashMap<>();
            for (Map.Entry<String, DeskExtensions.Act> e : DeskExtensions.acts().entrySet()) {
                DeskExtensions.Act act = e.getValue();
                map.put(e.getKey(), new Node.Act(act.label(), act.hint(), act.run()));
            }
            ACTS = map;
        }
        return ACTS;
    }

    private static Map<String, Node.Act> ACTS;
}
