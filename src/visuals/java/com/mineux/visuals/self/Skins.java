package com.mineux.visuals.self;

import com.mineux.core.ui.term.Notify;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.platform.NativeImage;
import com.mineux.core.config.ConfigFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skin changer: the skin you see yourself in -- in third person, in the inventory, on the tab list
 * -- swapped for a png of your own (config/mineux/skins), the default skin, or another player's.
 * Only this client draws it; the server and everyone else still see your own.
 * <p>
 * Your skin is asked for through the tab list's entry for you; that answer is swapped on its way
 * out. A png is read and uploaded once, on the client thread, and a 64x32 one is widened to the
 * 64x64 layout first. Another player's is the one on the tab list when they are on the server,
 * and otherwise looked up from Mojang on a thread of its own, once, with a pause after a miss.
 */
final class Skins {

    private static final List<String> MODELS = Arrays.asList("auto", "slim", "wide");
    private static final int SLIM = 1, WIDE = 2;

    private static final Map<String, Identifier> UPLOADED = new ConcurrentHashMap<>();
    private static final Map<String, PlayerSkin> FETCHED = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> MISSED = new ConcurrentHashMap<>();

    /** The png wanted and the texture it became; null while there is none. */
    private static volatile String loadedFile = "";
    private static volatile Identifier texture;
    private static int retryAt;
    private static int ticks;
    private static String lastError = "";

    private Skins() {}

    /** Whether the skin named is the game's default one. */
    private static boolean isDefault(String file) {
        return file != null && Objects.equals("default", file.trim().toLowerCase(Locale.ROOT));
    }

    static int modelIndex(String name) {
        return MODELS.indexOf(name);
    }

    static Path folder() {
        Path dir = ConfigFile.directory().resolve("skins");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            // Read below as "no such file".
        }
        return dir;
    }

    /** Your skin as the game has it, or the one to draw instead; null leaves it. */
    static PlayerSkin override(PlayerSkin original) {
        SelfConfig config = SelfConfig.get();
        if (!config.skin || original == null) return null;
        String copy = config.skinCopy.trim();
        if (!copy.isEmpty()) {
            PlayerSkin copied = copied(copy);
            if (copied != null) return copied;
        }
        if (isDefault(config.skinFile)) return DefaultPlayerSkin.getDefaultSkin();
        Identifier tex = texture;
        if (tex == null) return null;
        ClientAsset.Texture body = new ClientAsset.ResourceTexture(tex, tex);
        return new PlayerSkin(body, original.cape(), original.elytra(), model(config, original.model()), original.secure());
    }

    private static PlayerModelType model(SelfConfig config, PlayerModelType original) {
        int m = modelIndex(config.skinModel);
        if (m == SLIM) return PlayerModelType.SLIM;
        if (m == WIDE) return PlayerModelType.WIDE;
        return original;
    }

    private static PlayerSkin copied(String name) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        LocalPlayer me = mc.player;
        if (connection != null && me != null) {
            PlayerInfo info = connection.getPlayerInfo(name);
            if (info != null && !info.getProfile().id().equals(me.getUUID())) return info.getSkin();
        }
        return FETCHED.get(name.toLowerCase(Locale.ROOT));
    }

    static void onClientTick(Minecraft mc) {
        ticks++;
        SelfConfig config = SelfConfig.get();
        if (!config.skin) {
            texture = null;
            loadedFile = "";
            return;
        }
        String file = config.skinFile.trim();
        boolean wantsFile = !file.isEmpty() && !isDefault(file);
        boolean retry = wantsFile && texture == null && file.equals(loadedFile) && ticks >= retryAt;
        if (!file.equals(loadedFile) || retry) {
            loadedFile = file;
            texture = null;
            if (wantsFile) {
                texture = load(mc, file);
                if (texture == null) retryAt = ticks + 100;
            }
        }
        String copy = config.skinCopy.trim();
        if (!copy.isEmpty()) fetch(mc, copy);
    }

    private static Identifier load(Minecraft mc, String file) {
        Identifier cached = UPLOADED.get(file);
        if (cached != null) return cached;
        Path dir = folder();
        Path path = dir.resolve(file.endsWith(".png") ? file : file + ".png").normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) {
            fail(mc, "no skin " + file + " in config/mineux/skins");
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            String problem = SkinFiles.problem(bytes, file);
            if (problem != null) {
                fail(mc, problem);
                return null;
            }
            NativeImage image;
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                image = NativeImage.read(in);
            }
            if (image.getHeight() == 32) image = widen(image);
            String key = file.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
            Identifier id = Identifier.fromNamespaceAndPath("mineux", "dynamic/skin/" + key);
            mc.getTextureManager().register(id, new DynamicTexture(new SkinLabel(file), image));
            UPLOADED.put(file, id);
            lastError = "";
            return id;
        } catch (Exception | LinkageError e) {
            fail(mc, "could not read " + file);
            return null;
        }
    }

    /** A skin just chosen (SkinBrowse): read again on the next tick, even under a name read before. */
    static void reload(String file) {
        UPLOADED.remove(file);
        lastError = "";
        loadedFile = "";
        retryAt = 0;
    }

    private static void fail(Minecraft mc, String message) {
        if (message.equals(lastError)) return;
        lastError = message;
        Notify.show("skin", "skin: " + message, Notify.BAD);
    }

    /** An old 64x32 skin in the 64x64 layout: its right arm and leg mirrored into the left's place. */
    private static NativeImage widen(NativeImage old) {
        NativeImage img = new NativeImage(64, 64, true);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) img.setPixel(x, y, old.getPixel(x, y));
        }
        int[] moves = {
            4, 16, 20, 48, 4, 4, 8, 16, 24, 48, 4, 4, 0, 20, 24, 52, 4, 12, 4, 20, 20, 52, 4, 12,
            8, 20, 16, 52, 4, 12, 12, 20, 28, 52, 4, 12, 44, 16, 36, 48, 4, 4, 48, 16, 40, 48, 4, 4,
            40, 20, 40, 52, 4, 12, 44, 20, 36, 52, 4, 12, 48, 20, 32, 52, 4, 12, 52, 20, 44, 52, 4, 12};
        for (int i = 0; i < moves.length; i += 6) {
            mirror(img, moves[i], moves[i + 1], moves[i + 2], moves[i + 3], moves[i + 4], moves[i + 5]);
        }
        old.close();
        return img;
    }

    private static void mirror(NativeImage img, int sx, int sy, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) img.setPixel(dx + w - 1 - x, dy + y, img.getPixel(sx + x, sy + y));
        }
    }

    /** Another player's skin, looked up once off the client thread; a miss waits 30 seconds. */
    private static void fetch(Minecraft mc, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (FETCHED.containsKey(key)) return;
        Long missed = MISSED.get(key);
        if (missed != null && System.currentTimeMillis() - missed < 30000L) return;
        if (!PENDING.add(key)) return;
        Thread t = new Thread(new Fetch(mc, name, key), "mineux-skin-fetch");
        t.setDaemon(true);
        t.start();
    }

    private static final class Fetch implements Runnable {
        private final Minecraft mc;
        private final String name;
        private final String key;

        Fetch(Minecraft mc, String name, String key) {
            this.mc = mc;
            this.name = name;
            this.key = key;
        }

        @Override
        public void run() {
            try {
                Optional<GameProfile> found = mc.services().profileResolver().fetchByName(name);
                if (found.isEmpty()) throw new IllegalStateException("no such player");
                GameProfile profile = found.get();
                try {
                    ProfileResult full = mc.services().sessionService().fetchProfile(profile.id(), true);
                    if (full != null && full.profile() != null) profile = full.profile();
                } catch (RuntimeException ignored) {
                    // The plain profile still names the skin's owner.
                }
                Optional<PlayerSkin> skin = mc.getSkinManager().get(profile).get();
                if (skin.isEmpty()) throw new IllegalStateException("no skin");
                FETCHED.put(key, skin.get());
            } catch (Exception | LinkageError e) {
                MISSED.put(key, System.currentTimeMillis());
            } finally {
                PENDING.remove(key);
            }
        }
    }

    /** The texture's name in the debug screens. */
    private static final class SkinLabel implements java.util.function.Supplier<String> {
        private final String file;

        SkinLabel(String file) {
            this.file = file;
        }

        @Override
        public String get() {
            return "mineux skin " + file;
        }
    }
}
