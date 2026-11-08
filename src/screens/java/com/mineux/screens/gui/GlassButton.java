package com.mineux.screens.gui;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/* Button in the glass style, see GlassTheme
 *
 * Besides a plain centered label it can be a list row (left aligned label, status dot, dimmed detail text
 * on the right) or a toggle with a switch on the right.
 */
public class GlassButton extends AbstractWidget {

    // Styles, as ints: an obfuscator a host may run over this code, such as Skidfuscator, renames enum constants
    public static final int NORMAL = 0;
    // Currently selected or switched on
    public static final int SELECTED = 1;
    // Destructive action
    public static final int DANGER = 2;
    // Live: something is going out (a shared screen)
    public static final int LIVE = 3;

    private final Runnable onPress;

    private int style = NORMAL;
    private boolean leftAligned = false;
    private @Nullable String detail = null;
    private @Nullable Boolean toggleState = null;

    // Color of the status dot in front of the label, 0 for none
    private int dotColor = 0;

    public GlassButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public GlassButton(int width, int height, Component message, Runnable onPress) {
        this(0, 0, width, height, message, onPress);
    }

    public GlassButton style(int style) {
        this.style = style;
        return this;
    }

    // Make this a list row: the label is left aligned, `detail` is shown dimmed at the right edge
    public GlassButton row(@Nullable String detail) {
        this.leftAligned = true;
        this.detail = detail;
        return this;
    }

    public GlassButton dot(int color) {
        this.dotColor = color;
        return this;
    }

    // Show a switch in the given state at the right edge
    public GlassButton toggle(boolean state) {
        this.leftAligned = true;
        this.toggleState = state;
        return this;
    }

    public GlassButton active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onPress.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        Font font = Minecraft.getInstance().font;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = active && isHoveredOrFocused();

        int face = hot ? GlassTheme.LIFT_HOT : GlassTheme.LIFT;
        int edge = hot ? GlassTheme.EDGE_HOT : GlassTheme.EDGE;
        int textColor = GlassTheme.TEXT;

        if (style == SELECTED) {
            face = GlassTheme.ACCENT_FAINT;
            edge = GlassTheme.ACCENT_EDGE;
            textColor = GlassTheme.ACCENT;
        } else if (style == LIVE) {
            face = GlassTheme.RED_FAINT;
            edge = GlassTheme.RED_EDGE;
            textColor = GlassTheme.RED;
        } else if (style == DANGER) {
            textColor = GlassTheme.RED;
            if (hot) {
                face = GlassTheme.RED_FAINT;
                edge = GlassTheme.RED_EDGE;
            }
        }

        if (!active) {
            face = GlassTheme.LIFT_SOFT;
            edge = GlassTheme.EDGE_SOFT;
            textColor = GlassTheme.TEXT_MUTED;
        }

        GlassTheme.roundRect(g, x + 1, y + 1, w - 2, h - 2, 1, face);
        GlassTheme.ring(g, x, y, w, h, 2, edge);

        int textY = y + (h - font.lineHeight) / 2 + 1;
        int left = x + 6;
        int right = x + w - 6;

        if (toggleState != null) {
            right -= extractSwitch(g, right, y + h / 2, toggleState);
        }

        if (detail != null) {
            int detailWidth = Math.min(font.width(detail), (right - left) / 2);
            String shown = GlassTheme.fit(font, detail, detailWidth);
            GlassTheme.text(g, font, shown, right - font.width(shown), textY, GlassTheme.TEXT_MUTED);
            right -= font.width(shown) + 6;
        }

        if (dotColor != 0) {
            GlassTheme.roundRect(g, left, y + h / 2 - 2, 4, 4, 1, dotColor);
            left += 8;
        }

        String label = GlassTheme.fit(font, getMessage().getString(), right - left);
        int labelX = leftAligned ? left : left + (right - left - font.width(label)) / 2;
        GlassTheme.text(g, font, label, labelX, textY, textColor);
    }

    // Draw a switch ending at `right`, vertically centered on `centerY`. Returns the space it takes up.
    private int extractSwitch(GuiGraphicsExtractor g, int right, int centerY, boolean on) {
        final int width = 18;
        final int height = 8;

        int x = right - width;
        int y = centerY - height / 2;

        GlassTheme.roundRect(g, x + 1, y + 1, width - 2, height - 2, 3, on ? GlassTheme.ACCENT_FAINT : GlassTheme.WELL);
        GlassTheme.ring(g, x, y, width, height, 4, on ? GlassTheme.ACCENT_EDGE : GlassTheme.EDGE);

        int knobX = on ? x + width - height + 1 : x + 2;
        GlassTheme.roundRect(g, knobX, y + 2, height - 3, height - 4, 2, on ? GlassTheme.ACCENT : GlassTheme.TEXT_DIM);

        return width + 6;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

}
