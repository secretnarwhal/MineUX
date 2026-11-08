package com.mineux.visuals.self;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Body view: your own body under you in first person, as a third-person camera would show it but
 * without the head, which is where the camera is, and without the arms unless asked -- the
 * first-person hand already draws one.
 * <p>
 * The game leaves the camera's own entity out of the frame in first person. Straight after it has
 * extracted everything else, this extracts you the same way and adds you to the frame, set back a
 * little along your body's facing so the camera is not inside your chest. Nothing else changes:
 * the frame's other entities, and your hand, are the game's own.
 */
final class BodyView {

    /** The state added this frame, so the model hook knows which one to strip the head off. */
    private static EntityRenderState bodyState;
    private static boolean extracting;

    /** Models whose head (and arms) this hid, put back for the next state they draw. */
    private static final Map<HumanoidModel<?>, Boolean> HID = new WeakHashMap<>();

    private BodyView() {}

    /** End of the game's entity extraction: you, added to the frame when it would not have you. */
    static void addState(Minecraft mc, LevelRenderState level, float partialTick) {
        bodyState = null;
        SelfConfig config = SelfConfig.get();
        LocalPlayer p = mc.player;
        if (!config.body || level == null || p == null || mc.level == null) return;
        if (!mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != p) return;
        if (p.isSleeping() || p.isSpectator()) return;
        if (config.bodyHideSwimFly && (p.isSwimming() || p.isFallFlying() || p.isVisuallySwimming())) return;

        EntityRenderState state;
        extracting = true;
        try {
            state = mc.getEntityRenderDispatcher().extractEntity(p, partialTick);
        } finally {
            extracting = false;
        }
        if (state == null) return;
        double yaw = 0.0;
        if (LivingEntityRenderState.class.isInstance(state)) {
            LivingEntityRenderState living = (LivingEntityRenderState) state;
            yaw = Math.toRadians(living.bodyRot);
            living.isInvisible = false;
        }
        // Back along the body's facing, which is (-sin, cos) for the game's yaw.
        double off = config.bodyOffset;
        state.x = state.x + Math.sin(yaw) * off;
        state.z = state.z - Math.cos(yaw) * off;
        state.nameTag = null;
        state.scoreText = null;
        if (!config.bodyShadow) {
            state.shadowRadius = 0.0F;
            state.shadowPieces.clear();
        }
        bodyState = state;
        level.entityRenderStates.add(state);
    }

    /** Whether the state being extracted now is the body's, which the player model leaves to the game. */
    static boolean extracting() {
        return extracting;
    }

    static boolean isBody(Object state) {
        return state != null && state == bodyState;
    }

    /** After a humanoid model is posed: the body's head (and arms) hidden, anyone else's put back. */
    static void onPose(HumanoidModel<?> model, Object state) {
        if (isBody(state) && SelfConfig.get().body) {
            // The hat is the head's child, and goes with it.
            model.head.visible = false;
            if (!SelfConfig.get().bodyArms) {
                model.rightArm.visible = false;
                model.leftArm.visible = false;
            }
            HID.put(model, Boolean.TRUE);
        } else if (HID.remove(model) != null) {
            model.head.visible = true;
            // The player model sets its own arms on every pose (hidden for a spectator); only
            // another humanoid model needs them put back.
            if (!PlayerModel.class.isInstance(model)) {
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
        }
    }
}
