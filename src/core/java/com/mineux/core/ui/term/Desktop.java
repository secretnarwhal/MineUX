package com.mineux.core.ui.term;

/**
 * The screen a terminal is drawn on, as the terminal sees it: the in-game desktop
 * ({@link TerminalScreen}) on a workstation, the main menu or the Esc desk. What a shell command asks
 * of the screen goes through here.
 */
interface Desktop {

    /** A module in a terminal: cd'd into and listed, where its settings are typed. */
    default void openInTerminal(String module) {
    }

    /** {@code exit} or SUPER+Q on a pane. */
    void closeWindow(Pane pane);

    /** {@code close}: close the whole desktop. */
    void quit();

    /** A yes/no question in a floating window of its own ({@link Prompt}); yes runs {@code onYes}. */
    void confirm(String question, String detail, String yes, Runnable onYes);

    void workstationsChanged();

    /** The main menu's buttons as commands, or null anywhere but the main menu. */
    default MenuButtons menu() {
        return null;
    }

    /**
     * Opens a window of that kind ({@code worlds}, {@code servers}, {@code art}) on the desk, or
     * brings the open one forward.
     *
     * @return false where there are no such windows (worlds and servers anywhere but the main menu)
     */
    default boolean openWindow(String kind) {
        return false;
    }

    /** A window manager dispatcher ({@code hyprctl dispatch}); null when it ran, or why it did not. */
    default String dispatch(String dispatcher, String arg) {
        return "hyprctl: no window manager here";
    }
}
