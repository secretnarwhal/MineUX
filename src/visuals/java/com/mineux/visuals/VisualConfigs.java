package com.mineux.visuals;

import com.mineux.core.Extensions;
import com.mineux.visuals.cosmetics.CosmeticsConfig;
import com.mineux.visuals.fx.FxConfig;
import com.mineux.visuals.hand.HandConfig;
import com.mineux.visuals.hud.SkinConfig;
import com.mineux.visuals.self.SelfConfig;
import com.mineux.visuals.trail.FakeCursorConfig;
import com.mineux.visuals.trail.TrailConfig;
import com.mineux.visuals.view.CameraConfig;
import com.mineux.visuals.view.ViewConfig;
import com.mineux.visuals.world.ShaderConfig;
import com.mineux.visuals.world.TotemConfig;
import com.mineux.visuals.world.WorldConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Every config the visual modules have, by the {@code cfg=} name {@code /mineux/visuals.txt} uses,
 * with its json file under the host's config dir. {@link #register} hands each to
 * {@link Extensions#addConfig}, which makes it per workstation: every workstation keeps its own
 * copy, and a module switched on in one stays off in the others. None of these belongs to the
 * person rather than to a loadout (their keys -- zoom, freelook, inspect -- are module settings),
 * so every one is per workstation.
 * <p>
 * The entries are lambdas over the JDK's {@code Supplier}/{@code Runnable}, never an interface
 * of ours (an obfuscator a host may run over this code, such as Skidfuscator, breaks those), built in two small methods.
 */
public final class VisualConfigs {

    /** One config: its cfg= name, its file, its class, the live object, and how a write is put right and saved. */
    public record Entry(String cfg, String file, Class<?> type, Supplier<Object> live, Runnable commit) {}

    private static List<Entry> all;

    private VisualConfigs() {}

    public static synchronized List<Entry> all() {
        if (all == null) {
            List<Entry> list = new ArrayList<>();
            camera(list);
            looks(list);
            all = Collections.unmodifiableList(list);
        }
        return all;
    }

    public static Entry byCfg(String cfg) {
        for (Entry e : all()) if (e.cfg().equals(cfg)) return e;
        return null;
    }

    /** Hands every entry to core. */
    public static void register() {
        for (Entry e : all()) Extensions.addConfig(e.cfg(), e.file(), e.live(), e.commit());
    }

    private static void camera(List<Entry> list) {
        list.add(new Entry("view", "view.json", ViewConfig.class, ViewConfig::get, () -> { ViewConfig c = ViewConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("camera", "camera.json", CameraConfig.class, CameraConfig::get, () -> { CameraConfig c = CameraConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("hand", "hand.json", HandConfig.class, HandConfig::get, () -> { HandConfig c = HandConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("hudskin", "hudskin.json", SkinConfig.class, SkinConfig::get, () -> { SkinConfig c = SkinConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("fx", "fx.json", FxConfig.class, FxConfig::get, () -> { FxConfig c = FxConfig.get(); c.ensureDefaults(); c.save(); }));
    }

    private static void looks(List<Entry> list) {
        list.add(new Entry("world", "world.json", WorldConfig.class, WorldConfig::get, () -> { WorldConfig c = WorldConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("totempop", "totempop.json", TotemConfig.class, TotemConfig::get, () -> { TotemConfig c = TotemConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("shaders", "shaders.json", ShaderConfig.class, ShaderConfig::get, () -> { ShaderConfig c = ShaderConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("self", "self.json", SelfConfig.class, SelfConfig::get, () -> { SelfConfig c = SelfConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("cosmetics", "cosmetics.json", CosmeticsConfig.class, CosmeticsConfig::get, () -> { CosmeticsConfig c = CosmeticsConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("trail", "trail.json", TrailConfig.class, TrailConfig::get, () -> { TrailConfig c = TrailConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("cursor", "cursor.json", FakeCursorConfig.class, FakeCursorConfig::get, () -> { FakeCursorConfig c = FakeCursorConfig.get(); c.ensureDefaults(); c.save(); }));
    }
}
