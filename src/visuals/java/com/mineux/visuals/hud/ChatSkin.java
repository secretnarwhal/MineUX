package com.mineux.visuals.hud;

import com.mineux.visuals.mixin.ChatListsAccessor;
import com.mineux.visuals.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The client's chat, drawn by the game's own chat with a few things changed: a timestamp on
 * every line, a message the same as one of the last few folded into it with a count, a thousand
 * lines kept instead of a hundred, and the lines' background, accent and the slide-in of a new
 * line done by {@link ChatDraw}.
 */
public final class ChatSkin {

    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H24S = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);

    /** The lines this made, with what each said before the stamp and count and how many it stands for. */
    private static final int KEPT = 64;
    private static final Component[] MADE = new Component[KEPT];
    private static final String[] BASES = new String[KEPT];
    private static final int[] COUNTS = new int[KEPT];
    private static int next;

    /** The line the game is laying out now, for {@link ChatDraw}. */
    static GuiMessage.Line line;

    private ChatSkin() {}

    static boolean on() {
        return SkinConfig.get().chat;
    }

    /** How many lines to keep: 0 for the game's own hundred. */
    public static double historyLines() {
        SkinConfig config = SkinConfig.get();
        return config.chat && config.chatHistory ? 1000.0 : 0.0;
    }

    public static void onLine(Object value) {
        line = value == null || !GuiMessage.Line.class.isInstance(value) ? null : (GuiMessage.Line) value;
    }

    /** A message coming in: stamped, and folded into the same one a moment ago with a count. */
    public static Component onAdd(Component content) {
        SkinConfig config = SkinConfig.get();
        if (!config.chat || content == null) return content;
        String base = content.getString();
        int count = 1;
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        if (config.chatAntiSpam && !base.isBlank() && ChatListsAccessor.class.isInstance(chat)) {
            count = fold((ChatListsAccessor) chat, base, config.chatSpamRange);
        }
        MutableComponent out = Component.empty();
        if (config.chatTimestamps) {
            out.append(Component.literal("[" + stamp(config.chatTimeFormat) + "] ").withStyle(ChatFormatting.DARK_GRAY));
        }
        out.append(content);
        if (count > 1) {
            out.append(Component.literal(" [x" + count + "]")
                .withStyle(style -> style.withColor(TextColor.fromRgb(Theme.GOLD & 0xFFFFFF))));
        }
        Component made = out.getSiblings().size() == 1 ? content : out;
        MADE[next] = made;
        BASES[next] = base;
        COUNTS[next] = count;
        next = (next + 1) % KEPT;
        return made;
    }

    /**
     * Takes the last message saying {@code base}, within {@code range} of the newest, out of
     * chat; the count the new one comes in with.
     */
    private static int fold(ChatListsAccessor lists, String base, int range) {
        List<GuiMessage> all = lists.mineux$allMessages();
        int reach = Math.min(Math.max(1, range), all.size());
        for (int i = 0; i < reach; i++) {
            GuiMessage old = all.get(i);
            int at = made(old.content());
            String said = at >= 0 ? BASES[at] : old.content().getString();
            if (!Objects.equals(said, base)) continue;
            int count = (at >= 0 ? COUNTS[at] : 1) + 1;
            all.remove(i);
            lists.mineux$trimmedMessages().removeIf(l -> l.parent() == old);
            return count;
        }
        return 1;
    }

    private static int made(Component content) {
        for (int i = 0; i < KEPT; i++) {
            if (MADE[i] == content) return i;
        }
        return 0 - 1;
    }

    private static String stamp(String format) {
        DateTimeFormatter f = Objects.equals("24hs", format) ? H24S : Objects.equals("12h", format) ? H12 : H24;
        return LocalTime.now().format(f);
    }

    /** The game's drawing access, wrapped for the background and the slide; as it was with chat off. */
    public static Object wrap(Object access) {
        if (!on() || access == null || !ChatComponent.ChatGraphicsAccess.class.isInstance(access) || ChatDraw.class.isInstance(access)) return access;
        return new ChatDraw((ChatComponent.ChatGraphicsAccess) access);
    }
}
