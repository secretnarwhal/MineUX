package com.mineux.core.greeter;

import com.mineux.core.MineUX;
import com.mineux.core.account.Account;
import com.mineux.core.net.MuxApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The gate in front of the home desk. The first title screen of a launch goes to the greeter
 * ({@link GreeterScreen}) instead of the desk; once the user is through -- signed in, or as a
 * guest -- every title screen after that goes straight to the desk, until {@link #show()}
 * (after a sign-out) puts the greeter up again.
 * <p>
 * A remembered session ("stay signed in") is checked with the server first. While that runs
 * the greeter is a black screen -- the same black the desk's boot sequence starts from -- with
 * a "resuming" line after a moment; when the server says yes it goes on to the desk without
 * ever drawing its box, so in practice the greeter is not seen at all. Only a refused or
 * unreachable session shows the box, with why.
 */
public final class Greeter {

    private static boolean passed;
    private static boolean configured;
    /** The launch's remembered session has been offered to a greeter already. */
    private static boolean resumeTaken;

    private Greeter() {}

    /** Whether the greeter has been passed this launch (since the last {@link #show()}). */
    public static boolean passed() {
        return passed;
    }

    /**
     * From {@code Hooks.titleScreen}, with {@code title} showing: true when the greeter took
     * its place, false when the desk should have it.
     */
    public static boolean intercept(TitleScreen title) {
        if (hostSignsIn()) return false;
        configure();
        if (passed) return false;
        Minecraft.getInstance().gui.setScreen(new GreeterScreen(title, takeResume()));
        return true;
    }

    /**
     * The API address from the system property and the dotfile, and the remembered session's
     * check started, once a launch. Done on the first title screen at the latest; harmless to
     * call earlier.
     */
    public static void configure() {
        if (configured) return;
        configured = true;
        MuxApi.configure(LyConfig.load().api);
        Account.startResume();
    }

    /**
     * Puts the greeter up again, as after a sign-out ({@link Account#signOut()} first). Out of
     * a world it gates the home desk as at launch; in a world it is only a way to sign back in,
     * and Esc goes back to the game. Call it from the render thread.
     */
    public static void show() {
        if (hostSignsIn()) {
            MineUX.host().showSignIn();
            return;
        }
        configure();
        passed = false;
        Minecraft mc = Minecraft.getInstance();
        resumeTaken = true;
        mc.gui.setScreen(new GreeterScreen(mc.level == null ? new TitleScreen() : null, false));
    }

    /** A host that signs in to MineUX itself ({@link com.mineux.core.Host#ownSignIn}): the greeter never shows. */
    private static boolean hostSignsIn() {
        return MineUX.ready() && MineUX.host().ownSignIn();
    }

    /** Whether this greeter is the launch's first, which waits on the remembered session. */
    private static boolean takeResume() {
        boolean first = !resumeTaken;
        resumeTaken = true;
        return first;
    }

    /** Through the gate: the next title screen is the desk's. */
    static void pass() {
        passed = true;
    }
}
