package com.mineux.visuals.self.model;

import com.mojang.blaze3d.platform.NativeImage;
import com.mineux.core.config.ConfigFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The custom models in config/mineux/models: read on a thread of their own (a big model takes
 * a while), read again when the file changes, and their textures uploaded on the render thread
 * the first time the model is drawn.
 */
public final class ModelFiles {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/self");

    private static volatile Imported model;
    private static volatile String modelKey = "";
    private static volatile String loadingKey = "";
    private static volatile String error = "";

    /** The textures of the model last uploaded, one per piece. */
    private static final List<Identifier> TEXTURES = new ArrayList<>();
    private static Imported uploadedFor;
    private static Identifier white;
    private static int uploads;

    private ModelFiles() {}

    public static Path folder() {
        Path dir = ConfigFile.directory().resolve("models");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            // Read below as "no such file".
        }
        return dir;
    }

    /** The model in {@code file}, or null while it is loading or will not load ({@link #status} says which). */
    public static Imported get(String file, boolean zUp) {
        if (file == null || file.isBlank()) {
            error = "name a model file in config/mineux/models";
            return null;
        }
        Path dir = folder();
        Path p = dir.resolve(file.trim()).normalize();
        if (!p.startsWith(dir)) {
            error = "the model must be in config/mineux/models";
            return null;
        }
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            error = "no " + file.trim() + " in config/mineux/models";
            return null;
        }
        String key = p + "|" + mtime + "|" + zUp;
        if (key.equals(modelKey)) return model;
        if (!key.equals(loadingKey)) {
            loadingKey = key;
            error = "";
            Thread t = new Thread(new Load(p, zUp, key), "mineux-model-loader");
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    /** What the model is up to: loading, its error, or its name and size. */
    public static String status() {
        if (!error.isEmpty()) return error;
        if (!loadingKey.isEmpty() && !loadingKey.equals(modelKey)) return "loading...";
        Imported m = model;
        return m == null ? "no model" : m.name + ", " + m.triangles + " triangles";
    }

    /** Every model file in the folder (and one folder down), for the settings. */
    public static List<String> files() {
        Path dir = folder();
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir, 2)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p) && Imported.supported(p)) out.add(dir.relativize(p).toString().replace('\\', '/'));
            }
        } catch (Exception ignored) {
            // None, then.
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static final class Load implements Runnable {
        private final Path path;
        private final boolean zUp;
        private final String key;

        Load(Path path, boolean zUp, String key) {
            this.path = path;
            this.zUp = zUp;
            this.key = key;
        }

        @Override
        public void run() {
            try {
                Imported m = Imported.load(path, zUp);
                if (!key.equals(loadingKey)) return;
                model = m;
                modelKey = key;
                error = "";
            } catch (Exception | LinkageError | OutOfMemoryError e) {
                if (!key.equals(loadingKey)) return;
                model = null;
                modelKey = key;
                error = "could not load " + path.getFileName() + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                LOGGER.warn(error, e);
            }
        }
    }

    /** The texture of each piece, uploaded the first time this model is drawn; render thread only. */
    static List<Identifier> textures(Imported m) {
        if (m == uploadedFor) return TEXTURES;
        Minecraft mc = Minecraft.getInstance();
        Map<String, Identifier> byImage = new HashMap<>();
        uploads++;
        int slot = 0;
        for (Map.Entry<String, byte[]> e : m.images.entrySet()) {
            try {
                NativeImage img = readImage(e.getValue());
                Identifier id = Identifier.fromNamespaceAndPath("mineux", "dynamic/self_model/" + uploads + "_" + slot++);
                mc.getTextureManager().register(id, new DynamicTexture(new Label("mineux model texture"), img));
                byImage.put(e.getKey(), id);
            } catch (Exception | LinkageError ex) {
                LOGGER.warn("model texture {} could not be read", e.getKey(), ex);
            }
        }
        if (white == null) {
            NativeImage img = new NativeImage(1, 1, false);
            img.setPixel(0, 0, 0xFFFFFFFF);
            white = Identifier.fromNamespaceAndPath("mineux", "dynamic/self_model/white");
            mc.getTextureManager().register(white, new DynamicTexture(new Label("mineux model white"), img));
        }
        TEXTURES.clear();
        for (Piece p : m.parts) {
            Identifier id = p.image == null ? null : byImage.get(p.image);
            TEXTURES.add(id == null ? white : id);
        }
        uploadedFor = m;
        return TEXTURES;
    }

    /** A png as it is; a jpeg or other image the JDK reads, as a png first. */
    private static NativeImage readImage(byte[] bytes) throws Exception {
        boolean png = bytes.length > 8 && (bytes[0] & 0xFF) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
        if (!png) {
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bi == null) throw new IllegalArgumentException("not an image the game can read");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", out);
            bytes = out.toByteArray();
        }
        return NativeImage.read(new ByteArrayInputStream(bytes));
    }

    private static final class Label implements java.util.function.Supplier<String> {
        private final String text;

        Label(String text) {
            this.text = text;
        }

        @Override
        public String get() {
            return text;
        }
    }
}
