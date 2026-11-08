package com.mineux.visuals.self;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Your own player, as you see it: {@link BodyView} (your body in first person),
 * {@link Moves} (walk styles), {@link JumpRings} (rings where
 * you jump), {@link SelfModel} (another model in your place), {@link Skins} (another skin) and
 * {@link ArmorShade} (armour in a material). All of it is drawn on this screen only and none of it
 * is sent anywhere; all of it is off by default.
 * <p>
 * Every hook comes from the middle of the game's frame, so a part that throws is logged once and
 * switched off for the session rather than taking the frame down with it.
 */
public final class Self {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/self");

    private static final String[] PARTS = {"body view", "unused", "move animations", "jump effect", "player model", "skin changer", "armour shaders"};
    private static final int BODY = 0, LOOK = 1, MOVES = 2, JUMP = 3, MODEL = 4, SKIN = 5, ARMOR = 6;
    private static final boolean[] FAILED = new boolean[PARTS.length];

    private Self() {}

    private static void fail(int part, Throwable e) {
        if (FAILED[part]) return;
        FAILED[part] = true;
        LOGGER.error("{} failed and is off until the game restarts", PARTS[part], e);
    }

    private static boolean ok(int part) {
        return !FAILED[part];
    }

    /** Below the entitlement gate, once a tick. */
    public static void onClientTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        try {
            if (ok(JUMP)) JumpRings.onClientTick(mc, p);
        } catch (RuntimeException | LinkageError e) {
            fail(JUMP, e);
        }
        try {
            if (ok(SKIN)) Skins.onClientTick(mc);
        } catch (RuntimeException | LinkageError e) {
            fail(SKIN, e);
        }
    }

    public static void onRenderFrame(Minecraft mc) {
        if (!com.mineux.visuals.Visuals.on() || !ok(JUMP)) return;
        try {
            JumpRings.onRenderFrame(mc);
        } catch (RuntimeException | LinkageError e) {
            fail(JUMP, e);
        }
    }

    /** The end of the game's entity extraction. */
    public static void onEntitiesExtracted(Object level, float partialTick) {
        if (!ok(BODY) || !com.mineux.visuals.Visuals.on() || !LevelRenderState.class.isInstance(level)) return;
        try {
            BodyView.addState(Minecraft.getInstance(), (LevelRenderState) level, partialTick);
        } catch (RuntimeException | LinkageError e) {
            fail(BODY, e);
        }
    }

    /** A player's state, just extracted. */
    public static void onAvatarExtracted(Object arg, float partialTick) {
        if (!com.mineux.visuals.Visuals.on() || !AvatarRenderState.class.isInstance(arg)) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        ClientLevel level = mc.level;
        if (level == null) return;
        AvatarRenderState state = (AvatarRenderState) arg;
        boolean self = me != null && state.id == me.getId();
        try {
            if (ok(MOVES) && SelfConfig.get().moves) {
                Entity entity = self ? me : level.getEntity(state.id);
                if (LivingEntity.class.isInstance(entity)) Moves.onExtract((LivingEntity) entity, state, self);
            }
        } catch (RuntimeException | LinkageError e) {
            fail(MOVES, e);
        }
        try {
            if (self && ok(MODEL)) SelfModel.onExtract(state, me);
        } catch (RuntimeException | LinkageError e) {
            fail(MODEL, e);
        }
    }

    /** A player's state handed to its renderer: {state, pose stack, collector}. */
    public static void onAvatarSubmit(Object arg) {
        if (!ok(MODEL) || !com.mineux.visuals.Visuals.on() || !Object[].class.isInstance(arg)) return;
        Object[] a = (Object[]) arg;
        if (a.length < 3 || !AvatarRenderState.class.isInstance(a[0]) || !PoseStack.class.isInstance(a[1])
            || !SubmitNodeCollector.class.isInstance(a[2])) return;
        try {
            SelfModel.onSubmit((AvatarRenderState) a[0], (PoseStack) a[1], (SubmitNodeCollector) a[2]);
        } catch (RuntimeException | LinkageError | ReflectiveOperationException e) {
            fail(MODEL, e);
        }
    }

    /** A humanoid model just posed for a state. */
    public static void onHumanoidPose(Object model, Object state) {
        if (!com.mineux.visuals.Visuals.on() || !HumanoidModel.class.isInstance(model)) return;
        HumanoidModel<?> m = (HumanoidModel<?>) model;
        try {
            if (ok(MOVES)) Moves.onPose(m, state);
        } catch (RuntimeException | LinkageError e) {
            fail(MOVES, e);
        }
        try {
            if (ok(BODY)) BodyView.onPose(m, state);
        } catch (RuntimeException | LinkageError e) {
            fail(BODY, e);
        }
    }

    /** Your skin on its way out of the tab list's entry for you. */
    public static Object skin(Object skin) {
        if (!ok(SKIN) || !com.mineux.visuals.Visuals.on() || !PlayerSkin.class.isInstance(skin)) return skin;
        try {
            PlayerSkin out = Skins.override((PlayerSkin) skin);
            return out == null ? skin : out;
        } catch (RuntimeException | LinkageError e) {
            fail(SKIN, e);
            return skin;
        }
    }

    /** The head of the armour layer, for whichever entity's state it draws. */
    public static void armorBegin(Object state) {
        if (!ok(ARMOR) || !com.mineux.visuals.Visuals.on()) {
            ArmorShade.begin(null);
            return;
        }
        try {
            ArmorShade.begin(state);
        } catch (RuntimeException | LinkageError e) {
            fail(ARMOR, e);
            ArmorShade.begin(null);
        }
    }

    public static void armorTexture(Object id) {
        ArmorShade.texture(id);
    }

    public static Object armorType(Object vanilla) {
        if (!ok(ARMOR)) return vanilla;
        try {
            return ArmorShade.type(vanilla);
        } catch (RuntimeException | LinkageError e) {
            fail(ARMOR, e);
            return vanilla;
        }
    }

    public static Object armorTint(Object vanilla) {
        return ok(ARMOR) ? ArmorShade.tint(vanilla) : vanilla;
    }
}
