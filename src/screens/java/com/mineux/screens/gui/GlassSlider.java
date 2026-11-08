package com.mineux.screens.gui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

// Slider over a range of whole numbers in the glass style, see GlassTheme
public class GlassSlider extends AbstractSliderButton {

    private final String label;
    private final int min;
    private final int max;
    private final IntFunction<String> format;
    private final IntConsumer onChange;

    public GlassSlider(int x, int y, int width, int height, String label, int min, int max, int value, IntFunction<String> format, IntConsumer onChange) {
        super(x, y, width, height, Component.literal(label), (double) (Math.clamp(value, min, max) - min) / (max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.format = format;
        this.onChange = onChange;
        updateMessage();
    }

    public GlassSlider active(boolean active) {
        this.active = active;
        return this;
    }

    public int intValue() {
        return min + (int) Math.round(value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label + ": " + format.apply(intValue())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(intValue());
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        Font font = Minecraft.getInstance().font;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = active && isHoveredOrFocused();
        int accent = active ? GlassTheme.ACCENT : GlassTheme.TEXT_MUTED;

        // The filled part and the rest of the track are separate fills, translucent colors must not overlap
        int inner = w - 2;
        int filled = (int) Math.round(value * inner);

        g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, active ? GlassTheme.ACCENT_FAINT : GlassTheme.LIFT_SOFT);
        g.fill(x + 1 + filled, y + 1, x + w - 1, y + h - 1, GlassTheme.WELL);
        GlassTheme.ring(g, x, y, w, h, 2, hot ? GlassTheme.EDGE_HOT : active ? GlassTheme.EDGE : GlassTheme.EDGE_SOFT);

        // Handle
        int handleX = Math.clamp(x + 1 + filled - 1, x + 1, x + w - 3);
        g.fill(handleX, y + 2, handleX + 2, y + h - 2, accent);

        int textY = y + (h - font.lineHeight) / 2 + 1;
        String valueText = format.apply(intValue());
        GlassTheme.text(g, font, label, x + 6, textY, active ? GlassTheme.TEXT : GlassTheme.TEXT_MUTED);
        GlassTheme.text(g, font, valueText, x + w - 6 - font.width(valueText), textY, active ? GlassTheme.TEXT_DIM : GlassTheme.TEXT_MUTED);
    }

}
