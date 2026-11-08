package com.mineux.core.ui.term;

import com.mineux.core.DeskExtensions;
import com.mineux.core.MineUX;
import com.mineux.core.TermProgram;

import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Line;
import com.mineux.core.ui.term.Term.Span;
import com.mineux.core.ui.term.Term.Suggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.mineux.core.ui.term.Term.*;

/**
 * One terminal window: its own shell, prompt and scrollback, and -- while {@code pipes.sh} or
 * {@code bonsai} is running in it -- that program in place of the scrollback.
 * <p>
 * Where it sits is the window manager's business ({@link TerminalScreen}); this only knows
 * the rectangle it is drawn into this frame, which eases towards wherever the layout wants
 * it so a new window grows out of the cursor and a retiled one slides into place.
 */
final class TermWindow extends Pane implements Shell.Host {

    static final int FOOTER_H = 12;
    static final int PAD = 5;
    static final int LINE_H = 10;
    static final int SUGGEST_ROWS = 8;

    final Shell shell;
    Desktop screen;

    // ---- a full-window program ------------------------------------------
    private String app;
    private Pipes pipes;
    private Bonsai bonsai;
    /** An extension's program ({@link DeskExtensions#addProgram}), and the name it runs under. */
    private TermProgram ext;
    private String extName;
    private ChatView chat;
    private FriendsView friends;

    // ---- prompt ---------------------------------------------------------
    private final StringBuilder input = new StringBuilder();
    private int cursor;
    private List<Suggestion> suggestions = new ArrayList<>();
    private int selected;
    private boolean suggestionsHidden = true;
    private boolean navigated;
    private int tokenStart;
    private int historyIndex = -1;
    private String historyDraft = "";
    private int scroll;
    private long lastKey;
    /** The cursor's x this frame, for which slider handle is lit. */
    private int lastMouseX;

    // ---- the main menu's art line -----------------------------------------
    /**
     * The box the word art was last drawn into, {x, y, w, h}, or null when it is scrolled out
     * of sight -- so the menu's boot sequence can light the logo exactly where it will stay.
     */
    int[] artBox;
    /** When the fetch beside the art starts typing itself in; 0 for all of it at once. */
    long fetchFrom;
    private String[][] fetchRows;
    private long fetchRowsAt;

    /** The Esc menu's own terminal, which alone signs its footer. */
    boolean credit;

    TermWindow(Desktop screen, float spawnX, float spawnY) {
        this.screen = screen;
        this.shell = new Shell(this);
        shell.motd();

        // Where it floats if it is floated before it is ever tiled: centred on the point asked for.
        // Where it is drawn is the window manager's; it pops in there (Pane#animate).
        fx = spawnX - fw / 2;
        fy = spawnY - fh / 2f;
    }

    @Override
    boolean terminal() {
        return true;
    }

    /** A program's field being typed: Escape and the window manager's keys are its too, then. */
    @Override
    boolean capturing() {
        return ext != null && ext.editing();
    }

    @Override
    boolean typing() {
        return capturing();
    }

    @Override
    String title() {
        if (running()) return app;
        Node cwd = shell.cwd();
        String shell = com.mineux.core.MineUX.shellName();
        return cwd == null || cwd.parent == null ? shell + " ~" : shell + " " + cwd.name;
    }

    // ---- Shell.Host -----------------------------------------------------

    @Override
    public void exit() {
        screen.closeWindow(this);
    }

    @Override
    public void quit() {
        screen.quit();
    }

    @Override
    public void confirm(String question, String detail, String yes, Runnable onYes) {
        screen.confirm(question, detail, yes, onYes);
    }

    @Override
    public void art(String arg) {
        ArtView.cycle(arg);
    }

    @Override
    public String artName() {
        return ArtView.name();
    }

    @Override
    public String[] artNames() {
        return ArtView.names();
    }

    @Override
    public boolean launches() {
        return true;
    }

    @Override
    public void launch(String name) {
        if (name.startsWith(Shell.APP_FRIENDS)) {
            // friends, or friends:<account> on their profile; running already, it changes friend.
            String target = name.length() > Shell.APP_FRIENDS.length() + 1 ? name.substring(Shell.APP_FRIENDS.length() + 1) : "";
            if (friends == null) {
                stopApp();
                friends = new FriendsView(shell, target);
            } else {
                friends.show(target);
            }
            app = Shell.APP_FRIENDS;
            return;
        }
        friends = null;
        if (name.startsWith(Shell.APP_IRC)) {
            // irc, or irc:@friend on a conversation; running already, it changes tab.
            String target = name.length() > Shell.APP_IRC.length() + 1 ? name.substring(Shell.APP_IRC.length() + 1) : "";
            stopOthers();
            if (chat == null) chat = new ChatView(target);
            else chat.show(target);
            app = Shell.APP_IRC;
            return;
        }
        chat = null;
        if (name.startsWith(Shell.APP_EXT)) {
            // ext:<program>:<arg>; running already, the same program is handed the new arg.
            String rest = name.substring(Shell.APP_EXT.length());
            int colon = rest.indexOf(':');
            String which = colon < 0 ? rest : rest.substring(0, colon);
            String arg = colon < 0 ? "" : rest.substring(colon + 1);
            if (ext != null && Objects.equals(extName, which)) {
                ext.show(arg);
                return;
            }
            TermProgram.Type type = programType(which);
            if (type == null) return;
            stopApp();
            try {
                ext = type.start(arg, new Shell.Out(shell));
            } catch (RuntimeException | LinkageError e) {
                MineUX.LOGGER.warn("{} failed to start", which, e);
                shell.error(which + ": failed to start - " + e);
                ext = null;
            }
            if (ext == null) return;
            extName = which;
            app = which;
            return;
        }
        app = name;
        if (Objects.equals(Shell.APP_PIPES, name)) {
            pipes = new Pipes();
            pipes.resize(contentW(), contentH());
        } else {
            bonsai = new Bonsai();
            bonsai.grow();
        }
    }

    @Override
    public void workstationsChanged() {
        screen.workstationsChanged();
    }

    @Override
    public MenuButtons menu() {
        return screen.menu();
    }

    @Override
    public boolean openWindow(String kind) {
        return screen.openWindow(kind);
    }

    @Override
    public String hypr(String dispatcher, String arg) {
        return screen.dispatch(dispatcher, arg);
    }

    @Override
    void attach(Desktop screen) {
        this.screen = screen;
    }

    @Override
    void close() {
        screen.closeWindow(this);
    }

    boolean running() {
        return app != null;
    }

    /** Whether this terminal is showing the friends app, for the bar to bring it forward rather than open another. */
    boolean showingFriends() {
        return friends != null;
    }

    /** Whether this terminal is showing irc, for a DM's notification to open the conversation there. */
    boolean showingChat() {
        return chat != null;
    }

    /** Whether the program running is the whole window, edge to edge; one that throws on the question is not. */
    private boolean borderless() {
        try {
            return ext.borderless();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** An extension's program by name, or null. */
    private static TermProgram.Type programType(String name) {
        for (TermProgram.Type type : DeskExtensions.programs()) if (type.name().equals(name)) return type;
        return null;
    }

    /** An extension's program, let go of; one that throws on the way out is logged. */
    private void stopExt() {
        quitExt(ext);
    }

    /** {@code was}: the program running, a parameter rather than a local copy of the field (HoistCheck). */
    private void quitExt(TermProgram was) {
        ext = null;
        extName = null;
        if (was == null) return;
        try {
            was.quit();
        } catch (RuntimeException | LinkageError e) {
            MineUX.LOGGER.warn("A program failed to quit", e);
        }
    }

    /** Whatever else has this terminal gives it up to irc. */
    private void stopOthers() {
        friends = null;
        stopExt();
        pipes = null;
        bonsai = null;
    }

    private void stopApp() {
        stopExt();
        chat = null;
        friends = null;
        app = null;
        pipes = null;
        bonsai = null;
    }

    // ---- geometry -------------------------------------------------------

    private int contentX() { return ix() + 1; }
    private int contentY() { return iy() + 1; }
    private int contentW() { return Math.max(1, iw() - 2); }
    private int contentH() { return Math.max(1, ih() - 2); }

    private int outputTop() {
        return iy() + PAD;
    }

    private int inputY() {
        return iy() + ih() - FOOTER_H - LINE_H - 2;
    }

    private int visibleLines() {
        return Math.max(1, (inputY() - 3 - outputTop()) / LINE_H);
    }

    // ---- ticking --------------------------------------------------------

    @Override
    void tick(boolean settled) {
        if (pipes != null) {
            if (settled) pipes.resize(contentW(), contentH());
            pipes.tick();
        }
        if (bonsai != null) bonsai.tick();
        if (ext != null) {
            try {
                ext.tick();
            } catch (RuntimeException | LinkageError e) {
                MineUX.LOGGER.warn("{} failed", extName, e);
                stopApp();
            }
        }
    }

    // ---- rendering ------------------------------------------------------

    @Override
    void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                List<Hit> hits, Hit hovered) {
        int wx = ix(), wy = iy(), ww = iw(), wh = ih();
        if (ww < 8 || wh < 8) return;
        lastMouseX = mouseX;

        if (ext != null && borderless()) {
            // A program that is the whole window, edge to edge: no glass, no border.
            hits.add(new Hit(wx, wy, wx + ww, wy + wh, this, null, -1, null, ACT_BODY));
            g.enableScissor(wx, wy, wx + ww, wy + wh);
            renderExt(g, font, now, wx, wy, ww, wh, mouseX, mouseY, hits, hovered);
            g.disableScissor();
            return;
        }

        // Glass: a dark, see-through pane over the blurred world, lit along its top edge.
        // The border is four lines rather than a rectangle underneath, which would tint the
        // whole pane now that it can be seen through.
        g.fillGradient(wx, wy, wx + ww, wy + wh, 0xA6121620, 0xBC07080C);
        g.fill(wx + 1, wy + 1, wx + ww - 1, wy + 2, 0x24FFFFFF);

        Decor.border(g, this, wx, wy, ww, wh, focused, 0);

        // No title bar: moving, resizing and closing are the window manager's keys, and the
        // prompt already says who and where you are.
        hits.add(new Hit(wx, wy, wx + ww, wy + wh, this, null, -1, null, ACT_BODY));

        g.enableScissor(wx, wy, wx + ww, wy + wh);

        if (ext != null) {
            // Its own buttons and rows take the clicks, so no app-wide hit goes over them.
            g.enableScissor(contentX(), contentY(), contentX() + contentW(), contentY() + contentH());
            renderExt(g, font, now, contentX(), contentY(), contentW(), contentH(), mouseX, mouseY, hits, hovered);
            g.disableScissor();
        } else if (friends != null) {
            g.enableScissor(contentX(), contentY(), contentX() + contentW(), contentY() + contentH());
            friends.render(g, font, now, contentX(), contentY(), contentW(), contentH(), mouseX, mouseY, hits);
            g.disableScissor();
            if (friends.done()) stopApp();
        } else if (chat != null) {
            g.enableScissor(contentX(), contentY(), contentX() + contentW(), contentY() + contentH());
            chat.render(g, font, now, contentX(), contentY(), contentW(), contentH(), mouseX, mouseY, hits);
            g.disableScissor();
            if (chat.done()) stopApp();
        } else if (running()) {
            renderApp(g, font);
            hits.add(new Hit(contentX(), contentY(), contentX() + contentW(), contentY() + contentH(), this, null, -1, null, ACT_APP));
        } else if (wh > FOOTER_H + LINE_H + 8) {
            renderShell(g, font, now, mouseX, mouseY, focused, hits, hovered);
        }

        g.disableScissor();
    }

    private void renderApp(GuiGraphicsExtractor g, Font font) {
        int cx = contentX(), cy = contentY(), cw = contentW(), ch = contentH();
        g.enableScissor(cx, cy, cx + cw, cy + ch);

        if (pipes != null) pipes.render(g, cx, cy);

        if (bonsai != null) {
            int gridRows = Bonsai.ROWS + Bonsai.POT.length;
            int cellW = 6, cellH = 8;
            float scale = Math.min(1.2f, Math.min((cw - 8f) / (Bonsai.COLS * cellW), (ch - 8f) / (gridRows * cellH)));
            float bw = Bonsai.COLS * cellW * scale;
            float bh = gridRows * cellH * scale;

            g.pose().pushMatrix();
            g.pose().translate(cx + (cw - bw) / 2f, cy + ch - bh - 4);
            g.pose().scale(scale, scale);

            int shown = bonsai.shown();
            for (int i = 0; i < shown && i < bonsai.glyphs.size(); i++) {
                Bonsai.Glyph glyph = bonsai.glyphs.get(i);
                cells(g, font, glyph.text(), glyph.col(), glyph.row(), cellW, cellH, glyph.color());
            }

            int potCol = Bonsai.COLS / 2 - Bonsai.POT[0].length() / 2;
            for (int r = 0; r < Bonsai.POT.length; r++) {
                String line = Bonsai.POT[r];
                for (int c = 0; c < line.length(); c++) {
                    if (line.charAt(c) == ' ') continue;
                    int color = r == 0 && c >= 12 && c <= 18 ? 0xFF5AF78E : 0xFF8A8F9C;
                    cells(g, font, String.valueOf(line.charAt(c)), potCol + c, Bonsai.ROWS + r, cellW, cellH, color);
                }
            }

            g.pose().popMatrix();
        }

        g.disableScissor();
    }

    private static void cells(GuiGraphicsExtractor g, Font font, String text, int col, int row, int cw, int ch, int color) {
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            g.text(font, c, (col + i) * cw + (cw - font.width(c)) / 2, row * ch, color, false);
        }
    }

    /** The scrollback as drawn: each drawer's line replaced by whatever of it is open. */
    private List<Line> rows(long now) {
        List<Line> out = shell.out;
        boolean drawers = false;
        for (Line line : out) drawers |= line.drawer != null;
        if (!drawers) return out;
        List<Line> rows = new ArrayList<>(out.size() + 16);
        for (Line line : out) {
            if (line.drawer == null) rows.add(line);
            else rows.addAll(line.drawer.rows(now));
        }
        return rows;
    }

    private void renderShell(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                             List<Hit> hits, Hit hovered) {
        int wx = ix(), ww = iw();
        int left = wx + PAD;
        int top = outputTop();
        int visible = visibleLines();
        List<Line> lines = rows(now);

        int maxScroll = Math.max(0, lines.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int first = Math.max(0, lines.size() - visible - scroll);
        int last = Math.min(lines.size(), first + visible);

        g.enableScissor(wx + 1, top - 1, wx + ww - 4, inputY() - 1);
        artBox = null;
        // Start far enough back to catch word art whose first row has scrolled off the top
        // while the rows it draws over are still showing.
        for (int i = Math.max(0, first - Shell.MENU_ART_ROWS + 1); i < last; i++) {
            Line line = lines.get(i);
            int ly = top + (i - first) * LINE_H;
            if (line.artRows > 0) {
                int artW = ww - 2 * PAD - 4;
                // The main menu's fetch sits beside the art when there is room for both.
                boolean side = line.fetch && artW >= Fetch.WIDTH * 2 + 40;
                if (side) {
                    artW -= Fetch.WIDTH + 12;
                    drawFetch(g, font, wx + ww - PAD - 6 - Fetch.WIDTH, ly + Math.max(0, (line.artRows * LINE_H - Fetch.height()) / 2), now, hits);
                }
                artBox = new int[] {left, ly, artW, line.artRows * LINE_H};
                int[] b = ArtView.draw(g, font, left, ly, artW, line.artRows * LINE_H);
                int clipTop = Math.max(b[1], top), clipBottom = Math.min(b[1] + b[3], inputY() - 1);
                if (clipBottom > clipTop) {
                    Span hint = new Span("art", FG);
                    hint.hint = "click: next art  right click: all the art";
                    hits.add(new Hit(b[0], clipTop, b[0] + b[2], clipBottom, this, hint, -1, null, ACT_ART_NEXT));
                }
            } else {
                // neofetch's campfire, down the left of its lines, lower than their top.
                if (line.fireRows > 0) {
                    Fetch.campfire(g, font, left, ly + 2 * LINE_H, Fetch.LOGO_W, (line.fireRows - 2) * LINE_H + 2, hits, this);
                }
                if (i >= first) drawLine(g, font, line, left, ly, hits, hovered, true);
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = inputY() - 4 - top;
            int thumbH = Math.max(8, trackH * visible / lines.size());
            int thumbY = top + (trackH - thumbH) * (maxScroll - scroll) / maxScroll;
            g.fill(wx + ww - 3, top, wx + ww - 2, top + trackH, 0x18FFFFFF);
            g.fill(wx + ww - 3, thumbY, wx + ww - 2, thumbY + thumbH, 0x80EFC648);
        }

        // Prompt.
        int iy = inputY();
        g.fill(wx + 1, iy - 3, wx + ww - 1, iy - 2, 0x14FFFFFF);
        int px = drawLine(g, font, shell.prompt(new Line()), left, iy, hits, hovered, false);

        String text = input.toString();
        g.enableScissor(wx + 1, iy - 2, wx + ww - 2, iy + LINE_H);
        g.text(font, text, px, iy, FG, false);

        String tail = tail();
        if (!tail.isEmpty()) g.text(font, tail, px + font.width(text), iy, FAINT, false);

        if (focused && ((now - lastKey) / 530) % 2 == 0) {
            int cx = px + font.width(text.substring(0, cursor));
            if (cursor < text.length()) {
                String under = text.substring(cursor, cursor + 1);
                g.fill(cx, iy - 1, cx + font.width(under), iy + 8, 0xCCEFC648);
                g.text(font, under, cx, iy, 0xFF15110A, false);
            } else {
                g.fill(cx, iy - 1, cx + 5, iy + 8, 0xCCEFC648);
            }
        } else if (!focused && cursor == text.length()) {
            int cx = px + font.width(text);
            g.fill(cx, iy - 1, cx + 5, iy, 0x66EFC648);
        }
        g.disableScissor();

        // Footer: what the hovered thing does, or the keys.
        int fy = iy() + ih() - FOOTER_H;
        g.fill(wx, fy, wx + ww, iy() + ih(), 0x38000000);
        g.fill(wx, fy, wx + ww, fy + 1, 0x1CFFFFFF);

        String hint;
        if (hovered != null && hovered.win() == this && hovered.span() != null && hovered.span().hint != null) {
            hint = hovered.span().hint;
        } else if (focused && screen.menu() != null) {
            hint = "tab complete | " + Shell.superKey() + "+drag move | " + Shell.superKey() + "+right drag resize | " + Shell.superKey() + "+f float";
        } else if (focused) {
            hint = "tab complete | up/down history | " + Shell.superKey() + "+space new terminal";
        } else {
            hint = "hover to focus";
        }
        int right = wx + ww - PAD;
        if (credit) right = drawCredit(g, font, right, fy + 2) - 8;
        String where = shell.cwd().path();
        right -= font.width(where);
        g.text(font, where, right, fy + 2, CYAN, false);

        g.enableScissor(wx, fy, Math.max(wx, right - 8), iy() + ih());
        g.text(font, hint, wx + PAD, fy + 2, hovered != null && hovered.win() == this ? FG : FAINT, false);
        g.disableScissor();
    }

    private static final String LOVE = "made with love, by ";
    private static final String AUTHOR = "goblinpiss";

    /** The Esc terminal's signature, ending at {@code right}; returns the x it starts at. */
    private static int drawCredit(GuiGraphicsExtractor g, Font font, int right, int y) {
        int x = right - font.width(LOVE) - font.width(AUTHOR);
        g.text(font, LOVE, x, y, MAGENTA, false);
        g.text(font, AUTHOR, x + font.width(LOVE), y, GREEN, false);
        return x;
    }

    /** The fetch beside the main menu's art: typed in line by line from {@link #fetchFrom}. */
    private void drawFetch(GuiGraphicsExtractor g, Font font, int x, int y, long now, List<Hit> hits) {
        if (fetchFrom > now) return;
        if (fetchRows == null || now - fetchRowsAt > 1000) {
            fetchRows = Fetch.rows(shell.user(), screen.menu());
            fetchRowsAt = now;
        }
        int shown = fetchFrom == 0 ? -1 : (int) ((now - fetchFrom) / 55);
        Fetch.draw(g, font, x, y, fetchRows, shown, hits, this);
    }

    /** The completion list, drawn after every window so nothing covers it. */
    @Override
    void renderPopup(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, List<Hit> hits) {
        if (running() || suggestionsHidden || suggestions.isEmpty()) return;

        int iy = inputY();
        int px = ix() + PAD + promptWidth(font);
        int count = Math.min(SUGGEST_ROWS, suggestions.size());
        int start = Math.max(0, Math.min(selected - count / 2, suggestions.size() - count));

        int labelW = 0, noteW = 0;
        for (int i = start; i < start + count; i++) {
            Suggestion s = suggestions.get(i);
            labelW = Math.max(labelW, font.width(s.label()));
            noteW = Math.max(noteW, font.width(s.note()));
        }

        boolean icons = false;
        for (int i = start; i < start + count; i++) icons |= suggestions.get(i).server() != null || suggestions.get(i).button() != null;
        if (icons) noteW += ServerIcons.SIZE + 3;
        int pw = Math.max(80, Math.min(Math.max(iw() - 2 * PAD, 200), labelW + 14 + noteW + 8));
        int ph = count * LINE_H + 4;
        int x = Math.max(ix() + PAD, Math.min(px + font.width(input.substring(0, tokenStart)) - 4, ix() + iw() - PAD - pw));
        int y = iy - 4 - ph;

        g.fill(x - 1, y - 1, x + pw + 1, y + ph + 1, 0xFF3A3420);
        g.fill(x, y, x + pw, y + ph, 0xF70B0C10);

        g.enableScissor(x, y, x + pw, y + ph);
        for (int i = 0; i < count; i++) {
            int index = start + i;
            Suggestion s = suggestions.get(index);
            int ry = y + 2 + i * LINE_H;
            boolean hot = mouseX >= x && mouseX < x + pw && mouseY >= ry && mouseY < ry + LINE_H;

            if (index == selected) g.fill(x + 1, ry - 1, x + pw - 1, ry + LINE_H - 1, 0x40EFC648);
            else if (hot) g.fill(x + 1, ry - 1, x + pw - 1, ry + LINE_H - 1, 0x1CFFFFFF);

            g.text(font, s.label(), x + 4, ry, s.color(), false);
            int nx = x + 4 + labelW + 10;
            // A server's icon beside its address, filled in as soon as its ping brings one.
            if (s.server() != null) {
                ServerIcons.draw(g, s.server(), nx, ry - 1);
                nx += ServerIcons.SIZE + 3;
            } else if (s.button() != null) {
                ButtonMods.draw(g, s.button(), nx, ry - 1);
                nx += ButtonMods.SIZE + 3;
            }
            g.text(font, s.note(), nx, ry, FAINT, false);
            hits.add(new Hit(x, ry - 1, x + pw, ry + LINE_H - 1, this, null, index, null, ACT_NONE));
        }
        g.disableScissor();

        if (suggestions.size() > count) {
            String more = (selected + 1) + "/" + suggestions.size();
            g.text(font, more, x + pw - font.width(more) - 3, y - 10, FAINT, false);
        }
    }

    private int promptWidth(Font font) {
        int width = 0;
        for (Span span : shell.prompt(new Line()).spans) width += font.width(span.text());
        return width;
    }

    /** Draws one line of spans; returns the x it ended at. */
    private int drawLine(GuiGraphicsExtractor g, Font font, Line line, int left, int y, List<Hit> hits, Hit hovered,
                         boolean interactive) {
        int x = left;
        // Icons have no opacity to fade by: they wait until a fading row is most of the way in.
        boolean icons = line.fade > 0.6f;

        for (Span span : line.spans) {
            if (span.x >= 0) x = Math.max(x + (x > left ? 4 : 0), left + span.x);

            if (span.server != null) {
                if (icons) ServerIcons.draw(g, span.server, x, y - 1);
                x += ServerIcons.SIZE + 3;
                continue;
            }

            if (span.button != null) {
                if (icons) ButtonMods.draw(g, span.button, x, y - 1);
                x += ButtonMods.SIZE + 3;
                continue;
            }

            if (span.slider != null) {
                boolean hot = interactive && hovered != null && hovered.span() == span;
                int held = hot ? Slider.nearest(span.slider, x, x + span.width, lastMouseX) : -1;
                Slider.draw(g, span.slider, x, x + span.width, y, held);
                if (interactive) hits.add(new Hit(x, y - 2, x + span.width, y + 9, this, span, -1, null, ACT_SLIDER));
                x += span.width;
                continue;
            }

            String text = span.text();
            if (text.isEmpty()) continue;

            int width = font.width(text);
            int color = faded(span.color(), line.fade);

            if (interactive && span.clickable()) {
                if (hovered != null && hovered.span() == span) {
                    g.fill(x - 1, y - 1, x + width + 1, y + 9, 0x22FFFFFF);
                    g.fill(x, y + 8, x + width, y + 9, color);
                    color = mix(color, 0xFFFFFFFF, 0.3f);
                }
                hits.add(new Hit(x - 1, y - 1, x + width + 1, y + 9, this, span, -1, null, ACT_NONE));
            }

            g.text(font, text, x, y, color, false);
            x += width;
        }

        return x;
    }

    /** A colour at a fraction of its opacity. */
    static int faded(int color, float fade) {
        if (fade >= 1f) return color;
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0.05f, fade));
        return a << 24 | (color & 0xFFFFFF);
    }

    // ---- prompt editing -------------------------------------------------

    private String tail() {
        if (suggestionsHidden || suggestions.isEmpty() || cursor != input.length()) return "";
        Suggestion s = suggestions.get(Math.min(selected, suggestions.size() - 1));
        String token = input.substring(tokenStart, cursor);
        if (token.isEmpty()) return "";
        if (!s.insert().toLowerCase(Locale.ROOT).startsWith(token.toLowerCase(Locale.ROOT))) return "";
        return s.insert().substring(token.length());
    }

    private void refreshSuggestions() {
        String text = input.toString();
        int start = cursor;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1)) && text.charAt(start - 1) != ';') start--;
        tokenStart = start;

        if (text.isBlank()) {
            suggestions = new ArrayList<>();
            suggestionsHidden = true;
            return;
        }

        try {
            suggestions = shell.complete(text.substring(0, start), text.substring(start, cursor));
        } catch (RuntimeException e) {
            suggestions = new ArrayList<>();
        }
        selected = 0;
    }

    private void edited() {
        historyIndex = -1;
        navigated = false;
        suggestionsHidden = false;
        refreshSuggestions();
        lastKey = System.currentTimeMillis();
    }

    @Override
    void setInput(String text) {
        input.setLength(0);
        input.append(text);
        cursor = input.length();
        edited();
    }

    void accept(int index) {
        if (index < 0 || index >= suggestions.size()) return;
        selected = index;
        String insert = suggestions.get(index).insert();
        boolean open = insert.endsWith(":") || insert.endsWith("/") || insert.endsWith("=");

        input.replace(tokenStart, cursor, insert + (open ? "" : " "));
        cursor = tokenStart + insert.length() + (open ? 0 : 1);
        edited();
    }

    private void submit() {
        String command = input.toString();
        input.setLength(0);
        cursor = 0;
        scroll = 0;
        historyIndex = -1;
        suggestionsHidden = true;
        refreshSuggestions();
        shell.run(command);
    }

    /** A click on a switch: done, and nothing printed, since the switch itself shows it. */
    void runQuiet(String command) {
        shell.quiet(command);
    }

    /** Runs a command a click stood for, echoed as though typed, so the scrollback says what happened. */
    void runVisible(String command) {
        scroll = 0;
        shell.run(command);
    }

    /** Runs a command something outside the terminal asked for: echoed and scrolled to, but kept out of the history. */
    void runFor(String command) {
        scroll = 0;
        shell.run(command, false);
    }

    private void history(int direction) {
        List<String> history = Workstations.state().history;
        if (history.isEmpty()) return;

        if (historyIndex < 0) {
            if (direction > 0) return;
            historyDraft = input.toString();
            historyIndex = history.size();
        }

        historyIndex += direction;
        if (historyIndex < 0) historyIndex = 0;

        input.setLength(0);
        if (historyIndex >= history.size()) {
            historyIndex = -1;
            input.append(historyDraft);
        } else {
            input.append(history.get(historyIndex));
        }

        cursor = input.length();
        suggestionsHidden = true;
        lastKey = System.currentTimeMillis();
    }

    /** The extension's program, drawn; one that throws comes off, logged, and the prompt is back. */
    private void renderExt(GuiGraphicsExtractor g, Font font, long now, int x, int y, int w, int h, int mouseX, int mouseY,
                           List<Hit> hits, Hit hovered) {
        try {
            ext.render(g, font, now, x, y, w, h, mouseX, mouseY, new Clicks(this, hits, hovered));
            if (ext.done()) stopApp();
        } catch (RuntimeException | LinkageError e) {
            MineUX.LOGGER.warn("{} failed", extName, e);
            shell.error(extName + ": failed - " + e);
            stopApp();
        }
    }

    /** Where an extension's program puts what a click lands on: this terminal's hits. */
    private static final class Clicks extends TermProgram.Clicks {
        private final TermWindow win;
        private final List<Hit> hits;
        private final Hit hovered;

        Clicks(TermWindow win, List<Hit> hits, Hit hovered) {
            this.win = win;
            this.hits = hits;
            this.hovered = hovered;
        }

        @Override
        public List<Hit> hits() {
            return hits;
        }

        @Override
        public Hit hovered() {
            return hovered;
        }

        @Override
        public void add(int x0, int y0, int x1, int y1, String hint, Runnable action) {
            Span span = new Span("", FG);
            span.hint = hint;
            span.action = action;
            hits.add(new Hit(x0, y0, x1, y1, null, span, -1, null, ACT_NONE));
        }

        @Override
        public void command(int x0, int y0, int x1, int y1, String hint, String command) {
            Span span = new Span("", FG);
            span.hint = hint;
            span.run = command;
            hits.add(new Hit(x0, y0, x1, y1, win, span, -1, null, ACT_NONE));
        }
    }

    @Override
    boolean charTyped(CharacterEvent event) {
        if (ext != null) {
            ext.charTyped(event);
            return true;
        }
        if (chat != null) {
            chat.charTyped(event);
            return true;
        }
        if (friends != null) {
            friends.charTyped(event);
            return true;
        }
        if (running()) return true;
        if (!event.isAllowedChatCharacter()) return false;
        String s = event.codepointAsString();
        input.insert(cursor, s);
        cursor += s.length();
        edited();
        return true;
    }

    @Override
    boolean keyPressed(KeyEvent event) {
        int key = event.key();
        lastKey = System.currentTimeMillis();

        if (ext != null) {
            // Ctrl+C quits, as it would any program in a terminal; every other key is the program's.
            if (key == GLFW.GLFW_KEY_C && event.hasControlDown()) {
                String was = extName;
                stopApp();
                shell.line().t("^C", DIM).t("  " + was + " closed", FAINT);
            } else {
                ext.keyPressed(event);
            }
            return true;
        }
        if (friends != null) {
            if (key == GLFW.GLFW_KEY_C && event.hasControlDown()) stopApp();
            else friends.keyPressed(event);
            return true;
        }
        if (chat != null) {
            if (key == GLFW.GLFW_KEY_C && event.hasControlDown()) {
                stopApp();
                shell.line().t("^C", DIM).t("  irc: still connected - irc opens it again", FAINT);
            } else {
                chat.keyPressed(event);
            }
            return true;
        }
        if (running()) {
            if (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_SPACE) {
                if (pipes != null) pipes.reset();
                if (bonsai != null) bonsai.grow();
            } else if (!isModifier(key)) {
                stopApp();
            }
            return true;
        }

        boolean ctrl = event.hasControlDown();
        boolean popup = !suggestionsHidden && !suggestions.isEmpty();

        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_V -> {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    clip = clip.replaceAll("[\\r\\n]+", "; ").replace("\t", " ");
                    input.insert(cursor, clip);
                    cursor += clip.length();
                    edited();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    shell.cancel(input.toString());
                    input.setLength(0);
                    cursor = 0;
                    edited();
                    suggestionsHidden = true;
                    return true;
                }
                case GLFW.GLFW_KEY_L -> {
                    shell.clear();
                    return true;
                }
                case GLFW.GLFW_KEY_U -> {
                    input.delete(0, cursor);
                    cursor = 0;
                    edited();
                    return true;
                }
                case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_BACKSPACE -> {
                    int start = cursor;
                    while (start > 0 && input.charAt(start - 1) == ' ') start--;
                    while (start > 0 && input.charAt(start - 1) != ' ') start--;
                    input.delete(start, cursor);
                    cursor = start;
                    edited();
                    return true;
                }
                case GLFW.GLFW_KEY_A -> {
                    cursor = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_E -> {
                    cursor = input.length();
                    return true;
                }
                default -> {
                }
            }
        }

        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (popup && navigated) accept(selected);
                else submit();
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (!popup) {
                    suggestionsHidden = false;
                    refreshSuggestions();
                    if (suggestions.size() == 1) accept(0);
                } else if (event.hasShiftDown()) {
                    selected = Math.floorMod(selected - 1, suggestions.size());
                    navigated = true;
                } else {
                    accept(selected);
                }
            }
            case GLFW.GLFW_KEY_UP -> {
                if (popup) {
                    selected = Math.floorMod(selected - 1, suggestions.size());
                    navigated = true;
                } else {
                    history(-1);
                }
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (popup) {
                    selected = Math.floorMod(selected + 1, suggestions.size());
                    navigated = true;
                } else {
                    history(1);
                }
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursor > 0) cursor--;
                refreshSuggestions();
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursor < input.length()) {
                    cursor++;
                    refreshSuggestions();
                } else if (!tail().isEmpty()) {
                    accept(selected);
                }
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                refreshSuggestions();
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = input.length();
                refreshSuggestions();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    input.deleteCharAt(--cursor);
                    edited();
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < input.length()) {
                    input.deleteCharAt(cursor);
                    edited();
                }
            }
            case GLFW.GLFW_KEY_PAGE_UP -> scroll += visibleLines() - 1;
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.max(0, scroll - (visibleLines() - 1));
            default -> {
            }
        }
        return true;
    }

    private static boolean isModifier(int key) {
        return key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
            || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
            || key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
            || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    @Override
    void scrolled(double mouseY, double amount) {
        if (ext != null) {
            ext.scrolled(lastMouseX, (int) mouseY, amount);
            return;
        }
        if (chat != null) {
            chat.scrolled(amount);
            return;
        }
        if (friends != null) {
            // The pane under the mouse scrolls: the list or the profile.
            friends.scrolled(lastMouseX, amount);
            return;
        }
        boolean overPopup = !running() && !suggestionsHidden && !suggestions.isEmpty()
            && mouseY < inputY() && mouseY > inputY() - 4 - SUGGEST_ROWS * LINE_H;
        if (overPopup) {
            selected = Math.floorMod(selected - (int) Math.signum(amount), suggestions.size());
            navigated = true;
        } else {
            scroll = Math.max(0, scroll + (int) Math.signum(amount) * 3);
        }
    }

    /** A click on a running program: bonsai grows again, pipes start over. Nothing is typed. */
    void clickApp() {
        if (pipes != null) pipes.reset();
        if (bonsai != null) bonsai.grow();
    }

    void clearScrollback() {
        shell.clear();
    }
}
