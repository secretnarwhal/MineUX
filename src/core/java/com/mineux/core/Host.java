package com.mineux.core;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/**
 * What MineUX's core asks of whatever runs it: MineUX's own Fabric layer, or a host that builds
 * core into a mod of its own. An abstract class rather than an interface (no lambdas, and the
 * branding getters below have MineUX's own values as defaults): extend it as a named class.
 * <p>
 * Everything after {@link #setSession} is the host's brand and the few things a host may want
 * its own way. Each default is what MineUX itself shows, so a host overrides only what differs.
 * They are read often (every frame for some), so keep them cheap.
 */
public abstract class Host {

    /** {@link MineUX#FULL} or {@link MineUX#LITE}. */
    public abstract String edition();

    /** Where MineUX keeps its files: {@code config/mineux} for MineUX itself. */
    public abstract Path configDir();

    /** The brand drawn in the bar and the prompt: {@code mineux} for MineUX itself. */
    public abstract String brand();

    /**
     * Puts another Minecraft session in place, for the account switcher: the user and the three
     * services vanilla builds from it once at startup and never again (chat signing, the profile
     * key pair, reporting). Called on the client thread with no server connection. Vanilla has no
     * setters for these, so the host writes them through its own accessor mixin.
     *
     * @return false when this host cannot, and nothing was changed
     */
    public abstract boolean setSession(User user, UserApiService service, ProfileKeyPairManager keys, ReportingContext reporting);

    // ---- brand -----------------------------------------------------------

    /** The shell's name, in its prompt words, help, neofetch and the window manager's {@code exec}: {@code muxsh}. */
    public String shellName() {
        return "muxsh";
    }

    /** neofetch's {@code os} line: {@code MineUX <version>}. */
    public String os() {
        String version = modVersion("mineux");
        return version.isEmpty() ? "MineUX" : "MineUX " + version;
    }

    /** What the special workspace (SUPER+S) is called on its tab and its cards: {@code special}. Typing it opens it too. */
    public String specialName() {
        return "special";
    }

    /** The key that opens and closes the desktop in a world, a GLFW key code: right shift. Read on every press. */
    public int deskKey() {
        return GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    /**
     * The Microsoft (Azure) application the account switcher signs in with; blank turns the
     * browser sign-in off ({@code accounts login} says so and points at session tokens). MineUX's
     * own application is waiting on Mojang's approval, so it is blank for now.
     */
    public String microsoftClientId() {
        return "";
    }

    /** The Discord application the card is shown under; blank for none (the card stays off unless rpc.json names one). */
    public String discordAppId() {
        return "";
    }

    /** The large art's asset key in that application. */
    public String discordArt() {
        return "mineux";
    }

    /** The large art's hover text. */
    public String discordArtText() {
        return brand();
    }

    /** The site the card's button opens, {@code https://} and all; its label is the address without the scheme. */
    public String site() {
        return "https://mineux.com";
    }

    // ---- sign-in -----------------------------------------------------------

    /**
     * True when the host signs in to MineUX itself, so MineUX's greeter is never put up: not at
     * the first title screen, and not when something asks for it ({@link #showSignIn}). Default
     * false: the greeter is MineUX's sign-in.
     */
    public boolean ownSignIn() {
        return false;
    }

    /** With {@link #ownSignIn}: something asked for the sign-in (the account's session ended, say). Called on the client thread. */
    public void showSignIn() {
    }

    // ---- input -----------------------------------------------------------

    /** Whether a key is held, for the binds ({@code bind}): the physical key, as GLFW has it. */
    public boolean keyDown(Window window, int key) {
        return InputConstants.isKeyDown(window, key);
    }

    /** Whether a mouse button (GLFW's number) is held, for the binds. */
    public boolean mouseDown(long window, int button) {
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }

    /** A Fabric mod's version, or blank where there is no such mod (or no Fabric, in a test). */
    protected static String modVersion(String mod) {
        try {
            return FabricLoader.getInstance().getModContainer(mod)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }
}
