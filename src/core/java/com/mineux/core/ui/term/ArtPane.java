package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.mineux.core.ui.term.Term.*;

/**
 * The art window: every word art there is, previewed, to pick from, with the palette and the
 * two animation switches above them and a folder button for adding your own. Opened by a right
 * click on the art in any terminal, or {@code art list}; tiled in on the right like the main
 * menu's worlds and servers windows.
 * <p>
 * Every click is an {@code art} command run quietly ({@link ArtCommands}), so it can all be typed.
 * The list follows {@link ArtView#version}: a file saved into the folder appears in it by itself.
 */
final class ArtPane extends Pane {

    static final String KIND = "art";

    private static final int HEADER_H = 12;
    private static final int FOOTER_H = 12;
    private static final int LINE_H = 10;
    private static final int PAD = 5;
    /** Tallest a preview is drawn, in GUI px. */
    private static final int THUMB_MAX_H = 56;
    private static final int GAP = 6;

    private Desktop desktop;
    /** How far the list is scrolled, in GUI px. */
    private int scroll;

    private final Span closeSpan = new Span("x", RED);
    private final Span folderSpan = new Span("", GOLD);
    private final Span[] paletteSpans = new Span[ArtView.PALETTES.length];
    private final Span glitchSpan = new Span("", FG);
    private final Span motionSpan = new Span("", FG);
    private final Span randomSpan = new Span("random", CYAN);
    /** One per art, for the whole of its entry; rebuilt when the art is reloaded. */
    private final List<Span> entries = new ArrayList<>();
    private int entriesFor = -1;

    /** Where the next option goes, while the options are laid out. */
    private int penX, penY, penLeft, penRight;

    ArtPane(Desktop desktop) {
        this.desktop = desktop;
        this.w = 1;
        this.h = 1;
        closeSpan.hint = "close (esc)";
        folderSpan.run = "art folder";
        folderSpan.quiet = true;
        folderSpan.hint = "show folder in file explorer - drop .txt art in it";
        for (int i = 0; i < paletteSpans.length; i++) {
            String p = ArtView.PALETTES[i];
            paletteSpans[i] = new Span(p, FG);
            paletteSpans[i].run = "art palette " + p;
            paletteSpans[i].quiet = true;
            paletteSpans[i].hint = "colour the art " + p;
        }
        glitchSpan.quiet = true;
        glitchSpan.hint = "the art glitching now and then";
        motionSpan.quiet = true;
        motionSpan.hint = "the art's float and the band of light down it";
        randomSpan.run = "art random";
        randomSpan.quiet = true;
        randomSpan.hint = "any art at all";
        // A folder saved into since the art was last read is read again as the window opens.
        ArtView.reload();
    }

    @Override
    int minW() {
        return 170;
    }

    @Override
    int minH() {
        return HEADER_H + FOOTER_H + 90;
    }

    @Override
    int header() {
        return HEADER_H;
    }

    @Override
    void attach(Desktop screen) {
        this.desktop = screen;
    }

    @Override
    void close() {
        desktop.closeWindow(this);
    }

    @Override
    void scrolled(double mouseY, double amount) {
        scroll = Math.max(0, scroll - (int) Math.round(amount * 24));
    }

    private void rebuildEntries() {
        ArtView.checkFolder();
        int version = ArtView.version();
        if (version == entriesFor) return;
        entriesFor = version;
        entries.clear();
        for (int i = 0; i < ArtView.count(); i++) {
            Span span = new Span(ArtView.nameOf(i), FG);
            span.run = "art " + (i + 1);
            span.quiet = true;
            span.hint = "show " + ArtView.nameOf(i) + (ArtView.isUsers(i) ? " (from your folder)" : "");
            entries.add(span);
        }
    }

    @Override
    String title() {
        return "art";
    }

    @Override
    void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                List<Hit> hits, Hit hovered) {
        int wx = ix(), wy = iy(), ww = iw(), wh = ih();
        if (ww < 8 || wh < 8) return;
        rebuildEntries();

        g.fillGradient(wx, wy, wx + ww, wy + wh, 0xA6121620, 0xBC07080C);
        Decor.border(g, this, wx, wy, ww, wh, focused, 0);
        hits.add(new Hit(wx, wy, wx + ww, wy + wh, null, null, -1, null, ACT_BODY));

        g.enableScissor(wx, wy, wx + ww, wy + wh);
        drawHeader(g, font, wx, wy, ww, mouseX, mouseY, hits, hovered);
        int listTop = drawOptions(g, font, wx, wy + HEADER_H + 4, ww, hits, hovered);
        drawList(g, font, wx, listTop, ww, wy + wh - FOOTER_H - 1, mouseX, mouseY, hits, hovered);
        drawFooter(g, font, wx, wy + wh - FOOTER_H, ww, hovered);
        g.disableScissor();
        drawGrip(g, onGrip(mouseX, mouseY));
    }

    private void drawHeader(GuiGraphicsExtractor g, Font font, int wx, int wy, int ww, int mouseX, int mouseY,
                            List<Hit> hits, Hit hovered) {
        g.fill(wx, wy, wx + ww, wy + HEADER_H, 0x40000000);
        g.fill(wx, wy + HEADER_H - 1, wx + ww, wy + HEADER_H, 0x1CFFFFFF);
        g.text(font, "art", wx + PAD, wy + 2, GOLD, false);
        g.text(font, " " + ArtView.count(), wx + PAD + font.width("art"), wy + 2, FAINT, false);

        // The close box, and the folder button left of it.
        int cx = wx + ww - PAD - font.width("x");
        boolean closeHot = hovered != null && hovered.span() == closeSpan;
        if (closeHot) g.fill(cx - 3, wy, cx + font.width("x") + 3, wy + HEADER_H - 1, 0x40FF5C57);
        g.text(font, "x", cx, wy + 2, closeHot ? RED : DIM, false);
        hits.add(new Hit(cx - 3, wy, cx + font.width("x") + 3, wy + HEADER_H - 1, null, closeSpan, -1, null, ACT_CLOSE));

        int fx = cx - 3 - 6 - 9;
        boolean folderHot = hovered != null && hovered.span() == folderSpan;
        if (folderHot) g.fill(fx - 3, wy, fx + 9 + 3, wy + HEADER_H - 1, 0x30EFC648);
        drawFolder(g, fx, wy + 2, folderHot ? GOLD : DIM);
        hits.add(new Hit(fx - 3, wy, fx + 9 + 3, wy + HEADER_H - 1, null, folderSpan, -1, null, ACT_NONE));
    }

    /** A folder, 9 by 8: its tab, and its front a shade darker. */
    private static void drawFolder(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 4, y + 2, color);
        g.fill(x, y + 1, x + 9, y + 8, color);
        g.fill(x + 1, y + 3, x + 8, y + 7, mix(color, 0xFF000000, 0.45f));
    }

    /** The palette and the switches, wrapped to the window; returns where the list starts. */
    private int drawOptions(GuiGraphicsExtractor g, Font font, int wx, int top, int ww, List<Hit> hits, Hit hovered) {
        penLeft = wx + PAD;
        penRight = wx + ww - PAD;
        penX = penLeft;
        penY = top;

        label(g, font, "palette");
        String palette = ArtView.palette();
        for (int i = 0; i < paletteSpans.length; i++) {
            String p = ArtView.PALETTES[i];
            boolean on = Objects.equals(p, palette);
            option(g, font, paletteSpans[i], p, on ? ArtView.swatch(p) : DIM, on, hits, hovered);
        }

        newRow();
        boolean glitch = ArtView.glitch(), motion = ArtView.motion();
        glitchSpan.run = "art glitch " + (glitch ? "off" : "on");
        motionSpan.run = "art motion " + (motion ? "off" : "on");
        label(g, font, "glitch");
        option(g, font, glitchSpan, glitch ? "[on ]" : "[off]", glitch ? GREEN : DIM, false, hits, hovered);
        label(g, font, "motion");
        option(g, font, motionSpan, motion ? "[on ]" : "[off]", motion ? GREEN : DIM, false, hits, hovered);
        option(g, font, randomSpan, "random", CYAN, false, hits, hovered);

        int bottom = penY + LINE_H + 2;
        g.fill(wx + 1, bottom, wx + ww - 1, bottom + 1, 0x14FFFFFF);
        return bottom + 3;
    }

    private void newRow() {
        penX = penLeft;
        penY += LINE_H + 1;
    }

    private void label(GuiGraphicsExtractor g, Font font, String text) {
        int width = font.width(text);
        if (penX > penLeft && penX + width > penRight) newRow();
        g.text(font, text, penX, penY, FAINT, false);
        penX += width + 5;
    }

    private void option(GuiGraphicsExtractor g, Font font, Span span, String text, int color, boolean underline,
                        List<Hit> hits, Hit hovered) {
        int width = font.width(text);
        if (penX > penLeft && penX + width > penRight) newRow();
        if (hovered != null && hovered.span() == span) {
            g.fill(penX - 1, penY - 1, penX + width + 1, penY + 9, 0x22FFFFFF);
            color = mix(color, 0xFFFFFFFF, 0.3f);
            underline = true;
        }
        if (underline) g.fill(penX, penY + 8, penX + width, penY + 9, color);
        g.text(font, text, penX, penY, color, false);
        hits.add(new Hit(penX - 1, penY - 1, penX + width + 1, penY + 9, null, span, -1, null, ACT_NONE));
        penX += width + 6;
    }

    /** Every art: its number and name, and a still of it. Only what is in sight is drawn. */
    private void drawList(GuiGraphicsExtractor g, Font font, int wx, int top, int ww, int bottom,
                          int mouseX, int mouseY, List<Hit> hits, Hit hovered) {
        if (bottom - top < 8) return;
        int thumbW = ww - 2 * PAD - 4;
        int current = ArtView.current();

        int total = 0;
        int[] heights = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            heights[i] = LINE_H + 2 + ArtView.stillHeight(font, i, thumbW, THUMB_MAX_H) + GAP;
            total += heights[i];
        }
        int maxScroll = Math.max(0, total - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        g.enableScissor(wx + 1, top, wx + ww - 1, bottom);
        int y = top - scroll;
        for (int i = 0; i < entries.size(); i++) {
            int eh = heights[i];
            if (y + eh > top && y < bottom) {
                Span span = entries.get(i);
                boolean on = i == current;
                boolean hot = hovered != null && hovered.span() == span;
                int y1 = y + eh - GAP / 2;
                if (on) {
                    g.fill(wx + 2, y - 2, wx + ww - 2, y1, 0x1CEFC648);
                    g.fill(wx + 2, y - 2, wx + 4, y1, GOLD);
                } else if (hot) {
                    g.fill(wx + 2, y - 2, wx + ww - 2, y1, 0x12FFFFFF);
                }

                String number = String.valueOf(i + 1);
                g.text(font, number, wx + PAD + 2, y, FAINT, false);
                int nx = wx + PAD + 2 + font.width(number) + 5;
                g.text(font, span.text, nx, y, on ? GOLD : hot ? 0xFFFFFFFF : FG, false);
                if (ArtView.isUsers(i)) g.text(font, "yours", nx + font.width(span.text) + 6, y, MAGENTA, false);

                ArtView.drawStill(g, font, i, wx + PAD + 2, y + LINE_H + 2, thumbW, THUMB_MAX_H);
                hits.add(new Hit(wx + 2, Math.max(top, y - 2), wx + ww - 2, Math.min(bottom, y1), null, span, -1, null, ACT_NONE));
            }
            y += eh;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = bottom - top;
            int thumbH = Math.max(8, trackH * trackH / total);
            int thumbY = top + (trackH - thumbH) * scroll / maxScroll;
            g.fill(wx + ww - 3, top, wx + ww - 2, bottom, 0x18FFFFFF);
            g.fill(wx + ww - 3, thumbY, wx + ww - 2, thumbY + thumbH, 0x80EFC648);
        }
    }

    /** What the hovered thing does, or where your own art goes. */
    private void drawFooter(GuiGraphicsExtractor g, Font font, int wx, int fy, int ww, Hit hovered) {
        g.fill(wx, fy, wx + ww, fy + FOOTER_H, 0x38000000);
        g.fill(wx, fy, wx + ww, fy + 1, 0x1CFFFFFF);
        boolean mine = hovered != null && hovered.span() != null && hovered.span().hint != null
            && hovered.x0() >= wx && hovered.x1() <= wx + ww && hovered.y0() >= iy() && hovered.y1() <= iy() + ih();
        String hint = mine ? hovered.span().hint : "your own art: config/mineux/ascii";
        g.text(font, hint, wx + PAD, fy + 2, mine ? FG : FAINT, false);
    }
}
