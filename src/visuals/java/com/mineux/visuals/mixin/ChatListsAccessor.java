package com.mineux.visuals.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Chat's two lists, the messages and the lines they were broken into, for the client's chat to
 * take a repeated message out before it comes in again with a count. See {@code ChatSkinMixin}.
 */
@Mixin(ChatComponent.class)
public interface ChatListsAccessor {
    @Accessor("allMessages")
    List<GuiMessage> mineux$allMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> mineux$trimmedMessages();
}
