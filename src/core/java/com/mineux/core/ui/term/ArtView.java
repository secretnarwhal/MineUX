package com.mineux.core.ui.term;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;
import com.mineux.core.util.Words;

import static com.mineux.core.ui.term.Term.AMBER;
import static com.mineux.core.ui.term.Term.BLUE;
import static com.mineux.core.ui.term.Term.CYAN;
import static com.mineux.core.ui.term.Term.GOLD;
import static com.mineux.core.ui.term.Term.GREEN;
import static com.mineux.core.ui.term.Term.MAGENTA;
import static com.mineux.core.ui.term.Term.mix;

/**
 * The client's name in word art, as every terminal prints it at the top of its scrollback:
 * a palette's two colours down it, a bright band sweeping down it, a slight float, and now and
 * then a glitch. Which art is showing is one choice for the whole client, cycled by {@code art}
 * or a click, picked from the art window ({@link ArtPane}), and kept in {@code terminal.json}
 * with the palette and the two animation switches.
 * <p>
 * A monospace grid in a font that is not one: every glyph as wide as a regular cell is drawn
 * in runs, and each narrow one ({@code | : . ,}) alone, centred in its own cell.
 */
final class ArtView {

    /** One art cell: the default font's advance for every glyph that is not narrow. */
    private static final int CW = 6;
    private static final int CH = 9;

    static final String GOLD_PALETTE = "gold";
    /** The palettes, in the order the art window offers them. String constants, not an enum: obfuscators rename enum constants. */
    static final String[] PALETTES = {GOLD_PALETTE, "rose", "mint", "ice", "mono", "rainbow"};

    /** How often the user's art folder is looked at for new or changed files, while anything asks. */
    private static final long FOLDER_CHECK_MS = 1500;

    private static Arts arts;
    private static String folderSig;
    private static long folderCheckedAt;
    /** A folder listing under way off the render thread, which a slow disk would otherwise stall. */
    private static CompletableFuture<String> folderCheck;
    /** Bumped whenever the art is reloaded, so whatever lists it knows to rebuild. */
    private static int version;
    /** Each art's glyph runs, built the first time it is drawn; parallel to {@link Arts#arts}. */
    private static final List<Glyphs> glyphs = new ArrayList<>();

    private static long changedAt;
    private static long glitchUntil, nextGlitch;
    private static int glitchSeed;
    private static final Random RANDOM = new Random();
    private static final long STARTED = System.currentTimeMillis();

    private ArtView() {}

    /**
     * One art laid out for drawing: each run of glyphs with its x in art pixels (its cell plus its
     * nudge inside it) and its row. Flat arrays, because every visible terminal walks them every frame.
     */
    private static final class Glyphs {
        int[] x;
        int[] row;
        String[] text;
        int count, cols = 1, rows;
    }

    /** Per-row colour and glitch shift for the draw in progress; only ever used on the render thread. */
    private static int[] rowColor = new int[16];
    private static int[] rowShift = new int[16];

    /** {@link #current}'s last answer, kept while the chosen name and the loaded art stay the same. */
    private static String currentName;
    private static int currentVersion = -1, currentIndex;

    private static Arts arts() {
        if (arts == null) {
            arts = Arts.load();
            glyphs.clear();
            version++;
        }
        return arts;
    }

    /** Drops the art so the next draw reads it all again: the bundled set and the folder. */
    static void reload() {
        arts = null;
        folderSig = Arts.folderSignature();
        folderCheckedAt = System.currentTimeMillis();
    }

    /**
     * Reloads when the user's folder has changed since last looked at; at most every so often.
     * The listing runs on a worker and a later call picks it up, so a draw never waits on the disk.
     */
    static void checkFolder() {
        takeFolderCheck(folderCheck);
    }

    /** {@code pending}: the folder check under way, a parameter rather than a local copy of the field (HoistCheck). */
    private static void takeFolderCheck(CompletableFuture<String> pending) {
        if (pending != null) {
            if (!pending.isDone()) return;
            folderCheck = null;
            String sig = pending.getNow(null);
            if (sig == null) return;
            if (folderSig == null) {
                folderSig = sig;
            } else if (!sig.equals(folderSig)) {
                folderSig = sig;
                arts = null;
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now - folderCheckedAt < FOLDER_CHECK_MS) return;
        folderCheckedAt = now;
        folderCheck = CompletableFuture.supplyAsync(Arts::folderSignature);
    }

    static int version() {
        arts();
        return version;
    }

    static int count() {
        return arts().arts.size();
    }

    static String nameOf(int art) {
        return arts().names.get(art);
    }

    /** Whether that art came from the user's folder rather than with the client. */
    static boolean isUsers(int art) {
        return art >= arts().bundled;
    }

    static int current() {
        Workstations.State state = Workstations.state();
        Arts loaded = arts();
        if (state.artName != null) {
            if (state.artName == currentName && version == currentVersion) return currentIndex;
            int found = loaded.names.indexOf(state.artName);
            if (found >= 0) {
                currentName = state.artName;
                currentVersion = version;
                currentIndex = found;
                return found;
            }
        }
        return Math.floorMod(state.art, loaded.arts.size());
    }

    static String name() {
        return (current() + 1) + "/" + arts().arts.size() + " " + arts().names.get(current());
    }

    static String[] names() {
        return arts().names.toArray(new String[0]);
    }

    private static final Set<String> W_NEXT = Words.of("next n +");
    private static final Set<String> W_PREV = Words.of("prev p - back");
    private static final Set<String> W_RANDOM = Words.of("random r");

    static void cycle(String arg) {
        int size = arts().arts.size();
        int art = current();
        String a = arg.toLowerCase(Locale.ROOT);

        if (W_NEXT.contains(a)) {
            art = (art + 1) % size;
        } else if (W_PREV.contains(a)) {
            art = Math.floorMod(art - 1, size);
        } else if (W_RANDOM.contains(a)) {
            art = RANDOM.nextInt(size);
        } else {
            int found = arts().names.indexOf(a);
            if (found < 0) {
                try {
                    found = Integer.parseInt(a) - 1;
                } catch (NumberFormatException ignored) {
                }
            }
            if (found >= 0 && found < size) art = found;
        }
        select(art);
    }

    static void select(int art) {
        if (art < 0 || art >= arts().arts.size()) return;
        changedAt = System.currentTimeMillis();
        glitchUntil = changedAt + 220;
        glitchSeed = RANDOM.nextInt();
        Workstations.State state = Workstations.state();
        state.art = art;
        state.artName = arts().names.get(art);
        Workstations.saveState();
    }

    static String palette() {
        String p = Workstations.state().artPalette;
        for (String known : PALETTES) if (Objects.equals(known, p)) return known;
        return GOLD_PALETTE;
    }

    /** @return false for a palette there is none of */
    static boolean setPalette(String name) {
        for (String known : PALETTES) {
            if (Objects.equals(known, name)) {
                Workstations.state().artPalette = known;
                Workstations.saveState();
                return true;
            }
        }
        return false;
    }

    static boolean glitch() {
        return Workstations.state().artGlitch;
    }

    static boolean motion() {
        return Workstations.state().artMotion;
    }

    static void setGlitch(boolean on) {
        Workstations.state().artGlitch = on;
        Workstations.saveState();
    }

    static void setMotion(boolean on) {
        Workstations.state().artMotion = on;
        Workstations.saveState();
    }

    /**
     * A palette's colour a fraction {@code along} the way down the art; {@code t} in seconds,
     * for the one that moves.
     */
    static int color(String palette, float along, float t) {
        return colorAt(paletteIndex(palette), along, t);
    }

    /** A palette's place in {@link #PALETTES}; gold's for one there is none of. Once a draw, not once a glyph. */
    private static int paletteIndex(String palette) {
        for (int i = 0; i < PALETTES.length; i++) if (Objects.equals(PALETTES[i], palette)) return i;
        return 0;
    }

    /** {@link #color(String, float, float)} by the palette's place in {@link #PALETTES}. */
    private static int colorAt(int palette, float along, float t) {
        // An if chain, not a switch: see Words for why this code has none.
        if (palette == 1) return mix(MAGENTA, 0xFFB48CFF, along);
        if (palette == 2) return mix(GREEN, CYAN, along);
        if (palette == 3) return mix(0xFFBFEFFF, BLUE, along);
        if (palette == 4) return mix(0xFFF2F4F8, 0xFF8A90A0, along);
        if (palette == 5) return hsv(along * 0.45f + t * 0.06f, 0.55f, 1f);
        return mix(GOLD, AMBER, along);
    }

    /** Grows the two per-row buffers to at least {@code rows}. */
    private static void rowBuffers(int rows) {
        if (rowColor.length < rows) {
            rowColor = new int[rows];
            rowShift = new int[rows];
        }
    }

    /** The colour a palette shows as, where it is named: its top colour. */
    static int swatch(String palette) {
        return Objects.equals("rainbow", palette) ? hsv((System.currentTimeMillis() - STARTED) / 1000f * 0.2f, 0.55f, 1f) : color(palette, 0f, 0f);
    }

    private static int hsv(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f * 6f;
        int i = (int) h;
        float f = h - i, p = v * (1 - s), q = v * (1 - s * f), u = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = u; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = u; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = u; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    private static Glyphs glyphs(Font font, int art) {
        arts();
        while (glyphs.size() <= art) glyphs.add(null);
        Glyphs built = glyphs.get(art);
        if (built == null) {
            built = build(font::width, arts().arts.get(art));
            glyphs.set(art, built);
        }
        return built;
    }

    /**
     * The layout of a small fixed art with a made-up font, for the deploy's self-test: this
     * loop came out of the obfuscator never ending once, which froze the game on the
     * first frame a terminal drew, and nothing but running it shows that.
     */
    static String probeBuild() {
        Glyphs gl = build(s -> Objects.equals("i", s) ? 2 : CW, new String[] {"ab  cd", " i x i", "", "abc"});
        StringBuilder out = new StringBuilder();
        out.append(gl.count).append(' ').append(gl.cols).append(' ').append(gl.rows);
        for (int i = 0; i < gl.count; i++) out.append(' ').append(gl.x[i]).append(',').append(gl.row[i]).append(',').append(gl.text[i]);
        return out.toString();
    }

    /** {@code width} is the font's, as a function so the layout can be run without one. */
    private static Glyphs build(ToIntFunction<String> width, String[] lines) {
        Glyphs out = new Glyphs();
        out.rows = lines.length;
        List<int[]> ops = new ArrayList<>();
        List<String> text = new ArrayList<>();

        for (int row = 0; row < lines.length; row++) {
            String line = lines[row];
            out.cols = Math.max(out.cols, line.length());
            int runStart = -1;
            StringBuilder run = new StringBuilder();

            for (int col = 0; col <= line.length(); col++) {
                char c = col < line.length() ? line.charAt(col) : ' ';
                boolean regular = c != ' ' && width.applyAsInt(String.valueOf(c)) == CW;

                if (regular) {
                    if (runStart < 0) runStart = col;
                    run.append(c);
                    continue;
                }

                if (runStart >= 0) {
                    ops.add(new int[] {runStart * CW, row});
                    text.add(run.toString());
                    run.setLength(0);
                    runStart = -1;
                }

                if (c != ' ') {
                    ops.add(new int[] {col * CW + (CW - width.applyAsInt(String.valueOf(c))) / 2, row});
                    text.add(String.valueOf(c));
                }
            }
        }

        out.count = ops.size();
        out.x = new int[out.count];
        out.row = new int[out.count];
        out.text = text.toArray(new String[0]);
        for (int i = 0; i < out.count; i++) {
            int[] op = ops.get(i);
            out.x[i] = op[0];
            out.row[i] = op[1];
        }
        return out;
    }

    /** How tall an art is drawn {@code w} wide and no taller than {@code maxH}, as {@link #drawStill} draws it. */
    static int stillHeight(Font font, int art, int w, int maxH) {
        Glyphs gl = glyphs(font, art);
        float scale = Math.min(1f, Math.min((float) w / (gl.cols * CW), (float) maxH / (gl.rows * CH)));
        return Math.max(1, Math.round(gl.rows * CH * scale));
    }

    /**
     * An art as a still, in the chosen palette: centred in a box {@code w} wide, as large as
     * fits under {@code maxH} and never above its natural size. For the art window's previews.
     */
    static void drawStill(GuiGraphicsExtractor g, Font font, int art, int x, int y, int w, int maxH) {
        Glyphs gl = glyphs(font, art);
        float scale = Math.min(1f, Math.min((float) w / (gl.cols * CW), (float) maxH / (gl.rows * CH)));
        float dw = gl.cols * CW * scale;
        int palette = paletteIndex(palette());
        float t = (System.currentTimeMillis() - STARTED) / 1000f;

        rowBuffers(gl.rows);
        int[] colors = rowColor;
        for (int r = 0; r < gl.rows; r++) colors[r] = colorAt(palette, gl.rows <= 1 ? 0f : (float) r / (gl.rows - 1), t);

        g.pose().pushMatrix();
        g.pose().translate(x + (w - dw) / 2f, y);
        g.pose().scale(scale, scale);
        int[] xs = gl.x, rs = gl.row;
        String[] text = gl.text;
        for (int i = 0; i < gl.count; i++) {
            g.text(font, text[i], xs[i], rs[i] * CH, colors[rs[i]], false);
        }
        g.pose().popMatrix();
    }

    /**
     * Draws the art on show centred in a box, as large as fits and never above its natural size.
     *
     * @return the art's drawn bounds {x, y, w, h}, for a click to land on
     */
    static int[] draw(GuiGraphicsExtractor g, Font font, int boxX, int boxY, int boxW, int boxH) {
        // A file saved into the folder shows up without anything being run.
        checkFolder();
        int art = current();
        Glyphs gl = glyphs(font, art);
        int count = gl.count;
        int[] xs = gl.x, rs = gl.row;
        String[] text = gl.text;
        int cols = gl.cols;
        int rows = gl.rows;
        Workstations.State state = Workstations.state();
        int palette = paletteIndex(state.artPalette);
        boolean motion = state.artMotion;

        long now = System.currentTimeMillis();
        float t = (now - STARTED) / 1000f;
        float scale = Math.min(1f, Math.min((float) boxW / (cols * CW), (float) (boxH - 4) / (rows * CH)));
        float w = cols * CW * scale;
        float h = rows * CH * scale;
        float x = boxX + (boxW - w) / 2f;
        float y = boxY + (boxH - h) / 2f + (motion ? (float) Math.sin(t * 1.15) * 1.5f : 0f);
        float fadeIn = Math.min(1f, (now - changedAt) / 250f);

        if (now > nextGlitch) {
            if (state.artGlitch) {
                glitchUntil = now + 90 + RANDOM.nextInt(140);
                glitchSeed = RANDOM.nextInt();
            }
            nextGlitch = now + 3500 + RANDOM.nextInt(5000);
        }
        boolean glitch = now < glitchUntil;
        float scan = (t * 9f) % (rows + 12) - 6;

        // Everything that varies does so by row, so it is worked out once a row rather than once
        // a glyph: the palette, the sweeping band, the fade and the glitch's shift.
        rowBuffers(rows);
        int[] colors = rowColor, shift = rowShift;
        Random jitter = glitch ? new Random(glitchSeed) : null;
        for (int r = 0; r < rows; r++) {
            int color = colorAt(palette, rows <= 1 ? 0f : (float) r / (rows - 1), t);
            if (motion) {
                float d = Math.abs(r - scan);
                if (d < 2.5f) color = mix(color, 0xFFFFF8E0, (2.5f - d) / 2.5f * 0.8f);
            }
            colors[r] = Pipes.withAlpha(color, fadeIn);
            shift[r] = jitter != null && jitter.nextInt(5) == 0 ? (jitter.nextInt(7) - 3) * CW / 2 : 0;
        }

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        if (glitch) {
            for (int i = 0; i < count; i++) {
                int ox = xs[i] + shift[rs[i]];
                g.text(font, text[i], ox - 2, rs[i] * CH, 0x9957C7FF, false);
                g.text(font, text[i], ox + 2, rs[i] * CH, 0x99FF6AC1, false);
            }
        }

        // The font's own shadow, not a second pass of glyphs: one draw per run instead of two.
        // Still only where it is cheap, as before, because it doubles the quads.
        boolean shadow = count < 500;
        for (int i = 0; i < count; i++) {
            int row = rs[i];
            g.text(font, text[i], xs[i] + shift[row], row * CH, colors[row], shadow);
        }

        g.pose().popMatrix();
        return new int[] {Math.round(x), Math.round(y), Math.round(w), Math.round(h)};
    }
}
