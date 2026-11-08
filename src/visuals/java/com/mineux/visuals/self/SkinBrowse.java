package com.mineux.visuals.self;

import com.mojang.blaze3d.platform.NativeImage;
import com.mineux.core.ui.term.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The skin changer's "browse files" row: the system's open-file dialog (File Explorer's on
 * Windows), through LWJGL's tinyfd, which the game ships and loads at start.
 * <p>
 * The dialog waits for the person, so it runs on a thread of its own; the png is read, checked
 * and copied into config/mineux/skins there too, and only the result -- the skin's name and
 * its arms -- is put into the config on the client thread. Where no dialog opens, the skins
 * folder is opened instead, with its path said, to drop a png into.
 */
public final class SkinBrowse {

    private static final AtomicBoolean OPEN = new AtomicBoolean();
    private static volatile String said = "";

    private SkinBrowse() {}

    /** What the last browse came to, for the settings window's line under the skin rows. */
    public static String stat() {
        String s = said;
        return s.isEmpty() ? "browse: a 64x64 png, kept in config/mineux/skins" : s;
    }

    /** Opens the dialog, unless one is open already. Never waits for it. */
    public static void open() {
        if (!OPEN.compareAndSet(false, true)) {
            tell("the file picker is open already", Notify.INFO);
            return;
        }
        Thread t = new Thread(new Pick(Skins.folder()), "mineux-skin-browse");
        t.setDaemon(true);
        t.start();
    }

    private static void tell(String text, int color) {
        said = "browse: " + text;
        Notify.show("skin", "skin: " + text, color);
    }

    /** From any thread: said on the client thread. */
    private static void later(String text, int color) {
        Minecraft.getInstance().execute(new Say(text, color));
    }

    /** Where the dialog starts: the skins folder once it has skins in it, else Downloads, else home. */
    private static String start(Path dir) {
        if (SkinFiles.anyPng(dir)) return dir.toAbsolutePath() + File.separator;
        Path home = Path.of(System.getProperty("user.home", "."));
        Path downloads = home.resolve("Downloads");
        return (Files.isDirectory(downloads) ? downloads : home).toAbsolutePath() + File.separator;
    }

    private static String dialog(String startAt) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filter = stack.mallocPointer(1);
            filter.put(stack.UTF8("*.png"));
            filter.flip();
            return TinyFileDialogs.tinyfd_openFileDialog("Choose a skin (64x64 png)", startAt, filter, "skin png", false);
        }
    }

    /** Reads, checks and keeps the file chosen; the result goes to the client thread. */
    private static void take(Path chosen, Path dir) {
        String name = chosen.getFileName() == null ? chosen.toString() : chosen.getFileName().toString();
        byte[] bytes;
        try {
            if (Files.size(chosen) > SkinFiles.MAX_BYTES) {
                later(name + " is too big to be a skin", Notify.BAD);
                return;
            }
            bytes = Files.readAllBytes(chosen);
        } catch (Exception e) {
            later("could not read " + name, Notify.BAD);
            return;
        }
        String problem = SkinFiles.problem(bytes, name);
        if (problem != null) {
            later(problem, Notify.BAD);
            return;
        }
        int arms = arms(bytes);
        if (arms < 0) {
            later(name + " is not a picture the game can read", Notify.BAD);
            return;
        }
        try {
            String kept = SkinFiles.store(bytes, chosen, dir);
            Minecraft.getInstance().execute(new Apply(kept, arms == 1));
        } catch (Exception e) {
            later("could not copy " + name + " into " + dir.toAbsolutePath(), Notify.BAD);
        }
    }

    /** 1 for slim arms, 0 for wide, -1 when the png does not decode. */
    private static int arms(byte[] bytes) {
        try (NativeImage image = NativeImage.read(bytes)) {
            int w = image.getWidth(), h = image.getHeight();
            if (!SkinFiles.skinSize(w, h)) return -1;
            int[] argb = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) argb[y * w + x] = image.getPixel(x, y);
            }
            return SkinFiles.slim(argb, w, h) ? 1 : 0;
        } catch (Exception | LinkageError e) {
            return -1;
        }
    }

    /** The dialog's thread. */
    private static final class Pick implements Runnable {
        private final Path dir;

        Pick(Path dir) {
            this.dir = dir;
        }

        @Override
        public void run() {
            try {
                String chosen;
                try {
                    chosen = dialog(start(dir));
                } catch (Exception | LinkageError e) {
                    Minecraft.getInstance().execute(new Fallback(dir));
                    return;
                }
                if (chosen == null || chosen.isBlank()) {
                    later("nothing chosen -- or drop a png into " + dir.toAbsolutePath(), Notify.INFO);
                    return;
                }
                take(Path.of(chosen.trim()), dir);
            } finally {
                OPEN.set(false);
            }
        }
    }

    /** No dialog here: the skins folder opens in the file manager, and its path is said. */
    private static final class Fallback implements Runnable {
        private final Path dir;

        Fallback(Path dir) {
            this.dir = dir;
        }

        @Override
        public void run() {
            try {
                Util.getPlatform().openPath(dir);
            } catch (Exception | LinkageError ignored) {
                // The path below is enough.
            }
            tell("no file picker -- drop a png into " + dir.toAbsolutePath() + " and type its name as file", Notify.INFO);
        }
    }

    /** On the client thread: wear the skin kept, on the arms it was drawn for. */
    private static final class Apply implements Runnable {
        private final String file;
        private final boolean slim;

        Apply(String file, boolean slim) {
            this.file = file;
            this.slim = slim;
        }

        @Override
        public void run() {
            SelfConfig config = SelfConfig.get();
            config.skin = true;
            config.skinFile = file;
            config.skinCopy = "";
            config.skinModel = slim ? "slim" : "wide";
            config.ensureDefaults();
            config.save();
            Skins.reload(file);
            tell("wearing " + file + ", " + (slim ? "slim" : "wide") + " arms (model changes them)", Notify.GOOD);
        }
    }

    private static final class Say implements Runnable {
        private final String text;
        private final int color;

        Say(String text, int color) {
            this.text = text;
            this.color = color;
        }

        @Override
        public void run() {
            tell(text, color);
        }
    }
}
