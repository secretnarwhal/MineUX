package com.mineux.screens;

import com.mineux.screens.capture.CaptureManager;
import com.mineux.screens.capture.Freecam;
import com.mineux.screens.gui.CaptureManagerScreen;
import com.mineux.screens.gui.HoverIndicator;
import com.mineux.screens.gui.LiveHud;
import com.mineux.screens.render.WorldDraw;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;


/**
 * Every way the game reaches Screens, one static method per hook: MineUX's screens mixins
 * (com.mineux.screens.fabric.mixin) call these, and a host mod that builds Screens into itself could too.
 * Each does nothing before {@link Screens#install}.
 */
public final class ScreensHooks {

    private static boolean inWorld;

    private ScreensHooks() {}

    private static boolean on() {
        return Screens.keys() != null && ScreensConfig.get().enabled;
    }

    /** Head of {@code Minecraft.runTick}: every frame, in and out of a world. */
    public static void frame() {
        if (Screens.keys() == null) return;
        CaptureManager manager = Screens.manager();
        Minecraft mc = Minecraft.getInstance();
        if (manager != null && (on() || !manager.displays.isEmpty())) {
            if (!on()) manager.stopAll();
            else manager.update();
        }
        Screens.sharing().remotes.frame(mc, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    /** Tail of the client tick. */
    public static void tick(Minecraft mc) {
        if (Screens.keys() == null) return;
        boolean now = mc.level != null && mc.player != null;
        CaptureManager manager = Screens.manager();
        // Left the world: every capture stops, as the original mod did on disconnect.
        if (inWorld && !now && manager != null) manager.stopAll();
        inWorld = now;
        Screens.sharing().tick(mc);
        if (!now) return;
        if (manager == null || !on()) return;
        if (Screens.keys().menuPressed(mc)) openMenu(mc);
        if (Screens.keys().freecamPressed(mc)) manager.toggleFreecam();
    }

    /** The menu, from the key or {@code screens open}. */
    public static void openMenu(Minecraft mc) {
        CaptureManager manager = Screens.manager();
        if (manager == null || mc.player == null) return;
        manager.pointer.releaseGrab();
        mc.gui.setScreen(new CaptureManagerScreen(manager));
    }

    /** Return of {@code Minecraft.pick}: a screen under the crosshair hides the block behind it. */
    public static void afterPick(Minecraft mc, float partialTicks) {
        CaptureManager manager = Screens.manager();
        if (manager == null) return;
        manager.pointer.worldTarget = mc.hitResult;
        if (!manager.pointer.blockWorldTargeting || mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition(partialTicks);
        mc.hitResult = BlockHitResult.miss(eye, Direction.UP, BlockPos.containing(eye));
        mc.crosshairPickEntity = null;
    }

    /** Tail of the level's extraction, every frame in a world. */
    public static void extracted(Camera camera) {
        CaptureManager manager = Screens.manager();
        if (manager != null && on()) manager.pointer.updateWorld(camera);
    }

    /** In the level's feature submit, before its gizmos: our screens go in with the rest. */
    public static void submit(SubmitNodeCollector collector, Vec3 camera) {
        if (on()) WorldDraw.submit(collector, camera);
    }

    /** Tail of the HUD: the hover marks (with the HUD shown) and the LIVE line (always). */
    public static void hud(GuiGraphicsExtractor g, boolean hidden) {
        if (Screens.keys() == null) return;
        if (!hidden) HoverIndicator.extract(g);
        LiveHud.extract(g);
    }

    // ---- input and the freecam ------------------------------------------

    public static boolean mouseButton(int button, int action) {
        CaptureManager m = Screens.manager();
        return m != null && on() && m.pointer.onButtonPress(button, action);
    }

    public static boolean mouseScroll(double amount) {
        CaptureManager m = Screens.manager();
        return m != null && on() && m.pointer.onScroll(amount);
    }

    public static boolean mouseTurn(double dx, double dy) {
        CaptureManager m = Screens.manager();
        return m != null && on() && m.pointer.onMouseTurn(dx, dy);
    }

    /** The freecam while it flies, else null. */
    /**
     * Head of {@code EntityRenderDispatcher#shouldRender}: while the screens freecam flies, no
     * other player is drawn, only you. Arranging screens needs nobody else, and a camera that can
     * go anywhere must not show anyone the player could not see.
     */
    public static boolean hidesEntity(Object entity) {
        if (freecam() == null || !net.minecraft.world.entity.player.Player.class.isInstance(entity)) return false;
        return entity != net.minecraft.client.Minecraft.getInstance().player;
    }

    public static @Nullable Freecam freecam() {
        CaptureManager m = Screens.manager();
        return m != null && m.freecam.isActive() ? m.freecam : null;
    }
}
