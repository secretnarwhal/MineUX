package com.mineux.core.ui.term;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A directory in the terminal's tree: a module, or a group of them.
 * <p>
 * A module is a directory because that is what it is to somebody at a prompt -- somewhere
 * to {@code cd} into and {@code ls}, with its settings as the entries. The pvp tabs are the
 * one place the tree is more than one level deep: {@code clicker} is a directory with its
 * own master switch, holding {@code autoclicker}, {@code hitselect} and {@code reach}.
 */
final class Node {

    final String name;
    final String desc;
    final Node parent;
    final List<Node> children = new ArrayList<>();
    final List<Setting> settings = new ArrayList<>();

    /** The classic panel's tab for this module, for {@code gui}; -1 when it has none. */
    int tab = -1;

    /** The on/off switch {@code toggle} flips; null for a directory that is only a group. */
    Setting toggle;

    /** Other names it answers to when typed ({@code alias=} on its dir line): an old name kept for binds written against it. */
    String[] aliases = new String[0];

    /** Whether {@code name} is one of its {@link #aliases}. */
    boolean aliased(String name) {
        for (String a : aliases) if (a.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** In a settings window, its rows fold open when its switch goes on and shut when it goes off. */
    boolean follows;

    /** A line under its rows that says what the module last did, read every frame; null for none. */
    Supplier<String> stat;

    /** A button row under its settings that does something rather than set something (browse files); null for none. */
    Act act;

    /** A button row: what it says, its hint, and what a click does. */
    record Act(String label, String hint, Runnable action) {}

    private final Supplier<Object> owner;
    private final Runnable commit;

    Node(String name, String desc, Node parent, Supplier<Object> owner, Runnable commit) {
        this.name = name;
        this.desc = desc;
        this.parent = parent;
        this.owner = owner;
        this.commit = commit;
    }

    // ---- building -------------------------------------------------------

    Node dir(String name, String desc, int tab, Supplier<Object> owner, Runnable commit) {
        Node child = new Node(name, desc, this, owner, commit);
        child.tab = tab;
        children.add(child);
        return child;
    }

    /** A child that edits a nested object -- {@code PvpConfig#reach} -- but saves the whole config. */
    Node sub(String name, String desc, Supplier<Object> owner) {
        return dir(name, desc, tab, owner, commit);
    }

    Node toggle(String field) {
        this.toggle = new Setting(this, "enabled", field, "master switch", owner);
        return this;
    }

    Setting add(String key, String field, String desc) {
        Setting setting = new Setting(this, key, field, desc, owner);
        settings.add(setting);
        return setting;
    }

    Setting num(String key, String field, double min, double max, String desc) {
        return add(key, field, desc).range(min, max);
    }

    // ---- reading --------------------------------------------------------

    void commit() {
        if (commit != null) commit.run();
    }

    boolean on() {
        return toggle != null && toggle.bool();
    }

    String path() {
        if (parent == null) return "~";
        return parent.path() + "/" + name;
    }

    Node child(String name) {
        for (Node child : children) if (child.name.equalsIgnoreCase(name)) return child;
        return null;
    }

    Setting setting(String name) {
        for (Setting setting : settings) if (setting.matches(name)) return setting;
        if (toggle != null && name.equalsIgnoreCase("enabled")) return toggle;
        return null;
    }

    /** Every directory under this one, depth first, this one excluded. */
    void walk(List<Node> out) {
        for (Node child : children) {
            out.add(child);
            child.walk(out);
        }
    }
}
