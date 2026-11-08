package com.mineux.core.ui.term;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A menu screen's buttons as terminal commands: on the main menu Singleplayer is
 * {@code singleplayer} and Quit Game is {@code quit}; on the pause menu (Esc) Back to Game is
 * {@code resume}, and so on.
 * <p>
 * Read off the live title screen every time rather than listed here, so a button another mod
 * adds -- Mod Menu's "Mods", anything added through Fabric's screen events -- becomes a command
 * the same way, named after its label. Running one presses the real button, so it does
 * exactly what clicking it would have, parent screen and all: backing out of the world list
 * comes back to the title screen, which hands itself straight back to the desktop.
 * <p>
 * Vanilla's own buttons are named by their translation key rather than their text, so they
 * keep the same command in every language. A button another mod added is named after that mod
 * ({@link ButtonMods}) -- Mod Menu's "Mods" is {@code mod-menu} -- with its label after the
 * mod's name when the mod added more than one; a button no mod can be traced to is named after
 * what it says.
 */
final class MenuButtons {

    /** One button: the command, what the button says, the button, and the mod that added it (null for vanilla's). */
    record Entry(String name, String label, AbstractButton button, ButtonMods.Mod mod) {
        boolean enabled() {
            return button.active;
        }

        /** What it says, and whose it is: "Mods - Mod Menu". */
        String describe() {
            return mod == null ? label : label + " - " + mod.name();
        }
    }

    /**
     * The shell's own commands a button may not take over. Quit and disconnect are the
     * exceptions: they are the buttons, and the shell runs them its own way (quit asks first;
     * disconnect lands on the main menu rather than vanilla's server list).
     */
    private static final Set<String> RESERVED = new HashSet<>();

    static {
        for (String[] c : Shell.COMMANDS) RESERVED.add(c[0]);
        RESERVED.remove("quit");
        RESERVED.remove("disconnect");
        RESERVED.add("q");
    }

    private final Screen title;
    private final boolean pause;

    MenuButtons(Screen title) {
        this.title = title;
        this.pause = PauseScreen.class.isInstance(title);
    }

    Screen title() {
        return title;
    }

    /** The pause menu's (Esc) buttons rather than the main menu's. */
    boolean pause() {
        return pause;
    }

    /** Which menu, as the fetch and the listings name it. */
    String where() {
        return pause ? "esc desk" : "home desk";
    }

    List<Entry> entries() {
        // A title screen made by the mod itself has never been laid out; its buttons
        // only exist once it has been.
        if (title.children().isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            title.init(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }

        List<AbstractButton> buttons = new ArrayList<>();
        collect(title.children(), buttons, 0);

        List<AbstractButton> shown = new ArrayList<>();
        List<ButtonMods.Mod> mods = new ArrayList<>();
        Map<String, Integer> perMod = new HashMap<>();
        for (AbstractButton button : buttons) {
            if (!button.visible) continue;
            ButtonMods.Mod mod = known(button) != null ? null : ButtonMods.of(button);
            shown.add(button);
            mods.add(mod);
            if (mod != null) perMod.merge(mod.id(), 1, Integer::sum);
        }

        List<Entry> out = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < shown.size(); i++) {
            AbstractButton button = shown.get(i);
            ButtonMods.Mod mod = mods.get(i);
            String name = mod == null ? name(button) : modName(button, mod, perMod.get(mod.id()) > 1);
            if (name.isEmpty()) name = "button";
            if (RESERVED.contains(name)) name = name + "-btn";
            String unique = name;
            for (int n = 2; !taken.add(unique); n++) unique = name + "-" + n;
            out.add(new Entry(unique, label(button), button, mod));
        }
        return out;
    }

    Entry find(String command) {
        for (Entry entry : entries()) if (entry.name().equalsIgnoreCase(command)) return entry;
        return null;
    }

    /**
     * Presses a button as a click would, sound included.
     *
     * @return why it could not be pressed, or null once it has been
     */
    String press(Entry entry) {
        if (!entry.enabled()) return entry.name() + " is disabled right now";
        Minecraft mc = Minecraft.getInstance();
        entry.button().playDownSound(mc.getSoundManager());
        entry.button().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
        return null;
    }

    /** Buttons in the order the screen holds them, looking inside layouts and lists that hold more. */
    private static void collect(List<? extends GuiEventListener> children, List<AbstractButton> out, int depth) {
        if (depth > 4) return;
        for (GuiEventListener child : children) {
            // Class#isInstance, not instanceof: the obfuscator breaks instanceof for any class with subclasses.
            if (AbstractButton.class.isInstance(child)) {
                out.add((AbstractButton) child);
            } else if (ContainerEventHandler.class.isInstance(child)) {
                collect(((ContainerEventHandler) child).children(), out, depth + 1);
            }
        }
    }

    private static String label(AbstractButton button) {
        Component message = button.getMessage();
        String text = message == null ? "" : message.getString().trim();
        return text.isEmpty() ? name(button) : text;
    }

    /** The command a button no mod can be traced to becomes. */
    static String name(AbstractButton button) {
        String known = known(button);
        if (known != null) return known;
        Component message = button.getMessage();
        return message == null ? "" : slug(message.getString());
    }

    /** Vanilla's name for one of its own buttons, or null. */
    private static String known(AbstractButton button) {
        // The copyright line is a button too, to the credits.
        if (PlainTextButton.class.isInstance(button)) return "credits";
        Component message = button.getMessage();
        if (message == null || !TranslatableContents.class.isInstance(message.getContents())) return null;
        return known(((TranslatableContents) message.getContents()).getKey());
    }

    /**
     * A mod's button: named after the mod, since what a button says is often only an icon, a
     * count or a word like "Settings"; with the label after it when the mod added several.
     */
    static String modName(AbstractButton button, ButtonMods.Mod mod, boolean several) {
        String modSlug = slug(mod.name());
        if (modSlug.isEmpty() || modSlug.length() > 20) modSlug = slug(mod.id());
        if (!several) return modSlug;
        String label = labelSlug(button);
        if (label.isEmpty()) return modSlug;
        return label.startsWith(modSlug) ? label : modSlug + "-" + label;
    }

    /** What the button says as a word, without a trailing count: "Mods (42)" is {@code mods}. */
    private static String labelSlug(AbstractButton button) {
        Component message = button.getMessage();
        String slug = message == null ? "" : slug(message.getString());
        return slug.replaceAll("(-[0-9]+)+$", "");
    }

    /**
     * Vanilla's buttons, by translation key: the main menu's, then the pause menu's. Key and
     * command in pairs, flat for the same reason {@code NameTable} is.
     * <p>
     * <b>A table, not a {@code switch} on the key.</b> It was one, and Skidfuscator's flow
     * obfuscation miscompiled it in one obfuscated build: {@code menu.singleplayer} fell through to
     * the next case and came out {@code multiplayer}, so the main menu listed
     * {@code multiplayer} and {@code multiplayer-2}. The same source obfuscated on another
     * run was fine -- the damage is random per build. {@link HashMap#get} does its
     * comparing inside the JDK, which the obfuscator never touches.
     */
    private static final String[] KNOWN_PAIRS = {
        "menu.singleplayer", "singleplayer",
        "menu.multiplayer", "multiplayer",
        "menu.online", "realms",
        "menu.options", "options",
        "menu.quit", "quit",
        "menu.playdemo", "demo",
        "menu.resetdemo", "reset-demo",
        "options.language", "language",
        "narrator.button.language", "language",
        "options.accessibility", "accessibility",
        "narrator.button.accessibility", "accessibility",
        "menu.returnToGame", "resume",
        "gui.advancements", "advancements",
        "gui.stats", "stats",
        "menu.reportBugs", "report-bugs",
        "menu.sendFeedback", "feedback",
        "gui.friends.open", "friends",
        "menu.playerReporting", "player-reporting",
        "menu.multiplayerOptions.button", "multiplayer-options",
        "menu.shareToLan", "lan",
        "menu.disconnect", "disconnect",
        "menu.returnToMenu", "disconnect",
    };

    private static final Map<String, String> KNOWN = new HashMap<>();

    static {
        for (int i = 0; i + 1 < KNOWN_PAIRS.length; i += 2) KNOWN.put(KNOWN_PAIRS[i], KNOWN_PAIRS[i + 1]);
    }

    /** Vanilla's name for one of its own buttons by translation key, or null. */
    static String known(String key) {
        return key == null ? null : KNOWN.get(key);
    }

    /** "Minecraft Realms" becomes {@code minecraft-realms}, "Options..." {@code options}. */
    static String slug(String text) {
        StringBuilder out = new StringBuilder();
        boolean dash = false;
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (dash && out.length() > 0) out.append('-');
                out.append(c);
                dash = false;
            } else {
                dash = true;
            }
        }
        return out.toString();
    }
}
