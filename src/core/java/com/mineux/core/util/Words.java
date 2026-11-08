package com.mineux.core.util;

import java.util.Set;

/**
 * Word sets, for matching a string against a handful of names without a {@code switch}.
 * <p>
 * <b>Why not a switch.</b> Skidfuscator's flow obfuscation miscompiles a {@code switch} on a
 * string at random: in one obfuscated build the main menu's {@code menu.singleplayer} case
 * fell through to the next one and came out {@code multiplayer}, while the same source,
 * obfuscated on another run, was fine. The string switches were converted to
 * these, to plain {@code Objects.equals} chains, and to lookup tables; {@link Set#contains}
 * does its comparing inside the JDK, which is never obfuscated. It costs what the switch did:
 * the string's cached hash code, and one {@code equals}.
 * <p>
 * Built from one space-separated string rather than a literal per word because the
 * obfuscator turns every string literal into decryption code, and enough of them in one
 * method push it past the JVM's 64KB method limit.
 */
public final class Words {

    private Words() {}

    /** The words of {@code words}, split on single spaces. No duplicates. */
    public static Set<String> of(String words) {
        return Set.of(words.split(" "));
    }
}
