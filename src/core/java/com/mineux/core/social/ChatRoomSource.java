package com.mineux.core.social;

import java.net.URI;

/**
 * A chatroom that is not MineUX's own: one more tab in {@code irc}, streamed from and said to a
 * server of the extension's, with the extension's own sign-in. Added through
 * {@code DeskExtensions.addChatRoom}, once, from an init.
 * <p>
 * The server speaks the MineUX API's chat wire: {@code GET <base>/chat/stream} is a
 * server-sent-events stream ({@code hello} with {@code nick}; {@code history}, {@code msg},
 * {@code join}, {@code part}, {@code del}, each with {@code room}), and {@code POST
 * <base>/chat/send} takes {@code {room, body, action}}. Only the frames of {@link #room} are
 * read off that stream: any other room it carries is left alone. Nothing of this room is ever
 * sent to the MineUX API, and nothing of MineUX's is sent to {@link #base}.
 * <p>
 * Its stream is its own, with its own reconnect: opened once {@code irc} has been, while
 * {@link #shown} and {@link #authorization} has something to send; closed and the tab emptied
 * when either stops, or when {@link #authorization} changes (another account), and opened again
 * under the new one. Asked from the client tick, so every method here must be cheap and must not
 * throw. An abstract class, not an interface (no lambdas: an obfuscator a host may run breaks them).
 */
public abstract class ChatRoomSource {

    /** The room's name on its server, without the {@code #}: the tab says {@code #<room>}. Fixed. */
    public abstract String room();

    /** The server's API base, no trailing slash: the stream is {@code <base>/chat/stream}. */
    public abstract String base();

    /**
     * The {@code Authorization} header's value for the stream and for saying a line
     * ({@code Bearer <token>}), or null or empty while there is no sign-in: nothing is opened.
     */
    public abstract String authorization();

    /** Whether the tab is there now. Default always; false hides it and closes its stream. */
    public boolean shown() {
        return true;
    }

    /** What the tab's hover and irc's header call where it is: the base's host by default. */
    public String site() {
        try {
            String host = URI.create(base()).getHost();
            return host == null ? base() : host;
        } catch (RuntimeException e) {
            return base();
        }
    }
}
