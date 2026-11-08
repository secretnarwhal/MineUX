package com.mineux.fabric;

import com.mineux.core.Host;
import com.mineux.core.MineUX;
import com.mineux.fabric.mixin.MinecraftSessionAccessor;
import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

/**
 * MineUX's entry point: starts core on {@link FabricHost}, and the visual modules when this jar
 * is the full edition. Which edition it is is simply whether the visuals' classes are in the
 * jar: the lite jar is built without them.
 */
public final class MineUXMod implements ClientModInitializer {

    private static final String VISUALS = "com.mineux.visuals.Visuals";

    @Override
    public void onInitializeClient() {
        Class<?> visuals = visuals();
        MineUX.init(new FabricHost(visuals != null ? MineUX.FULL : MineUX.LITE));
        if (visuals == null) return;
        try {
            visuals.getMethod("install").invoke(null);
        } catch (InvocationTargetException e) {
            MineUX.LOGGER.error("The visual modules failed to start", e.getCause());
        } catch (ReflectiveOperationException e) {
            MineUX.LOGGER.error("The visual modules could not be started", e);
        }
    }

    private static Class<?> visuals() {
        try {
            return Class.forName(VISUALS, false, MineUXMod.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** MineUX's own host: Fabric's config folder and the MineUX brand. */
    static final class FabricHost extends Host {
        private final String edition;

        FabricHost(String edition) {
            this.edition = edition;
        }

        @Override
        public String edition() {
            return edition;
        }

        @Override
        public Path configDir() {
            return FabricLoader.getInstance().getConfigDir().resolve("mineux");
        }

        @Override
        public String brand() {
            return "mineux";
        }

        @Override
        public boolean setSession(User user, UserApiService service, ProfileKeyPairManager keys, ReportingContext reporting) {
            Object mc = Minecraft.getInstance();
            if (mc == null) return false;
            MinecraftSessionAccessor session = (MinecraftSessionAccessor) mc;
            session.mineux$setUser(user);
            session.mineux$setUserApiService(service);
            session.mineux$setProfileKeyPairManager(keys);
            session.mineux$setReportingContext(reporting);
            return true;
        }

        @Override
        public String toString() {
            return "MineUX (" + edition + ")";
        }
    }
}
