package com.mineux.core.ui;

import com.mineux.core.greeter.Dotfile;

import java.util.Locale;

/**
 * How the settings are shown, kept in {@code dotfiles/ui/ui.conf} so it stays put across
 * launches and can be edited by hand:
 * <ul>
 *   <li>{@code commands = window|text}: what typing a module's name does -- open its settings
 *       window on its page (the default), or list it in the terminal as text.</li>
 *   <li>{@code topbar = window|text}: what a click on a topbar icon does -- the same two.</li>
 * </ul>
 * Both kinds of display read the same module list ({@code modules.txt}), so a setting added there
 * shows in either.
 */
public final class GuiChoice {

    public static final String WINDOW = "window";
    public static final String TEXT = "text";

    private static final Dotfile FILE = Dotfile.of("ui/ui.conf", """
        # What typing a module's name does: window (its settings window, on its page) or text
        # (its settings listed in the terminal). `gui commands text` sets it.
        commands = window
        # What clicking a topbar icon does: window or text, as above. `gui topbar text` sets it.
        topbar = window
        """);

    private GuiChoice() {}

    /** Whether typing a module's name opens its window rather than listing it. */
    public static boolean commandsOpenWindows() {
        return !isText(value("commands"));
    }

    /** Whether a click on a topbar icon opens its window rather than listing it in a terminal. */
    public static boolean topbarOpensWindows() {
        return !isText(value("topbar"));
    }

    private static boolean isText(String value) {
        return value != null && (value.startsWith(TEXT) || value.startsWith("term"));
    }

    /** Sets {@code commands} or {@code topbar} to {@link #WINDOW} or {@link #TEXT}. */
    public static void setDisplay(String key, String mode) {
        FILE.write(key, isText(mode) ? TEXT : WINDOW);
    }

    /** A key's value, lower case and trimmed; null when the file does not have it. */
    private static String value(String key) {
        String value = FILE.read().get(key);
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
