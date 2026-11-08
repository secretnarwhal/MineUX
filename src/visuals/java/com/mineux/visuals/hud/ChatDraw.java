package com.mineux.visuals.hud;

import com.mineux.visuals.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The game's chat drawing, with each line's background in the client's glass (or none, or the
 * game's), an accent down its left edge if asked, and a new line sliding in from the left and
 * fading up. Which line is being drawn comes from {@link ChatSkin#line}; a line's background is
 * the fill the game starts at x = -4.
 */
final class ChatDraw implements ChatComponent.ChatGraphicsAccess {

    private final ChatComponent.ChatGraphicsAccess inner;
    private final Shift shift = new Shift();

    ChatDraw(ChatComponent.ChatGraphicsAccess inner) {
        this.inner = inner;
    }

    @Override
    public void updatePose(Consumer<Matrix3x2f> updater) {
        inner.updatePose(updater);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int color) {
        if (x0 != 0 - 4) {
            inner.fill(x0, y0, x1, y1, color);
            return;
        }
        SkinConfig config = SkinConfig.get();
        float entry = entry(config);
        int dx = Math.round(slide(config, entry));
        float alpha = lineAlpha();
        float fade = fade(config, entry);
        int a;
        int rgb;
        if (Objects.equals("none", config.chatBackground)) {
            a = 0;
            rgb = 0;
        } else if (Objects.equals("vanilla", config.chatBackground)) {
            a = color >>> 24;
            rgb = color & 0xFFFFFF;
        } else {
            a = Math.round(255.0F * config.chatOpacity * alpha);
            rgb = Hotbar.SURFACE;
        }
        a = Math.round(a * fade);
        if (a > 0) inner.fill(x0 + dx, y0, x1 + dx, y1, (a << 24) | rgb);
        if (config.chatAccentBar) {
            int edge = Math.round(220.0F * alpha * fade);
            if (edge > 0) inner.fill(x0 + dx, y0, x0 + dx + 1, y1, (edge << 24) | (Theme.GOLD & 0xFFFFFF));
        }
    }

    @Override
    public boolean handleMessage(int textTop, float opacity, FormattedCharSequence message) {
        SkinConfig config = SkinConfig.get();
        float entry = entry(config);
        float dx = slide(config, entry);
        float fade = fade(config, entry);
        if (dx == 0.0F) return inner.handleMessage(textTop, opacity * fade, message);
        shift.dx = dx;
        inner.updatePose(shift);
        try {
            return inner.handleMessage(textTop, opacity * fade, message);
        } finally {
            shift.dx = 0.0F - dx;
            inner.updatePose(shift);
        }
    }

    @Override
    public void handleTag(int x0, int y0, int x1, int y1, float opacity, GuiMessageTag tag) {
        inner.handleTag(x0, y0, x1, y1, opacity, tag);
    }

    @Override
    public void handleTagIcon(int left, int bottom, boolean forceVisible, GuiMessageTag tag, GuiMessageTag.Icon icon) {
        inner.handleTagIcon(left, bottom, forceVisible, tag, icon);
    }

    /** How far in the line being drawn is, 0 just arrived to 1 settled. */
    private static float entry(SkinConfig config) {
        GuiMessage.Line line = ChatSkin.line;
        if (line == null || Objects.equals("none", config.chatAnimation)) return 1.0F;
        Minecraft mc = Minecraft.getInstance();
        float age = mc.gui.hud.getGuiTicks() - line.addedTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float ticks = Math.max(1.0F, 8.0F / Math.max(0.2F, config.chatSpeed));
        return Strokes.easeOut(age / ticks);
    }

    private static float slide(SkinConfig config, float entry) {
        return Objects.equals("slide", config.chatAnimation) ? (entry - 1.0F) * 40.0F : 0.0F;
    }

    private static float fade(SkinConfig config, float entry) {
        return Objects.equals("none", config.chatAnimation) ? 1.0F : entry;
    }

    /** The game's own fade of the line being drawn: gone over its tenth second, unless chat is open. */
    private static float lineAlpha() {
        GuiMessage.Line line = ChatSkin.line;
        Minecraft mc = Minecraft.getInstance();
        if (line == null || mc.gui.hud.getChat().isChatFocused()) return 1.0F;
        double left = (1.0 - (mc.gui.hud.getGuiTicks() - line.addedTime()) / 200.0) * 10.0;
        double clamped = Math.max(0.0, Math.min(1.0, left));
        return (float) (clamped * clamped);
    }

    /** Moves the pose sideways by {@link #dx}: a named class, for the obfuscator's sake. */
    private static final class Shift implements Consumer<Matrix3x2f> {
        float dx;

        @Override
        public void accept(Matrix3x2f pose) {
            pose.translate(dx, 0.0F);
        }
    }
}
