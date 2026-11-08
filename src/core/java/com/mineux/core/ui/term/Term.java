package com.mineux.core.ui.term;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The terminal's scrollback: lines made of coloured spans, any of which can be clicked.
 * <p>
 * Public as an extension's drawing kit (see {@code Extensions} and {@code DeskExtensions}): a command writes
 * {@link Line}s through {@code CommandOut.line()}, and a program or a window of an extension's
 * draws {@link Span}s and adds {@link Hit}s through {@code TermProgram.Clicks}. The palette below
 * is the terminal's. Fields not marked public are the desktop's own.
 * <p>
 * A span can run a command when clicked, or drop text into the prompt for editing, and its
 * text and colour can be live -- a {@code [ON ]} printed by {@code ls} goes on reading the
 * switch it stands for, so a listing never shows a state that has since changed.
 */
public final class Term {

    // ---- palette --------------------------------------------------------
    public static final int FG = 0xFFD7DCE6;
    public static final int DIM = 0xFF6E7487;
    public static final int FAINT = 0xFF464B5C;
    public static final int GOLD = 0xFFEFC648;
    public static final int AMBER = 0xFFFFA928;
    public static final int GREEN = 0xFF5AF78E;
    public static final int CYAN = 0xFF57C7FF;
    public static final int BLUE = 0xFF6C9EFF;
    public static final int MAGENTA = 0xFFFF6AC1;
    public static final int RED = 0xFFFF5C57;

    private Term() {}

    public static final class Span {
        final String text;
        final int color;
        public Supplier<String> liveText;
        public IntSupplier liveColor;
        /** Run as a command when clicked. */
        public String run;
        /** A switch: its click runs {@link #run} without printing, since the span itself shows the result. */
        public boolean quiet;
        /** Put into the prompt when clicked, for the user to finish. */
        public String fill;
        /** Done in place when clicked, with nothing typed or printed: opening a {@link Drawer}. */
        public Runnable action;
        /** Shown while the span is hovered. */
        public String hint;
        /** Absolute x inside the line, in GUI px; -1 to follow the previous span. */
        public int x = -1;
        /** A timing slider for this setting, drawn {@link #width} wide in place of text. */
        Setting slider;
        int width;
        /** A server whose icon this span draws, tiny, in place of text. */
        net.minecraft.client.multiplayer.ServerData server;
        /** A menu button whose icon (its mod's, or its sprite) this span draws, tiny, in place of text. */
        MenuButtons.Entry button;

        public Span(String text, int color) {
            this.text = text;
            this.color = color;
        }

        public String text() {
            return liveText != null ? liveText.get() : text;
        }

        public int color() {
            return liveColor != null ? liveColor.getAsInt() : color;
        }

        public boolean clickable() {
            return run != null || fill != null || action != null;
        }
    }

    public static final class Line {
        public final List<Span> spans = new ArrayList<>();

        public Line() {
        }

        /**
         * Rows of scrollback this line's word art fills, starting at this line, or 0 for an
         * ordinary line. The rows under it are blank lines kept for it to draw over.
         */
        int artRows;

        /**
         * The fetch beside the art, on the right: only the main menu's terminal prints one, and
         * no command can, so it cannot be made to look like this by hand.
         */
        boolean fetch;

        /** Rows of scrollback {@code neofetch}'s campfire burns down the left of, from this line. */
        int fireRows;

        /** Rows that fold open here ({@link Drawer}); the line itself takes no row. */
        Drawer drawer;

        /** How opaque it is drawn: below 1 while a drawer is fading it in or out. */
        float fade = 1f;

        public Line t(String text, int color) {
            spans.add(new Span(text, color));
            return this;
        }

        public Line t(String text) {
            return t(text, FG);
        }

        /** Moves the next span to a fixed column, for tables. */
        public Line at(int px) {
            spans.add(new Span("", FG));
            spans.get(spans.size() - 1).x = px;
            return this;
        }

        public Line run(String text, int color, String command, String hint) {
            Span span = new Span(text, color);
            span.run = command;
            span.hint = hint;
            spans.add(span);
            return this;
        }

        public Line fill(String text, int color, String input, String hint) {
            Span span = new Span(text, color);
            span.fill = input;
            span.hint = hint;
            spans.add(span);
            return this;
        }

        public Line live(Supplier<String> text, IntSupplier color, String command, String hint) {
            Span span = new Span("", FG);
            span.liveText = text;
            span.liveColor = color;
            span.run = command;
            span.hint = hint;
            spans.add(span);
            return this;
        }

        /** A slider for a timing setting, {@code width} px long, dragged to set it. */
        Line slider(Setting setting, int width, String hint) {
            Span span = new Span("", FG);
            span.slider = setting;
            span.width = width;
            span.hint = hint;
            spans.add(span);
            return this;
        }

        /** A server's icon, tiny, before whatever comes next. */
        Line icon(net.minecraft.client.multiplayer.ServerData server) {
            Span span = new Span("", FG);
            span.server = server;
            spans.add(span);
            return this;
        }

        /** A menu button's icon, tiny, before whatever comes next; nothing for a button without one. */
        Line icon(MenuButtons.Entry button) {
            if (!ButtonMods.hasIcon(button)) return this;
            Span span = new Span("", FG);
            span.button = button;
            spans.add(span);
            return this;
        }

        public Span last() {
            return spans.get(spans.size() - 1);
        }
    }

    /** A completion: what replaces the token being typed, how it is listed, and a dim note beside it. */
    public record Suggestion(String insert, String label, String note, int color, net.minecraft.client.multiplayer.ServerData server,
                      MenuButtons.Entry button) {
        public Suggestion(String insert, String label, String note, int color) {
            this(insert, label, note, color, null, null);
        }

        Suggestion(String insert, String label, String note, int color, net.minecraft.client.multiplayer.ServerData server) {
            this(insert, label, note, color, server, null);
        }

        /** A menu button, with its icon when it has one. */
        Suggestion(MenuButtons.Entry button) {
            this(button.name(), button.name(), button.describe(), MAGENTA, null, withIcon(button));
        }

        /**
         * No branch may come before a constructor's {@code this(...)}: the obfuscator threads its
         * flow through every branch, and one ahead of {@code this(...)} fails verification.
         */
        private static MenuButtons.Entry withIcon(MenuButtons.Entry button) {
            return ButtonMods.hasIcon(button) ? button : null;
        }
    }

    /**
     * Something on screen a click can land on, rebuilt every frame in draw order so the last
     * one drawn -- the one on top -- is the one that answers.
     */
    public record Hit(int x0, int y0, int x1, int y1, TermWindow win, Span span, int suggestion, String ws, int action) {
        public boolean contains(double mx, double my) {
            return mx >= x0 && mx < x1 && my >= y0 && my < y1;
        }

        /** A rectangle that answers as {@code span} does (its run, fill or action, and its hint), or as {@code action} ({@link #ACT_BODY}...). */
        public static Hit of(int x0, int y0, int x1, int y1, Span span, int action) {
            return new Hit(x0, y0, x1, y1, null, span, -1, null, action);
        }
    }

    public static final int ACT_NONE = 0, ACT_PLUS = 1, ACT_ART_PREV = 5, ACT_ART_NEXT = 6, ACT_BODY = 8, ACT_APP = 9, ACT_TRAY = 11, ACT_SLIDER = 12, ACT_CLOSE = 13, ACT_STOKE = 14,
        ACT_YES = 15, ACT_NO = 16;

    /** The mouse button of the click being answered, for a span's action that cares which. */
    static int clickButton;

    /** The mouse button (GLFW's number) of the click being answered, for a span's action that cares which. */
    public static int mouseButton() {
        return clickButton;
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
            | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
