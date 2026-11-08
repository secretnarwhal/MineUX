package com.mineux.visuals.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mineux.visuals.VisualBridge;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/**
 * The level's frame, for the sky and world shaders: the view and the camera it is drawn from at
 * the head, the finished world at the return (before the hand), and the clouds, which a shader
 * sky may leave out.
 */
@Mixin(LevelRenderer.class)
public abstract class WorldFrameMixin {

    @Inject(
        method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
            + "Lnet/minecraft/client/DeltaTracker;"
            + "ZLnet/minecraft/client/renderer/state/level/CameraRenderState;"
            + "Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            + "Lorg/joml/Vector4f;Z)V",
        at = @At("HEAD")
    )
    private void mineux$frame(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
                                  CameraRenderState cameraRenderState, Matrix4fc view, GpuBufferSlice fogBuffer,
                                  Vector4f clearColor, boolean renderSky, CallbackInfo ci) {
        VisualBridge.query(OP_WORLD_FRAME, view, cameraRenderState);
    }

    @Inject(
        method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
            + "Lnet/minecraft/client/DeltaTracker;"
            + "ZLnet/minecraft/client/renderer/state/level/CameraRenderState;"
            + "Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            + "Lorg/joml/Vector4f;Z)V",
        at = @At("RETURN")
    )
    private void mineux$frameEnd(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
                                     CameraRenderState cameraRenderState, Matrix4fc view, GpuBufferSlice fogBuffer,
                                     Vector4f clearColor, boolean renderSky, CallbackInfo ci) {
        VisualBridge.dispatch(OP_WORLD_FRAME_END);
    }

    @Inject(
        method = "addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;JFIFI)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mineux$clouds(FrameGraphBuilder frame, CloudStatus status, Vec3 camera, long gameTime, float partialTicks,
                                   int color, float height, int range, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_CLOUDS)) ci.cancel();
    }
}
