package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mineux.visuals.VisualOps.*;

/**
 * The client's clocks, as every environment attribute reads them: the sun's angle, the sky's
 * colour, the stars and the light all follow from these totals, so one hook here moves the
 * whole time of day on this client and nothing the server keeps.
 */
@Mixin(ClientClockManager.class)
public abstract class WorldClockMixin {

    @Inject(method = "getTotalTicks(Lnet/minecraft/core/Holder;)J", at = @At("RETURN"), cancellable = true)
    private void mineux$clock(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        Long ticks = cir.getReturnValue();
        Long shown = VisualBridge.transform(OP_WORLD_CLOCK, ticks, Long.class);
        if (shown != ticks) cir.setReturnValue(shown);
    }
}
