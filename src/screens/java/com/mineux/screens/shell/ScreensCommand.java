package com.mineux.screens.shell;

import com.mineux.core.CommandOut;
import com.mineux.core.ShellCommand;
import com.mineux.screens.Screens;
import com.mineux.screens.ScreensHooks;
import com.mineux.screens.bridge.CaptureTarget;
import com.mineux.screens.capture.CaptureManager;
import com.mineux.screens.displays.Anchor;
import com.mineux.screens.displays.CapturedScreenDisplay;
import com.mineux.screens.net.RemoteScreen;
import com.mineux.screens.net.Sharing;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * muxsh's {@code screens}: list, the menu, sharing, the freecam, and adding screens by name.
 * Game thread only (the shell runs there).
 */
public final class ScreensCommand extends ShellCommand {

    @Override
    public String name() {
        return "screens";
    }

    @Override
    public String help() {
        return "your monitors and windows in the world: screens [list|sources|add m1|remove 1|open|share 1|unshare 1|freecam]";
    }

    @Override
    public void run(String[] args, CommandOut out) {
        String verb = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        CaptureManager manager = Screens.manager();
        Minecraft mc = Minecraft.getInstance();
        if (verb.equals("list") || verb.equals("ls")) list(manager, out);
        else if (manager == null) out.error("screens: capturing needs Windows; friends' screens still show");
        else if (mc.player == null) out.error("screens: join a world first");
        else if (verb.equals("open") || verb.equals("menu")) ScreensHooks.openMenu(mc);
        else if (verb.equals("sources")) sources(manager, out);
        else if (verb.equals("add")) add(manager, args, out);
        else if (verb.equals("remove") || verb.equals("rm")) {
            CapturedScreenDisplay d = pick(manager, args, out);
            if (d != null) manager.stopCapture(d);
        } else if (verb.equals("share")) share(manager, args, out);
        else if (verb.equals("unshare")) {
            CapturedScreenDisplay d = pick(manager, args, out);
            if (d != null) Screens.sharing().unshare(d);
            if (d != null) out.println("screens: " + (args.length > 1 ? args[1] : "") + " no longer shared");
        } else if (verb.equals("freecam")) manager.toggleFreecam();
        else out.error("screens: no such command '" + verb + "'; " + help());
    }

    private static void list(CaptureManager manager, CommandOut out) {
        if (manager == null || manager.displays.isEmpty()) out.println("no screens of yours; `screens sources`, then `screens add m1`, or press B");
        else {
            for (int i = 0; i < manager.displays.size(); i++) {
                CapturedScreenDisplay d = manager.displays.get(i);
                String live = d.shared ? "  LIVE, " + d.viewers + " watching, " + Screens.sharing().sent(d) + " frames sent" : "";
                out.println((i + 1) + "  " + d.target.title() + "  (" + Anchor.title(d.anchorMode()).toLowerCase(Locale.ROOT) + ")" + live);
            }
        }
        Sharing sharing = Screens.sharing();
        String why = sharing.unavailable();
        out.println(why != null ? "sharing: " + why : "sharing: relay " + (sharing.connected() ? "connected" : "connecting") + " (" + Sharing.relayUrl() + ")");
        int others = 0;
        for (RemoteScreen r : sharing.remotes.all()) {
            others++;
            out.println("  " + r.info.ownerName + "'s screen  " + (r.visible ? "showing" : sharing.remotes.watching(r.info.id) ? "watching" : "out of range"));
        }
        if (others == 0 && why == null) out.println("  no friend shares a screen here (friends only, with screens enabled on both sides)");
    }

    private static void sources(CaptureManager manager, CommandOut out) {
        CaptureTarget[] monitors = manager.monitors();
        for (int i = 0; i < monitors.length; i++) {
            CaptureTarget m = monitors[i];
            out.println("m" + (i + 1) + "  " + m.title() + "  " + m.width() + "x" + m.height() + (m.primary() ? ", primary" : ""));
        }
        CaptureTarget[] windows = manager.windows();
        for (int i = 0; i < windows.length; i++) {
            out.println("w" + (i + 1) + "  " + windows[i].title() + "  (" + windows[i].detail() + ")");
        }
    }

    private static void add(CaptureManager manager, String[] args, CommandOut out) {
        if (args.length < 2 || args[1].length() < 2) {
            out.error("screens add m<n> | w<n> (see screens sources)");
            return;
        }
        String which = args[1].toLowerCase(Locale.ROOT);
        CaptureTarget[] targets = which.charAt(0) == 'm' ? manager.monitors() : manager.windows();
        int n = number(which.substring(1));
        if (n < 1 || n > targets.length) {
            out.error("screens: no " + which + "; see screens sources");
            return;
        }
        try {
            CapturedScreenDisplay d = manager.startCapture(targets[n - 1]);
            out.println("screens: showing " + d.target.title() + " as screen " + manager.displays.size());
        } catch (RuntimeException e) {
            out.error("screens: could not capture " + targets[n - 1].title() + ": " + e.getMessage());
        }
    }

    private static void share(CaptureManager manager, String[] args, CommandOut out) {
        CapturedScreenDisplay d = pick(manager, args, out);
        if (d == null) return;
        boolean confirm = args.length > 2 && (args[2].equals("confirm") || args[2].equals("-y") || args[2].equals("yes"));
        String why = Screens.sharing().share(d, confirm);
        if (why == null) out.println("screens: " + d.target.title() + " is LIVE on this server; `screens unshare " + args[1] + "` stops it");
        else if (why.startsWith("Confirm")) out.error(why + ". Type `screens share " + args[1] + " confirm` to share it.");
        else out.error("screens: " + why);
    }

    private static CapturedScreenDisplay pick(CaptureManager manager, String[] args, CommandOut out) {
        int n = args.length > 1 ? number(args[1]) : 0;
        if (n < 1 || n > manager.displays.size()) {
            out.error("screens: no screen " + (args.length > 1 ? args[1] : "given") + "; `screens list` numbers them");
            return null;
        }
        return manager.displays.get(n - 1);
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
