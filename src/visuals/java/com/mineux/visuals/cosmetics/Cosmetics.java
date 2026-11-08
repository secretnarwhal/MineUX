package com.mineux.visuals.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cosmetics, drawn on this client only and seen by nobody else: a cape, wings, a hat, an aura,
 * an effect where someone you killed fell, and a pet that follows
 * you. One pick a slot ({@link CosmeticsConfig}); ported from Clarity.
 * <p>
 * Everything but the cape is built each frame out of quads ({@link Builder}, {@link Prim}) and
 * handed to the world's gizmo pass ({@link QuadBatch}); the cape is a texture swapped into your
 * render state. A cosmetic that throws is switched off for the session and logged once, so one
 * broken shape never takes the frame with it.
 */
public final class Cosmetics {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/cosmetics");

    private static final long START = System.nanoTime();

    /** A slot that threw, by bit: hat, wings, aura, (3 unused), kill, pet, cape. */
    private static int broken = 0;

    private Cosmetics() {}

    static double seconds() {
        return (System.nanoTime() - START) / 1.0E9;
    }

    private static boolean on(Minecraft mc) {
        if (mc.level == null || mc.player == null || !com.mineux.visuals.Visuals.on()) return false;
        return CosmeticsConfig.get().enabled;
    }

    // ---- hooks ------------------------------------------------------------------------------

    /** Once a frame, from the world's gizmo pass. */
    public static void onRenderFrame(Minecraft mc, float partialTick) {
        if (!on(mc)) return;
        CosmeticsConfig config = CosmeticsConfig.get();
        LocalPlayer me = mc.player;
        boolean shown = !me.isInvisible() && !me.isSpectator();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        if (shown && (!firstPerson || config.hatFirstPerson)) guard(0, mc, me, partialTick, config);
        if (shown && !firstPerson) guard(1, mc, me, partialTick, config);
        if (shown && (!firstPerson || config.auraFirstPerson)) guard(2, mc, me, partialTick, config);
        guard(4, mc, me, partialTick, config);
        guard(5, mc, me, partialTick, config);
    }

    private static void guard(int slot, Minecraft mc, LocalPlayer me, float partialTick, CosmeticsConfig config) {
        if ((broken & (1 << slot)) != 0) return;
        try {
            if (slot == 0) WornHat.draw(mc, me, partialTick, config);
            else if (slot == 1) WornWings.draw(mc, me, partialTick, config);
            else if (slot == 2) WornAura.draw(mc, me, partialTick, config);
            else if (slot == 4) KillShow.draw(mc, config);
            else PetWalk.draw(mc, me, partialTick, config);
        } catch (Throwable t) {
            broken |= 1 << slot;
            LOGGER.error("cosmetic slot {} threw; it is off until the game restarts", slot, t);
        }
    }

    /** From the avatar renderer, once a player's render state is filled in (VisualOps.OP_COSMETIC_CAPE). */
    public static void onAvatarState(Object state) {
        if (!AvatarRenderState.class.isInstance(state) || (broken & (1 << 6)) != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!on(mc)) return;
        try {
            Capes.onAvatarState(mc, (AvatarRenderState) state, CosmeticsConfig.get());
        } catch (Throwable t) {
            broken |= 1 << 6;
            LOGGER.error("the cape threw; it is off until the game restarts", t);
        }
    }

    /** A kill of yours, as {@code fx/Fx} tells it: someone died a moment after your hit. */
    public static void onKill(Entity dead) {
        Minecraft mc = Minecraft.getInstance();
        if (!on(mc)) return;
        try {
            KillShow.spawn(dead, CosmeticsConfig.get());
        } catch (Throwable t) {
            LOGGER.error("the kill effect would not start", t);
        }
    }

    /** Whether your hits and kills should be looked for at all: fx's detection runs for these too. */
    public static boolean wantsHits() {
        CosmeticsConfig config = CosmeticsConfig.get();
        return config.enabled && KillShow.style(config) > 0;
    }

    // ---- shared -----------------------------------------------------------------------------

    /** The player as {@link Attach} wants them, at this frame's partial tick. */
    static Attach.Subject subject(LocalPlayer p, float partialTick, double followTilt) {
        double x = Mth.lerp(partialTick, p.xo, p.getX());
        double y = Mth.lerp(partialTick, p.yo, p.getY());
        double z = Mth.lerp(partialTick, p.zo, p.getZ());
        float bodyYaw = Mth.rotLerp(partialTick, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, p.yHeadRotO, p.yHeadRot);
        float pitch = Mth.lerp(partialTick, p.xRotO, p.getXRot());
        Pose pose = p.getPose();
        return new Attach.Subject(x, y, z, bodyYaw, headYaw, pitch, p.getBbHeight(),
            pose == Pose.CROUCHING, pose == Pose.SWIMMING, pose == Pose.FALL_FLYING, followTilt);
    }

    /** A builder into {@code batch} in the config's colours, seen from the camera. */
    static Builder builder(Minecraft mc, QuadBatch batch, int c1, int c2, CosmeticsConfig config, double time) {
        Builder b = new Builder(batch, c1, c2, config.opacity, time, config.animate);
        net.minecraft.world.phys.Vec3 cam = mc.gameRenderer.mainCamera().position();
        b.view = new double[] {cam.x, cam.y, cam.z};
        return b;
    }

    static int color(CosmeticsConfig config) {
        return CosmeticsConfig.rgb(config.color, 0xFF7FD9FF) & 0xFFFFFF;
    }

    static int color2(CosmeticsConfig config) {
        return CosmeticsConfig.rgb(config.color2, 0xFFC77DFF) & 0xFFFFFF;
    }
}
