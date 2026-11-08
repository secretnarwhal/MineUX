package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static com.mineux.visuals.VisualOps.*;

/**
 * The client's chat: lines coming in (timestamps, repeats folded with a count), how many are
 * kept, and the drawing access wrapped for its background and the slide of a new line. The
 * name spoof's own hook on {@code addMessage} is {@code ChatComponentMixin}'s.
 */
@Mixin(ChatComponent.class)
public abstract class ChatSkinMixin {

    @ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue"}, constant = @Constant(intValue = 100))
    private int mineux$history(int vanilla) {
        if (!VisualBridge.on()) return vanilla;
        int kept = (int) VisualBridge.queryDouble(OP_CHAT_HISTORY);
        return Math.max(vanilla, kept);
    }

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Component mineux$chatAdd(Component message) {
        if (!VisualBridge.on()) return message;
        return VisualBridge.transform(OP_CHAT_ADD, message, Component.class);
    }

    @ModifyVariable(
        method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private ChatComponent.ChatGraphicsAccess mineux$chatDraw(ChatComponent.ChatGraphicsAccess access) {
        if (!VisualBridge.on()) return access;
        return VisualBridge.transform(OP_CHAT_DRAW, access, ChatComponent.ChatGraphicsAccess.class);
    }

    /** Each line as {@code forEachLine} takes it out, just before it is drawn. */
    @ModifyVariable(method = "forEachLine", at = @At("STORE"), ordinal = 0)
    private GuiMessage.Line mineux$chatLine(GuiMessage.Line line) {
        if (VisualBridge.on()) VisualBridge.dispatch(OP_CHAT_LINE, line);
        return line;
    }
}
