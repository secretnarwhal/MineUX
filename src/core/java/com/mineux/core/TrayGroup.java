package com.mineux.core;

import java.util.List;

/**
 * One group of icons on the right of the bar: the visual modules' groups in the full edition,
 * a host's own groups in a host mod. An abstract class rather than an interface so that
 * nobody can hand one over as a lambda (an obfuscator a host may run over this code, such as Skidfuscator, breaks those).
 * <p>
 * The group is one icon in the bar, lit while any of its modules is on. Hovering it fades its
 * {@link #rows} in under it, an icon each; a click on a row opens that module's settings window
 * (a Glass window holding the whole group, a page per row), a right click flips it. A click on
 * the group's icon opens the window on its first row, a right click turns the whole group off and
 * back on as it was (remembered per workstation). Each row names a module of the settings tree
 * by its path ({@code world/sky}): a {@code dir} of a file added through
 * {@link Extensions#addModuleFile}, whose {@code toggle=} field is the switch.
 * <p>
 * Icons are item ids ({@code minecraft:ender_eye}), turned into items once the registries are up.
 */
public abstract class TrayGroup {

    /** The group's id, as typed in the shell: {@code hud}, {@code world}... */
    public abstract String name();

    /** What the bar shows for it. */
    public String label() {
        return name();
    }

    /** The group icon's item id. */
    public String icon() {
        return "minecraft:paper";
    }

    /** A line on what the group's modules are for, under its window's title. */
    public String blurb() {
        return "";
    }

    /** Its modules, in the order they are shown. Empty: the group is a name in the bar only. */
    public List<Row> rows() {
        return List.of();
    }

    /** Whether it is in the bar only on the special workspace (SUPER+S), as a host may keep some modules to it. */
    public boolean specialOnly() {
        return false;
    }

    /**
     * One module of a group: its path in the settings tree, its label, its icon's item id, and
     * whether it is in the bar's strip as well as in the group's window. A path that starts with
     * {@code !} is a muxsh command rather than a module: its cell runs it (an editor's window, say).
     */
    public record Row(String path, String label, String icon, boolean inStrip) {

        public Row(String path, String label, String icon) {
            this(path, label, icon, true);
        }
    }
}
