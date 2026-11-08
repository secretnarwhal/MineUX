package com.mineux.core.ui.term;

import com.mineux.core.MineUX;
import com.mineux.core.Backdrop;
import com.mineux.core.DeskExtensions;
import com.mineux.core.greeter.LyConfig;
import com.mineux.core.ui.DeskScreen;
import com.mineux.core.ui.Overlay;
import com.mineux.core.greeter.StreamFill;
import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import com.mineux.core.util.Words;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * The MineUX desktop, and the window manager that runs it: Hyprland's, as far as a game
 * screen can have it. {@code dotfiles/hypr/hyprland.conf} ({@link HyprConf}) sets the gaps,
 * borders, animations, layout (columns or dwindle) and every key; {@link Hypr} is its
 * dispatchers, {@code hyprctl} runs them by hand. On top of what is described below there are
 * groups ({@link Group}), the special workspace that drops in the first time it is asked for,
 * panes pinned to show over the game ({@link Desk#PINS}), and a workstation switch
 * slides ({@link Slide}).
 * <p>
 * <p>
 * Each workstation -- a whole-client config profile, numbered i3-style in the bar across the
 * top, whose right end is a tray of module switches ({@link Tray}) -- has its own set of
 * borderless panes ({@link Desk}): terminals, and the debug readouts of whichever modules
 * have theirs on ({@link Readouts}). Tiled panes sit in columns, any number to a column --
 * or in rows, which SUPER+J flips between. Focus follows the mouse -- the window under the
 * cursor takes the keyboard -- and a click brings a window to the top; the cursor passing over
 * one never raises it. Everything else is on
 * the SUPER key (Alt unless {@code super} says otherwise): Space for a new terminal at the
 * cursor, drag to move (dropped near a pane's edge it goes beside it on that side, on its
 * middle the two swap), right-drag to resize, F to float, a number to go to that workstation
 * or make it -- silently, since a keystroke is not something to scroll past later.
 * <p>
 * <b>One desktop, three kinds of desk.</b> Beside the workstations there are two desks of the
 * client's own, neither a config profile. In a world the first tab in the bar is always
 * {@code esc}: the pause menu, taken over ({@link #escape}). Its terminal cannot be closed and
 * its {@code ls} lists the pause menu's buttons; Alt+Esc anywhere else on the desktop comes
 * back to it. Esc always closes the desktop, whatever has the keyboard -- on the Esc desk
 * that is back to the game. With no world, the whole screen is the main menu
 * ({@link #mainMenu}): its own pinned terminal over the login screen's tint, the title
 * screen's buttons as commands, and the worlds and servers as windows tiled beside it. All
 * three are tiled, dragged and resized by the same window manager.
 * <p>
 * <b>Frame cost.</b> The first version drew pipes.sh, a bonsai and a dot grid behind
 * everything, all the time -- over ten thousand GUI elements a frame at its worst, which is
 * what the frame rate was going on. The desk itself now draws next to nothing; the word art
 * lives at the top of each terminal's scrollback ({@link ArtView}), and pipes and the bonsai
 * are programs a terminal runs when asked.
 */
public class TerminalScreen extends DeskScreen implements Desktop {

    private static final int BAR_H = 16;
    /** How dark a fade between workstations goes at its middle. */
    private static final float FADE_DARK = 0.85f;

    private static final int ACT_NEW = 10;

    /** Floating windows keep at least this much of themselves on screen. */
    private static final int KEEP_VISIBLE = 16;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    /** Runs the odd command no window asked for -- a workstation switch from the bar with no terminal open. */
    private static Shell headless;

    /** The main menu's desk and the Esc menu's: the client's own, not config profiles. */
    static final String MENU_DESK = "\u0000menu", ESC_DESK = "\u0000esc";
    /** The world the Esc desk was made in; another world gets a fresh one. */
    private static WeakReference<Object> escLevel = new WeakReference<>(null);

    private final Screen parent;
    /** Which desk is showing: {@link #MENU_DESK}, {@link #ESC_DESK}, or null for the open workstation's. */
    private String special;
    /** Opened as the main menu (no world): its home desk and the workstations, Esc going home. */
    private final boolean title;
    /** The title screen's buttons (main menu) or the pause screen's (Esc); null until wanted. */
    private MenuButtons buttons;
    /** The login screen's tint, carried on behind the main menu; null anywhere else. */
    private StreamFill tint;
    private final long opened = System.currentTimeMillis();
    /** Playing the main menu's boot sequence ({@link Boot}). */
    private boolean intro;
    private boolean pendingLs;
    private long lastFrame = System.nanoTime();
    /** Where the cursor was last frame: where SUPER+Space opens a terminal. */
    private int lastMouseX, lastMouseY;
    /**
     * Until when a typed character belongs to the SUPER combo just pressed rather than the
     * prompt. A deadline, not a flag: Alt combos produce no character at all on Windows, and a
     * flag left up would eat the next real keystroke.
     */
    private long swallowCharUntil;
    /** The desk key went down with nothing else since: its release closes the desktop. */
    private boolean deskTap;

    // ---- layout ---------------------------------------------------------
    private int areaX, areaY, areaW, areaH;

    // ---- hit testing (rebuilt every frame) ------------------------------
    private final List<Hit> hits = new ArrayList<>();
    private Hit hovered;
    /** A yes/no question floating over everything ({@code quit}, {@code reboot}); it has the input while it is up. */
    private Prompt prompt;

    /** The workstation slide: the desk being left and the one arriving, drawn side by side. */
    private final Slide slide = new Slide();
    /** The window manager's dispatchers, for binds and {@code hyprctl dispatch}. */
    private final Hypr hypr = new Hypr(this);
    /** When the scroll wheel last switched workstations: one switch per flick, not one per notch of a trackpad. */
    private long scrolledAt;
    /**
     * Keys down on this screen. The game hands a screen the OS's key repeats as presses, so a
     * press of a key already down is a repeat: a {@code bind} ignores it, a {@code binde} runs
     * again. Kept honest by {@link #keyReleased} and, for a release this screen never saw, by
     * checking GLFW every tick.
     */
    private final Set<Integer> keysDown = new HashSet<>();

    // ---- dragging -------------------------------------------------------
    private static final int DRAG_NONE = 0, DRAG_MOVE = 1, DRAG_RESIZE = 2;
    private int dragMode = DRAG_NONE;
    private Pane dragWin;
    /** Whether a move has gone far enough to count; until then a tiled window keeps its slot. */
    private boolean dragMoved;
    private double dragStartX, dragStartY;
    private float dragRectX, dragRectY, dragRectW, dragRectH;
    private boolean dragLeft, dragTop;
    /** Resizing tiled: the neighbouring column across, and the neighbouring pane along the column. */
    private Desk.Column dragColumn, dragColumnNeighbour;
    private boolean dragColumnBefore;
    private float dragColumnWeightA, dragColumnWeightB, dragColumnSize, dragColumnPair;
    private Pane dragNeighbour;
    private boolean dragNeighbourBefore;
    private float dragWeightA, dragWeightB, dragSize, dragPair;
    /** Resizing tiled in dwindle: the splits across and down that the pane's edges belong to. */
    private Dwindle.Node dragSplitX, dragSplitY;
    private float dragRatioX, dragRatioY;

    // ---- a timing slider being dragged ------------------------------------
    private Setting slider;
    private int sliderX0, sliderX1;
    private int sliderHeld;

    // ---- context menu ---------------------------------------------------
    private String menuFor;
    private int menuX, menuY;
    private boolean menuConfirmDelete;
    private static final String[] MENU = {"open", "rename", "duplicate", "delete"};

    public TerminalScreen(Screen parent) {
        this(parent, null, null, false);
    }

    private TerminalScreen(Screen parent, String special, Screen menuScreen, boolean intro) {
        super(Component.literal("MineUX"));
        this.parent = parent;
        this.special = special;
        this.title = Objects.equals(MENU_DESK, special);
        this.intro = intro;
        if (menuScreen != null) buttons = new MenuButtons(menuScreen);
        if (Objects.equals(MENU_DESK, special)) {
            // A sign-in starts a fresh main menu terminal, lit up as part of the boot sequence.
            if (intro) Desk.remove(MENU_DESK);
            tint = Objects.equals(LyConfig.STREAM, LyConfig.load().animation) ? StreamFill.ofDotfile() : null;
        }
        if (Objects.equals(ESC_DESK, special)) checkEscWorld();

        // Windows outlive the screen that drew them; this one draws them now.
        for (Desk desk : Desk.all()) for (Pane w : desk.windows) w.attach(this);
        Headless host = new Headless(this);
        if (headless == null) headless = new Shell(host);
        headless.setHost(host);
    }

    /**
     * The main menu.
     *
     * @param title the title screen, initialised, whose buttons become commands
     * @param intro play the boot sequence (straight after a sign-in)
     */
    public static TerminalScreen mainMenu(Screen title, boolean intro) {
        return new TerminalScreen(null, MENU_DESK, title, intro);
    }

    /**
     * The desktop on its Esc desk: the pause menu, taken over.
     *
     * @param pause the pause screen, initialised, whose buttons become commands
     */
    public static TerminalScreen escape(Screen pause) {
        return new TerminalScreen(null, ESC_DESK, pause, false);
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    /** The desk with the keyboard and mouse: the one showing. */
    Desk desk() {
        return baseDesk();
    }

    /** The desk showing: a workstation's, the main menu's or the Esc desk. */
    Desk baseDesk() {
        return Desk.of(special != null ? special : Workstations.current());
    }

    boolean onMenu() {
        return Objects.equals(MENU_DESK, special);
    }

    boolean onEsc() {
        return Objects.equals(ESC_DESK, special);
    }

    /** The main menu, on its home desk or on a workstation opened from it. */
    boolean atTitle() {
        return title;
    }

    /** To the home desk, from a workstation opened on the main menu. */
    private void showHome() {
        if (!title || onMenu()) return;
        special = MENU_DESK;
        deskChanged();
    }

    boolean inWorldDesk() {
        return inWorld();
    }

    /** The open desk's place in the bar, left to right, for which way a switch slides. */
    private int deskOrder() {
        if (onMenu()) return -2;
        if (onEsc()) return -1;
        return Workstations.names().indexOf(Workstations.current());
    }

    private boolean inWorld() {
        return minecraft != null && minecraft.level != null;
    }

    private static void checkEscWorld() {
        Object level = Minecraft.getInstance().level;
        if (escLevel.get() != level) {
            Desk.remove(ESC_DESK);
            escLevel = new WeakReference<>(level);
        }
    }

    /** To the Esc desk, from a workstation; a pause screen is laid out to read the buttons off if none was handed over. */
    private void showEsc() {
        if (!inWorld()) return;
        checkEscWorld();
        if (buttons == null || !buttons.pause()) buttons = new MenuButtons(new PauseScreen(true));
        special = ESC_DESK;
        deskChanged();
    }

    /** {@code workspace e-1} from the first workstation: the Esc desk. */
    void showEscDesk() {
        showEsc();
    }

    /** {@code workspace}: to that workstation (made, for a free number), from wherever the desktop is. */
    void goTo(String name) {
        special = null;
        if (!Objects.equals(name, Workstations.current())) command("ws open " + name);
        deskChanged();
    }

    // ---- the special workspace --------------------------------------------

    /**
     * SUPER+S ({@code togglespecialworkspace}): to the special workspace, or back off it to the
     * workstation open before. The first time there is no such workspace yet: it is made, a
     * copy of the open one, and drops in from the top. From then on it is a tab in the bar like
     * any other, and going to it slides like any other.
     */
    void toggleSpecial() {

        if (special == null && Workstations.isSpecial(Workstations.current())) {
            String back = Workstations.previous();
            if (back != null) goTo(back);
            return;
        }

        if (!Workstations.hasSpecial()) slide.dropNext();
        goTo(Workstations.SPECIAL);
    }

    /** Off the Esc desk, to a workstation: the open one, or that one. */
    private void showWorkstation(String name) {
        special = null;
        if (name != null && !name.equals(Workstations.current())) command("ws open " + name);
        deskChanged();
    }

    private void deskChanged() {
        closeMenu();
        endDrag();
        Desk base = baseDesk();
        for (Pane w : base.windows) w.attach(this);
        ensureSeeded(base);
        layout();
    }

    /** The desk's pinned terminal (main menu, Esc), or its first terminal; null for none. */
    private TermWindow mainTerminal() {
        Desk desk = desk();
        if (desk.pinned != null && desk.windows.contains(desk.pinned)) return (TermWindow) desk.pinned;
        for (Pane p : desk.windows) if (p.terminal()) return (TermWindow) p;
        return null;
    }

    /** Where the keyboard goes: the focused pane when it is a terminal, else the desk's main one. */
    private Pane keyboardPane() {
        Desk desk = desk();
        Pane focused = desk.focused;
        if (focused != null && desk.windows.contains(focused) && (focused.terminal() || focused.readout() != null || focused.takesKeys())) return focused;
        return mainTerminal();
    }

    // ---- the boot sequence (main menu) ------------------------------------

    private long clock() {
        return intro ? System.currentTimeMillis() - opened : Long.MAX_VALUE / 4;
    }

    private void skipIntro() {
        intro = false;
        TermWindow main = mainTerminal();
        if (main != null) main.fetchFrom = 0;
        runPendingLs();
    }

    private void runPendingLs() {
        if (!pendingLs) return;
        TermWindow main = mainTerminal();
        if (main == null) return;
        pendingLs = false;
        // Everything there is to type, before anything has been typed.
        main.shell.run("ls", false);
    }

    // ---- windows --------------------------------------------------------

    private TermWindow spawn(float atX, float atY) {
        return spawn(desk(), atX, atY);
    }

    private TermWindow spawn(Desk desk, float atX, float atY) {
        TermWindow win = new TermWindow(this, atX, atY);

        // Into the column the cursor is over, right after the pane there, so it appears there --
        // or, with a group focused, into the group as a new tab, as Hyprland opens them.
        desk.windows.add(win);
        Pane focused = desk.focused;
        if (focused != null && focused.group != null && desk.windows.contains(focused)) desk.joinGroup(win, focused.group);
        else desk.tileAt(win, atX, atY);
        desk.focus(win);
        layout();
        return win;
    }

    /** {@code exec muxsh}: a new terminal where the cursor is. */
    void spawnAtCursor() {
        spawn(lastMouseX, lastMouseY);
    }

    /**
     * A module opened in a terminal -- cd'd into and listed, where its settings are typed or
     * clicked -- in the terminal with the keyboard, or a new one when the desk has none. What a
     * settings window's {@code terminal} link does.
     */
    @Override
    public void openInTerminal(String module) {
        Pane pane = keyboardPane();
        TermWindow term = pane != null && pane.terminal() ? (TermWindow) pane : mainTerminal();
        if (term == null) term = spawn(areaX + areaW / 2f, areaY + areaH / 2f);
        desk().focus(term);
        // The keyboard stays on it until the mouse moves, as after any window manager key.
        term.runFor("cd /" + module + "; ls");
    }

    /** {@code killactive}: a terminal closes; a readout switches its module's debug off and goes. */
    void closePane(Pane pane) {
        pane.close();
        if (dragWin == pane) endDrag();
        layout();
    }

    @Override
    public void closeWindow(Pane win) {
        for (Desk desk : Desk.all()) {
            if (!desk.windows.contains(win)) continue;
            if (desk.pinned == win) {
                // The main menu's terminal and the Esc menu's stay.
                String where = buttons != null ? buttons.where() : "menu";
                if (win.terminal()) ((TermWindow) win).shell.error("exit: this terminal is the " + where + ", and it stays open");
                return;
            }
            desk.close(win);
            if (ExtPane.class.isInstance(win)) ((ExtPane) win).closed();
        }
        if (dragWin == win) endDrag();
        if (listPane("servers") == null) Servers.stop();
    }

    private void ensureSeeded(Desk desk) {
        if (desk.seeded) return;
        desk.seeded = true;
        if (!hasTerminal(desk)) {
            TermWindow win = spawn(desk, areaX + areaW / 2f, areaY + areaH / 2f);
            layout();
            win.snap();
            if (special != null && desk == baseDesk()) {
                // The main menu's and the Esc menu's own terminal: pinned, and it lists the buttons.
                desk.pinned = win;
                win.credit = onEsc();
                pendingLs = true;
                if (intro) win.fetchFrom = opened + Boot.FETCH_AT;
                else runPendingLs();
            }
        }
    }

    /** Whether a desk has a terminal on it; readouts and list windows alone do not count. */
    private static boolean hasTerminal(Desk desk) {
        for (Pane p : desk.windows) if (p.terminal()) return true;
        return false;
    }

    /** The main menu's worlds or servers window, when it is open. */
    private ListPane listPane(String kind) {
        for (Pane p : desk().windows) {
            if (ListPane.class.isInstance(p) && ((ListPane) p).kind.equals(kind)) return (ListPane) p;
        }
        return null;
    }

    void toggleFloat(Pane win) {
        if (win == null) return;
        Desk desk = desk();
        if (!win.floating) {
            win.fw = clamp(win.w * 0.8f, win.minW(), width * 0.7f);
            win.fh = clamp(win.h * 0.8f, win.minH(), height * 0.7f);
            win.fx = win.x + (win.w - win.fw) / 2f;
            win.fy = win.y + (win.h - win.fh) / 2f;
            desk.untile(win);
            win.floating = true;
        } else {
            desk.tileAt(win, win.x + win.w / 2f, win.y + win.h / 2f);
        }
        desk.focus(win);
        layout();
    }

    /** Topmost window under a point: floating ones first, top of the pile down, then tiled. */
    private Pane windowAt(double mx, double my) {
        Desk desk = desk();
        // The last raised is drawn over the rest (renderDesk), so it is the one under the cursor first.
        Pane top = desk.top();
        if (top != null && top.contains(mx, my)) return top;
        List<Pane> floating = desk.floating();
        for (int i = floating.size() - 1; i >= 0; i--) if (floating.get(i).contains(mx, my)) return floating.get(i);
        for (Pane w : desk.tiled()) if (w.contains(mx, my)) return w;
        return null;
    }

    // ---- layout ---------------------------------------------------------

    private void initDesk() {
        area();
        ensureSeeded(baseDesk());
        layout();
    }

    /** The desk's area inside {@code gaps_out}, under the bar. */
    private void area() {
        int out = HyprConf.get().gapsOut;
        areaX = out;
        areaY = BAR_H + out;
        areaW = width - 2 * out;
        areaH = height - BAR_H - 2 * out;
    }

    /**
     * Where every pane on the open workstation should be; they ease there on their own.
     * Columns share the desk's width by weight and the panes in a column share its height by
     * theirs -- or, in rows, the same with width and height the other way round.
     */
    void layout() {
        area();
        layoutDesk(baseDesk());
    }

    private void layoutDesk(Desk desk) {
        boolean moving = dragMode == DRAG_MOVE && dragMoved;
        HyprConf conf = HyprConf.get();
        int gap = 2 * conf.gapsIn;
        if (Desk.dwindleLayout()) {
            desk.dwindle.place(areaX, areaY, areaW, areaH, gap, moving ? dragWin : null, conf.preserveSplit);
        } else {
            layoutColumns(desk, moving, gap);
        }
        floatAndGroup(desk);
    }

    private void layoutColumns(Desk desk, boolean moving, int gap) {

        List<List<Pane>> groups = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        for (Desk.Column column : desk.columns) {
            List<Pane> panes = new ArrayList<>(column.panes);
            if (moving) panes.remove(dragWin);
            if (panes.isEmpty()) continue;
            groups.add(panes);
            weights.add(column.weight);
        }

        int n = groups.size();
        if (n > 0) {
            boolean rows = desk.rows;
            float across = rows ? areaH : areaW;
            float along = rows ? areaW : areaH;
            float total = 0;
            for (float weight : weights) total += weight;
            float free = across - gap * (n - 1);
            float at = rows ? areaY : areaX;

            for (int i = 0; i < n; i++) {
                float size = free * weights.get(i) / total;
                List<Pane> panes = groups.get(i);
                float sum = 0;
                for (Pane p : panes) sum += p.weight;
                float freeAlong = along - gap * (panes.size() - 1);
                float a = rows ? areaX : areaY;
                for (Pane p : panes) {
                    float length = freeAlong * p.weight / sum;
                    if (rows) place(p, a, at, length, size);
                    else place(p, at, a, size, length);
                    a += length + gap;
                }
                at += size + gap;
            }
        }
    }

    /**
     * Floating panes where they float, and each group's tab strip cut from the top of its showing
     * pane. A pane is drawn pulled inside the screen, but its own place is left as it was: a
     * smaller window for a while (mini, a resize) moves nothing once it is back.
     */
    private void floatAndGroup(Desk desk) {
        for (Pane w : desk.floating()) {
            float fw = clamp(w.fw, w.minW(), width - 4);
            float fh = clamp(w.fh, w.minH(), height - BAR_H - 4);
            float fx = clamp(w.fx, 40 - fw, width - 40);
            // A floating group wears its tabs above it: room for them under the bar.
            float fy = clamp(w.fy, BAR_H + 1 + (w.group != null ? Group.BAR_H : 0), height - KEEP_VISIBLE);
            place(w, fx, fy, fw, fh);
        }

        for (Pane w : desk.windows) {
            Group group = w.group;
            if (group == null || group.active != w || w == dragWin && dragMode == DRAG_MOVE && dragMoved) continue;
            // A tiled group gives its tile's top to the tabs; a floating one wears them above it.
            if (!w.floating) {
                w.targetY += Group.BAR_H;
                w.targetH = Math.max(w.minH(), w.targetH - Group.BAR_H);
            }
            // The hidden tabs keep the showing one's place, so any of them shows without moving.
            for (Pane tab : group.members) {
                if (tab == w) continue;
                place(tab, w.targetX, w.targetY, w.targetW, w.targetH);
                tab.snap();
            }
        }
    }

    private static void place(Pane w, float x, float y, float width, float height) {
        w.targetX = x;
        w.targetY = y;
        w.targetW = width;
        w.targetH = height;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---- what windows and the headless shell call back into -------------

    /**
     * The headless shell's host: this screen, seen as a {@link Shell.Host}. A class of its own
     * rather than the screen implementing Host itself: a method that implements the same
     * method of two interfaces ({@code menu} of both Host and {@link Desktop}) is keyed by the
     * obfuscator for one of them only, and a call through the other lands on its decoy throw.
     */
    private static final class Headless implements Shell.Host {
        private final TerminalScreen screen;

        Headless(TerminalScreen screen) {
            this.screen = screen;
        }

        @Override
        public void exit() {
        }

        @Override
        public void quit() {
            screen.quit();
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
        public void launch(String app) {
        }

        @Override
        public void workstationsChanged() {
            screen.workstationsChanged();
        }

        @Override
        public void confirm(String question, String detail, String yes, Runnable onYes) {
            screen.confirm(question, detail, yes, onYes);
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
    }

    @Override
    public String dispatch(String dispatcher, String arg) {
        return hypr.dispatch(dispatcher, arg);
    }

    /** {@code close}: back to the game (or wherever the desktop was opened from); nothing behind the main menu. */
    @Override
    public void quit() {
        if (title) {
            TermWindow main = mainTerminal();
            if (main != null) main.shell.error("close: this is the home desk - there is nothing behind it");
            return;
        }
        onClose();
    }

    /** The menu's buttons on the main menu's desk and the Esc desk; null on a workstation. */
    @Override
    public MenuButtons menu() {
        return special != null ? buttons : null;
    }

    /**
     * The worlds or servers window, on the main menu, or the art window, on any desk: tiled in
     * beside the terminal (the right side, when the terminal is alone), or brought forward when
     * it is already open.
     */
    @Override
    public boolean openWindow(String kind) {
        if (kind != null && com.mineux.core.DeskExtensions.panes().containsKey(kind)) return openExt(kind);
        if (Objects.equals(ArtPane.KIND, kind)) return openArt();
        if (kind != null && kind.startsWith(ModuleWindow.KIND)) return openModule(kind.substring(ModuleWindow.KIND.length()));
        if (kind != null && kind.startsWith(LookupPane.KIND)) return openLookup(kind.substring(LookupPane.KIND.length()));
        if (kind != null && kind.startsWith(NoticeLines.CHAT)) return openChat(kind.substring(NoticeLines.CHAT.length()));
        if (Objects.equals(NoticeLines.NOTICES, kind) || Objects.equals(NoticeLines.FRIENDS, kind)
            || kind != null && kind.startsWith(NoticeLines.FRIEND)) return openSocial(kind);
        if (!title) return false;
        Desk desk = desk();
        ListPane open = listPane(kind);
        if (open != null) {
            desk.focus(open);
            return true;
        }
        // An if chain, not a switch on the kind: see Words for why this code has none.
        ListPane pane = null;
        if (Objects.equals("worlds", kind)) {
            pane = new ListPane(this, kind, "singleplayer - worlds", PlayCommands::worldLines);
        } else if (Objects.equals("servers", kind)) {
            pane = new ListPane(this, kind, "multiplayer - servers", PlayCommands::serverLines);
        }
        if (pane == null) return false;
        tileRight(pane);
        return true;
    }

    /**
     * The bar's social windows, on any desk: the bell's notifications, the friends list, a
     * friend's profile ({@link NoticeLines}). Floating under the bar's right end, where the bell
     * and the friends count are; one of each, brought forward when it is open already.
     */
    private boolean openSocial(String kind) {
        Desk desk = desk();
        ListPane open = listPane(kind);
        if (open != null) {
            desk.focus(open);
            return true;
        }
        if (!Objects.equals(NoticeLines.NOTICES, kind)) return openFriends(kind);
        java.util.function.Consumer<String> opener = this::openWindow;
        ListPane pane;
        int fw;
        if (Objects.equals(NoticeLines.NOTICES, kind)) {
            com.mineux.core.social.Notices.refresh();
            pane = new ListPane(this, kind, "notifications", () -> NoticeLines.notices(opener));
            fw = 270;
        } else if (Objects.equals(NoticeLines.FRIENDS, kind)) {
            pane = new ListPane(this, kind, "friends", () -> NoticeLines.friends(opener));
            fw = 250;
        } else {
            String user = kind.substring(NoticeLines.FRIEND.length());
            if (user.isEmpty()) return false;
            pane = new ListPane(this, kind, "friend - " + user, () -> NoticeLines.profile(user, opener));
            fw = 260;
        }
        float room = height - BAR_H - 12;
        pane.fw = Math.min(fw, width - 12);
        pane.fh = Math.min(230, room);
        int profiles = 0;
        for (Pane p : desk.windows) if (ListPane.class.isInstance(p) && ((ListPane) p).kind.startsWith(NoticeLines.FRIEND)) profiles++;
        // A profile sits beside the friends list; the list and the bell under the right of the bar.
        float shift = kind.startsWith(NoticeLines.FRIEND) ? pane.fw + 8 + profiles * 12 : 0;
        pane.fx = clamp(width - pane.fw - 6 - shift, 6, Math.max(6, width - pane.fw - 6));
        pane.fy = BAR_H + 6 + Math.min(profiles * 10, Math.max(0, room - pane.fh));
        float_(desk, pane);
        return true;
    }

    /**
     * The friends app ({@link FriendsView}): the terminal on this desk already showing it, brought
     * forward and moved to that friend, else a new terminal floating under the bar's right end.
     */
    private boolean openFriends(String kind) {
        String user = kind.startsWith(NoticeLines.FRIEND) ? kind.substring(NoticeLines.FRIEND.length()) : "";
        String app = Shell.APP_FRIENDS + (user.isEmpty() ? "" : ":" + user);
        Desk desk = desk();
        for (Pane p : desk.windows) {
            if (p.terminal() && ((TermWindow) p).showingFriends()) {
                desk.focus(p);
                ((TermWindow) p).launch(app);
                return true;
            }
        }
        float room = height - BAR_H - 12;
        TermWindow win = new TermWindow(this, width - 200f, BAR_H + 100f);
        // Big enough for the whole profile, every button included; a smaller screen scrolls it.
        win.fw = Math.min(FriendsView.WANT_W, width - 12);
        win.fh = Math.min(FriendsView.WANT_H, room);
        win.fx = Math.max(6, width - win.fw - 6);
        win.fy = BAR_H + 6;
        float_(desk, win);
        win.launch(app);
        return true;
    }

    /**
     * A friend's conversation (a DM's notification, in the bell or a card): the terminal on this
     * desk already showing irc, brought forward on their tab, else a new one floating under the
     * bar's right end. Opening it reads their notifications.
     */
    private boolean openChat(String name) {
        com.mineux.core.social.Friend friend = com.mineux.core.social.FriendList.find(name);
        String user = friend != null ? friend.user : name;
        if (user.isEmpty()) return false;
        com.mineux.core.social.Notices.readFrom(user);
        String app = Shell.APP_IRC + ":@" + user;
        Desk desk = desk();
        for (Pane p : desk.windows) {
            if (p.terminal() && ((TermWindow) p).showingChat()) {
                desk.focus(p);
                ((TermWindow) p).launch(app);
                return true;
            }
        }
        float room = height - BAR_H - 12;
        TermWindow win = new TermWindow(this, width - 200f, BAR_H + 100f);
        win.fw = Math.min(430, width - 12);
        win.fh = Math.min(280, room);
        win.fx = Math.max(6, width - win.fw - 6);
        win.fy = BAR_H + 6;
        float_(desk, win);
        win.launch(app);
        return true;
    }

    /** The lookup window open for a player on this desk, or null. */
    private LookupPane lookupPane(String player) {
        for (Pane p : desk().windows) {
            if (LookupPane.class.isInstance(p) && ((LookupPane) p).profile.asked.equalsIgnoreCase(player)) return (LookupPane) p;
        }
        return null;
    }

    /**
     * A player's lookup window ({@link LookupPane}), floating in the middle of the desk, or
     * ({@code name:friends}) one of its lists in a floating window beside it; either is
     * brought forward when it is open already.
     */
    private boolean openLookup(String what) {
        int cut = what.indexOf(':');
        String player = cut < 0 ? what : what.substring(0, cut);
        if (player.isEmpty()) return false;
        Desk desk = desk();
        LookupPane main = lookupPane(player);
        float room = height - BAR_H - 12;

        if (cut < 0) {
            if (main != null) {
                desk.focus(main);
                return true;
            }
            int open = 0;
            for (Pane p : desk.windows) if (LookupPane.class.isInstance(p)) open++;
            LookupPane pane = new LookupPane(this, Profile.of(player));
            pane.fw = Math.min(LookupPane.WIDTH, width - 12);
            pane.fh = Math.min(LookupPane.HEIGHT, room);
            pane.fx = clamp((width - pane.fw) / 2f + open * 14, 6, Math.max(6, width - pane.fw - 6));
            pane.fy = BAR_H + 6 + Math.min(open * 10, Math.max(0, room - pane.fh));
            float_(desk, pane);
            return true;
        }

        String section = what.substring(cut + 1);
        String kind = LookupPane.KIND + player.toLowerCase(java.util.Locale.ROOT) + ":" + section;
        ListPane list = listPane(kind);
        if (list != null) {
            desk.focus(list);
            return true;
        }
        Profile profile = main != null ? main.profile : Profile.of(player);
        int lists = 0;
        for (Pane p : desk.windows) if (ListPane.class.isInstance(p) && p.floating) lists++;
        ListPane pane = new ListPane(this, kind, LookupLists.title(profile, section), () -> LookupLists.lines(profile, section));
        pane.fw = Math.min(section != null && section.equals(LookupLists.MATCHES) ? 330 : 190, width - 12);
        pane.fh = Math.min(210, room);
        // Beside the lookup it came from, on whichever side has the room, each a step down from the last.
        float beside = main == null ? width - pane.fw - 6 : main.fx + main.fw + 4;
        if (main != null && beside + pane.fw > width - 6) beside = main.fx - pane.fw - 4;
        pane.fx = clamp(beside, 6, Math.max(6, width - pane.fw - 6));
        pane.fy = BAR_H + 6 + Math.min(lists % 6 * 16, Math.max(0, room - pane.fh));
        float_(desk, pane);
        return true;
    }

    /** Puts a new pane on a desk floating where its {@code fx}, {@code fy} say, on top and focused. */
    private void float_(Desk desk, Pane pane) {
        pane.floating = true;
        desk.windows.add(pane);
        desk.focus(pane);
        layout();
    }

    /** The art window, on any desk: tiled in on the right, or brought forward when it is open. */
    /** An extension's window ({@link com.mineux.core.DeskExtensions#addPane}): tiled in on the right, or brought forward. */
    private boolean openExt(String kind) {
        Desk desk = desk();
        for (Pane p : desk.windows) {
            if (ExtPane.class.isInstance(p) && Objects.equals(((ExtPane) p).kind, kind)) {
                desk.focus(p);
                return true;
            }
        }
        DeskWindow window;
        try {
            window = com.mineux.core.DeskExtensions.panes().get(kind).open();
        } catch (RuntimeException | LinkageError e) {
            com.mineux.core.MineUX.LOGGER.warn("The {} window failed to open", kind, e);
            return false;
        }
        if (window == null) return false;
        tileRight(new ExtPane(kind, window, this));
        return true;
    }

    private boolean openArt() {
        Desk desk = desk();
        for (Pane p : desk.windows) {
            if (ArtPane.class.isInstance(p)) {
                desk.focus(p);
                return true;
            }
        }
        tileRight(new ArtPane(this));
        return true;
    }

    /**
     * A module's settings ({@link ModuleWindow}): its group's window -- or its own, outside every
     * group -- tiled in on the right, or brought forward on that module when it is open already.
     */
    private boolean openModule(String path) {
        String key = ModuleWindow.keyFor(path);
        if (key == null) return false;
        Desk desk = desk();
        for (Pane p : desk.windows) {
            if (ModuleWindow.class.isInstance(p) && Objects.equals(((ModuleWindow) p).key, key)) {
                ((ModuleWindow) p).show(path);
                desk.focus(p);
                layout();
                return true;
            }
        }
        tileRight(ModuleWindow.open(this, path));
        return true;
    }

    private void tileRight(Pane pane) {
        Desk desk = desk();
        desk.windows.add(pane);
        desk.tileAt(pane, areaX + areaW - 2, areaY + areaH / 2f);
        desk.focus(pane);
        layout();
    }

    @Override
    public void workstationsChanged() {
        // A switch made from the Esc desk (`ws open 2` typed there) goes to that workstation.
        if (onEsc()) special = null;
        deskChanged();
    }

    /** A command from the bar or a SUPER key, run without printing anything anywhere. */
    void command(String command) {
        // Silent: switching workstations is a keystroke, not something to scroll past later.
        headless.quiet(command);
    }

    // ---- ticking --------------------------------------------------------

    private void tickDesk() {
        if (intro && clock() >= Boot.LS_AT) runPendingLs();
        if (!keysDown.isEmpty() && minecraft != null) {
            long handle = minecraft.getWindow().handle();
            keysDown.removeIf(k -> GLFW.glfwGetKey(handle, k) != GLFW.GLFW_PRESS);
        }
        for (Pane w : baseDesk().windows) {
            boolean settled = Math.abs(w.targetW - w.w) < 0.5f && Math.abs(w.targetH - w.h) < 0.5f;
            w.tick(settled);
        }
    }

    // ---- rendering ------------------------------------------------------

    /** The last extension backdrop that is on, drawn; false for none (or one that failed). */
    private boolean backdrop(GuiGraphicsExtractor g) {
        List<Backdrop> all = DeskExtensions.backdrops();
        for (int i = all.size() - 1; i >= 0; i--) {
            Backdrop backdrop = all.get(i);
            try {
                if (!backdrop.on()) continue;
                backdrop.draw(g, width, height, lastMouseX, lastMouseY, 0f);
                return true;
            } catch (RuntimeException | LinkageError e) {
                MineUX.LOGGER.warn("A desk backdrop failed; it is skipped", e);
                return false;
            }
        }
        return false;
    }

    private void drawBackground(GuiGraphicsExtractor g) {
        if (title) {
            // No world behind the home desk: black, or an extension's backdrop (the visuals' menu
            // scenes), and the greeter's tint carried on.
            if (!backdrop(g)) g.fillGradient(0, 0, width, height, 0xFF0A0B10, 0xFF020203);
            if (tint != null) tint.drawSettled(g, width, height, 1f);
            return;
        }
        // Not over a game screen that blurred the frame already (Overlay).
        if (minecraft != null && minecraft.options.getMenuBackgroundBlurriness() >= 1 && !Overlay.blurTaken()) g.blurBeforeThisStratum();
        g.fillGradient(0, 0, width, height, 0x8C050608, 0xA8020203);
    }

    private void drawDesk(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Font font = font();
        hits.clear();
        long now = System.currentTimeMillis();
        long nano = System.nanoTime();
        float dt = Math.min(0.1f, (nano - lastFrame) / 1e9f);
        lastFrame = nano;
        // Focus follows the mouse: the window the cursor moves onto takes the keyboard -- only when
        // the cursor moves, so a window chosen by a key keeps it until then. The top of the pile is
        // a click's, a key's or an opening's (Desk.focus), never the cursor's.
        boolean moved = mouseX != lastMouseX || mouseY != lastMouseY;
        if (moved && dragMode == DRAG_NONE && !Tray.over(mouseX, mouseY)) {
            Pane under = windowAt(mouseX, mouseY);
            if (under != null) desk().hover(under);
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        Desk desk = desk();
        long t = clock();
        if (intro && t > Boot.END) {
            intro = false;
            runPendingLs();
        }
        // A debug switch flipped anywhere opens or closes its readout here, this frame. Only on
        // workstations: the menus' desks are not where the modules are tuned.
        Desk base = baseDesk();
        if (special == null && !title) Readouts.sync(base, width, height);
        if (dragWin != null && !desk.windows.contains(dragWin)) endDrag();
        layout();
        animateDesk(base, dt);

        drawDesks(g, font, now, base, mouseX, mouseY);

        // Whatever is being dragged, over everything.
        if (dragWin != null) {
            if (dragWin.group != null) Decor.groupBar(g, font, desk, dragWin.group, true, mouseX, mouseY, hits);
            dragWin.draw(g, font, now, mouseX, mouseY, true, hits, hovered);
            Group bar = dragMode == DRAG_MOVE && dragMoved ? Decor.groupBarAt(desk, mouseX, mouseY) : null;
            if (bar != null && bar != dragWin.group) {
                // Let go here and it joins that group as a tab.
                Pane a = bar.active;
                outline(g, a.ix(), a.iy() - Group.BAR_H, a.iw(), Group.BAR_H);
            } else if (dragMode == DRAG_MOVE && dragMoved && !dragWin.floating) {
                Pane target = dropTarget(mouseX, mouseY);
                if (target != null) {
                    // Where it would land: half the pane beside an edge, all of it for a swap.
                    float[] r = zoneRect(target, dropZone(target, mouseX, mouseY));
                    outline(g, Math.round(r[0]), Math.round(r[1]), Math.round(r[2]), Math.round(r[3]));
                }
            }
        }

        // Only on an empty desk: any window open, terminal or not, hides it (Desk.empty).
        if (desk.empty()) {
            String keys = HyprConf.get().terminalKeys();
            String hint = keys == null ? "click here for a terminal"
                : keys.replace("SUPER", Shell.superKey().toUpperCase(Locale.ROOT)).toUpperCase(Locale.ROOT) + "  or click here for a terminal";
            int hx = (width - font.width(hint)) / 2;
            int hy = height - 40;
            boolean hot = mouseX >= hx - 4 && mouseX < hx + font.width(hint) + 4 && mouseY >= hy - 3 && mouseY < hy + 11;
            g.fill(hx - 4, hy - 3, hx + font.width(hint) + 4, hy + 11, hot ? 0x40EFC648 : 0x20FFFFFF);
            g.text(font, hint, hx, hy, hot ? GOLD : DIM, false);
            hits.add(new Hit(hx - 4, hy - 3, hx + font.width(hint) + 4, hy + 11, null, null, -1, null, ACT_NEW));
        }

        if (desk.focused != null && desk.windows.contains(desk.focused)) {
            desk.focused.renderPopup(g, font, mouseX, mouseY, hits);
        }

        TermWindow main = mainTerminal();
        if (intro) Boot.draw(g, font, t, width, height, main == null ? null : main.artBox);
        if (t >= Boot.BAR_AT) {
            int top = intro ? Boot.barOffset(t, BAR_H) : 0;
            g.pose().pushMatrix();
            g.pose().translate(0, top);
            drawBar(g, font, mouseX, mouseY - top, desk);
            g.pose().popMatrix();
        }
        // News about a friend, under the bar: on the desktop a message's card is a click away from the conversation.
        if (!intro) FriendBanner.draw(g, font, width, BAR_H, now);
        if (menuFor != null) drawMenu(g, font, mouseX, mouseY);
        if (prompt != null) drawPrompt(g, font, now, dt, mouseX, mouseY);

        hovered = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(mouseX, mouseY)) {
                hovered = hits.get(i);
                break;
            }
        }

        // CRT scanlines over everything.
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x0C000000);

        super.deskRender(g, mouseX, mouseY, delta);
    }

    private void animateDesk(Desk desk, float dt) {
        for (Pane w : desk.windows) {
            if (w == dragWin && dragMode == DRAG_MOVE && dragMoved) continue;
            w.animate(dt);
        }
    }

    /**
     * The desks: the one being slid away from (a switch slides it off one side while the new one
     * comes in from the other, or up and down with {@code slidevert}), and the one showing, the
     * last used pane on top.
     * <p>
     * The special workspace's first appearance is the one switch that does not slide sideways:
     * the desk it was asked for from stays where it is and dims, and the new one drops over it
     * from the top, on {@code specialWorkspace}'s animation.
     */
    private void drawDesks(GuiGraphicsExtractor g, Font font, long now, Desk base, int mouseX, int mouseY) {
        HyprConf conf = HyprConf.get();
        slide.track(base, deskOrder(), now);
        if (slide.running(now) && slide.dropping()) {
            float down = slide.progress(now);
            renderDesk(g, font, now, slide.from(), false, mouseX, mouseY);
            Decor.dim(g, 0, BAR_H, width, height - BAR_H, conf.dimSpecial * down);
            g.pose().pushMatrix();
            g.pose().translate(0, (down - 1f) * height);
            renderDesk(g, font, now, base, true, mouseX, mouseY);
            Decor.afters(g, base, now);
            g.pose().popMatrix();
            return;
        }

        String style = conf.style("workspaces");
        boolean vertical = style.contains("vert");
        // fade and slidefade: the GUI has no fading of what a pane draws, so the old desk fades
        // out through dark and the new one in, moving only slidefade's percentage of the way.
        boolean fade = style.contains("fade");
        float distance = fade ? conf.percent("workspaces", style.startsWith("slide") ? 0.2f : 0f) : 1f;
        int size = vertical ? height : width;
        boolean sliding = slide.running(now);
        float progress = sliding ? slide.progress(now) : 1f;
        float arriving = sliding ? slide.arriving(now, size) * distance : 0f;
        if (sliding && (!fade || progress < 0.5f)) {
            float leaving = slide.leaving(now, size) * distance;
            g.pose().pushMatrix();
            g.pose().translate(vertical ? 0 : leaving, vertical ? leaving : 0);
            renderDesk(g, font, now, slide.from(), false, mouseX, mouseY);
            g.pose().popMatrix();
            if (fade) Decor.dim(g, 0, BAR_H, width, height - BAR_H, progress * 2f * FADE_DARK);
        }

        if (!sliding || !fade || progress >= 0.5f) {
            g.pose().pushMatrix();
            g.pose().translate(vertical ? 0 : arriving, vertical ? arriving : 0);
            renderDesk(g, font, now, base, true, mouseX, mouseY);
            Decor.afters(g, base, now);
            g.pose().popMatrix();
            if (sliding && fade) Decor.dim(g, 0, BAR_H, width, height - BAR_H, (1f - progress) * 2f * FADE_DARK);
        }
    }

    /**
     * One desk's panes, tiled then floating bottom to top, and on top of all of them the one last
     * used, the focused one, whether it is tiled, floating or pinned: the last window used always
     * stays on top. A pin has nothing to do with it ({@link Desk#togglePin}). Only the desk with the input
     * ({@code live}) shows focus and hover and takes clicks; the others are drawn as they stand.
     */
    private void renderDesk(GuiGraphicsExtractor g, Font font, long now, Desk desk, boolean live, int mouseX, int mouseY) {
        HyprConf conf = HyprConf.get();
        int mx = live ? mouseX : -1, my = live ? mouseY : -1;
        List<Hit> into = live ? hits : new ArrayList<>();
        Pane top = desk.top();
        if (top == dragWin && live) top = null;
        for (Pane w : desk.tiled()) {
            if (w == top || w == dragWin && live) continue;
            drawPane(g, font, now, desk, w, live, mx, my, into, conf);
        }
        for (Pane w : desk.floating()) {
            if (w == top || w == dragWin && live) continue;
            drawPane(g, font, now, desk, w, live, mx, my, into, conf);
        }
        if (top != null) drawPane(g, font, now, desk, top, live, mx, my, into, conf);
    }

    private void drawPane(GuiGraphicsExtractor g, Font font, long now, Desk desk, Pane w, boolean live, int mx, int my,
                          List<Hit> into, HyprConf conf) {
        boolean focused = live && w == desk.focused;
        if (w.group != null) Decor.groupBar(g, font, desk, w.group, focused, mx, my, into);
        w.draw(g, font, now, mx, my, focused, into, live ? hovered : null);
        if (live && !focused && conf.dimInactive && !w.glass()) Decor.dim(g, w.ix(), w.iy(), w.iw(), w.ih(), conf.dimStrength);
    }

    /** A gold box: where a dragged pane would land. */
    private static void outline(GuiGraphicsExtractor g, int x0, int y0, int w, int h) {
        int x1 = x0 + w, y1 = y0 + h;
        g.fill(x0, y0, x1, y1, 0x30EFC648);
        g.fill(x0, y0, x1, y0 + 1, 0xA0EFC648);
        g.fill(x0, y1 - 1, x1, y1, 0xA0EFC648);
        g.fill(x0, y0, x0 + 1, y1, 0xA0EFC648);
        g.fill(x1 - 1, y0, x1, y1, 0xA0EFC648);
    }

    private void drawPrompt(GuiGraphicsExtractor g, Font font, long now, float dt, int mouseX, int mouseY) {
        // Modal: only its own buttons answer the mouse.
        hits.clear();
        g.nextStratum();
        g.fill(0, 0, width, height, 0x70000000);
        prompt.place(width, height);
        prompt.animate(dt);
        prompt.render(g, font, now, mouseX, mouseY, true, hits, hovered);
    }

    /** A toast, for news that may arrive with no desktop open to print it on. */
    static void tell(String title, String detail) {
        Minecraft mc = Minecraft.getInstance();
        SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
            Component.literal(title), Component.literal(detail));
    }

    /** The desktop going down ({@link com.mineux.core.ShutdownLayer}), over everything; why it fell through, said once on the terminal. */
    private void drawShutdown(GuiGraphicsExtractor g, com.mineux.core.ShutdownLayer down) {
        String lost = Shutdowns.lost(down);
        if (lost != null) {
            say("reboot: " + lost);
            return;
        }
        g.nextStratum();
        Shutdowns.draw(down, g, font(), width, height, title, title ? tint : null);
    }

    /** A line of error on the desk's main terminal, if it has one. */
    private void say(String error) {
        TermWindow main = mainTerminal();
        if (main != null) main.shell.error(error);
    }

    @Override
    public void confirm(String question, String detail, String yes, Runnable onYes) {
        Prompt[] made = new Prompt[1];
        made[0] = new Prompt(question, detail, yes, onYes, () -> {
            if (prompt == made[0]) prompt = null;
        });
        prompt = made[0];
    }

    // ---- the bar --------------------------------------------------------

    private void drawBar(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, Desk desk) {
        g.fill(0, 0, width, BAR_H, 0xD80A0B0F);
        g.fill(0, BAR_H - 1, width, BAR_H, 0x30FFFFFF);
        int ty = (BAR_H - 8) / 2;

        int x = 0;
        String logo = " " + Shell.brand() + " ";
        g.fill(x, 0, x + font.width(logo), BAR_H - 1, GOLD);
        g.text(font, logo, x, ty, 0xFF15110A, false);
        x += font.width(logo) + 4;

        String current = Workstations.current();
        // The right side first: the tabs get what is left of the bar, and shrink to fit it.
        int right = drawBarRight(g, font, mouseX, mouseY, desk, current) - 8;
        // First the home desk on the main menu, or the Esc desk in a world.
        if (title) x = tab(g, font, " home ", x, onMenu(), mouseX, mouseY, MENU_DESK) + 3;
        else if (inWorld()) x = tab(g, font, " esc ", x, onEsc(), mouseX, mouseY, ESC_DESK) + 3;

        List<String> names = new ArrayList<>();
        for (String name : Workstations.names()) {
            // Not numbered, and after the plus: it is not one of the loadouts.
            if (!Workstations.isSpecial(name)) names.add(name);
        }
        String plus = " + ";
        int pw = font.width(plus);
        String specialLabel = " " + com.mineux.core.MineUX.specialName() + " ";
        int sw = Workstations.hasSpecial() ? font.width(specialLabel) : 0;
        int room = right - x - names.size() - pw - 3 - sw;
        int[] natural = new int[names.size()];
        int[] least = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String slot = String.valueOf(Workstations.slot(names.get(i)));
            natural[i] = font.width(" " + slot + ":" + names.get(i) + " ");
            least[i] = font.width(BarTabs.bare(slot));
        }
        int[] widths = BarTabs.widths(natural, least, room);
        x = drawTabs(g, font, names, widths, x, current, mouseX, mouseY);

        boolean plusHot = mouseX >= x && mouseX < x + pw && mouseY < BAR_H - 1;
        if (plusHot) g.fill(x, 0, x + pw, BAR_H - 1, 0x22FFFFFF);
        g.text(font, plus, x, ty, plusHot ? GREEN : DIM, false);
        hits.add(new Hit(x, 0, x + pw, BAR_H - 1, null, null, -1, null, ACT_PLUS));
        x += pw + 3;

        // The special workspace, once it has been made.
        if (Workstations.hasSpecial()) specialTab(g, font, x, special == null && Workstations.isSpecial(current), mouseX, mouseY);
    }

    /** The workstation tabs at the widths {@link BarTabs} gave them; returns where the next goes. */
    private int drawTabs(GuiGraphicsExtractor g, Font font, List<String> names, int[] widths, int x, String current, int mouseX, int mouseY) {
        int ty = (BAR_H - 8) / 2;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int w = widths[i];
            String label = BarTabs.label(font, String.valueOf(Workstations.slot(name)), name, w);
            boolean open = special == null && name.equals(current);
            boolean hot = mouseX >= x && mouseX < x + w && mouseY < BAR_H - 1;

            if (open) g.fill(x, 0, x + w, BAR_H - 1, 0xFF2B2413);
            else if (hot) g.fill(x, 0, x + w, BAR_H - 1, 0x22FFFFFF);
            if (open) g.fill(x, BAR_H - 3, x + w, BAR_H - 1, GOLD);

            g.text(font, label, x, ty, open ? GOLD : hot ? FG : DIM, false);
            hits.add(new Hit(x, 0, x + w, BAR_H - 1, null, null, -1, name, ACT_NONE));
            x += w + 1;
        }
        return x;
    }

    /** What the bar says about the layout: the focused pane's state, or how the desk tiles. */
    private static String layoutLabel(Desk desk) {
        Pane f = desk.focused;
        if (f != null && f.onAll) return "[pin]";
        if (f != null && f.group != null) return "[group " + (f.group.members.indexOf(f) + 1) + "/" + f.group.members.size() + "]";
        if (f != null && f.floating) return "[float]";
        if (Desk.dwindleLayout()) return "[dwindle]";
        return desk.rows ? "[rows]" : "[cols]";
    }

    /** A tab in the bar; returns where the next goes. */
    /** The special workspace's tab: a workstation's in everything but its colour, which says what it is for. */
    private void specialTab(GuiGraphicsExtractor g, Font font, int x, boolean open, int mouseX, int mouseY) {
        String label = " " + com.mineux.core.MineUX.specialName() + " ";
        int ty = (BAR_H - 8) / 2;
        int w = font.width(label);
        boolean hot = mouseX >= x && mouseX < x + w && mouseY < BAR_H - 1;
        if (open) g.fill(x, 0, x + w, BAR_H - 1, 0xFF2B1313);
        else if (hot) g.fill(x, 0, x + w, BAR_H - 1, 0x22FFFFFF);
        if (open) g.fill(x, BAR_H - 3, x + w, BAR_H - 1, RED);
        g.text(font, label, x, ty, open || hot ? RED : DIM, false);
        hits.add(new Hit(x, 0, x + w, BAR_H - 1, null, null, -1, Workstations.SPECIAL, ACT_NONE));
    }

    private int tab(GuiGraphicsExtractor g, Font font, String label, int x, boolean open, int mouseX, int mouseY, String desk) {
        int ty = (BAR_H - 8) / 2;
        int w = font.width(label);
        boolean hot = mouseX >= x && mouseX < x + w && mouseY < BAR_H - 1;
        if (open) g.fill(x, 0, x + w, BAR_H - 1, 0xFF2B2413);
        else if (hot) g.fill(x, 0, x + w, BAR_H - 1, 0x22FFFFFF);
        if (open) g.fill(x, BAR_H - 3, x + w, BAR_H - 1, GOLD);
        g.text(font, label, x, ty, open ? GOLD : hot ? FG : DIM, false);
        hits.add(new Hit(x, 0, x + w, BAR_H - 1, null, null, -1, desk, ACT_NONE));
        return x + w;
    }

    /** Right side, right to left: the clock, fps, the module tray, the layout, the config (or who you play as). */
    private int drawBarRight(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, Desk desk, String current) {
        int ty = (BAR_H - 8) / 2;
        int rx = drawAccount(g, font, drawSocial(g, font, width - 4, mouseX, mouseY), mouseX, mouseY);
        String clock = LocalTime.now().format(CLOCK);
        rx -= font.width(clock);
        g.text(font, clock, rx, ty, FG, false);

        String fps = minecraft == null ? "" : minecraft.getFps() + " fps";
        rx -= 10 + font.width(fps);
        g.text(font, fps, rx, ty, DIM, false);

        String ping = BarFacts.ping(minecraft);
        if (ping != null) {
            rx -= 10 + font.width(ping);
            g.text(font, ping, rx, ty, DIM, false);
        }
        String memory = BarFacts.memory();
        rx -= 10 + font.width(memory);
        g.text(font, memory, rx, ty, DIM, false);

        rx = drawReadouts(g, font, rx, mouseX, mouseY);

        rx = Tray.draw(g, font, rx - 8, BAR_H, width, mouseX, mouseY, hits);

        String layout = layoutLabel(desk);
        rx -= 8 + font.width(layout);
        g.text(font, layout, rx, ty, CYAN, false);

        if (onMenu()) {
            String as = "as ";
            String who = headless.user();
            rx -= 10 + font.width(as + who);
            g.text(font, as, rx, ty, DIM, false);
            g.text(font, who, rx + font.width(as), ty, GREEN, false);
            return rx;
        }

        String cfg = "config: ";
        rx -= 10 + font.width(cfg + current);
        g.text(font, cfg, rx, ty, DIM, false);
        g.text(font, current, rx + font.width(cfg), ty, MAGENTA, false);
        return rx;
    }

    /** The extensions' readouts ({@link DeskExtensions#addBarReadout}), right to left from {@code right}. */
    private int drawReadouts(GuiGraphicsExtractor g, Font font, int right, int mouseX, int mouseY) {
        int ty = (BAR_H - 8) / 2;
        int rx = right;
        List<com.mineux.core.BarReadout> all = DeskExtensions.barReadouts();
        for (int i = all.size() - 1; i >= 0; i--) {
            com.mineux.core.BarReadout readout = all.get(i);
            String text;
            try {
                text = readout.text();
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            if (text == null || text.isEmpty()) continue;
            int w = font.width(text);
            rx -= 10 + w;
            g.text(font, text, rx, ty, readout.color(), false);
            if (readout.command() != null || readout.hint() != null) {
                Span span = new Span(text, FG);
                span.run = readout.command();
                span.quiet = true;
                span.hint = readout.hint();
                hits.add(new Hit(rx - 2, 0, rx + w + 2, BAR_H - 1, null, span, -1, null, ACT_TRAY));
            }
        }
        return rx;
    }

    /**
     * The bar's right end, right to left: the bell (lit, with a count, while anything is unread)
     * and how many friends are online. Each opens its window. Returns where the next thing ends.
     */
    private int drawSocial(GuiGraphicsExtractor g, Font font, int right, int mouseX, int mouseY) {
        com.mineux.core.social.Notices.refreshIfStale();
        int ty = (BAR_H - 8) / 2;
        int unread = com.mineux.core.social.Notices.unread();

        String count = unread > 99 ? "99+" : String.valueOf(unread);
        int badge = unread > 0 ? font.width(count) + 4 : 0;
        int w = 11 + (badge > 0 ? badge + 1 : 0) + 4;
        int x = right - w;
        boolean hot = mouseX >= x && mouseX < x + w && mouseY < BAR_H - 1;
        if (hot) g.fill(x, 0, x + w, BAR_H - 1, 0x22FFFFFF);
        bell(g, x + 2, (BAR_H - 10) / 2, unread > 0 ? GOLD : hot ? FG : DIM);
        if (badge > 0) {
            int bx = x + 13;
            g.fill(bx, ty - 1, bx + badge, ty + 8, 0xFFD8403B);
            g.text(font, count, bx + 2, ty, 0xFFFFFFFF, false);
        }
        socialHit(x, x + w, NoticeLines.NOTICES);

        if (!com.mineux.core.social.FriendConfig.get().enabled) return x - 2;
        int online = com.mineux.core.social.FriendList.onlineCount();
        String label = " friends ";
        String n = com.mineux.core.social.FriendList.connected() ? String.valueOf(online) : "-";
        int fw = font.width(label + n) + 3;
        int fx = x - 2 - fw;
        boolean fhot = mouseX >= fx && mouseX < fx + fw && mouseY < BAR_H - 1;
        if (fhot) g.fill(fx, 0, fx + fw, BAR_H - 1, 0x22FFFFFF);
        g.text(font, label, fx, ty, fhot ? FG : DIM, false);
        g.text(font, n, fx + font.width(label), ty, online > 0 ? GREEN : FAINT, false);
        socialHit(fx, fx + fw, NoticeLines.FRIENDS);
        return fx - 2;
    }

    /**
     * Who is signed in to MineUX, left of the friends count: the account's name, or "guest".
     * A click runs {@code whoami} in the desk's terminal, which offers login or logout.
     */
    private int drawAccount(GuiGraphicsExtractor g, Font font, int right, int mouseX, int mouseY) {
        int ty = (BAR_H - 8) / 2;
        boolean signedIn = com.mineux.core.net.MuxApi.signedIn();
        String who = signedIn ? com.mineux.core.net.MuxApi.user() : "guest";
        if (who == null || who.isEmpty()) who = "guest";
        int w = font.width(who) + 6;
        int x = right - w;
        boolean hot = mouseX >= x && mouseX < right && mouseY < BAR_H - 1;
        if (hot) g.fill(x, 0, right, BAR_H - 1, 0x22FFFFFF);
        g.text(font, who, x + 3, ty, signedIn ? GOLD : hot ? FG : FAINT, false);
        Span span = new Span(who, FG);
        span.run = "whoami";
        span.hint = signedIn ? "your MineUX account" : "not signed in: whoami, then login";
        hits.add(new Hit(x, 0, right, BAR_H - 1, null, span, -1, null, ACT_NONE));
        return x - 2;
    }

    private void socialHit(int x0, int x1, String kind) {
        Span span = new Span("", FG);
        span.action = () -> openWindow(kind);
        hits.add(new Hit(x0, 0, x1, BAR_H - 1, null, span, -1, null, ACT_NONE));
    }

    /** A bell, 9 wide and 10 high, in pixels: dome, body, rim, clapper. */
    private static void bell(GuiGraphicsExtractor g, int x, int y, int colour) {
        g.fill(x + 4, y, x + 5, y + 1, colour);
        g.fill(x + 3, y + 1, x + 6, y + 2, colour);
        g.fill(x + 2, y + 2, x + 7, y + 6, colour);
        g.fill(x + 1, y + 6, x + 8, y + 7, colour);
        g.fill(x, y + 7, x + 9, y + 8, colour);
        g.fill(x + 3, y + 9, x + 6, y + 10, colour);
    }

    private void drawMenu(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        int w = font.width("really delete?") + 12;
        int h = MENU.length * 11 + 4;
        int x = Math.min(menuX, width - w - 2);
        int y = menuY;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF33363F);
        g.fill(x, y, x + w, y + h, 0xF50C0D11);
        g.text(font, menuFor, x + 4, y - 11, MAGENTA, false);

        for (int i = 0; i < MENU.length; i++) {
            int iy = y + 2 + i * 11;
            boolean hot = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + 11;
            boolean delete = MENU[i].equals("delete");
            boolean disabled = delete && Workstations.names().size() <= 1;

            if (hot && !disabled) g.fill(x + 1, iy, x + w - 1, iy + 11, delete ? 0x40FF5C57 : 0x28FFFFFF);
            String label = delete && menuConfirmDelete ? "really delete?" : MENU[i];
            int color = disabled ? FAINT : delete ? RED : hot ? FG : DIM;
            g.text(font, label, x + 6, iy + 2, color, false);
            hits.add(new Hit(x, iy, x + w, iy + 11, null, null, -1, "menu:" + i, ACT_NONE));
        }
    }

    private static final Set<String> W_OPEN = Words.of("open");
    private static final Set<String> W_RENAME = Words.of("rename");
    private static final Set<String> W_DUPLICATE = Words.of("duplicate");
    private static final Set<String> W_DELETE = Words.of("delete");

    private void menuAction(int index) {
        String target = menuFor;
        String word = MENU[index];
        if (W_OPEN.contains(word)) {
            closeMenu();
            if (!target.equals(Workstations.current())) command("ws open " + target);
        } else if (W_RENAME.contains(word)) {
            closeMenu();
            // Typed into a terminal: the focused one, any other, or a new one.
            Pane focused = desk().focused;
            if (focused == null || !focused.terminal()) {
                focused = null;
                for (Pane p : desk().windows) if (p.terminal()) focused = p;
            }
            if (focused == null) focused = spawn(width / 2f, height / 2f);
            focused.setInput("ws mv " + target + " ");
        } else if (W_DUPLICATE.contains(word)) {
            closeMenu();
            if (target.equals(Workstations.current())) command("ws new");
            else command("ws open " + target + "; ws new");
        } else if (W_DELETE.contains(word)) {
            if (Workstations.names().size() <= 1) return;
            if (!menuConfirmDelete) {
                menuConfirmDelete = true;
                return;
            }
            closeMenu();
            command("ws rm " + target);
        } else {
            closeMenu();
        }
    }

    private void closeMenu() {
        menuFor = null;
        menuConfirmDelete = false;
    }

    // ---- keyboard -------------------------------------------------------

    private boolean superDown(int modifiers) {
        return (wmMods(modifiers) & HyprConf.superBit()) != 0;
    }

    /** An event's modifiers with right Alt left out of them ({@link HyprConf#leftAlt}). */
    private int wmMods(int modifiers) {
        return minecraft == null ? modifiers : HyprConf.leftAlt(minecraft.getWindow().handle(), modifiers);
    }

    private boolean keyDesk(KeyEvent event) {
        // The desk key (right shift) closes the desktop in a world when it is tapped on its own;
        // held for a capital it is only a shift. Any other key while it is down spoils the tap.
        deskTap = event.key() == com.mineux.core.MineUX.deskKey() && inWorld() && !onMenu();
        if (intro) {
            skipIntro();
            return true;
        }
        Pane capture = keyboardPane();
        if (capture != null && capture.capturing()) {
            // A settings window waiting for a key, or being typed into: every key is its own,
            // Escape and the window manager's binds included.
            boolean text = capture.typing();
            capture.keyPressed(event);
            if (!text) swallowCharUntil = System.currentTimeMillis() + 60;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            // Escape is the desktop's, whatever has the keyboard: Alt+Escape goes to the Esc
            // desk, Escape on its own closes the page.
            closeMenu();
            boolean hadPrompt = prompt != null;
            if (hadPrompt) prompt.close();
            if ((wmMods(event.modifiers()) & GLFW.GLFW_MOD_ALT) != 0) {
                if (title) showHome();
                else if (!onEsc()) showEsc();
            } else if (!(hadPrompt && onMenu())) {
                escapeKey();
            }
            return true;
        }
        if (prompt != null) {
            prompt.keyPressed(event);
            // The y or n that answered it is not typing.
            swallowCharUntil = System.currentTimeMillis() + 60;
            return true;
        }
        int key = event.key();
        Desk desk = desk();

        // The window manager's keys: hyprland.conf's binds.
        boolean repeat = key >= 0 && !keysDown.add(key);
        HyprConf.Bind bind = HyprConf.get().match(key, wmMods(event.modifiers()));
        if (bind != null) {
            if (!repeat || bind.repeat) runBind(bind);
            swallowCharUntil = System.currentTimeMillis() + 60;
            return true;
        }

        Pane focused = keyboardPane();
        if (focused != null) focused.keyPressed(event);
        return true;
    }

    /**
     * Escape. On the main menu it closes the newest window, the terminal staying; anywhere else
     * it closes the desktop (the Esc desk back to the game).
     */
    private void escapeKey() {
        // A workstation opened from the main menu: Escape goes back to the home desk.
        if (title && !onMenu()) {
            showHome();
            return;
        }
        if (onMenu()) {
            Desk desk = desk();
            for (int i = desk.windows.size() - 1; i >= 0; i--) {
                Pane p = desk.windows.get(i);
                if (p != desk.pinned && !p.terminal()) {
                    closeWindow(p);
                    layout();
                    return;
                }
            }
            return;
        }
        onClose();
    }

    /** A bind's dispatcher; what went wrong, if anything, is said in the terminal with the keyboard. */
    private void runBind(HyprConf.Bind bind) {
        String error = hypr.dispatch(bind.dispatcher, bind.arg);
        if (error == null) return;
        Pane focused = keyboardPane();
        if (focused != null && focused.terminal()) ((TermWindow) focused).shell.error(error);
    }

    /** The modifiers held now, as GLFW's bits: a scroll event does not carry them. */
    private int heldMods() {
        return minecraft == null ? 0 : HyprConf.heldMods(minecraft.getWindow().handle());
    }

    private boolean charDesk(CharacterEvent event) {
        if (System.currentTimeMillis() < swallowCharUntil) {
            swallowCharUntil = 0;
            return true;
        }
        if (prompt != null || intro) return true;
        Pane focused = keyboardPane();
        return focused != null && focused.charTyped(event);
    }

    // ---- mouse ----------------------------------------------------------

    private boolean clickDesk(MouseButtonEvent event, boolean isHandled) {
        int button = event.button();
        double mx = event.x(), my = event.y();
        Desk desk = desk();
        Hit hit = hitAt(mx, my);

        if (intro) {
            skipIntro();
            return true;
        }
        if (prompt != null) return prompt.click(hit);
        Term.clickButton = button;

        // A friend's card under the bar: a message opens the conversation, a request the friends app.
        if (menuFor == null) {
            String open = FriendBanner.clickAt(mx, my);
            if (open != null) {
                openWindow(open);
                return true;
            }
        }

        // A key row waiting for its key takes a middle or side button as that key.
        Pane capture = keyboardPane();
        if (capture != null && capture.capturing() && capture.captureClick(button)) return true;

        if (menuFor != null) {
            if (hit != null && hit.ws() != null && hit.ws().startsWith("menu:") && button == 0) {
                menuAction(Integer.parseInt(hit.ws().substring(5)));
            } else {
                closeMenu();
            }
            return true;
        }

        // SUPER + a button on a window moves it (left) or resizes it (right), wherever on it the cursor is.
        if (superDown(event.modifiers())) {
            Pane win = windowAt(mx, my);
            if (win != null) {
                desk.focus(win);
                if (button == 0) beginMove(win, mx, my);
                else if (button == 1) beginResize(win, mx, my);
            }
            return true;
        }

        // A click on any pane focuses it, and brings a floating one to the top -- not through
        // the tray's open rows, which are drawn over the panes.
        Pane under = Tray.over(mx, my) ? null : windowAt(mx, my);
        if (under != null) desk.focus(under);

        // A window's corner resizes it, and its title bar moves it, as on any desktop. A readout
        // has no prompt to keep clicks for, so its header is a title bar too.
        if (under != null && button == 0 && under.onGrip(mx, my)) {
            beginResize(under, mx, my);
            return true;
        }
        int header = under == null ? 0 : under.readout() != null ? ReadoutPane.HEADER_H : under.header();
        if (under != null && header > 0 && button == 0 && my < under.y + header && (hit == null || hit.span() == null)) {
            beginMove(under, mx, my);
            return true;
        }

        if (hit == null) return super.deskClicked(event, isHandled);

        TermWindow win = hit.win();
        if (win != null) desk.focus(win);

        if (hit.suggestion() >= 0 && win != null) {
            win.accept(hit.suggestion());
            return true;
        }

        if (hit.ws() != null && (hit.ws().equals(ESC_DESK) || hit.ws().equals(MENU_DESK))) {
            if (button == 0 && hit.ws().equals(ESC_DESK) && !onEsc()) showEsc();
            if (button == 0 && hit.ws().equals(MENU_DESK) && !onMenu()) showHome();
            return true;
        }
        if (hit.ws() != null && special != null && !hit.ws().startsWith("menu:")) {
            // A workstation's tab, from the Esc desk: off it, to that one.
            if (button == 0) {
                showWorkstation(hit.ws());
                return true;
            }
        }
        if (hit.ws() != null) {
            if (button == 1) {
                menuFor = hit.ws();
                menuX = (int) mx;
                menuY = BAR_H + 12;
                menuConfirmDelete = false;
            } else if (button == 0 && !hit.ws().equals(Workstations.current())) {
                goTo(hit.ws());
            }
            return true;
        }

        switch (hit.action()) {
            case ACT_SLIDER -> {
                if (button == 0 && hit.span() != null && hit.span().slider != null) {
                    slider = hit.span().slider;
                    sliderX0 = hit.x0();
                    sliderX1 = hit.x1();
                    sliderHeld = Slider.nearest(slider, sliderX0, sliderX1, mx);
                    Slider.drag(slider, sliderX0, sliderX1, sliderHeld, mx);
                }
            }
            case ACT_PLUS -> command("ws new");
            case ACT_TRAY -> {
                // A click opens (its run), a right click flips (its action).
                Span span = hit.span();
                if (button == 1) {
                    if (span.action != null) span.action.run();
                } else if (span.run != null && span.run.startsWith("open ") && !com.mineux.core.ui.GuiChoice.topbarOpensWindows()) {
                    // `gui topbar text` (dotfiles/ui/ui.conf): listed in a terminal instead of its window.
                    openInTerminal(span.run.substring("open ".length()));
                } else if (span.run != null) {
                    command(span.run);
                }
            }
            case ACT_NEW -> spawn((float) mx, (float) my);
            case ACT_CLOSE -> {
                closeWindow(windowAt(mx, my));
                layout();
            }
            case ACT_STOKE -> Fetch.stoke(hit.span().text);
            case ACT_ART_NEXT -> {
                if (button == 1) openWindow(ArtPane.KIND);
                else ArtView.cycle("next");
            }
            case ACT_ART_PREV -> ArtView.cycle("prev");
            case ACT_APP -> win.clickApp();
            case ACT_BODY -> {
            }
            default -> {
                Span span = hit.span();
                if (span == null) return true;
                if (span.action != null) {
                    span.action.run();
                    return true;
                }
                // A click on a window with no prompt of its own (the worlds, the servers) is run,
                // and echoed, in the desk's terminal.
                if (win == null && span.run != null && !span.quiet) win = mainTerminal();
                if (win == null && span.fill != null) {
                    // A window's "type this" (friends' add friend): into the desk's terminal, which takes the keys.
                    win = mainTerminal();
                    if (win != null) desk.focus(win);
                }
                if (win == null) {
                    if (span.run != null) command(span.run);
                } else if (span.run != null && span.quiet) {
                    win.runQuiet(span.run);
                } else if (span.fill != null && (span.run == null || button == 1)) {
                    win.setInput(span.fill);
                } else if (span.run != null) {
                    win.runVisible(span.run);
                }
            }
        }

        return true;
    }

    private void beginMove(Pane win, double mx, double my) {
        dragMode = DRAG_MOVE;
        dragMoved = false;
        dragWin = win;
        dragStartX = mx;
        dragStartY = my;
        dragRectX = win.x;
        dragRectY = win.y;
        dragRectW = win.w;
        dragRectH = win.h;
    }

    private void beginResize(Pane win, double mx, double my) {
        Desk desk = desk();
        dragMode = DRAG_RESIZE;
        dragWin = win;
        dragStartX = mx;
        dragStartY = my;
        dragRectX = win.x;
        dragRectY = win.y;
        dragRectW = win.w;
        dragRectH = win.h;
        // Floating: the corner nearest the cursor moves. Tiled: the edges nearest it.
        dragLeft = mx < win.x + win.w / 2f;
        dragTop = my < win.y + win.h / 2f;
        dragColumn = null;
        dragColumnNeighbour = null;
        dragNeighbour = null;
        dragSplitX = null;
        dragSplitY = null;

        if (win.floating) return;
        if (Desk.dwindleLayout()) {
            dragSplitX = desk.dwindle.splitAbove(win, false);
            dragSplitY = desk.dwindle.splitAbove(win, true);
            if (dragSplitX != null) dragRatioX = dragSplitX.ratio;
            if (dragSplitY != null) dragRatioY = dragSplitY.ratio;
            return;
        }
        Desk.Column column = desk.columnOf(win);
        if (column == null) return;
        boolean rows = desk.rows;

        // Across: the column on the side nearest the cursor, or the other side at the desk's edge.
        int ci = desk.columns.indexOf(column);
        int other = neighbour(ci, rows ? dragTop : dragLeft, desk.columns.size());
        if (other >= 0) {
            dragColumn = column;
            dragColumnNeighbour = desk.columns.get(other);
            dragColumnBefore = other < ci;
            dragColumnWeightA = column.weight;
            dragColumnWeightB = dragColumnNeighbour.weight;
            Pane across = dragColumnNeighbour.panes.get(0);
            dragColumnSize = rows ? win.h : win.w;
            dragColumnPair = dragColumnSize + (rows ? across.h : across.w);
        }

        // Along: the pane before or after it in its column, chosen the same way.
        int pi = column.panes.indexOf(win);
        int next = neighbour(pi, rows ? dragLeft : dragTop, column.panes.size());
        if (next >= 0) {
            dragNeighbour = column.panes.get(next);
            dragNeighbourBefore = next < pi;
            dragWeightA = win.weight;
            dragWeightB = dragNeighbour.weight;
            dragSize = rows ? win.w : win.h;
            dragPair = dragSize + (rows ? dragNeighbour.w : dragNeighbour.h);
        }
    }

    /** The index beside {@code index} on the preferred side, the other side if there is none there, or -1. */
    private static int neighbour(int index, boolean before, int size) {
        int first = before ? index - 1 : index + 1;
        if (first >= 0 && first < size) return first;
        int second = before ? index + 1 : index - 1;
        return second >= 0 && second < size ? second : -1;
    }

    private boolean dragDesk(MouseButtonEvent event, double dragX, double dragY) {
        if (slider != null) {
            Slider.drag(slider, sliderX0, sliderX1, sliderHeld, event.x());
            return true;
        }
        if (dragMode == DRAG_NONE || dragWin == null) return super.deskDragged(event, dragX, dragY);

        float dx = (float) (event.x() - dragStartX);
        float dy = (float) (event.y() - dragStartY);
        Pane win = dragWin;

        if (dragMode == DRAG_MOVE) {
            if (!dragMoved && Math.abs(dx) + Math.abs(dy) < 6) return true;
            dragMoved = true;
            win.x = dragRectX + dx;
            win.y = dragRectY + dy;
            if (win.floating) {
                win.fx = win.x;
                win.fy = win.y;
            }
            return true;
        }

        if (win.floating) {
            float left = dragRectX, top = dragRectY, right = dragRectX + dragRectW, bottom = dragRectY + dragRectH;
            if (dragLeft) left = Math.min(left + dx, right - win.minW());
            else right = Math.max(right + dx, left + win.minW());
            if (dragTop) top = Math.min(top + dy, bottom - win.minH());
            else bottom = Math.max(bottom + dy, top + win.minH());
            win.fx = left;
            win.fy = top;
            win.fw = right - left;
            win.fh = bottom - top;
            layout();
            win.snap();
            return true;
        }

        // Tiled: the edge between its column and the next one follows the cursor across, and
        // the edge between it and its neighbour in the column follows it along.
        Desk desk = desk();
        if (Desk.dwindleLayout()) {
            // The split's edge follows the cursor: its ratio moves by the share of its space dragged.
            if (dragSplitX != null) dragSplitX.ratio = clamp(dragRatioX + 2f * dx / Math.max(1f, dragSplitX.w), 0.1f, 1.9f);
            if (dragSplitY != null) dragSplitY.ratio = clamp(dragRatioY + 2f * dy / Math.max(1f, dragSplitY.h), 0.1f, 1.9f);
            layout();
            for (Pane w : desk.tiled()) w.snap();
            return true;
        }
        boolean rows = desk.rows;
        float across = rows ? dy : dx;
        float along = rows ? dx : dy;

        if (dragColumnNeighbour != null) {
            float size = split(dragColumnSize, dragColumnBefore ? 0f - across : across, dragColumnPair,
                rows ? Pane.MIN_H : Pane.MIN_W);
            float sum = dragColumnWeightA + dragColumnWeightB;
            dragColumn.weight = sum * size / dragColumnPair;
            dragColumnNeighbour.weight = sum - dragColumn.weight;
        }

        if (dragNeighbour != null) {
            float size = split(dragSize, dragNeighbourBefore ? 0f - along : along, dragPair,
                rows ? Pane.MIN_W : Pane.MIN_H);
            float sum = dragWeightA + dragWeightB;
            win.weight = sum * size / dragPair;
            dragNeighbour.weight = sum - win.weight;
        }

        layout();
        for (Pane w : desk.tiled()) w.snap();
        return true;
    }

    /** One side of a shared edge grown by {@code grow}, leaving both sides at least {@code min} where there is room. */
    private static float split(float size, float grow, float pair, float min) {
        float floor = Math.min(min, pair / 2f);
        return clamp(size + grow, floor, pair - floor);
    }

    private boolean releaseDesk(MouseButtonEvent event) {
        if (slider != null) {
            // Written to disk once, when the drag is done, as the classic panel's sliders were.
            slider.node.commit();
            slider = null;
            return true;
        }

        Group bar = dragMode == DRAG_MOVE && dragMoved && dragWin != null ? Decor.groupBarAt(desk(), event.x(), event.y()) : null;
        if (bar != null && bar != dragWin.group) {
            // Dropped on a group's tabs: it joins them.
            desk().joinGroup(dragWin, bar);
        } else if (dragMode == DRAG_MOVE && dragMoved && dragWin != null && !dragWin.floating) {
            Pane target = dropTarget(event.x(), event.y());
            Desk desk = desk();
            if (target != null) {
                int zone = dropZone(target, event.x(), event.y());
                if (zone == Desk.SWAP) desk.swap(dragWin, target);
                else desk.tile(dragWin, target, zone);
            } else {
                // Dropped anywhere else: it floats where it was let go.
                desk.untile(dragWin);
                dragWin.floating = true;
                dragWin.fw = clamp(dragRectW, dragWin.minW(), width * 0.6f);
                dragWin.fh = clamp(dragRectH, dragWin.minH(), height * 0.6f);
                dragWin.fx = dragWin.x;
                dragWin.fy = dragWin.y;
                desk.focus(dragWin);
            }
        }

        boolean wasDragging = dragMode != DRAG_NONE;
        endDrag();
        layout();
        return wasDragging || super.deskReleased(event);
    }

    /** The tiled pane a dragged one would land beside, or swap with, if dropped here. */
    private Pane dropTarget(double mx, double my) {
        for (Pane w : desk().tiled()) {
            if (w != dragWin && w.contains(mx, my)) return w;
        }
        return null;
    }

    /**
     * What a drop on a pane does. Near an edge the dropped pane goes beside it on that side --
     * into its column for the two edges along the column, as a new column for the other two.
     * In the middle the two swap.
     */
    private int dropZone(Pane target, double mx, double my) {
        double fx = (mx - target.x) / Math.max(1f, target.w);
        double fy = (my - target.y) / Math.max(1f, target.h);
        double left = fx, right = 1 - fx, top = fy, bottom = 1 - fy;
        double nearest = Math.min(Math.min(left, right), Math.min(top, bottom));
        if (nearest > 0.3) return Desk.SWAP;

        boolean sideways = nearest == left || nearest == right;
        boolean before = nearest == left || nearest == top;
        boolean inColumn = desk().rows == sideways;
        if (inColumn) return before ? Desk.BEFORE : Desk.AFTER;
        return before ? Desk.COL_BEFORE : Desk.COL_AFTER;
    }

    /** The part of a pane a drop in that zone would take, for the preview: {x, y, w, h}. */
    private float[] zoneRect(Pane t, int zone) {
        if (zone == Desk.SWAP) return new float[] {t.x, t.y, t.w, t.h};
        boolean inColumn = zone == Desk.BEFORE || zone == Desk.AFTER;
        boolean topOrBottom = inColumn != desk().rows;
        boolean before = zone == Desk.BEFORE || zone == Desk.COL_BEFORE;
        if (topOrBottom) {
            return before ? new float[] {t.x, t.y, t.w, t.h / 2} : new float[] {t.x, t.y + t.h / 2, t.w, t.h / 2};
        }
        return before ? new float[] {t.x, t.y, t.w / 2, t.h} : new float[] {t.x + t.w / 2, t.y, t.w / 2, t.h};
    }

    private void endDrag() {
        dragSplitX = null;
        dragSplitY = null;
        dragMode = DRAG_NONE;
        dragMoved = false;
        dragWin = null;
        dragColumn = null;
        dragColumnNeighbour = null;
        dragNeighbour = null;
    }

    private boolean scrollDesk(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // SUPER+scroll: mouse_down and mouse_up binds, the next and previous workstation by default.
        if (verticalAmount != 0) {
            int code = verticalAmount < 0 ? HyprConf.SCROLL_DOWN : HyprConf.SCROLL_UP;
            HyprConf.Bind bind = HyprConf.get().match(code, heldMods());
            if (bind != null) {
                long now = System.currentTimeMillis();
                if (now - scrolledAt > 150) {
                    scrolledAt = now;
                    runBind(bind);
                }
                return true;
            }
        }
        // Over a group's tabs, the wheel steps through them.
        Group bar = Decor.groupBarAt(desk(), mouseX, mouseY);
        if (bar != null && verticalAmount != 0) {
            desk().cycleGroup(bar, verticalAmount < 0 ? 1 : -1);
            layout();
            return true;
        }
        Pane win = windowAt(mouseX, mouseY);
        if (win != null) {
            win.scrolled(mouseY, verticalAmount);
            return true;
        }
        return super.deskScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private Hit hitAt(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(mx, my)) return hits.get(i);
        }
        return null;
    }

    // ---- the safety net ---------------------------------------------------

    /*
     * Every way the game calls into the desktop goes through here. The desktop is the client's
     * menu -- the main menu, the Esc menu and the panel key all land on it -- so a bug anywhere
     * in it used to be a crash to desktop for the whole game. Now it is a stack trace in the
     * log, a toast, and vanilla's own menu for the rest of the launch.
     *
     * DeskScreen has already put everything in desk units (the desk scale, DeskScale): width
     * and height, the pose, every mouse position. Nothing here sees the game's GUI scale.
     */

    @Override
    protected void deskInit() {
        try {
            // The tray's item icons ready before the first hover, not on it.
            Tray.prepare();
            initDesk();
        } catch (RuntimeException | LinkageError e) {
            fail("opening", e);
        }
    }

    @Override
    public void tick() {
        try {
            tickDesk();
        } catch (RuntimeException | LinkageError e) {
            fail("ticking", e);
        }
    }

    @Override
    protected void deskBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (failed) return;
        try {
            drawBackground(g);
        } catch (RuntimeException | LinkageError e) {
            fail("drawing", e);
        }
    }

    @Override
    protected void deskRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (failed) return;
        try {
            com.mineux.core.ShutdownLayer down = Shutdowns.active();
            if (down == null || !Shutdowns.covers(down)) drawDesk(g, mouseX, mouseY, delta);
            if (down != null) drawShutdown(g, down);
        } catch (RuntimeException | LinkageError e) {
            fail("drawing", e);
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (Shutdowns.active() != null) return true;
        try {
            keysDown.remove(event.key());
            if (deskTap && event.key() == com.mineux.core.MineUX.deskKey() && prompt == null) {
                deskTap = false;
                onClose();
                return true;
            }
            deskTap = false;
            return super.keyReleased(event);
        } catch (RuntimeException | LinkageError e) {
            fail("a key release", e);
            return true;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        com.mineux.core.ShutdownLayer down = Shutdowns.active();
        if (down != null) {
            // Escape calls it off, while it still can be; every other key is ignored.
            if (event.key() == GLFW.GLFW_KEY_ESCAPE && Shutdowns.escape(down)) say("reboot: called off");
            return true;
        }
        try {
            return keyDesk(event);
        } catch (RuntimeException | LinkageError e) {
            fail("a key press", e);
            return true;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (Shutdowns.active() != null) return true;
        try {
            return charDesk(event);
        } catch (RuntimeException | LinkageError e) {
            fail("typing", e);
            return true;
        }
    }

    @Override
    protected boolean deskClicked(MouseButtonEvent event, boolean isHandled) {
        if (Shutdowns.active() != null) return true;
        try {
            return clickDesk(event, isHandled);
        } catch (RuntimeException | LinkageError e) {
            fail("a click", e);
            return true;
        }
    }

    @Override
    protected boolean deskDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (Shutdowns.active() != null) return true;
        try {
            return dragDesk(event, dragX, dragY);
        } catch (RuntimeException | LinkageError e) {
            fail("a drag", e);
            return true;
        }
    }

    @Override
    protected boolean deskReleased(MouseButtonEvent event) {
        if (Shutdowns.active() != null) return true;
        try {
            return releaseDesk(event);
        } catch (RuntimeException | LinkageError e) {
            fail("a click", e);
            return true;
        }
    }

    @Override
    protected boolean deskScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (Shutdowns.active() != null) return true;
        try {
            return scrollDesk(mouseX, mouseY, horizontalAmount, verticalAmount);
        } catch (RuntimeException | LinkageError e) {
            fail("scrolling", e);
            return true;
        }
    }

    /** Set once the desktop has thrown: from then on, this launch, the menus stay vanilla's. */
    private static volatile boolean broken;
    /** This screen has already given up; it draws nothing while the fallback is on its way. */
    private boolean failed;

    /** Whether the desktop has failed this launch, so the menus should be left as vanilla's. */
    public static boolean broken() {
        return broken;
    }

    /**
     * Logs what went wrong, in full, and swaps this screen for the vanilla one it stands in
     * for: the pause menu (or the game) in a world, the title screen outside one. Deferred to
     * the next task, since this can be called from the middle of a frame.
     */
    private void fail(String doing, Throwable error) {
        if (failed) return;
        failed = true;
        broken = true;
        MineUX.LOGGER.error("The MineUX desktop failed while {} and was closed; the menus are vanilla's until the game restarts", doing, error);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            // Held over the game (Overlay): put away, and the Esc menu it stood in for put up.
            if (Overlay.hosts(this)) {
                Overlay.close(mc, !onEsc());
                if (onEsc()) mc.gui.setScreen(new PauseScreen(true));
                SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("MineUX desktop closed"), Component.literal("It hit an error; see logs/latest.log"));
                return;
            }
            if (mc.gui.screen() != this) return;
            // This must get the screen off, whatever else is broken: a failed screen draws
            // nothing, so one left up is an invisible wall that eats every input.
            try {
                mc.gui.setScreen(mc.level != null ? (onEsc() ? new PauseScreen(true) : null) : new TitleScreen());
            } catch (RuntimeException | LinkageError again) {
                MineUX.LOGGER.error("The MineUX desktop's fallback failed too; closing it bare", again);
                mc.gui.setScreen(mc.level != null ? null : new TitleScreen());
            }
            SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal("MineUX desktop closed"), Component.literal("It hit an error; see logs/latest.log"));
        });
    }

    // ---- lifecycle ------------------------------------------------------

    /** The Esc desk pauses a singleplayer world, as vanilla's pause menu does; workstations never do. */
    @Override
    public boolean isPauseScreen() {
        return onEsc();
    }

    @Override
    public void onClose() {
        if (minecraft == null) return;
        if (title) {
            showHome();
            return;
        }
        if (onEsc()) {
            // Back to the game.
            minecraft.gui.setScreen(null);
            minecraft.mouseHandler.grabMouse();
            return;
        }
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        if (slider != null) slider.node.commit();
        slider = null;
        if (title) Servers.stop();
        Readouts.save();
        Workstations.save();
        Workstations.saveState();
        super.removed();
    }
}
