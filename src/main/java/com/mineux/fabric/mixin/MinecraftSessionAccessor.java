package com.mineux.fabric.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The account switcher's way into the session: vanilla sets these once in its constructor and
 * offers no setters, hence {@link Mutable} on the ones that are final. The user is the identity;
 * the other three are what let a switched account sign chat and file reports, because vanilla
 * built them for the session live at startup and never rebuilds them. Written only through
 * {@code FabricHost#setSession}, on the client thread.
 */
@Mixin(Minecraft.class)
public interface MinecraftSessionAccessor {

    @Mutable
    @Accessor("user")
    void mineux$setUser(User user);

    @Mutable
    @Accessor("userApiService")
    void mineux$setUserApiService(UserApiService service);

    @Mutable
    @Accessor("profileKeyPairManager")
    void mineux$setProfileKeyPairManager(ProfileKeyPairManager manager);

    @Accessor("reportingContext")
    void mineux$setReportingContext(ReportingContext context);
}
