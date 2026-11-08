package com.mineux.visuals.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * What the visuals' camera settings write on the camera once it is placed
 * ({@code OP_CAMERA_ALIGNED}): its position, for the smooth camera's lag, and the field of view
 * modifier, for no fov.
 */
@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setPosition")
    void mineux$setPosition(Vec3 position);

    @Accessor("fovModifier")
    void mineux$setFovModifier(float modifier);

    @Accessor("oldFovModifier")
    void mineux$setOldFovModifier(float modifier);
}
