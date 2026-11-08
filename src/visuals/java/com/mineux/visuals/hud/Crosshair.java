package com.mineux.visuals.hud;

import com.mineux.visuals.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * The client's crosshair, in place of the game's: a cross, dot, circle, cross and dot, broken
 * ring, chevron, diamond or corner brackets, any size, thickness and turn, spinning if asked,
 * in one colour, two blended round it or the rainbow, outlined and haloed. It opens out while
 * you move, jumps out on your hit with a marker and a flash, can send a ring out, and bursts
 * when the one you just hit dies. The attack charge is a ring round it while it fills.
 * <p>
 * Sizes are pixels of a 1080p window, scaled with the window, as Clarity's were.
 */
public final class Crosshair {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/crosshair");

    /** How long a hit's marker, flash and ring last, seconds. */
    private static final float HIT_S = 0.35F;
    /** How long after your hit their death counts as your kill, ns. */
    private static final long KILL_WINDOW_NS = 2_000_000_000L;
    private static final int OUTLINE = 0xC8050508;
    private static final long START = System.nanoTime();

    private static long hitAt = 0L - 1L;
    private static long killAt = 0L - 1L;
    private static int lastHitId = 0 - 1;

    private static float spread;
    private static float onTarget;
    private static float spin;
    private static long lastFrame;
    private static boolean failed;

    /** This frame's colours, for the shapes below. */
    private static final Palette PAL = new Palette();

    private Crosshair() {}

    // ---- your hits ----------------------------------------------------------------------------

    /** An attack on its way out (the visuals attack mixin): the marker on it, if it is on someone alive. */
    public static void onAttack(Minecraft mc, int entityId) {
        SkinConfig config = SkinConfig.get();
        if (!config.crosshair || mc.level == null) return;
        Entity struck = mc.level.getEntity(entityId);
        if (struck == null || !LivingEntity.class.isInstance(struck) || !struck.isAlive()) return;
        if (config.chPlayersOnly && !Player.class.isInstance(struck)) return;
        hitAt = System.nanoTime();
        lastHitId = entityId;
    }

    /** A death: your kill when it is the one you hit a moment ago. */
    public static void onEntityEvent(ClientboundEntityEventPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.level == null || packet.getEventId() != EntityEvent.DEATH) return;
        SkinConfig config = SkinConfig.get();
        if (!config.crosshair || !config.chKill || lastHitId < 0) return;
        Entity dead = packet.getEntity(mc.level);
        if (dead == null || dead.getId() != lastHitId) return;
        long now = System.nanoTime();
        if (now - hitAt > KILL_WINDOW_NS) return;
        killAt = now;
        lastHitId = 0 - 1;
    }

    // ---- once a frame ---------------------------------------------------------------------------

    /** From the HUD's crosshair: true when this drew, and the game's is to be left out. */
    public static boolean draw(GuiGraphicsExtractor g) {
        SkinConfig config = SkinConfig.get();
        if (failed || !config.crosshair) return false;
        try {
            return drawAll(g, config);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.error("The crosshair failed and is vanilla's until the game restarts", e);
            return false;
        }
    }

    private static boolean drawAll(GuiGraphicsExtractor g, SkinConfig config) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.gameMode == null) return false;
        // Where the game draws none, nor does this; a spectator's is the game's own business.
        if (!mc.options.getCameraType().isFirstPerson()) return true;
        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return false;
        // F3's 3D crosshair stands in for the flat one: the game's to draw, or not.
        if (mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)) return false;

        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
        lastFrame = now;
        Palette pal = PAL;
        pal.time = (float) ((now - START) / 1.0E9 % 3600.0);

        float spreadTo = 0.0F;
        if (config.chDynamic) {
            double speed = Math.hypot(me.getDeltaMovement().x, me.getDeltaMovement().z) * 20.0;
            spreadTo = (float) Math.min(4.0, speed * 0.45) + (me.onGround() ? 0.0F : 2.5F);
        }
        spread += (spreadTo - spread) * Math.min(1.0F, dt * 12.0F);
        boolean onEntity = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY;
        onTarget += ((config.chTarget && onEntity ? 1.0F : 0.0F) - onTarget) * Math.min(1.0F, dt * 14.0F);
        spin = (spin + config.chSpin * 90.0F * dt) % 360.0F;

        float hitAge = hitAt < 0L ? 99.0F : (now - hitAt) / 1.0E9F;
        float killAge = killAt < 0L ? 99.0F : (now - killAt) / 1.0E9F;

        pal.base = SkinConfig.rgb(config.chColor, 0xFFFFFF);
        pal.second = SkinConfig.rgb(config.chColor2, 0x7A8CFF);
        pal.mode = Objects.equals("gradient", config.chColorMode) ? 1 : Objects.equals("chroma", config.chColorMode) ? 2 : 0;
        int targetInk = SkinConfig.rgb(config.chTargetColor, 0xFF5050);
        if (onTarget > 0.01F) {
            pal.base = Theme.mix(pal.base, targetInk, onTarget);
            pal.second = Theme.mix(pal.second, targetInk, onTarget);
        }
        pal.flashInk = SkinConfig.rgb(config.chHitColor, 0xFF3B3B);
        pal.flash = config.chFlash && hitAge < HIT_S ? 1.0F - hitAge / HIT_S : 0.0F;

        float expand = config.chExpand && hitAge < 0.25F ? 3.0F * (1.0F - Strokes.easeOut(hitAge / 0.25F)) : 0.0F;
        float gap = config.chGap + spread + expand;

        int physH = mc.getWindow().getHeight();
        float unit = Math.max(0.75F, physH / 1080.0F) / (float) mc.getWindow().getGuiScale();
        g.nextStratum();
        g.pose().pushMatrix();
        g.pose().translate(g.guiWidth() / 2.0F, g.guiHeight() / 2.0F);
        g.pose().scale(unit, unit);

        if (config.chCooldown) cooldown(g, config, me, gap);

        g.pose().pushMatrix();
        g.pose().rotate((float) Math.toRadians(config.chRotation + spin));
        if (config.chGlow > 0.01F) {
            float glow = config.chGlow;
            layer(g, config, gap, 3.0F * glow + 1.0F, Theme.alpha(pal.base, 0.10F * Math.min(1.0F, glow)));
            layer(g, config, gap, 1.5F * glow + 0.5F, Theme.alpha(pal.base, 0.18F * Math.min(1.0F, glow)));
        }
        if (config.chOutline && config.chOutlineWidth > 0.0F) layer(g, config, gap, config.chOutlineWidth * 2.0F, OUTLINE);
        layer(g, config, gap, 0.0F, 0);
        g.pose().popMatrix();

        CrosshairFx.effects(g, config, gap, hitAge, killAge, pal.flashInk);
        g.pose().popMatrix();
        return true;
    }

    /** The attack charge, as a ring round the shape filling clockwise from the top. */
    private static void cooldown(GuiGraphicsExtractor g, SkinConfig config, LocalPlayer me, float gap) {
        float charge = me.getAttackStrengthScale(0.0F);
        if (charge >= 1.0F) return;
        float radius = gap + config.chSize + 5.0F;
        Strokes.ring(g, radius, 1.5F, 0x30FFFFFF);
        Strokes.arc(g, radius, 1.5F, 0.0F, charge, Theme.alpha(PAL.at(0.0F), 0.85F));
    }

    /**
     * The shape once: {@code extra} thicker on every side, and in {@code solid} when that is
     * not 0 -- a halo or the outline -- else in its own colours.
     */
    private static void layer(GuiGraphicsExtractor g, SkinConfig config, float gap, float extra, int solid) {
        String style = config.chStyle;
        float size = config.chSize;
        float thick = config.chThickness + extra;
        float pad = extra * 0.5F;
        float ring = gap + size * 0.5F;
        if (Objects.equals("cross", style) || Objects.equals("crossdot", style)) {
            for (int i = 0; i < 4; i++) {
                float deg = i * 90.0F;
                Strokes.spoke(g, deg, gap - pad, gap + size + pad, thick, solid != 0 ? solid : PAL.at(deg));
            }
        }
        if (Objects.equals("dot", style) || Objects.equals("crossdot", style) || Objects.equals("chevron", style)) {
            float dot = Objects.equals("dot", style) ? Math.max(1.0F, config.chDot) : config.chDot;
            if (dot > 0.0F) Strokes.disc(g, dot * 0.5F + pad, solid != 0 ? solid : PAL.at(0.0F));
        }
        if (Objects.equals("circle", style)) {
            if (solid != 0 || PAL.mode == 0) Strokes.ring(g, ring, thick, solid != 0 ? solid : PAL.at(0.0F));
            else for (int i = 0; i < 12; i++) Strokes.arc(g, ring, thick, i / 12.0F, (i + 1) / 12.0F, PAL.at(i * 30.0F));
        }
        if (Objects.equals("segments", style)) {
            // A ring broken at the four points of the compass.
            for (int i = 0; i < 4; i++) {
                float mid = 0.125F + i * 0.25F;
                Strokes.arc(g, ring, thick, mid - 0.085F, mid + 0.085F, solid != 0 ? solid : PAL.at(45.0F + i * 90.0F));
            }
        }
        if (Objects.equals("chevron", style)) {
            float tip = gap;
            float arm = size * 0.8F + pad;
            int left = solid != 0 ? solid : PAL.at(135.0F);
            int right = solid != 0 ? solid : PAL.at(45.0F);
            Strokes.line(g, 0.0F, tip, 0.0F - arm, tip + arm, thick, left);
            Strokes.line(g, 0.0F, tip, arm, tip + arm, thick, right);
        }
        if (Objects.equals("diamond", style)) {
            float r = ring;
            Strokes.line(g, 0.0F, 0.0F - r, r, 0.0F, thick, solid != 0 ? solid : PAL.at(315.0F));
            Strokes.line(g, r, 0.0F, 0.0F, r, thick, solid != 0 ? solid : PAL.at(45.0F));
            Strokes.line(g, 0.0F, r, 0.0F - r, 0.0F, thick, solid != 0 ? solid : PAL.at(135.0F));
            Strokes.line(g, 0.0F - r, 0.0F, 0.0F, 0.0F - r, thick, solid != 0 ? solid : PAL.at(225.0F));
        }
        if (Objects.equals("brackets", style)) brackets(g, ring, size * 0.6F + pad, thick, solid);
    }

    /** Four corners, as a camera's focus brackets. */
    private static void brackets(GuiGraphicsExtractor g, float r, float arm, float thick, int solid) {
        for (int i = 0; i < 4; i++) {
            float sx = i == 0 || i == 3 ? 1.0F : 0.0F - 1.0F;
            float sy = i < 2 ? 1.0F : 0.0F - 1.0F;
            int ink = solid != 0 ? solid : PAL.at(45.0F + i * 90.0F);
            Strokes.line(g, sx * r, sy * r, sx * (r - arm), sy * r, thick, ink);
            Strokes.line(g, sx * r, sy * r, sx * r, sy * (r - arm), thick, ink);
        }
    }

    /** This frame's colours: worked out once in {@link #drawAll}, read by every shape. */
    private static final class Palette {
        int base;
        int second;
        int mode;
        float time;
        int flashInk;
        float flash;

        /** The colour at {@code degrees} round the middle, with the hit's flash in it. */
        int at(float degrees) {
            int colour;
            if (mode == 1) {
                float t = 0.5F + 0.5F * (float) Math.sin(Math.toRadians(degrees) + time * 2.0F);
                colour = Theme.mix(base, second, t);
            } else if (mode == 2) {
                float hue = (time * 90.0F + degrees) % 360.0F;
                colour = Theme.hsvToRgb(hue, 0.75F, 1.0F);
            } else {
                colour = base;
            }
            return flash > 0.0F ? Theme.mix(colour, flashInk, flash) : colour;
        }
    }
}
