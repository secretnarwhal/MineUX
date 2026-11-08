package com.mineux.visuals.self;

import com.mineux.core.ui.term.Notify;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mineux.visuals.self.model.Dress;
import com.mineux.visuals.self.model.Imported;
import com.mineux.visuals.self.model.ImportedDraw;
import com.mineux.visuals.self.model.ModelFiles;
import com.mineux.visuals.self.model.Parka;
import com.mineux.visuals.self.model.Rig;
import com.mineux.visuals.self.model.RigDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.Arrays;
import java.util.List;

/**
 * Player model: in third person you are drawn as something else -- the parka or the dress, two
 * little outlined cartoon figures built into the client, or a model of your own from
 * config/mineux/models (.bbmodel, .obj, .glb, .gltf). Your screen only.
 * <p>
 * When your state is extracted the game's body is taken off it (made invisible, its armour and
 * parrots left off; an elytra stays, held items still show); when the game hands your state to its
 * renderer the model goes in its place, at your feet, turned with your body, its head with the look
 * the state has -- the server's, with {@link SentLook} on. It walks with you: a stride from how far
 * you moved since the last tick, a swing from your arm's, legs tucked in the air, a crouch.
 */
final class SelfModel {

    static final List<String> KINDS = Arrays.asList("parka", "dress", "custom");
    private static final int DRESS = 1, CUSTOM = 2;

    /** The state the model replaces this frame. */
    private static AvatarRenderState modelState;

    private static long lastNanos;
    private static double walkPhase, walkAmp, airFactor;
    private static String lastStatus = "";

    private SelfModel() {}

    static int kindIndex(String name) {
        return KINDS.indexOf(name);
    }

    /** Your state as extracted: the game's body off it, and its walk read. */
    static void onExtract(AvatarRenderState state, LocalPlayer p) {
        if (!SelfConfig.get().model || BodyView.extracting()) {
            if (modelState == state) modelState = null;
            return;
        }
        state.isInvisible = true;
        state.isInvisibleToPlayer = true;
        state.headEquipment = ItemStack.EMPTY;
        state.legsEquipment = ItemStack.EMPTY;
        state.feetEquipment = ItemStack.EMPTY;
        if (!state.chestEquipment.has(DataComponents.GLIDER)) state.chestEquipment = ItemStack.EMPTY;
        state.parrotOnLeftShoulder = null;
        state.parrotOnRightShoulder = null;
        modelState = state;
        walk(p);
    }

    private static void walk(LocalPlayer p) {
        long now = System.nanoTime();
        double dt = lastNanos == 0L ? 0.016 : Math.min(0.1, (now - lastNanos) / 1.0E9);
        lastNanos = now;
        double moved = Math.hypot(p.getX() - p.xOld, p.getZ() - p.zOld);
        double target = p.onGround() ? Math.min(1.0, moved / 0.2) : 0.25;
        walkAmp += (target - walkAmp) * Math.min(1.0, dt * 12.0);
        walkPhase += dt * moved * 20.0 * 3.6;
        if (walkPhase > 1000000.0) walkPhase -= 1000000.0;
        airFactor += ((p.onGround() ? 0.0 : 1.0) - airFactor) * Math.min(1.0, dt * 10.0);
    }

    /** The game handing your state to its renderer: the model in its place. */
    static void onSubmit(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector) throws ReflectiveOperationException {
        SelfConfig config = SelfConfig.get();
        if (state != modelState || !config.model) return;
        double headYaw = Math.toRadians(Math.max(0.0F - 85.0F, Math.min(85.0F, state.yRot)));
        double headPitch = Math.toRadians(Math.max(0.0F - 89.0F, Math.min(89.0F, state.xRot)));
        float yaw = (float) Math.toRadians(state.bodyRot + config.modelYaw);
        boolean crouch = state.isCrouching;
        pose.pushPose();
        try {
            pose.mulPose(new Quaternionf().rotationY(0.0F - yaw));
            int kind = kindIndex(config.modelKind);
            if (kind == CUSTOM) custom(state, pose, collector, config, crouch, (float) headYaw, (float) headPitch);
            else rig(kind == DRESS ? Dress.INSTANCE : Parka.INSTANCE, state, pose, collector, config, crouch, headYaw, headPitch);
        } finally {
            pose.popPose();
        }
    }

    private static void rig(Rig rig, AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, SelfConfig config,
                            boolean crouch, double headYaw, double headPitch) throws ReflectiveOperationException {
        Rig.Bones bones = new Rig.Bones();
        rig.pose(walkPhase, walkAmp, state.attackTime, crouch, headYaw, headPitch, bones);
        float s = config.modelScale;
        // The rigs are built with their right side on +x: seen from the front, a mirror of the
        // game's frame, which their outlines' winding counts on.
        pose.scale(0.0F - s, s, s);
        RigDraw.submit(collector, pose, rig, bones, 1.0F, config.modelWalls);
    }

    private static void custom(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, SelfConfig config, boolean crouch,
                               float headYaw, float headPitch) throws ReflectiveOperationException {
        Imported m = ModelFiles.get(config.modelFile, config.modelZUp);
        report();
        if (m == null) return;
        float scale = config.modelScale;
        float height = config.modelHeight * scale * (crouch ? 0.9F : 1.0F);
        boolean limbs = m.rigged && config.modelLimbs;
        float[] rig = limbs
            ? ImportedDraw.pose((float) walkPhase, (float) walkAmp, (float) airFactor, crouch ? 1.0F : 0.0F, headYaw, headPitch, state.attackTime)
            : null;
        float roll = !limbs && config.modelBob ? (float) (Math.sin(walkPhase) * 0.06 * walkAmp) : 0.0F;
        double bob = config.modelBob ? Math.abs(Math.cos(walkPhase)) * (limbs ? 0.02 : 0.04) * walkAmp * scale : 0.0;
        pose.translate(0.0, bob, 0.0);
        if (roll != 0.0F) pose.mulPose(new Quaternionf().rotationZ(roll));
        pose.scale(height, height, height);
        ImportedDraw.submit(collector, pose, m, rig, 1.0F, state.lightCoords, config.modelLit, config.modelWalls);
    }

    /** The custom model's loading, said once as a desktop notification when it settles. */
    private static void report() {
        String s = ModelFiles.status();
        if (s.equals(lastStatus) || s.startsWith("loading")) return;
        lastStatus = s;
        Notify.show("model", "model: " + s, Notify.NOTE);
    }
}
