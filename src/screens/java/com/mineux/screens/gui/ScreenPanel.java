package com.mineux.screens.gui;

import com.mineux.screens.Screens;
import com.mineux.screens.ScreensConfig;
import com.mineux.screens.displays.Anchor;
import com.mineux.screens.displays.CapturedScreenDisplay;
import com.mineux.screens.math.ViewFrame;
import com.mineux.screens.net.Sharing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;

import static com.mineux.screens.gui.CaptureManagerScreen.GAP;
import static com.mineux.screens.gui.CaptureManagerScreen.HEADING_HEIGHT;
import static com.mineux.screens.gui.CaptureManagerScreen.ROW_HEIGHT;
import static com.mineux.screens.gui.CaptureManagerScreen.SCROLLBAR_SPACE;

/** The menu's right column: your screens, the selected one's controls, sharing, and the settings for all. */
final class ScreenPanel {

    private static final int MIN_SIZE = 25;
    private static final int MAX_SIZE = 400;
    private static final int MIN_OPACITY = 10;
    private static final int MAX_VISIBLE_SCREENS = 3;

    private final CaptureManagerScreen menu;
    private int screenListY;
    private int screenListHeight;
    private int previewX, previewY, previewWidth, previewHeight;
    /** What the panel was built for: a rebuild when sharing or the viewer counts change. */
    private String builtFor = "";
    /** A whole monitor waiting for its second click to be shared. */
    private CapturedScreenDisplay confirming;

    ScreenPanel(CaptureManagerScreen menu) {
        this.menu = menu;
    }

    void init() {
        builtFor = state();
        int x = menu.rightX;
        int w = menu.columnWidth;
        int y = menu.contentY + HEADING_HEIGHT;
        CapturedScreenDisplay selected = menu.selected;
        boolean active = selected != null;
        screenList(x, y, w);
        y += screenListHeight + GAP * 2;
        y = actions(x, y, w, active) + ROW_HEIGHT + GAP;
        y = anchors(x, y, w, active) + ROW_HEIGHT + GAP;
        y = sliders(x, y, w, active) + ROW_HEIGHT + GAP;
        y = shareRow(x, y, w, active) + ROW_HEIGHT + GAP * 2;
        y = globals(x, y, w) + ROW_HEIGHT + GAP * 2;
        previewX = x;
        previewY = y;
        previewWidth = w;
        previewHeight = menu.contentBottom - y;
    }

    private void screenList(int x, int y, int w) {
        screenListY = y;
        screenListHeight = Math.max(1, Math.min(menu.manager.displays.size(), MAX_VISIBLE_SCREENS)) * (ROW_HEIGHT + 2) + 2;
        if (menu.manager.displays.isEmpty()) return;
        LinearLayout content = LinearLayout.vertical().spacing(2);
        for (CapturedScreenDisplay display : menu.manager.displays) {
            String detail = display.shared ? "LIVE " + display.viewers : Anchor.title(display.anchorMode());
            content.addChild(new GlassButton(w - SCROLLBAR_SPACE - 4, ROW_HEIGHT, Component.literal(display.target.title()), () -> {
                menu.selected = display;
                menu.rebuild();
            }).row(detail).dot(display.shared ? GlassTheme.RED : GlassTheme.GREEN)
                .style(display == menu.selected ? GlassButton.SELECTED : GlassButton.NORMAL));
        }
        ScrollableLayout list = new ScrollableLayout(Minecraft.getInstance(), content, screenListHeight - 4);
        list.setPosition(x + 2, y + 2);
        list.arrangeElements();
        list.visitWidgets(menu::add);
    }

    private int actions(int x, int y, int w, boolean active) {
        int third = (w - GAP * 2) / 3;
        menu.add(new GlassButton(x, y, third, ROW_HEIGHT, Component.literal("Move"), this::onMove).active(active))
            .setTooltip(Tooltip.create(Component.literal("Pick the screen up. It follows your view until you click to place it.\nScroll: closer / farther\nHold right click: slide it up, down, left and right\nAlt: attach to walls, Ctrl: snap to other screens")));
        menu.add(new GlassButton(x + third + GAP, y, third, ROW_HEIGHT, Component.literal("Bring here"), this::onBringHere).active(active))
            .setTooltip(Tooltip.create(Component.literal("Place the screen in front of you")));
        menu.add(new GlassButton(x + (third + GAP) * 2, y, w - (third + GAP) * 2, ROW_HEIGHT, Component.literal("Remove"), this::onRemove)
            .style(GlassButton.DANGER).active(active));
        return y;
    }

    private int anchors(int x, int y, int w, boolean active) {
        int segment = (w - GAP * (Anchor.COUNT - 1)) / Anchor.COUNT;
        for (int i = 0; i < Anchor.COUNT; i++) {
            int mode = i;
            int sx = x + (segment + GAP) * i;
            int sw = i == Anchor.COUNT - 1 ? w - (segment + GAP) * i : segment;
            boolean on = active && menu.selected.anchorMode() == mode;
            menu.add(new GlassButton(sx, y, sw, ROW_HEIGHT, Component.literal(Anchor.title(mode)), () -> onAnchor(mode))
                .style(on ? GlassButton.SELECTED : GlassButton.NORMAL).active(active))
                .setTooltip(Tooltip.create(Component.literal(Anchor.help(mode))));
        }
        return y;
    }

    private int sliders(int x, int y, int w, boolean active) {
        CapturedScreenDisplay s = menu.selected;
        int half = (w - GAP) / 2;
        menu.add(new GlassSlider(x, y, half, ROW_HEIGHT, "Size", MIN_SIZE, MAX_SIZE, active ? (int) Math.round(s.sizeScale * 100) : 100,
            value -> value + "%", value -> { if (menu.selected != null) menu.selected.sizeScale = value / 100.0; }).active(active))
            .setTooltip(Tooltip.create(Component.literal("How big the screen is in the world")));
        menu.add(new GlassSlider(x + half + GAP, y, w - half - GAP, ROW_HEIGHT, "Opacity", MIN_OPACITY, 100, active ? (int) Math.round(s.opacity * 100) : 100,
            value -> value + "%", value -> { if (menu.selected != null) menu.selected.opacity = value / 100.0; }).active(active))
            .setTooltip(Tooltip.create(Component.literal("Lower values make the screen see-through")));
        return y;
    }

    private int shareRow(int x, int y, int w, boolean active) {
        Sharing sharing = Screens.sharing();
        String why = sharing.unavailable();
        CapturedScreenDisplay s = menu.selected;
        boolean live = active && s.shared;
        String label = live ? "LIVE: shared with friends here" : confirming != null && confirming == s ? "Click again to share the whole monitor"
            : java.util.Objects.equals(Sharing.SIGNED_OUT, why) ? "Friends only: sign in to MineUX" : "Share with friends here";
        GlassButton share = new GlassButton(x, y, w, ROW_HEIGHT, Component.literal(label), this::onShare)
            .toggle(live).style(live || (confirming != null && confirming == s) ? GlassButton.LIVE : GlassButton.NORMAL)
            .active(active && (live || why == null));
        if (live) share.row(s.viewers + (s.viewers == 1 ? " watching" : " watching")).toggle(true);
        String help = "Seen only by friends on this server who have screens enabled with you (both of you turn on \"enable screens\" on each other's profile in the friends app), within 96 blocks. Off by default for every screen and never saved; only the picture and its place are sent, no window titles. Nothing is sent while nobody watches or while the game is minimised.";
        share.setTooltip(Tooltip.create(Component.literal(why != null ? why : help)));
        menu.add(share);
        return y;
    }

    private int globals(int x, int y, int w) {
        ScreensConfig config = ScreensConfig.get();
        int half = (w - GAP) / 2;
        String clickableHelp = "Clicking a screen switches to its window and puts the mouse cursor where you clicked. It never clicks or types into the window.";
        if (menu.manager.isReturnHotkeyRegistered()) clickableHelp += "\nPress " + menu.manager.returnHotkeyName() + " to return to Minecraft.";
        menu.add(new GlassButton(x, y, half, ROW_HEIGHT, Component.literal("Clickable"), () -> {
            config.clickable = !config.clickable;
            config.commit();
            menu.rebuild();
        }).toggle(config.clickable)).setTooltip(Tooltip.create(Component.literal(clickableHelp)));
        menu.add(new GlassButton(x + half + GAP, y, w - half - GAP, ROW_HEIGHT, Component.literal("Draggable"), () -> {
            config.draggable = !config.draggable;
            config.commit();
            menu.rebuild();
        }).toggle(config.draggable)).setTooltip(Tooltip.create(Component.literal("Clicking a screen picks it up so you can move it, click again to place it. Only while Clickable is off; screens can always be picked up in freecam.")));
        y += ROW_HEIGHT + GAP;
        menu.add(new GlassSlider(x, y, w, ROW_HEIGHT, "Curvature", 0, 100, config.curvature,
            value -> value == 0 ? "Flat" : value + "%", value -> { config.curvature = value; config.commit(); }))
            .setTooltip(Tooltip.create(Component.literal("Bend all screens around you. 100% is a half circle.")));
        y += ROW_HEIGHT + GAP;
        menu.add(new GlassSlider(x, y, half, ROW_HEIGHT, "Density", ScreensConfig.MIN_PIXELS_PER_BLOCK, ScreensConfig.MAX_PIXELS_PER_BLOCK, config.pixelsPerBlock,
            value -> value + " px", value -> { config.pixelsPerBlock = value; config.commit(); }))
            .setTooltip(Tooltip.create(Component.literal("How many pixels of a screen fit on one block. Higher values make all screens smaller.")));
        menu.add(new GlassSlider(x + half + GAP, y, w - half - GAP, ROW_HEIGHT, "Capture", 1, ScreensConfig.MAX_CAPTURE_FPS, config.captureFps,
            value -> value + " fps", value -> { config.captureFps = value; config.commit(); }))
            .setTooltip(Tooltip.create(Component.literal("How often screens are updated at most. Lower values use less CPU and GPU time.")));
        return y;
    }

    // ---- actions --------------------------------------------------------

    private void onMove() {
        if (menu.selected == null) return;
        menu.manager.pointer.startGrab(menu.selected);
        menu.onClose();
    }

    private void onBringHere() {
        if (menu.selected == null) return;
        menu.manager.placeInFront(menu.selected);
        menu.setStatus("Moved " + menu.selected.target.title() + " in front of you", GlassTheme.TEXT_DIM);
    }

    private void onAnchor(int mode) {
        Minecraft mc = Minecraft.getInstance();
        if (menu.selected == null || mc.player == null) return;
        menu.selected.setAnchorMode(mode, ViewFrame.of(mc.player));
        menu.rebuild();
    }

    private void onRemove() {
        if (menu.selected == null) return;
        menu.manager.stopCapture(menu.selected);
        menu.selected = menu.manager.displays.isEmpty() ? null : menu.manager.displays.getLast();
        menu.rebuild();
    }

    private void onShare() {
        CapturedScreenDisplay s = menu.selected;
        if (s == null) return;
        Sharing sharing = Screens.sharing();
        if (s.shared) {
            sharing.unshare(s);
            confirming = null;
            menu.setStatus("Stopped sharing " + s.target.title(), GlassTheme.TEXT_DIM);
        } else {
            String why = sharing.share(s, confirming == s);
            if (why == null) {
                confirming = null;
                menu.setStatus("LIVE: " + s.target.title() + " is shared with your friends here who have screens enabled", GlassTheme.RED);
            } else if (why.startsWith("Confirm")) {
                confirming = s;
                menu.setStatus(why.substring(9) + ". Click again to share it.", GlassTheme.RED);
            } else {
                menu.setStatus(why, GlassTheme.RED);
            }
        }
        menu.rebuild();
    }

    // ---- drawing --------------------------------------------------------

    boolean changed() {
        return !state().equals(builtFor);
    }

    private String state() {
        StringBuilder b = new StringBuilder();
        for (CapturedScreenDisplay d : menu.manager.displays) b.append(d.shared ? 'L' : '-').append(d.viewers).append(',');
        b.append(Screens.sharing().unavailable() == null);
        return b.toString();
    }

    void background(GuiGraphicsExtractor g) {
        Font font = Minecraft.getInstance().font;
        GlassTheme.heading(g, font, "Your screens", menu.rightX + 2, menu.contentY);
        GlassTheme.well(g, menu.rightX, screenListY, menu.columnWidth, screenListHeight);
        if (menu.manager.displays.isEmpty()) {
            String hint = GlassTheme.fit(font, "Pick a monitor or window on the left", menu.columnWidth - 12);
            GlassTheme.text(g, font, hint, menu.rightX + 6, screenListY + (screenListHeight - font.lineHeight) / 2 + 1, GlassTheme.TEXT_MUTED);
        }
    }

    /** The selected screen scaled into what is left of the column, with a LIVE tag when shared. */
    void preview(GuiGraphicsExtractor g) {
        CapturedScreenDisplay s = menu.selected;
        if (s == null || !s.framebuffer.isValid() || previewHeight < 24) return;
        int bw = s.framebuffer.width();
        int bh = s.framebuffer.height();
        float scale = Math.min((float) (previewWidth - 2) / bw, (float) (previewHeight - 2) / bh);
        int w = Math.max(1, (int) (bw * scale));
        int h = Math.max(1, (int) (bh * scale));
        int x = previewX + (previewWidth - w) / 2;
        g.outline(x - 1, previewY, w + 2, h + 2, s.shared ? GlassTheme.RED : GlassTheme.EDGE);
        g.blit(s.framebuffer.location(), x, previewY + 1, x + w, previewY + 1 + h, 0.0f, 1.0f, 0.0f, 1.0f);
        if (!s.shared) return;
        Font font = Minecraft.getInstance().font;
        String tag = "LIVE " + s.viewers;
        g.fill(x + 2, previewY + 3, x + 8 + font.width(tag), previewY + 14, 0xE0C0141E);
        g.text(font, tag, x + 5, previewY + 5, 0xFFFFFFFF, false);
    }
}
