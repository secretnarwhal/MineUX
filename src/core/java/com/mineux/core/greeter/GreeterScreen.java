package com.mineux.core.greeter;

import com.mineux.core.account.Account;
import com.mineux.core.account.SignInHook;
import com.mineux.core.account.SignInResult;
import com.mineux.core.Extensions;
import com.mineux.core.net.MuxApi;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The greeter, styled after ly, the TUI display manager: a bordered box on a black screen,
 * {@code F1 shutdown} in one corner and the clock in the other, and behind it the animation
 * its dotfile ({@code dotfiles/ly/config.ini}, see {@link LyConfig}) asks for: a stream
 * pouring in until the screen is tinted ({@link StreamFill}, the default), ly's own Doom fire
 * ({@link DoomFire}), a wall of fire in text ({@link AsciiFire}), or nothing.
 * <p>
 * It asks for a MineUX user name and password, or offers <b>continue as guest</b>; an account
 * is made on the website, which the line under the box names. The two remember boxes keep the
 * user name, and the session ("stay signed in": the token, never the password), between
 * launches; a box unticked is forgotten at once. See {@link Greeter} for when it shows.
 * <p>
 * After a sign-in or the guest choice the screen fades to black and hands the title screen on
 * ({@link #enter}), and the desk plays its boot sequence from that black.
 */
public class GreeterScreen extends Screen {

    private static final int BOX_W = 272;
    private static final int BOX_H = 162;
    private static final int LABEL_X = 18;
    private static final int FIELD_X = 86;

    private static final int ROW_USER = 50;
    private static final int ROW_PASS = 68;
    private static final int ROW_REMEMBER_USER = 92;
    private static final int ROW_REMEMBER_ME = 106;
    private static final int ROW_LOGIN = 126;
    private static final int ROW_GUEST = 142;

    static final int FG = 0xFFE4E4E4;
    static final int DIM = 0xFF7A7A7A;
    static final int FAINT = 0xFF3C3C3C;
    static final int GOLD = 0xFFEFC648;
    static final int RED = 0xFFFF5C57;
    static final int GREEN = 0xFF5AF78E;

    private static final int FOCUS_USER = 0;
    private static final int FOCUS_PASS = 1;
    private static final int FOCUS_REMEMBER_USER = 2;
    private static final int FOCUS_REMEMBER_ME = 3;
    private static final int FOCUS_LOGIN = 4;
    private static final int FOCUS_GUEST = 5;

    private static final int RESUMING = 0;
    private static final int IDLE = 1;
    private static final int SIGNING_IN = 2;
    private static final int FADING = 3;
    private static final int DONE = 4;
    private static final int SHUTTING = 5;

    private static final long FADE_MS = 750;
    private static final long SHUT_MS = 650;
    /** How long the black of a resume stays bare before it says what it is waiting for. */
    private static final long RESUME_QUIET_MS = 500;

    /** The registration and password reset mails can land in spam (a new sending domain). */
    static final String SPAM_NOTE = "check your spam folder if the mail isn't in your inbox";

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("EEE d MMM  HH:mm:ss", Locale.ROOT);

    /** The title screen this stands in front of; null when put up from inside a world. */
    private final TitleScreen parent;
    private final LyConfig ly = LyConfig.load();
    private final DoomFire fire = new DoomFire();
    /** Made only when the dotfile asks for it, so the other animations never touch it. */
    private AsciiFire asciiFire;
    private final StreamFill stream = new StreamFill(ly.streamColor, ly.tintOpacity, ly.streamX);
    private final SignInHook hook = Extensions.signInHook();
    private final boolean guestOffered = hook == null || hook.allowsGuest();
    private final long createdAt = System.currentTimeMillis();

    /**
     * When the animation started: the first frame the screen could really be seen, with no
     * loading overlay over it and the window done resizing. 0 until then.
     */
    private long shownAt;
    private long lastLayout;
    /** A windowed game is made to fill the screen once a launch, when the dotfile asks; see {@link #fitWindow}. */
    private static boolean fitted;

    private EditBox userBox;
    private EditBox passBox;
    private boolean rememberUser = Account.rememberUser();
    private boolean rememberMe = Account.rememberMe();
    private int focus = FOCUS_USER;
    private boolean placed;

    private int stage;
    private String info = "";
    private int infoColor = RED;
    private long fadeStart;
    /** What the sign-in worker came back with, picked up on the next tick. */
    private volatile SignInResult pending;
    private String typedUser = "";

    private int boxX;
    private int boxY;

    /**
     * @param parent the title screen it gates, or null from inside a world
     * @param resume whether to wait on the launch's remembered session first
     */
    public GreeterScreen(TitleScreen parent, boolean resume) {
        super(Component.literal("MineUX"));
        this.parent = parent;
        this.stage = resume && Account.resumeState() != Account.NO_SESSION ? RESUMING : IDLE;
    }

    /** Whether this is the launch gate rather than a sign-in from inside a world. */
    private boolean gate() {
        return minecraft == null || minecraft.level == null;
    }

    // ---- layout ---------------------------------------------------------

    @Override
    protected void init() {
        lastLayout = System.currentTimeMillis();
        boxX = (width - BOX_W) / 2;
        boxY = Math.max(24, (height - BOX_H) / 2 - 12);

        // Kept across a resize, which rebuilds the widgets; the remembered name the first time.
        String user = userBox != null ? userBox.getValue() : Account.lastUser();
        String pass = passBox != null ? passBox.getValue() : "";

        userBox = field(ROW_USER, "user name or email", 254);
        userBox.setValue(user);
        addRenderableWidget(userBox);

        passBox = field(ROW_PASS, "password", 128);
        // EditBox has no password mode; a formatter only changes what is drawn. It is handed
        // the visible slice of the text, so the mask matches that slice and not the whole value.
        passBox.addFormatter((text, index) -> Component.literal("*".repeat(text.length())).getVisualOrderText());
        passBox.setValue(pass);
        addRenderableWidget(passBox);

        // First time in: straight to the first box still empty.
        if (!placed) {
            placed = true;
            if (!userBox.getValue().isEmpty()) focus = FOCUS_PASS;
        }
        applyFocus();
    }

    private EditBox field(int row, String hint, int maxLength) {
        EditBox box = new EditBox(font, boxX + FIELD_X, boxY + row, BOX_W - FIELD_X - LABEL_X, 10, Component.literal(hint));
        box.setMaxLength(maxLength);
        box.setBordered(false);
        box.setTextShadow(false);
        box.setTextColor(FG);
        box.setHint(Component.literal(hint).withStyle(s -> s.withColor(FAINT & 0xFFFFFF)));
        return box;
    }

    private void applyFocus() {
        if (focus == FOCUS_USER) setFocused(userBox);
        else if (focus == FOCUS_PASS) setFocused(passBox);
        else setFocused(null);
    }

    private int focusCount() {
        return guestOffered ? 6 : 5;
    }

    // ---- rendering ------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xFF000000);
        if (stage == RESUMING) return;
        String animation = ly.animation == null ? "" : ly.animation;
        if (animation.equals(LyConfig.DOOM)) {
            fire.draw(g, width, height, 1f);
        } else if (animation.equals(LyConfig.ASCII)) {
            // Along the bottom, at most 40 rows tall, rather than stretched up a whole screen of cells.
            int rows = Math.min(40, height / 9 + 1);
            if (asciiFire == null) asciiFire = new AsciiFire(AsciiFire.WALL, ly.asciiPalette, 7);
            asciiFire.draw(g, font, 0, height - rows * 9, width / 6 + 1, rows, 6, 9, 1f);
        } else if (animation.equals(LyConfig.STREAM)) {
            long now = System.currentTimeMillis();
            boolean visible = minecraft == null || minecraft.gui.overlay() == null;
            if (shownAt == 0 && visible && now - lastLayout > 450) shownAt = now;
            if (shownAt != 0) stream.draw(g, width, height, now - shownAt);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (stage == RESUMING) {
            drawResuming(g, now);
            return;
        }
        if (stage == SHUTTING) {
            TubeOff.draw(g, width, height, Math.min(1f, (now - fadeStart) / (float) SHUT_MS));
            return;
        }
        corners(g);
        box(g, mouseX, mouseY, now);

        // ly's info line, just above the box.
        if (!info.isEmpty()) {
            int ix = (width - font.width(info)) / 2;
            g.fill(ix - 4, boxY - 17, ix + font.width(info) + 4, boxY - 3, 0xFF000000);
            g.text(font, info, ix, boxY - 14, infoColor, false);
        }
        register(g);

        super.extractRenderState(g, mouseX, mouseY, delta);

        if (stage == FADING || stage == DONE) {
            float t = stage == DONE ? 1f : Math.min(1f, (now - fadeStart) / (float) FADE_MS);
            g.nextStratum();
            g.fill(0, 0, width, height, Math.round(t * 255) << 24);
        }
    }

    private void drawResuming(GuiGraphicsExtractor g, long now) {
        g.fill(0, 0, width, height, 0xFF000000);
        if (now - createdAt < RESUME_QUIET_MS) return;
        String who = Account.resumeUser();
        String line = spinner(now) + " resuming " + (who == null ? "your session" : who + "'s session") + "...";
        g.text(font, line, (width - font.width(line)) / 2, height / 2 - 4, DIM, false);
    }

    /** The corners: ly's function key hint, and the clock. */
    private void corners(GuiGraphicsExtractor g) {
        g.text(font, "F1", 6, 5, GOLD, false);
        g.text(font, "shutdown", 6 + font.width("F1 "), 5, DIM, false);
        String clock = LocalDateTime.now().format(CLOCK).toLowerCase(Locale.ROOT);
        g.text(font, clock, width - 6 - font.width(clock), 5, DIM, false);
    }

    private void box(GuiGraphicsExtractor g, int mouseX, int mouseY, long now) {
        int x = boxX;
        int y = boxY;
        g.fill(x, y, x + BOX_W, y + BOX_H, 0xFF000000);
        g.fill(x, y, x + BOX_W, y + 1, FG);
        g.fill(x, y + BOX_H - 1, x + BOX_W, y + BOX_H, FG);
        g.fill(x, y, x + 1, y + BOX_H, FG);
        g.fill(x + BOX_W - 1, y, x + BOX_W, y + BOX_H, FG);

        g.text(font, "mineux", x + 8, y + 8, DIM, false);
        String version = version();
        g.text(font, version, x + BOX_W - 8 - font.width(version), y + 8, FAINT, false);

        // The session: the only desktop there is.
        String session = "mineux-wm";
        int sw = font.width(session);
        int sx = x + (BOX_W - sw) / 2;
        g.text(font, "<", sx - 16, y + 28, DIM, false);
        g.text(font, session, sx, y + 28, FG, false);
        g.text(font, ">", sx + sw + 11, y + 28, DIM, false);

        label(g, "login", ROW_USER, focus == FOCUS_USER);
        label(g, "password", ROW_PASS, focus == FOCUS_PASS);
        checkbox(g, "remember user name", rememberUser, ROW_REMEMBER_USER, FOCUS_REMEMBER_USER, mouseX, mouseY);
        checkbox(g, "stay signed in", rememberMe, ROW_REMEMBER_ME, FOCUS_REMEMBER_ME, mouseX, mouseY);
        button(g, loginLabel(now), ROW_LOGIN, FOCUS_LOGIN, mouseX, mouseY);
        if (guestOffered) button(g, "[ continue as guest ]", ROW_GUEST, FOCUS_GUEST, mouseX, mouseY);
    }

    private String loginLabel(long now) {
        if (stage == SIGNING_IN) return "[ " + spinner(now) + " signing in ]";
        if (stage == FADING || stage == DONE) return "[ welcome ]";
        return "[ login ]";
    }

    private void button(GuiGraphicsExtractor g, String text, int row, int index, int mouseX, int mouseY) {
        int bw = font.width(text);
        int bx = boxX + (BOX_W - bw) / 2;
        int y = boxY + row;
        boolean hot = stage == IDLE && inButton(row, mouseX, mouseY);
        int color = stage != IDLE ? DIM : focus == index || hot ? GOLD : FG;
        if (focus == index && stage == IDLE) g.fill(bx - 2, y - 2, bx + bw + 2, y + 9, 0x30EFC648);
        g.text(font, text, bx, y, color, false);
    }

    /** Under the box: where an account is made or a password reset, and where their mail can end up. */
    private void register(GuiGraphicsExtractor g) {
        String site = siteName();
        int y = boxY + BOX_H + 8;
        hint(g, "no account? register at ", site, y);
        hint(g, "forgot your password? reset it at ", site, y + 12);
        hint(g, SPAM_NOTE, "", y + 24);
    }

    /** One centred line, {@code lead} dim and {@code site} gold, on black. */
    private void hint(GuiGraphicsExtractor g, String lead, String site, int y) {
        int w = font.width(lead) + font.width(site);
        int x = (width - w) / 2;
        // On black, as all of ly's text is, so a fire behind it cannot drown it.
        g.fill(x - 4, y - 3, x + w + 4, y + 9, 0xFF000000);
        g.text(font, lead, x, y, DIM, false);
        if (!site.isEmpty()) g.text(font, site, x + font.width(lead), y, GOLD, false);
    }

    /** The website without its scheme: {@code mineux.com}. */
    static String siteName() {
        String site = MuxApi.site();
        int scheme = site.indexOf("://");
        return scheme < 0 ? site : site.substring(scheme + 3);
    }

    private void label(GuiGraphicsExtractor g, String text, int row, boolean focused) {
        g.text(font, text, boxX + LABEL_X, boxY + row, focused ? GOLD : FG, false);
        int lx = boxX + FIELD_X - 2;
        int ly = boxY + row + 10;
        g.fill(lx, ly, boxX + BOX_W - LABEL_X + 2, ly + 1, focused ? 0xFFB8962E : FAINT);
    }

    private void checkbox(GuiGraphicsExtractor g, String text, boolean on, int row, int index, int mouseX, int mouseY) {
        boolean hot = inRow(row, mouseX, mouseY);
        boolean focused = focus == index;
        int color = focused || hot ? GOLD : FG;
        g.text(font, on ? "[x]" : "[ ]", boxX + LABEL_X, boxY + row, color, false);
        g.text(font, text, boxX + LABEL_X + font.width("[x] "), boxY + row, focused || hot ? FG : DIM, false);
    }

    private static String spinner(long now) {
        return String.valueOf("|/-\\".charAt((int) (now / 120 % 4)));
    }

    private static String version() {
        return SharedConstants.getCurrentVersion().name() + " tty1";
    }

    private boolean inRow(int row, double mouseX, double mouseY) {
        return mouseX >= boxX + LABEL_X - 2 && mouseX < boxX + BOX_W / 2 + 40
            && mouseY >= boxY + row - 2 && mouseY < boxY + row + 10;
    }

    private boolean inButton(int row, double mouseX, double mouseY) {
        return mouseX >= boxX + BOX_W / 2 - 60 && mouseX < boxX + BOX_W / 2 + 60
            && mouseY >= boxY + row - 3 && mouseY < boxY + row + 11;
    }

    // ---- ticking --------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!fitted && gate()) fitWindow();
        if (stage == RESUMING) resumed();
        signedIn(pending);
        long now = System.currentTimeMillis();
        if (stage == FADING && now - fadeStart >= FADE_MS) {
            stage = DONE;
            enter();
        }
        if (stage == SHUTTING && now - fadeStart >= SHUT_MS && minecraft != null) minecraft.stop();
    }

    /**
     * Takes a finished sign-in. The field comes in as a parameter, never a local copy of it:
     * the obfuscator would read {@code pending} again after it is cleared (HoistCheck).
     */
    private void signedIn(SignInResult result) {
        if (stage != SIGNING_IN || result == null) return;
        pending = null;
        finish(result);
    }

    /** The remembered session's check, every tick while it runs: on to the desk, or the box with why not. */
    private void resumed() {
        int state = Account.resumeState();
        if (state == Account.RESUMING) return;
        if (state == Account.RESUMED) {
            // Already black: straight on, no fade.
            stage = DONE;
            enter();
            return;
        }
        stage = IDLE;
        info = Account.resumeMessage();
        infoColor = state == Account.UNREACHABLE ? GOLD : RED;
    }

    private void finish(SignInResult result) {
        if (!result.ok()) {
            fail(result.message());
            return;
        }
        Account.adopt(result, typedUser, rememberUser, rememberMe);
        info = "welcome, " + result.user();
        infoColor = GREEN;
        startFade();
    }

    private void startFade() {
        stage = FADING;
        fadeStart = System.currentTimeMillis();
    }

    /**
     * The game goes windowed and maximised, when the dotfile's {@code window = maximize} asks
     * for it: out of Minecraft's own fullscreen, which screen share often cannot capture.
     * Once a launch, on a tick: resizing the window lays the screen out again, which must not
     * happen from inside its own layout.
     */
    private void fitWindow() {
        fitted = true;
        String asked = ly.window;
        if (minecraft == null || asked == null || !asked.equals(LyConfig.MAXIMIZE)) return;
        Window window = minecraft.getWindow();
        if (window.isFullscreen()) {
            // Through the option, so options.txt keeps it windowed next launch too.
            minecraft.options.fullscreen().set(false);
            window.updateFullscreenIfChanged();
            minecraft.options.save();
            if (window.isFullscreen()) return;
        }
        long handle = window.handle();
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED) == GLFW.GLFW_FALSE) {
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        }
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_FALSE) GLFW.glfwMaximizeWindow(handle);
    }

    /** Through the gate: the title screen goes on to the desk, or a sign-in from a world goes back to it. */
    private void enter() {
        Greeter.pass();
        if (minecraft == null) return;
        if (!gate()) {
            minecraft.gui.setScreen(null);
            return;
        }
        // Put back up, its init hands it to the desk now that the gate is passed.
        minecraft.gui.setScreen(parent != null ? parent : new TitleScreen());
    }

    // ---- signing in -----------------------------------------------------

    private void signIn() {
        if (stage != IDLE) return;
        String user = userBox.getValue().trim();
        String password = passBox.getValue();
        if (user.isEmpty()) {
            failAt("enter your user name", FOCUS_USER);
            return;
        }
        if (password.isEmpty()) {
            failAt("enter your password", FOCUS_PASS);
            return;
        }
        stage = SIGNING_IN;
        info = "authenticating...";
        infoColor = DIM;
        typedUser = user;
        pending = null;
        Thread worker = new Thread(() -> pending = Account.signIn(user, password), "mineux-signin");
        // Daemon so a stalled sign-in cannot hold the JVM open after the player quits.
        worker.setDaemon(true);
        worker.start();
    }

    private void guest() {
        if (stage != IDLE || !guestOffered) return;
        Account.guest();
        info = "continuing as guest";
        infoColor = DIM;
        startFade();
    }

    private void failAt(String message, int field) {
        fail(message);
        focus = field;
        applyFocus();
    }

    private void fail(String message) {
        info = message;
        infoColor = RED;
        stage = IDLE;
        passBox.setValue("");
        focus = userBox.getValue().isEmpty() ? FOCUS_USER : FOCUS_PASS;
        applyFocus();
    }

    private void toggleRemember(int index) {
        if (index == FOCUS_REMEMBER_USER) rememberUser = !rememberUser;
        else rememberMe = !rememberMe;
        // Unticked is forgotten now, not only at the next sign-in.
        Account.setRemember(rememberUser, rememberMe);
    }

    // ---- input ----------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (stage == SHUTTING) return true;
        if (key == GLFW.GLFW_KEY_F1) {
            stage = SHUTTING;
            fadeStart = System.currentTimeMillis();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!gate() && stage == IDLE) onClose();
            return true;
        }
        if (stage != IDLE) return true;
        if (key == GLFW.GLFW_KEY_TAB) {
            move(event.hasShiftDown() ? 0 - 1 : 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            move(key == GLFW.GLFW_KEY_DOWN ? 1 : 0 - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            activate(true);
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE && focus >= FOCUS_REMEMBER_USER) {
            activate(false);
            return true;
        }
        return super.keyPressed(event);
    }

    /** Enter (or space, off the text boxes) on what has the focus. */
    private void activate(boolean enter) {
        if (focus == FOCUS_USER && enter) {
            focus = FOCUS_PASS;
            applyFocus();
        } else if (focus == FOCUS_REMEMBER_USER || focus == FOCUS_REMEMBER_ME) {
            toggleRemember(focus);
        } else if (focus == FOCUS_GUEST) {
            guest();
        } else {
            signIn();
        }
    }

    private void move(int by) {
        focus = Math.floorMod(focus + by, focusCount());
        applyFocus();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (stage != IDLE) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isHandled) {
        if (stage != IDLE) return true;
        double mx = event.x();
        double my = event.y();
        if (inRow(ROW_REMEMBER_USER, mx, my)) return clickRow(FOCUS_REMEMBER_USER);
        if (inRow(ROW_REMEMBER_ME, mx, my)) return clickRow(FOCUS_REMEMBER_ME);
        if (inButton(ROW_LOGIN, mx, my)) return clickRow(FOCUS_LOGIN);
        if (guestOffered && inButton(ROW_GUEST, mx, my)) return clickRow(FOCUS_GUEST);

        boolean handled = super.mouseClicked(event, isHandled);
        if (getFocused() == userBox) focus = FOCUS_USER;
        else if (getFocused() == passBox) focus = FOCUS_PASS;
        return handled;
    }

    private boolean clickRow(int index) {
        focus = index;
        applyFocus();
        activate(false);
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !gate();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (gate() || minecraft == null) return;
        minecraft.gui.setScreen(null);
    }
}
