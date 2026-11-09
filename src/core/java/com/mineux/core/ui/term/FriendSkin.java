package com.mineux.core.ui.term;

import com.mineux.core.ui.DeskScale;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A friend's Minecraft skin as a 3D player, drawn in the friends app and turning to look at the
 * cursor: the body a little, the head the rest of the way, as the inventory's paper doll does.
 * <p>
 * Drawn with the game's own GUI skin pass ({@code GuiGraphicsExtractor#skin}, the one the skin
 * customisation screen uses), so it is the same on OpenGL and Vulkan. The skin is looked up once
 * per account, off the render thread: Mojang's profile (by UUID, else by name) for its signed
 * textures, then the game's skin manager, which downloads and caches it. Until then, and for
 * anyone without a known account, the default skin that UUID would have.
 */
final class FriendSkin {

    private FriendSkin() {}

    private static final Map<String, Supplier<PlayerSkin>> SKINS = new ConcurrentHashMap<>();

    private static Model.Simple wide;
    private static Model.Simple slim;

    /**
     * Draws the player standing in the box, looking at the mouse.
     *
     * @param uuid  their Minecraft UUID, dashed or not, or empty
     * @param name  their Minecraft name, or empty
     * @param key   something stable to pick a default skin by when neither is known
     */
    static void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int mouseX, int mouseY,
                     String uuid, String name, String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || x1 - x0 < 8 || y1 - y0 < 16) return;
        if (wide == null) {
            wide = new Model.Simple(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
            slim = new Model.Simple(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
        }
        PlayerSkin skin = skin(uuid, name, key).get();
        Model.Simple model = skin.model() == PlayerModelType.SLIM ? slim : wide;

        // Where the eyes are, and how far off the cursor is from them, in degrees.
        float eyeX = (x0 + x1) / 2f;
        float eyeY = y0 + (y1 - y0) * 0.18f;
        float yaw = (float) Math.toDegrees(Math.atan((mouseX - eyeX) / 60f));
        float pitch = (float) Math.toDegrees(Math.atan((mouseY - eyeY) / 60f));

        // The body turns a third of the way and the head the rest: one model part each.
        ModelPart head = model.root().getChild("head");
        ModelPart hat = model.root().hasChild("hat") ? model.root().getChild("hat") : null;
        // Facing you, the model's own left is the screen's right, and a part's positive yRot
        // turns it to its own right: so a cursor on the right is a negative turn of the head.
        // The whole model turns the other way round (the skin widget drags it by +rotationY).
        float headYaw = (float) Math.toRadians(0f - yaw * 0.66f);
        float headPitch = (float) Math.toRadians(pitch * 0.8f);
        head.yRot = headYaw;
        head.xRot = headPitch;
        if (hat != null) {
            hat.yRot = headYaw;
            hat.xRot = headPitch;
        }

        // The model is drawn by the game into a box of its own, which takes GUI corners and
        // ignores the pose: the box goes through the pose here (the desk scale, a slide).
        float scale = 0.97f * (y1 - y0) / 2.125f * DeskScale.poseScale(g);
        int[] a = DeskScale.onScreen(g, x0, y0);
        int[] b = DeskScale.onScreen(g, x1, y1);
        g.skin(model, skin.body().texturePath(), scale, 0f - pitch * 0.15f, yaw * 0.33f, -1.0625f, a[0], a[1], b[0], b[1]);
    }

    /** Their face, flat, {@code size} px square: for the list. */
    static void face(GuiGraphicsExtractor g, int x, int y, int size, String uuid, String name, String key) {
        net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(g, skin(uuid, name, key).get(), x, y, size);
    }

    private static Supplier<PlayerSkin> skin(String uuid, String name, String key) {
        String id = uuid.isEmpty() ? name.toLowerCase(Locale.ROOT) : uuid.replace("-", "").toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            UUID fallback = UUID.nameUUIDFromBytes(("mineux:" + key).getBytes(StandardCharsets.UTF_8));
            PlayerSkin plain = DefaultPlayerSkin.get(fallback);
            return () -> plain;
        }
        Supplier<PlayerSkin> known = SKINS.get(id);
        if (known != null) return known;

        UUID guess = parse(uuid);
        PlayerSkin plain = DefaultPlayerSkin.get(guess != null ? guess
            : UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
        Supplier<PlayerSkin> waiting = () -> plain;
        SKINS.put(id, waiting);

        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> profile(mc, guess, name), Util.nonCriticalIoPool())
            .thenAccept(profile -> {
                if (profile != null) SKINS.put(id, mc.getSkinManager().createLookup(profile, false));
            });
        return waiting;
    }

    /** Mojang's profile with its textures, by UUID, else by name; null if neither answers. */
    private static GameProfile profile(Minecraft mc, UUID uuid, String name) {
        try {
            if (uuid != null) {
                ProfileResult result = mc.services().sessionService().fetchProfile(uuid, false);
                if (result != null) return result.profile();
            }
            if (!name.isEmpty()) {
                GameProfile byName = mc.services().profileResolver().fetchByName(name).orElse(null);
                if (byName != null) {
                    ProfileResult result = mc.services().sessionService().fetchProfile(byName.id(), false);
                    return result != null ? result.profile() : byName;
                }
            }
        } catch (RuntimeException e) {
            // Mojang down or rate limited: the default skin stays.
        }
        return null;
    }

    private static UUID parse(String text) {
        String hex = text.replace("-", "");
        if (hex.length() != 32) return null;
        try {
            return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
