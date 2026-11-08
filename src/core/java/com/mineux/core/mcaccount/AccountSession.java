package com.mineux.core.mcaccount;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mineux.core.MineUX;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;

import java.util.Optional;
import java.util.UUID;

/**
 * Swaps the running client session for a saved account.
 * <p>
 * This is the one place mappings matter. Everything else in this package is plain HTTP, Gson
 * and string handling; here we build a vanilla {@link User} and the services that hang off a
 * session, and write them over the fields the client reads its identity and signing keys out
 * of. The constructor and those fields are version-shaped, so if a build stops compiling
 * after a Minecraft bump it stops here first -- see {@link #buildUser} and {@link #rebuild}.
 * <p>
 * As of 26.2 {@link User} carries no account-type enum -- it is name, uuid, access token and
 * two optionals (xuid and client id) and nothing more -- so an offline account and a
 * Microsoft one build the same object and differ only in whether the token is real.
 * <p>
 * <b>Why more than the user.</b> Setting {@code Minecraft.user} alone is enough to join a
 * server: the auth handshake reads the access token straight off it. It is not enough to
 * <em>sign chat</em>. Vanilla builds a {@link UserApiService}, a {@link ProfileKeyPairManager}
 * and a {@link ReportingContext} once, in its constructor, against whatever session was live
 * at startup, and never rebuilds them. The profile key pair manager is what holds the key
 * that signs outgoing chat and the one a server verifies; leave it pointed at the old session
 * and a switched Microsoft account joins fine but sends unsigned -- or wrongly signed --
 * messages, which a server that enforces chat signing kicks. So the swap rebuilds all three
 * from the new session: a real {@code UserApiService} for a Microsoft login, and
 * {@link UserApiService#OFFLINE} for a cracked one, which is exactly what vanilla uses in a
 * dev environment and which yields a key pair manager that correctly signs nothing.
 * <p>
 * The whole thing is marshalled onto the client thread with {@link Minecraft#execute}, since
 * the switcher calls in off a worker (a login blocks on the network for seconds and must not
 * freeze a frame). A switch is refused outright while connected to a server -- see
 * {@link #canSwitch} -- because swapping the identity mid-connection cannot re-handshake it.
 */
public final class AccountSession {

    private AccountSession() {}

    public static boolean applyOffline(String name, UUID uuid) {
        return apply(buildUser(name, uuid, ""), null);
    }

    public static boolean applyMicrosoft(String name, UUID uuid, String accessToken) {
        return apply(buildUser(name, uuid, accessToken), accessToken);
    }

    /**
     * Whether a switch is allowed right now: only while not joined to a server.
     * <p>
     * A live {@code getConnection()} means a session is on the wire -- multiplayer or the
     * integrated server, both of which authed the identity we are about to pull out from
     * under them. Swapping the user and its signing keys mid-connection does not re-handshake
     * that connection; it just leaves the client disagreeing with the server about who it is,
     * which is worse than refusing. The switcher is a title-screen tool, and this is what
     * keeps it one even if some later entry point opens it somewhere it should not.
     */
    public static boolean canSwitch() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.getConnection() == null;
    }

    /**
     * @param accessToken the Minecraft access token for a real login, or {@code null} for an
     *                    offline account, which selects {@link UserApiService#OFFLINE}
     */
    private static boolean apply(User user, String accessToken) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return false;

        // The hard gate. Checked before the service is built so a refused switch does no work,
        // and enforced here rather than only at the screen because this is the one path every
        // login goes through.
        if (minecraft.getConnection() != null) {
            MineUX.LOGGER.warn("Refused account switch to {} while connected to a server", user.getName());
            return false;
        }

        // The API service is built off the client thread on purpose: creating it for a real
        // token opens an authenticated Yggdrasil service, which is network-shaped work that
        // has no business on the render thread. Only the field writes hop back.
        UserApiService service = userApiService(minecraft, accessToken);

        minecraft.execute(() -> {
            ProfileKeyPairManager keys = ProfileKeyPairManager.create(service, user, minecraft.gameDirectory.toPath());
            ReportingContext reporting = ReportingContext.create(ReportEnvironment.local(), service);
            if (!MineUX.ready() || !MineUX.host().setSession(user, service, keys, reporting)) {
                MineUX.LOGGER.warn("This host cannot switch the session; still {}", minecraft.getUser().getName());
                return;
            }
            MineUX.LOGGER.info("Switched session to {}", user.getName());
        });
        return true;
    }

    private static UserApiService userApiService(Minecraft minecraft, String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) return UserApiService.OFFLINE;

        try {
            return new YggdrasilAuthenticationService(minecraft.getProxy())
                .createUserApiService(accessToken);
        } catch (RuntimeException e) {
            // A bad token should cost signing, not the switch: the account still joins
            // offline-mode servers, and the OFFLINE service keeps the client's state whole.
            MineUX.LOGGER.warn("Could not build user API service; chat signing disabled for this session", e);
            return UserApiService.OFFLINE;
        }
    }

    /**
     * Builds the vanilla session object.
     * <p>
     * THE mapping-sensitive line. This is the 26.2 {@link User} constructor -- name, uuid,
     * access token, then the two optionals (xuid and client id, both empty for a client we
     * authed ourselves). If the game updates and only one thing in this package stops
     * compiling, it is almost certainly this signature; check it against the {@code User} the
     * new version ships and adjust the arguments to match.
     */
    private static User buildUser(String name, UUID uuid, String accessToken) {
        return new User(name, uuid, accessToken, Optional.empty(), Optional.empty());
    }
}
