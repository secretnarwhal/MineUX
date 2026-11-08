package com.mineux.visuals.cosmetics;

/**
 * One cosmetic: builds its quads into the builder it is handed, in the frame the builder was set
 * to. Implemented by classes, never by a lambda -- the obfuscation a host may apply breaks lambdas for
 * its own interfaces.
 */
public interface Cosmetic {
    void build(Builder b);
}
