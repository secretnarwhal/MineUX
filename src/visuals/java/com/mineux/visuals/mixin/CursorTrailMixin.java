package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the cursor trail and the fake cursor on every open {@link Screen}, and lets the fake
 * cursor go on every way a screen closes.
 */
@Mixin(Screen.class)
public abstract class CursorTrailMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void mineux$drawCursorTrail(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        VisualBridge.dispatch(OP_CURSOR_TRAIL, graphics, mouseX, mouseY);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void mineux$cursorTrailClosed(CallbackInfo ci) {
        VisualBridge.dispatch(OP_CURSOR_TRAIL_CLOSED);
    }
}
