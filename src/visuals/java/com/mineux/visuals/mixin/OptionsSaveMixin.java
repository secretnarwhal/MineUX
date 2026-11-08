package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Just before options.txt is written: the visuals puts back what it holds changed for the moment
 * (the zoom's slowed mouse), so the file only ever gets the user's own values. The game saves
 * with no screen open too -- F11, F3+P, F3+H -- which is how a held zoom could reach the file.
 */
@Mixin(Options.class)
public abstract class OptionsSaveMixin {

    @Inject(method = "save", at = @At("HEAD"))
    private void mineux$beforeSave(CallbackInfo ci) {
        // Never in the way of the save itself: a module that throws here costs the put-back only.
        try {
            VisualBridge.dispatch(OP_OPTIONS_SAVE, this);
        } catch (RuntimeException | LinkageError e) {
            com.mineux.core.MineUX.LOGGER.error("The visuals failed before the options were saved", e);
        }
    }
}
