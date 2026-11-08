package com.mineux.screens.render;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.lang.reflect.Method;

/**
 * {@code RenderType.create} is package-private in 26.2 (Fabric API widens it; MineUX has no
 * Fabric API and no access widener), so it is called by reflection: 26.2 runs unobfuscated, the
 * name is the same in dev and in a release, and it works the same from a host's obfuscated build.
 */
final class Types {

    private static Method create;

    private Types() {}

    static RenderType create(String name, RenderSetup setup) {
        try {
            if (create == null) {
                Method m = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
                m.setAccessible(true);
                create = m;
            }
            return (RenderType) create.invoke(null, name, setup);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RenderType.create is not reachable in this game version", e);
        }
    }
}
