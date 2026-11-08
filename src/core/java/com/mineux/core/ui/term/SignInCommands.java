package com.mineux.core.ui.term;

import com.mineux.core.account.Account;
import com.mineux.core.greeter.Greeter;
import com.mineux.core.net.MuxApi;

/**
 * {@code login} and {@code logout}: the MineUX account, through the greeter. {@code login} puts
 * the greeter up (a guest signing in); {@code logout} signs out ({@link Account#signOut()}: the
 * remembered session forgotten, the server told) and puts it up too. Out of a world the greeter
 * gates the home desk again, as at launch; in a world Esc goes back to the game. The social
 * client notices the change through {@link MuxApi#generation()} and reconnects.
 */
final class SignInCommands {

    private SignInCommands() {}

    static void login(Shell shell) {
        if (MuxApi.signedIn()) {
            shell.error("login: signed in as " + MuxApi.user() + " already - logout signs out and opens the greeter");
            return;
        }
        shell.ok("opening the greeter");
        Greeter.show();
    }

    static void logout(Shell shell) {
        if (MuxApi.signedIn()) shell.ok("signed out of " + MuxApi.user());
        else shell.ok("not signed in - opening the greeter");
        Account.signOut();
        Greeter.show();
    }
}
