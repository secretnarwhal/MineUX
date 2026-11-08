package com.mineux.core.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A screen of the desktop's, drawn at the desk scale ({@link DeskScale}) rather than the game's
 * GUI scale: the desktop itself, the greeter, an extension's menu (the screens menu).
 * <p>
 * It is laid out with {@code width} and {@code height} the desk's, draws through a pose scaled
 * up or down to the screen, and every mouse position, click, drag and scroll reaches it in desk
 * units -- so its own code, and vanilla widgets on it, never see the GUI scale at all. The
 * game's entry points are final here and hand over to the {@code desk...} methods, which a
 * screen overrides instead (calling {@code super.desk...} where it would have called
 * {@code super}). When the desk's size changes (the setting, the window) it is laid out again
 * before its next frame.
 * <p>
 * Anything the game draws without the pose -- the 3D model previews, which take GUI corners --
 * goes through {@link DeskScale#onScreen}.
 */
public abstract class DeskScreen extends Screen {

    protected DeskScreen(Component title) {
        super(title);
    }

    // ---- layout ---------------------------------------------------------

    @Override
    protected final void init() {
        width = DeskScale.width();
        height = DeskScale.height();
        deskInit();
    }

    /** {@code init}, with {@code width} and {@code height} the desk's. */
    protected void deskInit() {
    }

    /** Laid out again when the desk's size is not what it was laid out at. */
    private void sync() {
        if (width != DeskScale.width() || height != DeskScale.height()) rebuildWidgets();
    }

    // ---- drawing --------------------------------------------------------

    @Override
    public final void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        sync();
        g.pose().pushMatrix();
        try {
            scale(g, DeskScale.factor());
            deskBackground(g, deskMouseX(mouseX), deskMouseY(mouseY), delta);
        } finally {
            g.pose().popMatrix();
        }
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        sync();
        g.pose().pushMatrix();
        try {
            scale(g, DeskScale.factor());
            int mx = deskMouseX(mouseX);
            int my = deskMouseY(mouseY);
            deskRender(g, mx, my, delta);
            // Tooltips and the like that widgets left for the end of the frame, while the pose
            // and the cursor are still the desk's.
            g.extractDeferredElements(mx, my, delta);
        } finally {
            g.pose().popMatrix();
        }
    }

    private static void scale(GuiGraphicsExtractor g, float f) {
        if (f != 1f) g.pose().scale(f, f);
    }

    /**
     * The cursor in desk units for a frame given {@code mouseX} in GUI units. Instance methods:
     * a static (int)->int that reads the mouse is what PureCheck keeps out of the obfuscated code.
     */
    private int deskMouseX(int mouseX) {
        return ScaleMath.deskPixel(DeskScale.cursorX(mouseX));
    }

    private int deskMouseY(int mouseY) {
        return ScaleMath.deskPixel(DeskScale.cursorY(mouseY));
    }

    /** {@code extractBackground}, in desk units. */
    protected void deskBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
    }

    /** {@code extractRenderState}, in desk units. */
    protected void deskRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    // ---- the mouse ------------------------------------------------------

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return deskClicked(DeskScale.in(event), doubleClick);
    }

    protected boolean deskClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent event) {
        return deskReleased(DeskScale.in(event));
    }

    protected boolean deskReleased(MouseButtonEvent event) {
        return super.mouseReleased(event);
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return deskDragged(DeskScale.in(event), DeskScale.inX(dragX), DeskScale.inY(dragY));
    }

    protected boolean deskDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return deskScrolled(DeskScale.inX(mouseX), DeskScale.inY(mouseY), horizontalAmount, verticalAmount);
    }

    protected boolean deskScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public final void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(DeskScale.inX(mouseX), DeskScale.inY(mouseY));
    }

    @Override
    public final boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(DeskScale.inX(mouseX), DeskScale.inY(mouseY));
    }
}
