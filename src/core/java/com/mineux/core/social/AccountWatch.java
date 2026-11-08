package com.mineux.core.social;

import com.mineux.core.net.MuxApi;

/**
 * The social client following the MineUX account: when {@link MuxApi#generation()} moves (a
 * sign-in, a sign-out, another account), the friends and chat streams opened with the old token
 * are closed, everything the old account was shown (friends, requests, conversations, the bell)
 * is dropped, and the streams that were open are opened again under the new one. A sign-out
 * leaves them closed: they wait for a sign-in. Checked once a client tick from {@link FriendTick}.
 */
final class AccountWatch {

    private static int seen = Integer.MIN_VALUE;

    private AccountWatch() {}

    static void check() {
        int now = MuxApi.generation();
        if (now == seen) return;
        boolean first = seen == Integer.MIN_VALUE;
        seen = now;
        // The first look of a launch: nothing was opened under another account yet.
        if (first) return;
        changed();
    }

    /** Down with the old account's streams and what they said; up again with the new one's. */
    static void changed() {
        boolean friends = FriendNet.started();
        boolean chat = ChatNet.running();
        FriendNet.stop();
        ChatNet.stop();
        FriendList.forget();
        Chat.forget();
        Notices.forget();
        if (!MuxApi.signedIn()) return;
        if (friends) FriendNet.start();
        if (chat) ChatNet.connect();
        Notices.refresh();
    }
}
