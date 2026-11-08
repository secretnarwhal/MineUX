package com.mineux.screens.render;

import java.lang.reflect.Method;

/**
 * Whether an Iris shader pack is in use, asked by reflection: MineUX does not depend on Iris,
 * and a shader pack does not know our shader, so screens then use the game's entity shader.
 */
final class Iris {

    private static boolean looked;
    private static Object api;
    private static Method inUse;

    private Iris() {}

    static boolean shaderPackActive() {
        if (!looked) find();
        if (api == null) return false;
        try {
            return Boolean.TRUE.equals(inUse.invoke(api));
        } catch (ReflectiveOperationException | RuntimeException e) {
            api = null;
            return false;
        }
    }

    private static void find() {
        looked = true;
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            api = null;
        }
    }
}
