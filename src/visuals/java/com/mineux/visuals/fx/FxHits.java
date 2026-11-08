package com.mineux.visuals.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which of your hits this tick were crits, told the way the game tells it.
 * <p>
 * For a hit of yours the server writes, back to back in the one attack: the damage event naming
 * you as its cause, then -- only for a crit -- the crit stars on whoever you hit (an animate
 * packet, action 4), then -- only for an enchanted weapon -- its sparkles (action 5). The client
 * plays a tick's packets together before the tick, so a hit still waiting at the end of the tick
 * with no crit stars was not a crit. The animate packet does not say whose crit it is; stars on
 * someone with no hit of yours waiting are someone else's.
 * <p>
 * Against a player the client also draws the crit itself the moment you swing, before the server
 * says anything ({@code LocalPlayer#crit}, then {@code #magicCrit} in the same attack). Those
 * are always yours, and the crit stars just before the sparkles say the sparkles are a crit's.
 */
final class FxHits {

    /** Hit target id -> whether its crit stars have come yet. */
    private final Map<Integer, Boolean> waiting = new HashMap<>();

    /** The last target the client drew a crit on itself, and the player tick it did. */
    private int ownCritOn = Integer.MIN_VALUE;
    private int ownCritAt = Integer.MIN_VALUE;

    /** A damage event of yours on {@code id}: a hit, a crit or not as the next packets say. */
    void hit(int id) {
        waiting.put(Integer.valueOf(id), Boolean.FALSE);
    }

    /**
     * The server's crit stars on {@code id}: -1 when no hit of yours on it is waiting (not
     * yours), 0 when they make your hit a crit just now, 1 when it was a crit already.
     */
    int crit(int id) {
        Integer key = Integer.valueOf(id);
        Boolean was = waiting.get(key);
        if (was == null) return -1;
        if (was.booleanValue()) return 1;
        waiting.put(key, Boolean.TRUE);
        return 0;
    }

    /** The server's enchanted-hit sparkles on {@code id}: -1 not yours, 0 your plain hit, 1 your crit. */
    int magic(int id) {
        Boolean was = waiting.get(Integer.valueOf(id));
        if (was == null) return -1;
        if (was.booleanValue()) return 1;
        return 0;
    }

    boolean any() {
        return !waiting.isEmpty();
    }

    /** End of the tick: the targets of the hits that got no crit stars, and every hit forgotten. */
    List<Integer> flush() {
        List<Integer> plain = new ArrayList<>();
        for (Map.Entry<Integer, Boolean> entry : waiting.entrySet()) {
            if (!entry.getValue().booleanValue()) plain.add(entry.getKey());
        }
        waiting.clear();
        return plain;
    }

    /** The client drew crit stars itself on {@code id}, on player tick {@code tick}. */
    void ownCrit(int id, int tick) {
        ownCritOn = id;
        ownCritAt = tick;
    }

    /** Whether the client's own sparkles on {@code id} on {@code tick} came with its own crit stars. */
    boolean ownCritOf(int id, int tick) {
        return ownCritOn == id && ownCritAt == tick;
    }

    void clear() {
        waiting.clear();
        ownCritOn = Integer.MIN_VALUE;
        ownCritAt = Integer.MIN_VALUE;
    }
}
