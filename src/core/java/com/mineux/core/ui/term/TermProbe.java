package com.mineux.core.ui.term;

import java.util.List;
import org.lwjgl.glfw.GLFW;

/**
 * The desktop's pure lookups and parsers, run on fixed input, for a host that builds core through
 * an obfuscator: the host runs it on the plain and on the obfuscated build and compares the two
 * lists line by line (the answers are compared with each other, never with values written down
 * here). Public only so a host's self-test, in another package, can reach the package-private
 * methods it covers. Register the host's extensions before running it, so its modules are in the
 * tree it walks.
 */
public final class TermProbe {

    private TermProbe() {}

    private static List<String> extraNames = List.of();
    private static List<String> windowPaths = List.of();

    public static void probe(List<String> out) {
        probe(out, List.of(), List.of("rpc", "friends", "nosuch", ""));
    }

    /**
     * @param names more names to {@code cd} into and list beside every module's own (an alias, a removed one)
     * @param paths settings-window paths to look up ({@link Modules#at})
     */
    public static void probe(List<String> out, List<String> names, List<String> paths) {
        extraNames = names;
        windowPaths = paths;
        String[] keys = {
            "menu.singleplayer", "menu.multiplayer", "menu.online", "menu.options", "menu.quit",
            "menu.playdemo", "menu.resetdemo", "options.language", "narrator.button.language",
            "options.accessibility", "narrator.button.accessibility", "menu.returnToGame",
            "gui.advancements", "gui.stats", "menu.reportBugs", "menu.sendFeedback", "gui.friends.open",
            "menu.playerReporting", "menu.multiplayerOptions.button", "menu.shareToLan",
            "menu.disconnect", "menu.returnToMenu", "some.mod.button"};
        for (String key : keys) out.add("menu " + key + " = " + MenuButtons.known(key));

        out.add("slug = " + MenuButtons.slug("  Replay   Viewer! ") + " " + MenuButtons.slug("Options..."));

        for (String word : new String[] {"on", "Enabled", "0", "flip", "maybe"}) {
            out.add("bool " + word + " = " + Setting.parseBool(word, true) + " " + Setting.parseBool(word, false));
        }

        out.add("tokenize = " + Shell.tokenize("bind r \"toggle aim\"  x"));
        out.add("chain = " + Shell.chain("ws new; art next && clear"));

        out.add("dashed = " + DiscordCommands.dashed("d219c8eed32e4da2b22e0aa69d36c88a") + " " + DiscordCommands.dashed("short"));
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString("{\"name\":\"Marlowww\",\"discord_id\":null,\"e\":\"\"}").getAsJsonObject();
        out.add("json = " + DiscordCommands.string(json, "name") + " " + DiscordCommands.string(json, "discord_id") + " "
            + DiscordCommands.string(json, "e") + " " + DiscordCommands.string(json, "missing") + " " + DiscordCommands.string(null, "name"));
        for (String word : new String[] {"Marlowww", "a_b", "has space", "seventeen_chars_x", "", "630872658027872273", "12ab"}) {
            out.add("valid " + word + " = " + DiscordCommands.validName(word) + " " + DiscordCommands.validId(word));
        }
        for (String cmd : new String[] {"discord", "rdiscord", "rd", "disconnect"}) out.add("discord? " + cmd + " = " + DiscordCommands.completesPlayers(cmd));

        String page = "<span class=\"history-name\">Marlowww</span>\n <span\n class=\"history-date\">08/09/2026</span>"
            + "<span class=\"history-name\"> old_name </span><span class=\"history-date\"></span>"
            + "<button class=\"ranking-kit\" data-kit=\"end_game\" data-label=\"End Game\" title=\"x\">\n<img class=\"kit-icon\" src=\"/c.png\">"
            + "<span class=\"kit-rank tier-color-label\" data-tier-code=\"MT6\">MT6</span></button>"
            + "<span class=\"overall-tier tier-color-label\" data-tier-code=\"HT3\" style=\"\">HT3</span>"
            + "<div data-stat=\"kills\">32</div><div><span data-stat=\"kdr\">0.76</span></div><div data-stat=\"wins\"> 14 </div>"
            + "<span class=\"activity-online-pill is-offline\">\n Offline </span>";
        out.add("mcpvp = " + McPvp.names(page) + " " + McPvp.tiers(page) + " " + McPvp.stats(page) + " " + McPvp.online(page));
        out.add("mcpvp empty = " + McPvp.names("") + " " + McPvp.tiers("") + " " + McPvp.stats("") + " " + McPvp.online(""));
        String more = "<script type=\"application/json\" data-profile-community>{\"playerId\":7,\"ban\":true,\"client\":{\"mods\":[{\"name\":\"Sodium\"},"
            + "{\"name\":\"Xaero\",\"disabled\":true}],\"values\":{\"Client\":\"fabric\",\"Platform\":\"Java\"}}}</script>"
            + "<ul id=\"activity-top\"><li class=\"activity-row\"><span>1</span><span>MCPVP</span>\n<span>26 joins</span></li></ul>"
            + "const statsTierInfo = {\"sword\":{\"tier\":\"Unranked\",\"rank\":null,\"wins\":0,\"losses\":1,\"matches\":1,\"factors\":["
            + "{\"progress\":34,\"value\":\"a\"},{\"progress\":13,\"value\":\"b\"},{\"progress\":42,\"value\":\"c\"},{\"progress\":52,\"value\":\"d\"},{\"progress\":19,\"value\":\"e\"}]},"
            + "\"end_game\":{\"tier\":\"MT6\",\"rank\":113526,\"wins\":13,\"losses\":13,\"matches\":26,\"factors\":["
            + "{\"progress\":44,\"value\":\"a\"},{\"progress\":69,\"value\":\"b\"},{\"progress\":34,\"value\":\"c\"},{\"progress\":82,\"value\":\"d\"},{\"progress\":46,\"value\":\"e\"}]},"
            + "\"mace\":{\"tier\":\"Unranked\",\"matches\":0,\"factors\":[]}};";
        com.google.gson.JsonObject community = McPvp.community(more);
        out.add("mcpvp community = " + McPvp.ban(community) + " " + McPvp.client(community) + " " + McPvp.mods(community) + " " + McPvp.ban(null)
            + " " + McPvp.activity(more, "activity-top") + " " + McPvp.activity(more, "activity-recent"));
        out.add("mcpvp kits = " + McPvpKits.kits(more) + " " + McPvpKits.kits("") + " " + McPvpKits.label("netherite_pot"));
        com.google.gson.JsonObject fetched = com.google.gson.JsonParser.parseString("{\"total\":3,\"items\":[{\"name\":\"a\"},{\"name\":\".b\"}],"
            + "\"overall\":{\"metrics\":{\"rank\":[{\"at\":1,\"value\":10},{\"at\":2,\"value\":7}]}},"
            + "\"records\":[{\"playedAt\":\"2026-10-04T15:09:53+00:00\",\"kit\":\"End Game\",\"map\":\"Plains\",\"players\":[\"me\",\"them\"],\"score\":\"2 - 0\",\"win\":false}]}").getAsJsonObject();
        out.add("mcpvp json = " + McPvp.people(fetched) + " " + McPvp.matches(fetched, "ME") + " "
            + java.util.Arrays.toString(McPvp.history(com.google.gson.JsonParser.parseString("{\"overall\":{\"metrics\":{\"rank\":[{\"value\":10},{\"value\":7}]}}}").getAsJsonObject())));
        String club = "<span class=\"profile-status-dot\"></span> Offline</span><span class=\"profile-last-seen\" style=\"\" data-relative-time=\"1790946385984\">x</span>"
            + "<span class=\"profile-rank\" style=\"\">Default</span><span>Joined Jun 2022</span><span>33 views / month</span>"
            + "<a class=\"practice-kit-card\" href=\"\"><h3>Sword</h3><span>1,612 ELO</span><em><span>W/L:</span><span>132 / 67</span></em><em><span>Wins:</span><span>661</span></em></a>"
            + "<a class=\"practice-kit-card\" href=\"\"><h3>UHC</h3><span>1,016 ELO</span><em><span>W/L:</span><span>1 / 0</span></em><em><span>Wins:</span><span>12</span></em></a>"
            + "<button class=\"practice-chart-point\" style=\"\" data-date=\"Jan 1\" data-elo=\"1,600\"></button><button class=\"practice-chart-point\" data-date=\"x\" data-elo=\"1,612\"></button>"
            + "var friendsData = [{\"uuid\":\"u\",\"username\":\"krizaly\"}]; Global ELO</span> <span class=\"pw-summary-value\">1,612</span><span class=\"pw-rank-number\">#5</span>";
        out.add("minemen = " + Minemen.status(club) + " " + Minemen.seen(club) + " " + Minemen.rank(club) + " " + Minemen.joined(club) + " " + Minemen.views(club)
            + " " + Minemen.friends(club) + " " + Minemen.ladders(club) + " " + java.util.Arrays.toString(Minemen.elo(club)) + " " + Minemen.globalElo(club) + " " + Minemen.world(club)
            + " " + Minemen.world("class=\"pw-rank-number\">#__<") + " " + Minemen.number("1,2x") + " " + Minemen.ladders(""));
        for (int score : new int[] {0, 33, 34, 66, 67, 100}) out.add("score " + score + " = " + LookupSections.scoreColor(score));
        for (String tier : new String[] {"HT1", "LT2", "MT3", "overall HT4", "LT5", "MT6", "Unranked", ""}) out.add("tier " + tier + " = " + LookupSections.tierColor(tier));
        for (long ms : new long[] {0L, 59_000L, 60_000L, 3_600_000L, 86_400_000L, 259_200_000L}) out.add("ago " + ms + " = " + LookupSections.ago(ms));
        out.add("grouped = " + LookupSections.grouped(100048f) + " " + LookupSections.grouped(7f));
        for (String section : new String[] {"friends", "matches", "mmfriends", "names"}) {
            out.add("list " + section + " = " + LookupLists.title(Profile.blank("Marlowww"), section) + " " + LookupLists.lines(Profile.blank("Marlowww"), section).size());
        }
        for (String cmd : new String[] {"lookup", "whois", "discord", "look"}) out.add("lookup? " + cmd + " = " + LookupCommands.completesPlayers(cmd));

        for (String palette : new String[] {"gold", "rose", "mint", "ice", "mono", "rainbow", "nope"}) {
            StringBuilder colors = new StringBuilder();
            // Rainbow walks the six-way int switch in ArtView's hsv at these points.
            for (float along = 0f; along <= 1.01f; along += 0.25f) {
                colors.append(Integer.toHexString(ArtView.color(palette, along, 3.5f))).append(' ');
            }
            out.add("palette " + palette + " = " + colors.toString().trim());
        }

        probeHypr(out);
        probeTrace(out);
        probeListings(out);
        probeModuleWindow(out);
        out.add("art glyphs = " + timed(ArtView::probeBuild));
        HyprConf conf = new HyprConf();
        conf.parse(HyprConf.template());
        HyprConf.useForTest(conf);
        try {
            probeDesk(out);
            probeMotion(out);
        } finally {
            HyprConf.useForTest(null);
        }
    }

    /**
     * What a right click on a tray icon runs -- {@code cd /<module>; ls} -- for every module in
     * the tree, with each line's text read the way a frame reads it. A right click on combat+ (since removed)
     * froze the game in one obfuscated build, from source that lists it correctly unobfuscated.
     */
    private static void probeListings(List<String> out) {
        List<Node> all = new java.util.ArrayList<>();
        Modules.root().walk(all);
        List<String> names = new java.util.ArrayList<>();
        for (Node node : all) names.add(node.name);
        names.addAll(extraNames);

        for (String name : names) {
            Shell shell = new Shell(new ProbeHost());
            shell.run("cd /" + name + "; ls", false);
            StringBuilder text = new StringBuilder();
            for (Term.Line line : shell.out) {
                for (Term.Span span : line.spans) text.append(span.text()).append(span.slider != null ? "<slider>" : "");
                text.append(" / ");
            }
            out.add("ls " + name + " = " + shell.cwd().path() + " " + text);
        }
    }

    /**
     * What a settings window ({@link ModuleWindow}) works out without drawing: where a path leads,
     * what each slider's drag rounds to, and the glass's colour and easing arithmetic.
     */
    private static void probeModuleWindow(List<String> out) {
        for (String path : windowPaths) {
            Node node = Modules.at(path);
            out.add("at " + path + " = " + (node == null ? "none" : node.path() + " " + node.settings.size() + " " + (node.toggle != null)));
        }

        List<Node> all = new java.util.ArrayList<>();
        Modules.root().walk(all);
        StringBuilder steps = new StringBuilder();
        for (Node node : all) {
            for (Setting s : node.settings) {
                if (!Double.isNaN(s.min)) steps.append(node.name).append('/').append(s.key).append('=').append(Setting.number(s.dragStep())).append(' ');
            }
        }
        out.add("steps = " + steps.toString().trim());

        out.add("glass = " + Integer.toHexString(Glass.alpha(Glass.GOLD, 0.35f)) + " " + Integer.toHexString(Glass.lit(Glass.EDGE))
            + " " + Integer.toHexString(Glass.mix(Glass.TEXT_DIM, Glass.GOLD, 0.25f)) + " " + Glass.step(0f, 1f, 14f, 0.05f)
            + " " + Glass.step(1f, 0f, 14f, 0.05f) + " " + Glass.step(0.9995f, 1f, 14f, 0.016f));

        // One build shipped with faded's opaque branch calling itself for ever (a stack overflow on
        // the first frame of the desktop): both of its branches, on a thread that can be given up on.
        out.add("faded = " + timed(() -> Integer.toHexString(TermWindow.faded(0xFFEFC648, 1f)) + " "
            + Integer.toHexString(TermWindow.faded(0xFFEFC648, 0.5f)) + " " + Integer.toHexString(TermWindow.faded(0x80EFC648, 0f))));
    }

    /**
     * An answer worked out on a thread of its own and given up on after a few seconds, so that
     * code the obfuscator turned into an endless loop fails the self-test instead of hanging it.
     */
    /** A probe run on a thread of its own, given five seconds: an endless loop (the obfuscator has made those) answers TIMED OUT. */
    public static String timed(java.util.function.Supplier<String> probe) {
        String[] answer = {"TIMED OUT"};
        Thread worker = new Thread(() -> {
            try {
                answer[0] = probe.get();
            } catch (Throwable t) {
                answer[0] = "threw " + t.getClass().getSimpleName();
            }
        }, "mineux-probe");
        worker.setDaemon(true);
        worker.start();
        try {
            worker.join(5000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return answer[0];
    }

    /** A terminal with nothing behind it. */
    private static final class ProbeHost implements Shell.Host {
        public void openGui(int tab) {}
        public void openBinds() {}
        public void exit() {}
        public void quit() {}
        public void confirm(String question, String detail, String yes, Runnable onYes) {}
        public void signOut() {}
        public void art(String arg) {}
        public String artName() { return "art"; }
        public String[] artNames() { return new String[] {"art"}; }
        public void launch(String app) {}
        public void workstationsChanged() {}
    }

    /** hyprland.conf's parser, its colours, keys and curves, and the dwindle tree. */
    private static void probeHypr(List<String> out) {
        HyprConf conf = new HyprConf();
        conf.parse(HyprConf.template());
        out.add("hypr template = " + conf + " " + conf.errors);
        for (String c : new String[] {"rgba(efc648ee)", "rgb(33ccff)", "0xee33ccff", "rgba(255, 0, 0, 0.5)", "rgba(12)", "bogus"}) {
            Integer color = HyprConf.color(c);
            out.add("hypr color " + c + " = " + (color == null ? "null" : Integer.toHexString(color)));
        }
        // Every key in game goes through this lookup: a build where it never returns must not ship.
        out.add("hypr in-game binds = " + timed(() -> {
            StringBuilder found = new StringBuilder();
            for (int key : new int[] {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_TAB, -1}) {
                for (int held : new int[] {0, HyprConf.superBit(), HyprConf.superBit() | GLFW.GLFW_MOD_SHIFT}) {
                    HyprConf.Bind bind = InGameKeys.bound(conf.binds, key, held);
                    found.append(bind == null ? "-" : bind.arg).append(' ');
                }
            }
            return found.toString().trim();
        }));
        HyprConf.Gradient g = HyprConf.gradient("rgba(33ccffee) rgba(00ff99ee) 45deg");
        out.add("hypr gradient = " + (g == null ? "null" : g.colors.length + " " + g.angle));
        out.add("hypr mods = " + HyprConf.mods("SUPER SHIFT") + " " + HyprConf.mods("ctrl_alt") + " " + HyprConf.mods("") + " " + HyprConf.mods("HYPER"));
        StringBuilder keys = new StringBuilder();
        for (String k : new String[] {"Return", "space", "mouse_down", "mouse_up", "bracketleft", "Q", "1", "left", "F5", "nope", "mouse4"}) {
            keys.append(k).append('=').append(HyprConf.keyCode(k)).append(' ');
        }
        out.add("hypr keys = " + keys.toString().trim());
        out.add("hypr anim = " + conf.ms("windowsMove") + " " + conf.ms("windowsIn") + " " + conf.ms("specialWorkspace") + " " + conf.ms("fadeDim")
            + " " + conf.style("windowsIn") + " " + conf.percent("windowsIn", 0f) + " " + conf.style("workspaces"));
        StringBuilder curve = new StringBuilder();
        for (float t = 0f; t <= 1.001f; t += 0.25f) {
            curve.append(String.format(java.util.Locale.ROOT, "%.3f/%.3f ", conf.curve("windows", t), Bezier.at(Bezier.DEFAULT, t)));
        }
        out.add("hypr curve = " + curve.toString().trim());
        // The fields are read through a method. Read into locals between these calls, Skidfuscator
        // moves the calls past the reads (tools/HoistCheck.java; it broke this probe in its
        // first builds), which is a probe's own bug and not what this checks.
        String layoutError = conf.apply("general:layout", "spiral");
        String gapsError = conf.apply("general:gaps_in", "4 5");
        String gaps = values(conf);
        String unknownError = conf.apply("nope", "1");
        out.add("hypr apply = " + layoutError + " | " + gapsError + " " + gaps + " | " + unknownError);
        String borderError = conf.apply("general:border_size", "2");
        String dimError = conf.apply("decoration:dim_strength", "0.5");
        out.add("hypr apply2 = " + borderError + " " + dimError + " " + values(conf));
        HyprConf broken = new HyprConf();
        broken.parse("general {\n  gaps_in =\n  gaps_out = 9000\n  col.active_border = rgba(\n  layout = spiral\n}\n}\n"
            + "animations {\n  bezier = x, 1, 2\n  animation = ,\n  animation = windows, 1, NaN, nope\n}\n"
            + "bind = SUPER\nbind = HYPER, Q, killactive\nbind = SUPER, nokey, exec\nbind = SUPER, Q, fly\n$a = $a$a\nbind = $a, 1, pin\n"
            + "decoration:dim_strength = 7\nno equals here\n= value\n");
        out.add("hypr broken = " + broken.errors.size() + " gaps " + broken.gapsIn + "/" + broken.gapsOut + " dim " + broken.dimStrength
            + " layout " + broken.layout + " binds " + broken.binds.size() + " windows " + broken.ms("windows"));
        HyprConf repeats = new HyprConf();
        repeats.parse("binde = SUPER, X, resizeactive, 10 0\nbind = SUPER, Y, pin\nbindel = SUPER, Z, exec, muxsh\nbindr = SUPER, W, pin\n");
        StringBuilder flags = new StringBuilder();
        for (HyprConf.Bind bind : repeats.binds) flags.append(bind.keys).append('=').append(bind.repeat).append(' ');
        out.add("hypr repeat = " + flags.toString().trim() + " errors " + repeats.errors.size());
        out.add("hypr known = " + Hypr.known("movetoworkspacesilent") + " " + Hypr.known("fullscreen") + " " + Hypr.names().size());

        Pane a = new Probe("a"), b = new Probe("b"), c = new Probe("c");
        Dwindle tree = new Dwindle();
        tree.insert(a, null, Dwindle.AUTO);
        tree.place(0, 0, 300, 200, 6, null, false);
        tree.insert(b, a, Dwindle.AUTO);
        tree.place(0, 0, 300, 200, 6, null, false);
        tree.insert(c, b, Dwindle.BOTTOM);
        tree.place(0, 0, 300, 200, 6, null, false);
        out.add("dwindle = " + box(a) + " " + box(b) + " " + box(c));
        tree.toggleSplit(c);
        tree.place(0, 0, 300, 200, 6, b, false);
        out.add("dwindle skip = " + box(a) + " " + box(c));
        tree.remove(a);
        tree.place(0, 0, 300, 200, 6, null, false);
        List<Pane> order = new java.util.ArrayList<>();
        tree.order(order);
        out.add("dwindle removed = " + box(b) + " " + box(c) + " " + order.size());
        a.x = 0; a.y = 0; a.w = 100; a.h = 100;
        b.x = 110; b.y = 0; b.w = 100; b.h = 100;
        c.x = 0; c.y = 110; c.w = 100; c.h = 100;
        List<Pane> all = List.of(a, b, c);
        out.add("neighbour = " + (Hypr.neighbour(all, a, 'r') == b) + " " + (Hypr.neighbour(all, a, 'd') == c)
            + " " + (Hypr.neighbour(all, a, 'l') == null) + " " + (Hypr.neighbour(all, c, 'u') == a));
    }

    /** The debug panels' step records, read only where no clock is involved. */
    private static void probeTrace(List<String> out) {
        String id = "\u0000probe-trace";
        Trace.steps(id, "one", "two", "three", "four");
        Trace.arm(id, "one", 40L);
        Trace.fire(id, "one");
        Trace.fire(id, "three");
        Trace.gauge(id, "two", 0.5f, "half");
        Trace.Run run = Trace.get(id);
        Trace.Step one = run.find("one"), two = run.find("two"), three = run.find("three");
        out.add("trace fire = " + one.lastDelay + " " + one.count + " " + one.lastText + " " + one.waiting + " | unarmed " + three.count
            + " " + three.lastDelay);
        out.add("trace gauge = " + two.gauge + " " + two.gaugeText + " busy " + run.busy(0L) + " seq " + run.progress(0L));
        Trace.sample(id, "four", 7f, "seven");
        Trace.sample(id, "four", 3f, "three");
        Trace.Step four = run.find("four");
        out.add("trace sample = " + four.count + " " + four.sample(0) + " " + four.sample(1) + " " + four.lastText);
        Trace.skip(id, "two", "because");
        Trace.clear(id, "one");
        out.add("trace skip = " + two.gauge + " " + two.skipWhy + " busy " + run.busy(0L) + " steps " + run.steps.size());
        for (int i = 0; i < Trace.HISTORY + 5; i++) Trace.sample(id, "four", i, "");
        out.add("trace ring = " + four.count + " " + four.sample(0) + " " + four.sample(Trace.HISTORY - 1));
        Trace.idle(id);
        out.add("trace idle = " + run.busy(0L) + " " + run.progress(0L));

        Notify.show("\u0000probe", "same", Notify.GOOD);
        Notify.show("\u0000probe", "same", Notify.GOOD);
        Notify.show("\u0000probe", "other", Notify.BAD);
        out.add("notify = " + Notify.active());
    }

    private static String values(HyprConf conf) {
        return conf.gapsIn + " " + conf.gapsOut + " " + conf.borderSize + " " + conf.dimStrength;
    }

    private static final String PROBE_DESK = "\u0000probe", PROBE_OTHER = "\u0000probe2";

    /** Groups, pins, moving between desks and closing, on two scratch desks, both layouts kept in step. */
    private static void probeDesk(List<String> out) {
        Desk desk = Desk.of(PROBE_DESK), other = Desk.of(PROBE_OTHER);
        Pane a = new Probe("a"), b = new Probe("b"), c = new Probe("c"), d = new Probe("d");
        try {
            for (Pane p : List.of(a, b, c)) {
                desk.windows.add(p);
                desk.tile(p, null, Desk.AFTER);
                desk.focus(p);
            }
            out.add("desk tiled = " + state(desk));

            desk.focus(b);
            desk.toggleGroup(b);
            desk.windows.add(d);
            desk.joinGroup(d, b.group);
            out.add("desk group = " + state(desk) + " tabs " + names(b.group.members) + " showing " + name(b.group.active)
                + " hidden-b " + Group.hidden(b));
            desk.cycleGroup(d.group, 1);
            out.add("desk cycle = " + state(desk) + " showing " + name(d.group.active));
            desk.close(b);
            out.add("desk close tab = " + state(desk) + " showing " + name(d.group.active) + " tabs " + names(d.group.members));
            desk.toggleGroup(d);
            out.add("desk ungroup = " + state(desk) + " group " + (d.group == null));

            desk.togglePin(c);
            out.add("desk pin = " + state(desk) + " pins " + names(Desk.PINS) + " floating " + c.floating);
            out.add("desk pin stays = " + state(desk) + " | " + state(other));
            desk.togglePin(c);
            out.add("desk unpin = " + state(desk) + " pins " + Desk.PINS.contains(c));

            desk.moveTo(a, other);
            out.add("desk move = " + state(desk) + " | " + state(other));
            other.moveInColumns(a, true, false);
            out.add("desk movecol = " + state(other));
            other.swap(a, c);
            out.add("desk swap = " + state(other));
            desk.close(d);
            out.add("desk last = " + state(desk) + " afters " + desk.afters.size());
        } finally {
            for (Pane p : List.of(a, b, c, d)) Desk.PINS.remove(p);
            Desk.remove(PROBE_DESK);
            Desk.remove(PROBE_OTHER);
        }
    }

    /** A desk as the probe sees it: its columns, its dwindle tree's order, its floating panes and focus. */
    private static String state(Desk desk) {
        StringBuilder cols = new StringBuilder();
        for (Desk.Column column : desk.columns) cols.append('[').append(names(column.panes)).append(']');
        List<Pane> tree = new java.util.ArrayList<>();
        desk.dwindle.order(tree);
        return "cols " + cols + " tree " + names(tree) + " float " + names(desk.floating()) + " focus " + name(desk.focused)
            + " windows " + desk.windows.size();
    }

    private static String names(List<Pane> panes) {
        StringBuilder out = new StringBuilder();
        for (Pane p : panes) out.append(name(p));
        return out.toString();
    }

    private static String name(Pane p) {
        return p == null ? "-" : p.title();
    }

    /** A pane popping in and starting a move, and a workstation slide: each read at its first instant. */
    private static void probeMotion(List<String> out) {
        Pane p = new Probe("p");
        p.targetX = 100;
        p.targetY = 50;
        p.targetW = 200;
        p.targetH = 100;
        boolean moving = p.animate(0f);
        out.add("motion popin = " + moving + " " + rect(p));
        p.snap();
        p.targetX = 300;
        moving = p.animate(0f);
        out.add("motion move = " + moving + " " + rect(p) + " appear " + (p.appear(p.openedAt) == 0f));

        Desk from = Desk.of(PROBE_DESK), to = Desk.of(PROBE_OTHER);
        try {
            Slide slide = new Slide();
            slide.track(from, 0, 1000L);
            slide.track(to, 1, 1000L);
            boolean running = slide.running(1000L);
            out.add("slide = " + running + " " + (slide.from() == from) + " " + slide.arriving(1000L, 100) + " " + slide.leaving(1000L, 100)
                + " later " + slide.running(1000L + 60000L) + " " + (slide.from() == null));
            slide.track(from, 0, 2000L);
            out.add("slide back = " + slide.running(2000L) + " " + slide.arriving(2000L, 100) + " " + slide.leaving(2000L, 100));
        } finally {
            Desk.remove(PROBE_DESK);
            Desk.remove(PROBE_OTHER);
        }

        out.add("decor = " + Integer.toHexString(Decor.mix(0xFF000000, 0x80FFFFFF, 0.5f)) + " " + Integer.toHexString(Decor.fade(0xC0123456, 0.5f))
            + " " + Integer.toHexString(Decor.colorAt(new int[] {0xFF000000, 0xFFFF0000, 0xFFFFFFFF}, 0.75f)));
        // Decor turns a vertical gradient fill a quarter to draw a horizontal edge: where the
        // fill's corners land, for an edge from x=10 to x=30 along y=20..21.
        org.joml.Matrix3x2f turn = new org.joml.Matrix3x2f().translate(10, 21).rotate((float) (-Math.PI / 2));
        StringBuilder corners = new StringBuilder();
        float[] points = {0, 0, 1, 0, 0, 20, 1, 20};
        for (int i = 0; i + 1 < points.length; i += 2) {
            org.joml.Vector2f v = turn.transformPosition(new org.joml.Vector2f(points[i], points[i + 1]));
            corners.append(Math.round(v.x)).append(',').append(Math.round(v.y)).append(' ');
        }
        out.add("decor turn = " + corners.toString().trim());
        out.add("template = " + HyprConf.untouchedOldTemplate(HyprConf.template()) + " " + HyprConf.untouchedOldTemplate("x")
            + " kp " + HyprConf.keyCode("KP_Enter"));
    }

    private static String rect(Pane p) {
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2fx%.2f", p.x, p.y, p.w, p.h);
    }

    private static String box(Pane p) {
        return Math.round(p.targetX) + "," + Math.round(p.targetY) + "," + Math.round(p.targetW) + "x" + Math.round(p.targetH);
    }

    /** A pane with nothing in it, for laying out; named for the probe's answers. */
    private static final class Probe extends Pane {
        private final String name;

        Probe(String name) {
            this.name = name;
        }

        @Override
        String title() {
            return name;
        }

        @Override
        void render(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, long now, int mouseX, int mouseY,
                    boolean focused, List<Term.Hit> hits, Term.Hit hovered) {
        }

        @Override
        void close() {
        }
    }
}
