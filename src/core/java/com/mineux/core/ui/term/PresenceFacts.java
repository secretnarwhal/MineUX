package com.mineux.core.ui.term;

import java.util.List;

/**
 * What the Discord presence ({@code com.mineux.core.rpc.Presence}) reads off the terminal, so
 * that it does not have to reach into the terminal's package itself.
 */
public final class PresenceFacts {

    private PresenceFacts() {}

    /** Whether the special workspace (SUPER+S) is the workstation open now: where a host may keep modules of its own. */
    public static boolean special() {
        return Workstations.isSpecial(Workstations.current());
    }

    /** The workstation open now, by name. */
    public static String workstation() {
        return Workstations.current();
    }

    /**
     * The names of the modules switched on, in tree order, for the HUD's module list. A switch
     * under another (clicker/autoclicker) goes by its own name.
     */
    public static List<String> onNames() {
        List<String> out = new java.util.ArrayList<>();
        for (Node n : Modules.toggleable()) if (running(n)) out.add(n.name);
        return out;
    }

    /**
     * Whether a module really runs: its own switch on and every switch above it too, as the tray
     * glows it. esp/cards defaults on, but with esp off it draws nothing and is not listed.
     */
    public static boolean running(Node n) {
        return n.on() && Tray.parentsOn(n);
    }

    /**
     * Every module key that is bound, for the HUD's keybind list: {label, key, "on" or "off" or
     * "" for a key that is no module's switch}. A module's own toggle key is labelled with the
     * module's name, any other key with the module's name and the key's.
     */
    public static List<String[]> binds() {
        List<Node> all = new java.util.ArrayList<>();
        Modules.root().walk(all);
        List<String[]> out = new java.util.ArrayList<>();
        for (Node n : all) {
            for (Setting s : n.settings) {
                if (!s.isKey) continue;
                Object raw = s.raw();
                if (!Integer.class.isInstance(raw) || (Integer) raw == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) continue;
                String label = java.util.Objects.equals("key", s.key) ? n.name : n.name + " " + s.key;
                String state = n.toggle == null ? "" : n.on() ? "on" : "off";
                out.add(new String[] {label, Setting.keyName((Integer) raw), state});
            }
        }
        return out;
    }

    /** Modules switched on, and how many could be: {on, of}. */
    public static int[] modules() {
        List<Node> toggles = Modules.toggleable();
        int on = 0;
        for (Node n : toggles) if (running(n)) on++;
        return new int[] {on, toggles.size()};
    }
}
