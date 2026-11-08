package com.mineux.core.ui.term;

import com.mojang.blaze3d.platform.InputConstants;
import com.mineux.core.DeskExtensions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * SUPER+number in game, with the desktop closed: {@code hyprland.conf}'s {@code workspace}
 * binds switch workstation from anywhere, as they do on the desktop -- under a screen the
 * server opened too. Its {@code opacity} binds (SUPER+K, SUPER+L) fade the pinned windows.
 * <p>
 * The same keys are the hotbar's, so while SUPER is held the number keys stop being hotbar
 * keys: every press queued on a hotbar mapping is taken off it here, at the head of
 * {@code handleKeybinds}, before vanilla drains the queue into a slot change. Those presses are
 * also how a quick tap is seen at all -- one that goes down and up between two ticks never
 * shows in a poll of the key, but it always leaves a click on its mapping. Keys that are not
 * hotbar keys are polled for their press instead.
 * <p>
 * SUPER counts as held for a moment after it is let go, so a number pressed with it and a SUPER
 * released a hair before the tick reads it still does what it was pressed with.
 * <p>
 * That is the old way, kept for a loader without the key hook. With it ({@link #keyPress}) the
 * press is taken as it arrives, before the game or any screen sees it, so SUPER+number never
 * also does what the number alone does: no slot change, no hotbar swap in a container, no digit
 * in chat.
 */
public final class InGameKeys {

    /** How long SUPER still counts as held after it goes up. */
    private static final long SUPER_GRACE_MS = 150;

    private static long superSeenAt;
    /** Bound keys down at the last look, so a press is seen once. */
    private static final Set<Integer> DOWN = new HashSet<>();

    /** How long after a swallowed press the character it types can still arrive. */
    private static final long CHAR_FOLLOWS_MS = 100;

    /** Set by the first key the loader's hook hands over: from then on nothing here polls. */
    private static boolean hooked;
    private static long eatCharUntil;

    private InGameKeys() {}

    /**
     * Every key, before vanilla: true to swallow it, for a {@code workspace} bind pressed with
     * exactly its modifiers held. A repeat is swallowed too, and switches nothing. Not under
     * this client's own screens, which have their own keys.
     */
    public static boolean keyPress(KeyEvent event, int action) {
        hooked = true;
        Minecraft mc = Minecraft.getInstance();
        if (action == GLFW.GLFW_RELEASE || mc.getWindow() == null || mc.player == null) return false;
        Screen screen = mc.gui.screen();
        if (screen != null && own(screen)) return false;
        long handle = mc.getWindow().handle();
        if (handle == 0L) return false;

        HyprConf.Bind bind = bound(HyprConf.get().binds, event.key(), HyprConf.heldMods(handle));
        if (bind == null) return false;

        boolean fade = Objects.equals("opacity", bind.dispatcher);
        if (fade && (action == GLFW.GLFW_PRESS || bind.repeat)) fadePinned(bind.arg);
        else if (!fade && action == GLFW.GLFW_PRESS) switchTo(bind.arg);
        eatCharUntil = System.currentTimeMillis() + CHAR_FOLLOWS_MS;
        return true;
    }

    /**
     * The {@code workspace} bind on this key with exactly these modifiers held, or null.
     * <p>
     * On its own, and on plain values, so the self-test can run it: this loop sat in
     * {@link #keyPress} until one build shipped it as one that never ended, and every key
     * pressed froze the game.
     */
    static HyprConf.Bind bound(List<HyprConf.Bind> binds, int key, int held) {
        HyprConf.Bind found = null;
        for (int i = 0; i < binds.size() && found == null; i++) {
            HyprConf.Bind bind = binds.get(i);
            boolean workspace = Objects.equals("workspace", bind.dispatcher) || Objects.equals("opacity", bind.dispatcher);
            if (workspace && bind.key == key && HyprConf.glfwMods(bind.mods, HyprConf.superBit()) == held) found = bind;
        }
        return found;
    }

    /** A typed character, before vanilla: true to swallow the one a swallowed press came with. */
    public static boolean charTyped() {
        if (System.currentTimeMillis() > eatCharUntil) return false;
        eatCharUntil = 0L;
        return true;
    }

    /** From the head of {@code handleKeybinds}: in a world, no screen up. */
    public static void beforeKeybinds(Minecraft mc) {
        if (hooked || mc.getWindow() == null || mc.player == null) return;
        long handle = mc.getWindow().handle();
        if (handle == 0L) return;

        long now = System.currentTimeMillis();
        int superBit = HyprConf.superBit();
        int held = HyprConf.heldMods(handle);
        if ((held & superBit) != 0) superSeenAt = now;
        boolean superHeld = now - superSeenAt <= SUPER_GRACE_MS;
        if (superHeld) held |= superBit;

        List<Integer> tapped = superHeld ? drainHotbar(mc) : List.of();
        fire(mc, handle, held, tapped);
    }

    /**
     * Tail of the tick, for a screen somebody else put up -- a server's dialog or menu, the
     * pause screen. Vanilla skips {@code handleKeybinds} under any screen, so the binds are
     * polled here instead; nothing queues on the hotbar mappings there, so there is nothing to
     * drain. Not under this client's own screens, which have their own keys, and not under
     * chat, where the number would also be typed.
     */
    public static void onClientTick(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (hooked || screen == null || own(screen) || ChatScreen.class.isInstance(screen)) return;
        if (mc.getWindow() == null || mc.player == null) return;
        long handle = mc.getWindow().handle();
        if (handle == 0L) return;

        long now = System.currentTimeMillis();
        int superBit = HyprConf.superBit();
        int held = HyprConf.heldMods(handle);
        if ((held & superBit) != 0) superSeenAt = now;
        if (now - superSeenAt <= SUPER_GRACE_MS) held |= superBit;

        fire(mc, handle, held, List.of());
    }

    /** Class#isInstance, not instanceof: the obfuscator breaks instanceof for any class with subclasses. */
    private static boolean own(Screen screen) {
        if (TerminalScreen.class.isInstance(screen)) return true;
        for (Class<?> c : DeskExtensions.overlayScreens()) if (c.isInstance(screen)) return true;
        for (Class<?> c : DeskExtensions.inGameScreens()) if (c.isInstance(screen)) return true;
        for (java.util.function.BooleanSupplier open : DeskExtensions.inGameChecks()) if (open.getAsBoolean()) return true;
        return false;
    }

    /**
     * Takes every queued press off the hotbar mappings, so none of them selects a slot, and
     * says which keys they were.
     */
    private static List<Integer> drainHotbar(Minecraft mc) {
        List<Integer> tapped = new ArrayList<>();
        for (KeyMapping mapping : mc.options.keyHotbarSlots) {
            boolean pressed = false;
            while (mapping.consumeClick()) pressed = true;
            if (!pressed) continue;
            InputConstants.Key key = InputConstants.getKey(mapping.saveString());
            if (key.getType() == InputConstants.Type.KEYSYM) tapped.add(key.getValue());
        }
        return tapped;
    }

    /** Runs each {@code workspace} bind pressed since the last look with exactly its modifiers held. */
    private static void fire(Minecraft mc, long handle, int held, List<Integer> tapped) {
        Set<Integer> downNow = new HashSet<>();
        for (HyprConf.Bind bind : HyprConf.get().binds) {
            boolean fade = Objects.equals("opacity", bind.dispatcher);
            if (!Objects.equals("workspace", bind.dispatcher) && !fade || bind.key < 0) continue;
            boolean down = GLFW.glfwGetKey(handle, bind.key) == GLFW.GLFW_PRESS;
            if (down) downNow.add(bind.key);
            boolean pressed = tapped.contains(bind.key) || down && !DOWN.contains(bind.key);
            if (!pressed || HyprConf.glfwMods(bind.mods, HyprConf.superBit()) != held) continue;
            if (fade) fadePinned(bind.arg);
            else switchTo(bind.arg);
        }
        DOWN.clear();
        DOWN.addAll(downNow);
    }

    /**
     * SUPER+K or SUPER+L in game: the windows pinned over it less or more see-through -- the one
     * last focused on the desktop when it is pinned, or else every pinned one.
     */
    static void fadePinned(String arg) {
        Pane focused = Desk.of(Workstations.current()).focused;
        if (focused != null && Desk.PINS.contains(focused)) {
            Hypr.opacity(focused, arg);
            return;
        }
        for (Pane pin : Desk.PINS) Hypr.opacity(pin, arg);
    }

    /**
     * To the workstation a bind names -- a number (made if free, as SUPER+number does on the
     * desktop), a name, {@code previous}, or a step through the bar ({@code e+1}) -- with a card
     * saying where.
     */
    static void switchTo(String arg) {
        String a = arg.trim().toLowerCase(Locale.ROOT);
        String target;
        if (Objects.equals("previous", a)) {
            target = Workstations.previous();
        } else if (a.startsWith("special")) {
            // The special workspace, made the first time; from it, back to the one before.
            target = Workstations.isSpecial(Workstations.current()) ? Workstations.previous() : Workstations.SPECIAL;
            if (target == null) return;
        } else if (a.contains("+") || a.startsWith("-") || a.startsWith("e-") || a.startsWith("r-") || a.startsWith("m-")) {
            target = relative(a);
        } else {
            target = Workstations.find(arg);
        }

        String error;
        if (target == null) {
            int slot = Workstations.parseSlot(arg);
            if (slot <= 0) return;
            error = Workstations.create(null, slot);
        } else if (target.equals(Workstations.current())) {
            Notify.show("ws", Workstations.tag(target) + " (already on it)", Notify.INFO);
            return;
        } else {
            error = Workstations.open(target);
        }

        if (error != null) {
            Notify.show("ws", error, Notify.BAD);
            return;
        }
        String now = Workstations.current();
        Notify.show("ws", Workstations.tag(now), Notify.NOTE);
    }

    /** {@code e+1}, {@code r-1}, {@code +2}: that many places along the bar, or null past either end. */
    private static String relative(String a) {
        String rel = a.startsWith("e") || a.startsWith("r") || a.startsWith("m") ? a.substring(1) : a;
        int step;
        try {
            step = Integer.parseInt(rel.replace("+", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        List<String> names = Workstations.names();
        int to = names.indexOf(Workstations.current()) + step;
        return to >= 0 && to < names.size() ? names.get(to) : null;
    }
}
