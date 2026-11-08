package com.mineux.core.ui.term;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Servers' icons, drawn tiny beside their names wherever a server is listed -- the servers
 * window, {@code join}'s completions, the in-game server list -- the way vanilla's server list
 * uploads them: one texture per address, re-uploaded when a ping brings a new icon, so a list
 * shows the icon the moment the ping lands.
 */
final class ServerIcons {

    /** Drawn size, in GUI px; the icon itself is 64x64. */
    static final int SIZE = 8;

    private static final class Icon {
        final FaviconTexture texture;
        byte[] bytes;
        boolean ok;

        Icon(FaviconTexture texture) {
            this.texture = texture;
        }
    }

    private static final Map<String, Icon> ICONS = new HashMap<>();

    private ServerIcons() {}

    /** The server's icon texture, or null while it has none (not pinged yet, or no icon at all). */
    private static Identifier texture(ServerData server) {
        byte[] bytes = server.getIconBytes();
        if (bytes == null) return null;
        Icon icon = ICONS.computeIfAbsent(server.ip,
            ip -> new Icon(FaviconTexture.forServer(Minecraft.getInstance().getTextureManager(), ip)));
        if (!Arrays.equals(icon.bytes, bytes)) {
            icon.bytes = bytes;
            try {
                icon.texture.upload(NativeImage.read(bytes));
                icon.ok = true;
            } catch (Exception e) {
                // Not a 64x64 png: vanilla clears it too.
                icon.texture.clear();
                icon.ok = false;
            }
        }
        return icon.ok ? icon.texture.textureLocation() : null;
    }

    /** The icon at {@code x, y}, or a dim square holding its place until there is one. */
    static void draw(GuiGraphicsExtractor g, ServerData server, int x, int y) {
        Identifier texture = texture(server);
        if (texture != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
        } else {
            g.fill(x, y, x + SIZE, y + SIZE, 0x30FFFFFF);
        }
    }
}
