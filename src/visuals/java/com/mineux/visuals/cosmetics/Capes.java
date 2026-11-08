package com.mineux.visuals.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Your cape, on this client only: a texture put on your render state in place of whatever cape
 * your skin has, and on your elytra too. Nobody else sees it; the server never hears of it.
 * <p>
 * Most are the jar's own ({@code mineux/capes/<name>.png}, the usual 64x32 cape layout or
 * a multiple of it; the anime ones were drawn by a script). Two read a
 * picture from {@code config/mineux/capes/} ({@link CapeFolder}): {@link #GIRL} its
 * {@code cute-anime-girl.png}, {@link #CUSTOM} whichever file {@code capeFile} names. Either shows
 * a placeholder until its file is there, and picks up a new or changed file within a second.
 */
public final class Capes {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/cosmetics");

    /** The slot for the user's own picture, {@code cute-anime-girl.png}. */
    public static final String GIRL = "cute anime girl";
    /** Any picture in the folder, by the name in {@code capeFile}. */
    public static final String CUSTOM = "custom";

    static final String GIRL_FILE = "cute-anime-girl";
    private static final String PLACEHOLDER = "cute-anime-girl-placeholder";
    /** How often a picture's file is looked at again, for a new or changed one. */
    private static final long LOOK_MS = 1000L;
    /** A file bigger than this is not read. */
    private static final long MAX_BYTES = 16L * 1024L * 1024L;

    public static final List<String> NAMES = Arrays.asList(
        "off", GIRL, "sakura", "kitsune", "neko", "starlight", "koi", "aurora", "nebula", "flow", "circuit", "prism", CUSTOM);

    /** Name -> its texture once registered; a name whose file would not load maps to null. */
    private static final Map<String, ClientAsset.Texture> LOADED = new HashMap<>();
    /** Folder file -> its texture, the file's stamp when it was read, and when to look again. */
    private static final Map<String, ClientAsset.Texture> FILES = new HashMap<>();
    private static final Map<String, Long> STAMPS = new HashMap<>();
    private static final Map<String, Long> NEXT_LOOK = new HashMap<>();

    private static boolean folderReady;

    private Capes() {}

    /** From the avatar renderer, for every player drawn: yours gets the chosen cape. */
    static void onAvatarState(Minecraft mc, AvatarRenderState state, CosmeticsConfig config) {
        if (mc.player == null || state.id != mc.player.getId()) return;
        if (!folderReady) {
            folderReady = true;
            CapeFolder.prepare();
        }
        ClientAsset.Texture cape = texture(mc, config);
        if (cape == null || state.skin == null) return;
        PlayerSkin skin = state.skin;
        state.skin = new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure());
        state.showCape = true;
    }

    private static ClientAsset.Texture texture(Minecraft mc, CosmeticsConfig config) {
        String name = config.cape;
        if (name == null || NAMES.indexOf(name) <= 0) return null;
        boolean girl = name.equals(GIRL);
        if (!girl && !name.equals(CUSTOM)) return builtIn(mc, name);
        String file = girl ? GIRL_FILE : CapeFolder.clean(config.capeFile);
        ClientAsset.Texture picture = file == null ? null : fromFolder(mc, file);
        return picture != null ? picture : builtIn(mc, PLACEHOLDER);
    }

    private static ClientAsset.Texture builtIn(Minecraft mc, String name) {
        if (LOADED.containsKey(name)) return LOADED.get(name);
        ClientAsset.Texture made = loadBuiltIn(mc, name);
        LOADED.put(name, made);
        return made;
    }

    private static ClientAsset.Texture loadBuiltIn(Minecraft mc, String name) {
        try (InputStream in = Capes.class.getResourceAsStream("/mineux/capes/" + name + ".png")) {
            if (in == null) {
                LOGGER.warn("cape {} is missing from the jar", name);
                return null;
            }
            return register(mc, "cape_" + name, NativeImage.read(in));
        } catch (Exception e) {
            LOGGER.warn("cape {} would not load", name, e);
            return null;
        }
    }

    /** The folder's picture {@code file}, read again when it changes; null while it is missing or bad. */
    private static ClientAsset.Texture fromFolder(Minecraft mc, String file) {
        long now = System.currentTimeMillis();
        Long next = NEXT_LOOK.get(file);
        if (next != null && now < next.longValue()) return FILES.get(file);
        NEXT_LOOK.put(file, now + LOOK_MS);

        long stamp = CapeFolder.stamp(file);
        Long had = STAMPS.get(file);
        if (had != null && had.longValue() == stamp) return FILES.get(file);
        STAMPS.put(file, stamp);
        ClientAsset.Texture made = stamp == CapeFolder.MISSING ? null : loadFile(mc, file);
        FILES.put(file, made);
        return made;
    }

    private static ClientAsset.Texture loadFile(Minecraft mc, String file) {
        Path path = CapeFolder.file(file);
        try {
            if (Files.size(path) > MAX_BYTES) {
                LOGGER.warn("cape picture {} is over 16 MB; not read", path);
                return null;
            }
            NativeImage image;
            try (InputStream in = Files.newInputStream(path)) {
                image = NativeImage.read(NativeImage.Format.RGBA, in);
            }
            LOGGER.info("cape picture {} read, {}x{}", path, image.getWidth(), image.getHeight());
            return register(mc, CapeFolder.textureName(file), CapeFit.fit(image));
        } catch (Exception e) {
            LOGGER.warn("cape picture {} would not load", path, e);
            return null;
        }
    }

    /** Registers {@code image} (replacing, and closing, whatever had that name) and returns it as a cape. */
    private static ClientAsset.Texture register(Minecraft mc, String name, NativeImage image) {
        Identifier id = Identifier.fromNamespaceAndPath("mineux", "cosmetics/" + name);
        String label = "mineux " + name;
        mc.getTextureManager().register(id, new DynamicTexture(() -> label, image));
        return new ClientAsset.ResourceTexture(id, id);
    }
}
