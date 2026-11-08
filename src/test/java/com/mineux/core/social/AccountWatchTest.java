package com.mineux.core.social;

import com.mineux.core.net.MuxApi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sign-out (MuxApi's generation moving) drops what the old account was shown and leaves the
 * streams closed until the next sign-in. Offline: signed out, nothing is opened.
 */
class AccountWatchTest {

    @Test
    void signOutDropsTheOldAccountsConversations() {
        MuxApi.signOut();
        AccountWatch.check();
        Chat.conversation("someone");
        assertTrue(Chat.channels().stream().anyMatch(c -> c.dm), "a conversation tab is open");

        MuxApi.signIn("first", "token-1");
        MuxApi.signOut();
        AccountWatch.check();

        assertFalse(Chat.channels().stream().anyMatch(c -> c.dm), "the old account's conversations went");
        assertEquals(List.of(Chat.PUBLIC), Chat.channels().stream().filter(c -> c.source == null).map(c -> c.name).toList(), "MineUX's one room stays");
        assertEquals("", FriendList.selfUser());
        assertFalse(FriendNet.started(), "signed out: the friends stream waits for a sign-in");
        assertFalse(ChatNet.running(), "signed out: the chat stream waits for a sign-in");
        assertEquals(0, Notices.unread());
    }
}
