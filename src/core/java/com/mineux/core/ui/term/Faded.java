package com.mineux.core.ui.term;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * A window drawn see-through ({@link Pane#opacity}, SUPER+K and SUPER+L): the game's GUI has no
 * alpha for a whole drawing, so this stands in for the graphics a pane draws with and takes its
 * opacity off every fill, gradient, line of text and texture as it is handed on -- into the same
 * render state, from the same place ({@link #pose} copied), so it lands where it would have.
 * Only the calls everything else ends in are overridden, so nothing is faded twice. An item
 * picture stays solid; nothing in the panes draws one where it matters.
 */
final class Faded extends GuiGraphicsExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/term");
    private static Field stateField;
    private static boolean broken;

    private final GuiGraphicsExtractor under;
    private final float alpha;

    private Faded(GuiGraphicsExtractor under, GuiRenderState state, int mouseX, int mouseY, float alpha) {
        super(Minecraft.getInstance(), state, mouseX, mouseY);
        this.under = under;
        this.alpha = alpha;
        pose().set(under.pose());
    }

    /** {@code g} at {@code alpha}; {@code g} itself where that cannot be done. */
    static GuiGraphicsExtractor of(GuiGraphicsExtractor g, int mouseX, int mouseY, float alpha) {
        GuiRenderState state = state(g);
        if (state == null) return g;
        return new Faded(g, state, mouseX, mouseY, alpha);
    }

    private static GuiRenderState state(GuiGraphicsExtractor g) {
        if (broken) return null;
        try {
            if (stateField == null) {
                Field field = GuiGraphicsExtractor.class.getDeclaredField("guiRenderState");
                field.setAccessible(true);
                stateField = field;
            }
            return (GuiRenderState) stateField.get(g);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOGGER.warn("Window opacity is off: the GUI's render state could not be reached", e);
            return null;
        }
    }

    /** {@code argb} with this one's opacity taken off its alpha. */
    private int fade(int argb) {
        int a = argb >>> 24;
        return (Math.round(a * alpha) << 24) | (argb & 0xFFFFFF);
    }

    @Override
    public void fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int col) {
        super.fill(pipeline, x0, y0, x1, y1, fade(col));
    }

    @Override
    public void fillGradient(int x0, int y0, int x1, int y1, int col1, int col2) {
        super.fillGradient(x0, y0, x1, y1, fade(col1), fade(col2));
    }

    @Override
    public void text(Font font, FormattedCharSequence str, int x, int y, int color, boolean dropShadow) {
        int faded = fade(color);
        // Text this faint is dropped by the game rather than drawn faint; keep a trace of it.
        if ((color >>> 24) != 0 && (faded >>> 24) < 8) faded = (8 << 24) | (color & 0xFFFFFF);
        super.text(font, str, x, y, faded, dropShadow);
    }

    @Override
    public void blit(RenderPipeline renderPipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
                     int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color) {
        super.blit(renderPipeline, texture, x, y, u, v, width, height, srcWidth, srcHeight, textureWidth, textureHeight, fade(color));
    }

    @Override
    public void requestCursor(CursorType cursorType) {
        under.requestCursor(cursorType);
    }
}
