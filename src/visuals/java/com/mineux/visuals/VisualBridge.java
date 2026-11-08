package com.mineux.visuals;

import com.mineux.visuals.cosmetics.Cosmetics;
import com.mineux.visuals.fx.Fx;
import com.mineux.visuals.hand.Glint;
import com.mineux.visuals.hand.Hand;
import com.mineux.visuals.hand.HandShaders;
import com.mineux.visuals.hud.ChatSkin;
import com.mineux.visuals.hud.Crosshair;
import com.mineux.visuals.hud.Hotbar;
import com.mineux.visuals.hud.Scoreboard;
import com.mineux.visuals.hud.StatusBars;
import com.mineux.visuals.hud.ThemeTextures;
import com.mineux.visuals.self.Self;
import com.mineux.visuals.trail.FakeCursorRenderer;
import com.mineux.visuals.trail.TrailRenderer;
import com.mineux.visuals.view.AutoF5;
import com.mineux.visuals.view.CameraShape;
import com.mineux.visuals.view.Freelook;
import com.mineux.visuals.view.MotionBlur;
import com.mineux.visuals.view.Weather;
import com.mineux.visuals.view.Zoom;
import com.mineux.visuals.world.Ambience;
import com.mineux.visuals.world.TotemPop;
import com.mineux.visuals.world.World;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;

import static com.mineux.visuals.VisualOps.*;

/**
 * Where every one of the visuals' mixins comes in: one static method per shape of hook, routed
 * by its {@link VisualOps} number to the module that draws it, so a host mod with hooks of its
 * own can forward the visual ops here unchanged.
 * <p>
 * Every entry answers the game's own value (false, 0, the value handed in) until the visuals are
 * running ({@link Visuals#on}), so a mixin applied before the init, or in a jar whose init
 * failed, changes nothing. Class#isInstance rather than instanceof throughout: an obfuscator a
 * host may run breaks instanceof for interfaces and classes with subclasses.
 */
public final class VisualBridge {

    private VisualBridge() {}

    /** Whether the visual modules are running. */
    public static boolean on() {
        return Visuals.on();
    }

    // ---- questions --------------------------------------------------------------------------

    public static boolean query(int op) {
        if (!on()) return false;
        if (op == OP_CAMERA_LOOK) return CameraShape.ownLook();
        if (op == OP_NO_HURT_CAM) return CameraShape.noHurtCam();
        if (op == OP_NO_VIEW_BOB) return CameraShape.noBob();
        return false;
    }

    public static boolean query(int op, Object arg) {
        if (!on()) return false;
        if (op == OP_WORLD_HIDE) return World.hide(arg);
        if (op == OP_WORLD_HIDE_AT) return World.hideAt(arg);
        if (op == OP_WORLD_ITEM_DRAW) return World.itemDraw(arg);
        if (op >= OP_HAND_BEGIN && op <= OP_HAND_ITEM) return Hand.query(op, arg);
        if (op >= OP_HUD_CROSSHAIR && op <= OP_HUD_EXPERIENCE && GuiGraphicsExtractor.class.isInstance(arg)) {
            GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) arg;
            if (op == OP_HUD_CROSSHAIR) return Crosshair.draw(graphics);
            if (op == OP_HUD_HOTBAR) return Hotbar.draw(graphics);
            if (op == OP_HUD_STATUS) return StatusBars.draw(graphics);
            return Hotbar.ownsExperience();
        }
        return false;
    }

    public static boolean query(int op, Object a, Object b) {
        if (!on()) return false;
        if (op == OP_WORLD_ITEM) {
            World.item(a, b);
            return false;
        }
        if (op == OP_WORLD_FRAME) {
            World.frame(a, b);
            return false;
        }
        if (op == OP_HUD_SCOREBOARD) {
            return GuiGraphicsExtractor.class.isInstance(a) && Objective.class.isInstance(b)
                && Scoreboard.draw((GuiGraphicsExtractor) a, (Objective) b);
        }
        if (op == OP_THEME_TEXTURE) return ThemeTextures.recolor(a, b);
        if (op == OP_FX_CRIT_PACKET) return Fx.onCritPacket(a, b);
        if (op == OP_FX_CRIT_OWN) return Fx.onOwnCrit(a, b);
        if (op == OP_SELF_HUMANOID_POSE) Self.onHumanoidPose(a, b);
        return false;
    }

    /** 0 is "the game's own" for every one of these. */
    public static double queryDouble(int op) {
        if (!on()) return 0.0;
        if (op == OP_CHAT_HISTORY) return ChatSkin.historyLines();
        if (op == OP_ASPECT) return CameraShape.aspect();
        return 0.0;
    }

    public static double queryDouble(int op, float arg) {
        if (!on()) return 0.0;
        if (op == OP_CAMERA_YAW) return CameraShape.yaw(arg);
        if (op == OP_CAMERA_PITCH) return CameraShape.pitch();
        if (op == OP_CAMERA_DISTANCE) return CameraShape.distance(arg);
        if (op == OP_ZOOM_FOV) return Zoom.fov(Minecraft.getInstance(), arg);
        return 0.0;
    }

    /** The hand's mouse turn: true when freelook's camera took it, and the player is not turned. */
    public static boolean turn(double dx, double dy) {
        return on() && Freelook.turn(dx, dy);
    }

    // ---- values rewritten -------------------------------------------------------------------

    /**
     * A value in, the value to use instead out: the module's answer when it is a {@code type},
     * otherwise the value that went in, so a wrong answer costs the rewrite, never a
     * ClassCastException in the middle of the game's code.
     */
    public static <T> T transform(int op, T value, Class<T> type) {
        if (!on()) return value;
        Object out = rewrite(op, value);
        return type.isInstance(out) ? type.cast(out) : value;
    }

    private static Object rewrite(int op, Object value) {
        if (op == OP_WORLD_CLOCK) return World.clock(value);
        if (op == OP_HAND_ATTACK) return Hand.attack(value);
        if (op == OP_CHAT_ADD) return Component.class.isInstance(value) ? ChatSkin.onAdd((Component) value) : value;
        if (op == OP_CHAT_DRAW) return ChatSkin.wrap(value);
        if (op == OP_SELF_SKIN) return Self.skin(value);
        if (op == OP_SELF_ARMOR_TYPE) return Self.armorType(value);
        if (op == OP_SELF_ARMOR_TINT) return Self.armorTint(value);
        return value;
    }

    // ---- events -----------------------------------------------------------------------------

    public static void dispatch(int op) {
        if (!on()) return;
        if (op == OP_WORLD_FRAME_END) World.frameEnd();
        else if (op == OP_HAND_DRAWN) HandShaders.afterHand();
        else if (op == OP_CURSOR_TRAIL_CLOSED) FakeCursorRenderer.onScreenClosed();
    }

    public static void dispatch(int op, Object arg) {
        if (!on()) return;
        if (op == OP_TICK) {
            if (Minecraft.class.isInstance(arg)) tick((Minecraft) arg);
        } else if (op == OP_MOUSE_FRAME) {
            if (Minecraft.class.isInstance(arg)) Freelook.onFrame((Minecraft) arg);
        } else if (op == OP_ENTITY_EVENT) {
            if (ClientboundEntityEventPacket.class.isInstance(arg)) entityEvent((ClientboundEntityEventPacket) arg);
        } else if (op == OP_DAMAGE_EVENT) {
            if (ClientboundDamageEventPacket.class.isInstance(arg)) Fx.onDamage((ClientboundDamageEventPacket) arg);
        } else if (op == OP_ATTACK) {
            if (Entity.class.isInstance(arg)) Crosshair.onAttack(Minecraft.getInstance(), ((Entity) arg).getId());
        } else if (op == OP_MOTION_BLUR) {
            if (Minecraft.class.isInstance(arg)) MotionBlur.onWorldDrawn((Minecraft) arg);
        } else if (op == OP_LIGHTMAP) {
            if (LightmapRenderState.class.isInstance(arg)) Ambience.lightmap((LightmapRenderState) arg);
        } else {
            dispatchWorld(op, arg);
        }
    }

    private static void dispatchWorld(int op, Object arg) {
        if (op == OP_WORLD_FOG) World.fog(arg);
        else if (op == OP_WORLD_SKY) World.sky(arg);
        else if (op == OP_CHAT_LINE) ChatSkin.onLine(arg);
        else if (op == OP_SELF_AVATAR_SUBMIT) Self.onAvatarSubmit(arg);
        else if (op == OP_SELF_ARMOR_LAYER) Self.armorBegin(arg);
        else if (op == OP_SELF_ARMOR_TEXTURE) Self.armorTexture(arg);
        else if (op == OP_COSMETIC_CAPE) Cosmetics.onAvatarState(arg);
        else if (op == OP_OPTIONS_SAVE && Options.class.isInstance(arg)) Zoom.beforeSave((Options) arg);
    }

    public static void dispatch(int op, Object a, float b) {
        if (!on()) return;
        if (op == OP_CAMERA_ALIGNED && Camera.class.isInstance(a)) {
            CameraShape.aligned(Minecraft.getInstance(), (Camera) a, b);
        } else if (op == OP_RENDER_GIZMOS && Minecraft.class.isInstance(a)) {
            Minecraft mc = (Minecraft) a;
            World.onRenderFrame(mc, b);
            Cosmetics.onRenderFrame(mc, b);
            Self.onRenderFrame(mc);
        } else if (op == OP_SELF_ENTITIES_EXTRACTED) {
            Self.onEntitiesExtracted(a, b);
        } else if (op == OP_SELF_AVATAR_EXTRACTED) {
            Self.onAvatarExtracted(a, b);
        }
    }

    public static void dispatch(int op, Object a, int b, int c) {
        if (!on()) return;
        if (op == OP_CURSOR_TRAIL && GuiGraphicsExtractor.class.isInstance(a)) {
            TrailRenderer.render((GuiGraphicsExtractor) a, b, c);
            FakeCursorRenderer.render((GuiGraphicsExtractor) a, b, c);
        }
    }

    // ---- the parts -------------------------------------------------------------------------

    private static void tick(Minecraft mc) {
        Zoom.onClientTick(mc);
        ThemeTextures.onClientTick(mc);
        Fx.onClientTick(mc);
        Weather.onClientTick(mc);
        World.onClientTick(mc);
        AutoF5.onClientTick(mc);
        Self.onClientTick(mc);
        Glint.onClientTick(mc);
    }

    private static void entityEvent(ClientboundEntityEventPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Fx.onEntityEvent(packet);
        Crosshair.onEntityEvent(packet);
        if (mc.level == null || packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;
        Entity entity = packet.getEntity(mc.level);
        if (entity != null && Player.class.isInstance(entity)) TotemPop.onPop(entity);
    }
}
