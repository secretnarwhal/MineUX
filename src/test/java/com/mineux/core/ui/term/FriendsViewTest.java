
package com.mineux.core.ui.term;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The friends app's keys and wheel, without drawing it. */
class FriendsViewTest {

    private static final class NoHost implements Shell.Host {
        public void openGui(int tab) {}
        public void openBinds() {}
        public void exit() {}
        public void quit() {}
        public void signOut() {}
        public void art(String arg) {}
        public String artName() { return "art"; }
        public String[] artNames() { return new String[] {"art"}; }
        public void launch(String app) {}
        public void workstationsChanged() {}
    }

    private static FriendsView view() {
        return new FriendsView(new Shell(new NoHost()), "");
    }

    private static void type(FriendsView view, String text) {
        for (int i = 0; i < text.length(); i++) view.charTyped(new CharacterEvent(text.charAt(i)));
    }

    private static KeyEvent key(int key) {
        return new KeyEvent(key, 0, 0);
    }

    /** Opening the app leaves the add-friend line alone: typing goes nowhere, q quits. */
    @Test
    void opensWithNothingFocused() {
        FriendsView view = view();
        assertFalse(view.typing());
        type(view, "abc");
        assertEquals("", view.typed());
        view.keyPressed(key(GLFW.GLFW_KEY_Q));
        assertTrue(view.done());
    }

    /** Once the field is clicked it takes the keys, q included; tab gives them back. */
    @Test
    void clickedFieldTakesTheKeys() {
        FriendsView view = view();
        view.type(true);
        type(view, "qbert");
        view.keyPressed(key(GLFW.GLFW_KEY_Q));
        assertFalse(view.done());
        assertEquals("qbert", view.typed());
        view.keyPressed(key(GLFW.GLFW_KEY_BACKSPACE));
        assertEquals("qber", view.typed());
        view.keyPressed(key(GLFW.GLFW_KEY_TAB));
        assertFalse(view.typing());
        type(view, "x");
        assertEquals("qber", view.typed());
    }

    /** Before anything is drawn there is no profile pane, so the wheel goes to the list; never below zero. */
    @Test
    void wheelNeverScrollsAboveTheTop() {
        FriendsView view = view();
        view.scrolled(10, 1);
        assertEquals(0, view.listScroll());
        view.scrolled(10, 0 - 1);
        assertEquals(1, view.listScroll());
        assertEquals(0, view.cardScroll());
    }
}
