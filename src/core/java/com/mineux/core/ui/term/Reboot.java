package com.mineux.core.ui.term;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * {@code reboot}: the game closes as Quit Game closes it (a singleplayer world saved on the way
 * out), and a small helper left behind waits on this process's pid before starting the new one.
 * It is PowerShell on Windows and {@code sh} anywhere else; nothing is installed and nothing is
 * left behind.
 * <p>
 * The command is rebuilt from the running JVM rather than asked of the launcher: the same
 * java, the same JVM options and classpath, and the game arguments exactly as Fabric received
 * them. A launcher that starts the game through a wrapper of its own (Prism and MultiMC hand
 * the arguments over on stdin) is skipped: the new game starts straight into Fabric's
 * {@code KnotClient}, which is what the wrapper would have ended up calling. Those arguments
 * include the Minecraft session, so they go to the new JVM in an argument file only this user
 * can read, deleted as soon as the game has read it; they never appear on a command line.
 */
public final class Reboot {

    private static final String KNOT = "net.fabricmc.loader.impl.launch.knot.KnotClient";
    /** Properties a reboot of an older build handed the new game; never passed on again. */
    static final String PROPS = "-Dmineux.reboot.";
    /** Long enough for the new java to have read its argument file before it is deleted. */
    private static final int ARGFILE_SECONDS = 30;

    private Reboot() {}

    /** Why this game cannot be rebooted, or null when it can. */
    public static String problem() {
        try {
            command(List.of());
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    /** A singleplayer world is open: the reboot has to close the game properly first. */
    public static boolean mustClose() {
        return Minecraft.getInstance().isLocalServer();
    }

    /**
     * Closes the game properly, and starts it again once it has gone.
     *
     * @return why not, or null once the game is closing, to be started again
     */
    static String closeThenStart() {
        try {
            List<String> command = command(List.of());
            Path args = writeArgs(command);
            helper(command.get(0), args).start();
        } catch (Exception e) {
            return "could not start the helper: " + (e.getMessage() == null ? e.toString() : e.getMessage());
        }
        Minecraft.getInstance().stop();
        return null;
    }

    /**
     * Everything the desktop saves on its way out, now: its readouts, the workstation open and the
     * desktop's state. For a {@link com.mineux.core.RebootHook} that starts the new game while this
     * one runs on, so the new game reads this state.
     */
    public static void saveDesktop() {
        Readouts.save();
        Workstations.save();
        Workstations.saveState();
    }

    /** Writes everything after java to a new argument file only this user can read. */
    static Path writeArgs(List<String> command) throws IOException {
        Path args = Files.createTempFile("mineux-reboot", ".args");
        args.toFile().setReadable(false, false);
        args.toFile().setReadable(true, true);
        Files.writeString(args, argfile(command.subList(1, command.size())), StandardCharsets.UTF_8);
        return args;
    }

    /**
     * The whole command line this game was started with, java first.
     *
     * @param extra JVM options to add
     */
    static List<String> command(List<String> extra) throws IOException {
        return command(extra, null);
    }

    /**
     * The whole command line this game was started with, java first, for a {@link com.mineux.core.RebootHook}
     * that starts the new game itself: the same java, JVM options, classpath and game arguments.
     *
     * @param extra JVM options to add
     * @param drop  JVM options starting with this are not passed on (the hook's own handoff
     *              properties, which belong to this game only); null for none
     */
    public static List<String> command(List<String> extra, String drop) throws IOException {
        String launch = System.getProperty("sun.java.command");
        String classpath = System.getProperty("java.class.path");
        if (launch == null || launch.isBlank() || classpath == null || classpath.isBlank()) {
            throw new IOException("cannot tell how this game was started");
        }
        String main = launch.trim().split(" ", 2)[0];
        // A launcher's own wrapper (org.prismlauncher.EntryPoint, a jar) reads what it runs from
        // stdin; Fabric's entry point is where it would have gone.
        if (!main.startsWith("net.fabricmc.")) main = KNOT;

        List<String> out = new ArrayList<>();
        out.add(java());
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            // A game a reboot started must not pass the old game's handoff on to the next one.
            if (!arg.startsWith(PROPS) && (drop == null || !arg.startsWith(drop))) out.add(arg);
        }
        out.addAll(extra);
        out.add("-cp");
        out.add(classpath);
        out.add(main);
        out.addAll(Arrays.asList(FabricLoader.getInstance().getLaunchArguments(false)));
        return out;
    }

    private static String java() {
        return ProcessHandle.current().info().command()
            .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", windows() ? "javaw.exe" : "java").toString());
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** One argument a line, quoted, as java's {@code @file} reads them. */
    public static String argfile(List<String> args) {
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            out.append('"');
            for (char c : arg.toCharArray()) {
                switch (c) {
                    case '\\' -> out.append("\\\\");
                    case '"' -> out.append("\\\"");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    default -> out.append(c);
                }
            }
            out.append("\"\n");
        }
        return out.toString();
    }

    /** Waits for this process to end, starts the game again, then deletes the argument file. */
    private static ProcessBuilder helper(String java, Path args) {
        long pid = ProcessHandle.current().pid();
        String dir = System.getProperty("user.dir");
        ProcessBuilder builder;
        if (windows()) {
            String script = "$p = " + pid + "; try { Wait-Process -Id $p -ErrorAction Stop } catch {}; "
                + "Start-Process -FilePath " + ps(java) + " -ArgumentList " + ps("@\"" + args + "\"")
                + " -WorkingDirectory " + ps(dir) + "; "
                + "Start-Sleep -Seconds " + ARGFILE_SECONDS + "; Remove-Item -LiteralPath " + ps(args.toString())
                + " -ErrorAction SilentlyContinue";
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-EncodedCommand", encoded);
        } else {
            String script = "while kill -0 \"$1\" 2>/dev/null; do sleep 0.5; done; "
                + "cd \"$2\" && \"$3\" \"@$4\" >/dev/null 2>&1 & sleep " + ARGFILE_SECONDS + "; rm -f \"$4\"";
            builder = new ProcessBuilder("/bin/sh", "-c", script, "sh", String.valueOf(pid), dir, java, args.toString());
        }
        builder.directory(Path.of(dir).toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        return builder;
    }

    /** A PowerShell string literal. */
    private static String ps(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
