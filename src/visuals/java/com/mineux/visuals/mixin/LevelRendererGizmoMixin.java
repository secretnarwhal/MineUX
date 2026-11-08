package com.mineux.visuals.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one point in the frame where a world-space gizmo of this client's own can still land in
 * the same frame it was asked for.
 * <p>
 * {@code render} is called once a frame, on the same thread {@code Minecraft#tick} runs on --
 * this client has no second thread to race. HEAD rather than anywhere later: nothing this
 * class submits reads state {@code render} is still building, so there is nothing to wait for,
 * and a HEAD injection runs whether or not this particular frame goes on to skip a pass further
 * down.
 * <p>
 * The partial tick comes off the same {@code deltaTracker} this method was handed, read the
 * same way {@code render} itself reads it -- {@code getGameTimeDeltaPartialTick(false)}, per
 * the one call already inside it -- rather than a fresh one of this client's own asking. Any
 * other value would be a second, slightly different idea of "now" from the one every entity on
 * screen is actually being drawn at this frame.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererGizmoMixin {

    @Inject(
        method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
            + "Lnet/minecraft/client/DeltaTracker;"
            + "ZLnet/minecraft/client/renderer/state/level/CameraRenderState;"
            + "Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            + "Lorg/joml/Vector4f;Z)V",
        at = @At("HEAD")
    )
    private void mineux$drawGizmos(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                       boolean renderBlockOutline, CameraRenderState cameraRenderState,
                                       Matrix4fc projection, GpuBufferSlice fogBuffer, Vector4f clearColor,
                                       boolean renderSky, CallbackInfo ci) {
        VisualBridge.dispatch(OP_RENDER_GIZMOS, Minecraft.getInstance(), deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
