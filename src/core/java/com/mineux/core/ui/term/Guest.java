package com.mineux.core.ui.term;

import com.mineux.core.net.MuxApi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * What the social side says to a guest -- nobody signed in to a MineUX account ({@link MuxApi}) --
 * in place of a connection error: friends, DMs, irc and the bell all need an account.
 */
final class Guest {

    static final String NOTE = "you are a guest: sign in to a MineUX account to use friends, irc and the bell";

    private Guest() {}

    static boolean is() {
        return !MuxApi.signedIn();
    }

    /** True, after saying why, when a social command has to wait for a sign-in. */
    static boolean refuse(Shell shell, String what) {
        if (!is()) return false;
        shell.line().t(what + ": ", AMBER).t(NOTE, DIM);
        return true;
    }

    /** The note as a window's lines. */
    static List<Line> lines() {
        List<Line> out = new ArrayList<>();
        out.add(new Line().t("guest", AMBER));
        out.add(new Line().t(NOTE, DIM));
        return out;
    }

    /** The note, drawn in the middle of a program's box. */
    static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
        String head = "guest";
        String text = font.plainSubstrByWidth(NOTE, Math.max(0, w - 12));
        int cy = y + h / 2 - 10;
        g.text(font, head, x + (w - font.width(head)) / 2, cy, AMBER, false);
        g.text(font, text, x + (w - font.width(text)) / 2, cy + 12, DIM, false);
    }
}
