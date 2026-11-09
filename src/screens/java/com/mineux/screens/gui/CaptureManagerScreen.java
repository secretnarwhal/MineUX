package com.mineux.screens.gui;

import com.mineux.core.ui.DeskScreen;
import com.mineux.screens.Screens;
import com.mineux.screens.bridge.CaptureTarget;
import com.mineux.screens.capture.CaptureManager;
import com.mineux.screens.displays.CapturedScreenDisplay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The screens menu, in the glass style ({@link GlassTheme}). Left: monitors and windows that can
 * be captured. Right ({@link ScreenPanel}): your screens, their placement, sharing and the
 * settings that apply to all of them. Drawn at the desk scale, as the desktop is ({@link DeskScreen}).
 */
public class CaptureManagerScreen extends DeskScreen {

    static final int PADDING = 8;
    static final int HEADER_HEIGHT = 24;
    static final int FOOTER_HEIGHT = 14;
    static final int HEADING_HEIGHT = 12;
    static final int ROW_HEIGHT = 18;
    static final int GAP = 3;
    /** Width a scrollable list keeps free for its scrollbar. */
    static final int SCROLLBAR_SPACE = 10;

    final CaptureManager manager;
    private final ScreenPanel panel;

    private CaptureTarget[] monitors;
    private CaptureTarget[] windows;

    @Nullable CapturedScreenDisplay selected = null;

    /** Result of the last action, shown in the footer. */
    private @Nullable String status = null;
    private int statusColor = GlassTheme.TEXT_DIM;

    int cardX, cardY, cardWidth, cardHeight;
    int leftX, rightX, columnWidth, contentY, contentBottom;
    private int sourceListY;

    public CaptureManagerScreen(CaptureManager manager) {
        super(Component.literal("Screens"));
        this.manager = manager;
        this.panel = new ScreenPanel(this);
        refreshTargets();
        if (!manager.displays.isEmpty()) selected = manager.displays.getLast();
    }

    private void refreshTargets() {
        monitors = manager.monitors();
        windows = manager.windows();
    }

    @Override
    protected void deskInit() {
        cardWidth = Math.min(width - 16, 560);
        cardHeight = Math.min(height - 16, 352);
        cardX = (width - cardWidth) / 2;
        cardY = (height - cardHeight) / 2;
        columnWidth = (cardWidth - PADDING * 3) / 2;
        leftX = cardX + PADDING;
        rightX = leftX + columnWidth + PADDING;
        contentY = cardY + HEADER_HEIGHT + PADDING;
        contentBottom = cardY + cardHeight - FOOTER_HEIGHT - PADDING;
        if (selected != null && !manager.displays.contains(selected)) selected = null;
        initHeader();
        initSourceList();
        panel.init();
    }

    <T extends net.minecraft.client.gui.components.AbstractWidget> T add(T widget) {
        return addRenderableWidget(widget);
    }

    void rebuild() {
        rebuildWidgets();
    }

    private void initHeader() {
        int buttonHeight = 16;
        int y = cardY + (HEADER_HEIGHT - buttonHeight) / 2 + 1;
        int x = cardX + cardWidth - PADDING - 52;
        addRenderableWidget(new GlassButton(x, y, 52, buttonHeight, Component.literal("Refresh"), () -> {
            refreshTargets();
            rebuildWidgets();
        })).setTooltip(Tooltip.create(Component.literal("Look for new monitors and windows")));
        String freecamKey = com.mineux.screens.Keys.freecamName();
        x -= 56 + GAP;
        addRenderableWidget(new GlassButton(x, y, 56, buttonHeight, Component.literal("Freecam"), () -> {
            manager.toggleFreecam();
            onClose();
        }).style(manager.freecam.isActive() ? GlassButton.SELECTED : GlassButton.NORMAL))
            .setTooltip(Tooltip.create(Component.literal("Leave your body and fly around to arrange screens from any angle. Click a screen to pick it up.\nYou can also press " + freecamKey + " at any time.")));
    }

    private void initSourceList() {
        sourceListY = contentY + HEADING_HEIGHT;
        int rowWidth = columnWidth - SCROLLBAR_SPACE - 4;
        LinearLayout content = LinearLayout.vertical().spacing(2);
        content.addChild(new StringWidget(Component.literal("Monitors").withColor(GlassTheme.TEXT_DIM & 0xFFFFFF), font));
        for (CaptureTarget monitor : monitors) {
            String detail = monitor.width() + "x" + monitor.height() + (monitor.primary() ? ", primary" : "");
            content.addChild(sourceRow(monitor, monitor.title(), detail, rowWidth, null));
        }
        content.addChild(new StringWidget(Component.literal("Windows").withColor(GlassTheme.TEXT_DIM & 0xFFFFFF), font));
        for (CaptureTarget window : windows) {
            String tooltip = window.title() + "\n" + window.detail() + " - " + window.width() + "x" + window.height();
            if (window.blocked()) tooltip += "\nRuns as administrator. Windows may block capturing it or switching to it.";
            content.addChild(sourceRow(window, window.title(), window.blocked() ? "admin" : null, rowWidth, tooltip));
        }
        ScrollableLayout list = new ScrollableLayout(minecraft, content, contentBottom - sourceListY - 4);
        list.setPosition(leftX + 2, sourceListY + 2);
        list.arrangeElements();
        list.visitWidgets(this::addRenderableWidget);
    }

    private GlassButton sourceRow(CaptureTarget target, String label, @Nullable String detail, int width, @Nullable String tooltip) {
        CapturedScreenDisplay existing = manager.find(target);
        GlassButton row = new GlassButton(width, ROW_HEIGHT, Component.literal(label), () -> onSourcePressed(target)).row(detail);
        if (existing != null) {
            row.dot(existing.shared ? GlassTheme.RED : GlassTheme.GREEN);
            if (existing == selected) row.style(GlassButton.SELECTED);
        }
        if (tooltip != null) row.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return row;
    }

    private void onSourcePressed(CaptureTarget target) {
        CapturedScreenDisplay existing = manager.find(target);
        if (existing != null) {
            selected = existing;
            rebuildWidgets();
            return;
        }
        try {
            selected = manager.startCapture(target);
            setStatus("Showing " + target.title(), GlassTheme.TEXT_DIM);
        } catch (RuntimeException e) {
            setStatus("Couldn't capture " + target.title() + ": " + e.getMessage(), GlassTheme.RED);
        }
        rebuildWidgets();
    }

    void setStatus(@Nullable String text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // Captures can stop at any time (a window closed); viewer counts change.
        if (selected != null && !manager.displays.contains(selected)) {
            selected = null;
            rebuildWidgets();
        } else if (panel.changed()) {
            rebuildWidgets();
        }
    }

    @Override
    protected void deskBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        extractBlurredBackground(g);
        g.fillGradient(0, 0, width, height, GlassTheme.SCRIM_TOP, GlassTheme.SCRIM_BOTTOM);
        GlassTheme.card(g, cardX, cardY, cardWidth, cardHeight);
        GlassTheme.text(g, font, title.getString(), cardX + PADDING + 2, cardY + (HEADER_HEIGHT - font.lineHeight) / 2 + 1, GlassTheme.ACCENT);
        GlassTheme.divider(g, cardX + 1, cardY + HEADER_HEIGHT, cardWidth - 2);
        GlassTheme.heading(g, font, "Add a screen", leftX + 2, contentY);
        GlassTheme.well(g, leftX, sourceListY, columnWidth, contentBottom - sourceListY);
        panel.background(g);
        int footerY = cardY + cardHeight - FOOTER_HEIGHT - 1;
        GlassTheme.divider(g, cardX + 1, footerY - 2, cardWidth - 2);
        String footer = status;
        int color = statusColor;
        if (footer == null) {
            footer = com.mineux.screens.Keys.menuName() + ": this menu   "
                + com.mineux.screens.Keys.freecamName() + ": freecam";
            if (manager.isReturnHotkeyRegistered()) footer += "   " + manager.returnHotkeyName() + ": back to Minecraft";
            color = GlassTheme.TEXT_MUTED;
        }
        GlassTheme.text(g, font, GlassTheme.fit(font, footer, cardWidth - PADDING * 2 - 4), cardX + PADDING + 2, footerY + 3, color);
    }

    @Override
    protected void deskRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        super.deskRender(g, mouseX, mouseY, partialTicks);
        panel.preview(g);
    }
}
