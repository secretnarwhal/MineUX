package com.mineux.core;

import com.mineux.core.account.SignInHook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * What is added on top of core: the full edition's visual modules, Screens, or the modules of
 * a host mod that builds core into itself. Everything here is registered once, from an init,
 * before the desktop first opens. The desktop port (core) reads these lists: the settings
 * tree reads {@link #moduleFiles()}, the workstations and the settings tree read
 * {@link #configs()}, the shell reads {@link #commands()}.
 */
public final class Extensions {

    private static final List<TrayGroup> TRAY = new ArrayList<>();
    private static final List<String> MODULE_FILES = new ArrayList<>();
    private static final List<ConfigEntry> CONFIGS = new ArrayList<>();
    private static final List<ShellCommand> COMMANDS = new ArrayList<>();
    private static SignInHook signInHook;

    private Extensions() {}

    public static void addTrayGroup(TrayGroup group) {
        for (TrayGroup have : TRAY) {
            if (have.name().equals(group.name())) throw new IllegalArgumentException("tray group " + group.name() + " is already added");
        }
        TRAY.add(group);
    }

    /** The tray groups, in the order they were added. */
    public static List<TrayGroup> trayGroups() {
        return Collections.unmodifiableList(TRAY);
    }

    /**
     * A classpath resource in the {@code modules.txt} format (dir/set lines, cfg= names), read
     * after core's own. Example: {@code /mineux/visuals.txt}.
     */
    public static void addModuleFile(String resource) {
        if (!MODULE_FILES.contains(resource)) MODULE_FILES.add(resource);
    }

    public static List<String> moduleFiles() {
        return Collections.unmodifiableList(MODULE_FILES);
    }

    /**
     * A module config under its {@code cfg=} name: per workstation, like every core config.
     * {@code file} is its json file under the host's config dir. The suppliers are JDK
     * interfaces, so a lambda is fine here.
     */
    public static void addConfig(String cfg, String file, Supplier<Object> live, Runnable commit) {
        for (ConfigEntry have : CONFIGS) {
            if (have.cfg().equals(cfg)) throw new IllegalArgumentException("config " + cfg + " is already added");
        }
        CONFIGS.add(new ConfigEntry(cfg, file, live, commit));
    }

    public static List<ConfigEntry> configs() {
        return Collections.unmodifiableList(CONFIGS);
    }

    /** A shell command typed in muxsh. */
    public static void addCommand(ShellCommand command) {
        for (ShellCommand have : COMMANDS) {
            if (have.name().equals(command.name())) throw new IllegalArgumentException("command " + command.name() + " is already added");
        }
        COMMANDS.add(command);
    }

    public static List<ShellCommand> commands() {
        return Collections.unmodifiableList(COMMANDS);
    }

    /**
     * Replaces or extends the greeter's sign-in (a host's own licence check, say). One hook;
     * setting a second is refused. See {@link SignInHook}.
     */
    public static void setSignInHook(SignInHook hook) {
        if (signInHook != null) throw new IllegalArgumentException("a sign-in hook is already set");
        signInHook = hook;
    }

    /** The sign-in hook, or null for the plain MineUX sign-in. */
    public static SignInHook signInHook() {
        return signInHook;
    }

    /** One registered config. */
    public record ConfigEntry(String cfg, String file, Supplier<Object> live, Runnable commit) {}
}
