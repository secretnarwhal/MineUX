package com.mineux.core.ui.term;

import net.minecraft.client.Minecraft;

/**
 * {@code quit}/{@code shutdown} and {@code reboot}: the game closing, or closing and starting
 * again ({@link Reboot}), each only once a floating prompt has been answered yes. Quitting is
 * vanilla's own Quit Game -- the main loop ends and, in a world, the world is saved on the way
 * out. A reboot closes the game the same way and starts it again once it has gone
 * ({@link Reboot#closeThenStart}): with no loader of its own to hand a running game over to,
 * MineUX never has two games up at once.
 */
final class Power {

    private Power() {}

    static void quit(Shell.Host host) {
        host.confirm("exit minecraft?", inWorld() ? "the world is saved, then the game closes" : "the game closes",
            "exit", Power::stop);
    }

    static void reboot(Shell shell, Shell.Host host) {
        com.mineux.core.RebootHook hook = com.mineux.core.DeskExtensions.rebootHook();
        String busy = hook == null ? null : hook.busy();
        if (busy != null) {
            shell.error("reboot: " + busy);
            return;
        }
        String cannot = Reboot.problem();
        if (cannot != null) {
            shell.error("reboot: " + cannot);
            return;
        }
        boolean mustClose = Reboot.mustClose();
        // A host's own reboot (the new game first, say) takes what it says it takes; the rest is ours.
        boolean hooked = hook != null && hook.takes(mustClose);
        String detail = hooked ? hook.detail() : mustClose ? "saves the world, closes, then starts again" : "the game closes, then starts again";
        host.confirm("reboot minecraft?", detail, "reboot", () -> {
            String failed = hooked ? hook.reboot() : Reboot.closeThenStart();
            if (failed != null) shell.error("reboot: " + failed);
        });
    }

    static void stop() {
        Minecraft.getInstance().stop();
    }

    private static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null;
    }
}
