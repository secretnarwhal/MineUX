package com.mineux.visuals.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mineux.visuals.hud.SkinConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Theme textures: the game's gui textures (menus, buttons, the HUD's sprites) -- or, asked, its
 * block and item textures as well -- pulled toward one colour as they load, by their brightness,
 * so the whole look takes the client's accent. Done to each image once as the resource reload
 * reads it ({@code ThemeSpriteMixin}, {@code ThemeTextureMixin}); switching it, or changing its
 * colour or strength, reloads the resources once the setting has stood still a moment.
 * <p>
 * The textures loaded before sign-in were loaded plain, so a theme that is on reloads them once
 * when the visuals start.
 */
public final class ThemeTextures {

    /** The gui atlas's sprite folders. */
    private static final String[] GUI_SPRITES = {"widget/", "hud/", "container/", "toast/", "popup/", "tooltip/",
        "recipe_book/", "advancements/", "world_list/", "server_list/", "spectator/", "boss_bar/", "statistics/",
        "icon/", "notification/", "dialog/", "social_interactions/", "transferable_list/", "pending_invite/",
        "gamemode_switcher/", "player_list/", "realm_status/", "backup/"};

    private static final long SETTLE_MS = 500L;

    /** What the loaded textures were made with ("" for plain), and what the setting asks for since when. */
    private static String loaded = "";
    private static String wanted = "";
    private static long wantedSince;

    private ThemeTextures() {}

    /** Once a tick: a reload when the setting has changed from what the textures were made with and stood still. */
    public static void onClientTick(Minecraft mc) {
        String now = signature(SkinConfig.get());
        if (!Objects.equals(now, wanted)) {
            wanted = now;
            wantedSince = System.currentTimeMillis();
        }
        if (Objects.equals(wanted, loaded) || System.currentTimeMillis() - wantedSince < SETTLE_MS) return;
        loaded = wanted;
        mc.reloadResourcePacks();
    }

    private static String signature(SkinConfig config) {
        if (!config.themeTextures || config.themeStrength <= 0.001F) return "";
        return config.themeColor + "/" + Math.round(config.themeStrength * 100.0F) + "/" + config.themeScope;
    }

    /** From a resource reload's thread, for every sprite and texture it reads. Always false: it only recolours. */
    public static boolean recolor(Object id, Object image) {
        if (!Identifier.class.isInstance(id) || !NativeImage.class.isInstance(image)) return false;
        SkinConfig config = SkinConfig.get();
        if (!config.themeTextures || config.themeStrength <= 0.001F) return false;
        if (!wanted(((Identifier) id).getPath(), Objects.equals("all", config.themeScope))) return false;
        NativeImage picture = (NativeImage) image;
        if (picture.format() != NativeImage.Format.RGBA) return false;
        tint(picture, SkinConfig.rgb(config.themeColor, 0xEFC648), config.themeStrength);
        return false;
    }

    private static boolean wanted(String path, boolean all) {
        if (path.startsWith("textures/gui/") || path.contains("/gui/")) return true;
        for (String folder : GUI_SPRITES) {
            if (path.startsWith(folder)) return true;
        }
        return all && (path.startsWith("block/") || path.startsWith("item/") || path.startsWith("textures/block/") || path.startsWith("textures/item/"));
    }

    /** Each pixel toward its brightness in the colour, by {@code strength}; the alpha left alone. */
    private static void tint(NativeImage picture, int colour, float strength) {
        float r0 = (colour >>> 16) & 0xFF;
        float g0 = (colour >>> 8) & 0xFF;
        float b0 = colour & 0xFF;
        float top = Math.max(1.0F, Math.max(r0, Math.max(g0, b0)));
        float tr = r0 / top;
        float tg = g0 / top;
        float tb = b0 / top;
        int w = picture.getWidth();
        int h = picture.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = picture.getPixel(x, y);
                int a = p >>> 24;
                if (a == 0) continue;
                int r = (p >>> 16) & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int b = p & 0xFF;
                float lum = 0.299F * r + 0.587F * g + 0.114F * b;
                int nr = Math.min(255, Math.round(r + (lum * tr - r) * strength));
                int ng = Math.min(255, Math.round(g + (lum * tg - g) * strength));
                int nb = Math.min(255, Math.round(b + (lum * tb - b) * strength));
                picture.setPixel(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
    }
}
