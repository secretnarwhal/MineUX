package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.mineux.core.ui.term.Term.*;

/**
 * One module's debug panel as a pane on the desk. Tiles, floats and resizes like a terminal;
 * closing it switches the module's {@code debug} off. It goes on being drawn over the game
 * after the desktop closes ({@link Readouts}).
 * <p>
 * Top to bottom: the header; the module's whole sequence as one segmented bar; a row per
 * step ({@link Trace}) with its progress bar, the delay it last used (or its gauge's value)
 * and a graph of its recent delays; what the module says it is doing; and what happened last.
 */
final class ReadoutPane extends Pane {

    static final int HEADER_H = 12;
    private static final int LINE_H = 10;
    private static final int ROW_H = 11;
    private static final int SEQ_H = 8;
    private static final int PAD = 4;
    private static final int NAME_W = 50;
    private static final int VALUE_W = 54;
    /** Lines not refreshed for this long are the module having stopped saying anything. */
    private static final long IDLE_MS = 1500;
    /** How long the live lines stay lit after an event. */
    private static final long EVENT_MS = 600;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
        .withZone(ZoneId.systemDefault());

    private final Node node;

    ReadoutPane(Node node) {
        this.node = node;
    }

    @Override
    String title() {
        return node.name + " debug";
    }

    @Override
    String readout() {
        return node.name;
    }

    @Override
    int minW() {
        return 170;
    }

    /** Room for the header, the sequence bar and every step's row; the lines under them get what is left. */
    @Override
    int minH() {
        Trace.Run run = Trace.get(node.name);
        int steps = run == null ? 0 : run.steps.size();
        return HEADER_H + (steps > 0 ? SEQ_H + steps * ROW_H : 0) + canvasHeight(node.name, (int) fw) + LINE_H + 6;
    }

    /**
     * What a readout's own drawing takes at this width, with the gap under it and room for the
     * lines it had before the drawing (a panel left small from then grows to show both); 0 for a
     * readout with none.
     */
    private static int canvasHeight(String id, int width) {
        Readouts.Canvas canvas = Readouts.canvasOf(id);
        return canvas == null ? 0 : canvas.height(Math.max(minCanvas(), width) - 2 * PAD) + 3 + LINE_H * 5;
    }

    private static int minCanvas() {
        return 170;
    }

    /** The size a new panel opens at: every step, and a few lines of log under them. */
    static float naturalHeight(String id) {
        Trace.Run run = Trace.get(id);
        int steps = run == null ? 0 : run.steps.size();
        return HEADER_H + (steps > 0 ? SEQ_H + steps * ROW_H : 0) + canvasHeight(id, 250) + LINE_H * 2 + 8;
    }

    @Override
    void close() {
        Readouts.turnOff(node.name);
        for (Desk desk : Desk.all()) {
            if (desk.windows.contains(this)) desk.close(this);
        }
    }

    @Override
    void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                List<Hit> hits, Hit hovered) {
        int wx = ix(), wy = iy(), ww = iw(), wh = ih();
        if (ww < 8 || wh < 8) return;

        Readouts.Feed feed = Readouts.feed(node.name);
        Trace.Run run = Trace.get(node.name);
        long touched = Math.max(feed == null ? 0L : feed.at, run == null ? 0L : run.touchedAt);
        boolean idle = now - touched > IDLE_MS && (run == null || !run.busy(now));
        boolean lit = feed != null && now - feed.eventAt < EVENT_MS;

        g.fillGradient(wx, wy, wx + ww, wy + wh, 0xA6121620, 0xBC07080C);
        Decor.border(g, this, wx, wy, ww, wh, focused, lit ? 0x90EFC648 : 0);

        hits.add(new Hit(wx, wy, wx + ww, wy + wh, null, null, -1, null, ACT_BODY));

        g.enableScissor(wx, wy, wx + ww, wy + wh);
        header(g, font, wx, wy, ww, mouseX, mouseY, idle, lit, hits);

        int y = wy + HEADER_H + 3;
        int bottom = wy + wh - 2;
        if (run != null && !run.steps.isEmpty()) y = steps(g, font, run, wx, y, ww, bottom, now);
        Readouts.Canvas canvas = Readouts.canvasOf(node.name);
        if (canvas != null) {
            int tall = canvas.height(ww - 2 * PAD);
            if (y + tall <= bottom) {
                canvas.draw(g, font, wx + PAD, y, ww - 2 * PAD, tall);
                y += tall + 3;
            }
        }
        y = lines(g, font, feed, run, wx, y, bottom, idle, lit);
        log(g, font, feed, wx, y, ww, bottom);

        g.disableScissor();
    }

    /** Which readout, whether it is saying anything, and a close box. */
    private void header(GuiGraphicsExtractor g, Font font, int wx, int wy, int ww, int mouseX, int mouseY,
                        boolean idle, boolean lit, List<Hit> hits) {
        g.fill(wx, wy, wx + ww, wy + HEADER_H, 0x40000000);
        g.fill(wx, wy + HEADER_H - 1, wx + ww, wy + HEADER_H, 0x1CFFFFFF);
        int ty = wy + 2;
        g.fill(wx + PAD, ty + 2, wx + PAD + 3, ty + 5, idle ? FAINT : lit ? GOLD : GREEN);
        g.text(font, node.name, wx + PAD + 6, ty, GOLD, false);
        g.text(font, " debug", wx + PAD + 6 + font.width(node.name), ty, DIM, false);

        String close = "x";
        int cx = wx + ww - PAD - font.width(close);
        boolean closeHot = mouseX >= cx - 3 && mouseX < cx + font.width(close) + 3 && mouseY >= wy && mouseY < wy + HEADER_H - 1;
        String state = idle ? "idle" : "live";
        g.text(font, state, cx - 8 - font.width(state), ty, idle ? FAINT : DIM, false);
        if (closeHot) g.fill(cx - 3, wy, cx + font.width(close) + 3, wy + HEADER_H - 1, 0x40FF5C57);
        g.text(font, close, cx, ty, closeHot ? RED : DIM, false);

        Span off = new Span(close, RED);
        off.run = Shell.rel(node) + " debug:off";
        off.quiet = true;
        off.hint = "close: switches " + node.name + " debug off";
        hits.add(new Hit(cx - 3, wy, cx + font.width(close) + 3, wy + HEADER_H - 1, null, off, -1, null, ACT_NONE));
    }

    /**
     * The sequence bar and a row per step: name, progress bar, last delay (or the gauge's
     * value, or why it was skipped) and the graph of its recent delays.
     */
    private static int steps(GuiGraphicsExtractor g, Font font, Trace.Run run, int wx, int y, int ww, int bottom, long now) {
        int inner = ww - 2 * PAD;
        Meter.sequence(g, wx + PAD, y, inner, 3, run, now, 1f);
        y += SEQ_H;

        int barX = wx + PAD + NAME_W;
        int rest = inner - NAME_W - VALUE_W - 6;
        int barW = Math.max(24, Math.min(110, rest * 11 / 20));
        int graphX = barX + barW + VALUE_W + 6;
        int graphW = wx + ww - PAD - graphX;

        for (Trace.Step s : run.steps) {
            if (y + ROW_H - 2 > bottom) break;
            int color = Meter.color(s, now);
            boolean active = s.active(now);
            g.text(font, font.plainSubstrByWidth(s.name, NAME_W - 3), wx + PAD, y, active ? FG : DIM, false);
            Meter.bar(g, barX, y + 2, barW, 5, s.progress(now), color, 1f);
            g.text(font, value(s, now), barX + barW + 4, y, color == FAINT ? DIM : color, false);
            if (graphW >= 12) Meter.graph(g, graphX, y, graphW, 8, s, 1f);
            y += ROW_H;
        }
        return y + 1;
    }

    /** What the row prints: the wait so far against the delay drawn, else the last delay used, or the gauge. */
    private static String value(Trace.Step s, long now) {
        if (now - s.skippedAt < Trace.FLASH_MS * 2 && s.skippedAt > s.firedAt) return "skip";
        if (s.waiting) return Math.min(now - s.armedAt, s.delay) + "/" + s.delay;
        if (s.gauge >= 0f) return s.gaugeText;
        return s.lastText.isEmpty() ? "-" : s.lastText;
    }

    /** What the module says it is doing: its one-line state, then whatever it posted. */
    private static int lines(GuiGraphicsExtractor g, Font font, Readouts.Feed feed, Trace.Run run, int wx, int y, int bottom,
                             boolean idle, boolean lit) {
        int color = lit ? GOLD : idle ? DIM : FG;
        if (run != null && !run.state.isEmpty() && y + 8 <= bottom) {
            g.text(font, run.state, wx + PAD, y, color, false);
            y += LINE_H;
        }
        if (feed == null || feed.lines.length == 0) {
            if ((run == null || run.state.isEmpty()) && y + 8 <= bottom) {
                g.text(font, "waiting for it to do something...", wx + PAD, y, FAINT, false);
                y += LINE_H;
            }
            return y;
        }
        for (String line : feed.lines) {
            if (y + 8 > bottom) break;
            g.text(font, line, wx + PAD, y, color, false);
            y += LINE_H;
        }
        return y;
    }

    /** What happened, newest first, for as far down as there is room. */
    private static void log(GuiGraphicsExtractor g, Font font, Readouts.Feed feed, int wx, int y, int ww, int bottom) {
        if (feed == null || feed.log.isEmpty() || y + 3 + 8 > bottom) return;
        g.fill(wx + PAD, y + 1, wx + ww - PAD, y + 2, 0x14FFFFFF);
        y += 4;
        for (String[] entry : feed.log) {
            if (y + 8 > bottom) break;
            String time = TIME.format(Instant.ofEpochMilli(Long.parseLong(entry[0])));
            g.text(font, time, wx + PAD, y, FAINT, false);
            g.text(font, entry[1], wx + PAD + font.width(time) + 5, y, DIM, false);
            y += LINE_H;
        }
    }
}
