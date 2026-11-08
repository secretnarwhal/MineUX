package com.mineux.core.ui.term;

import com.mineux.core.DeskExtensions;
import com.mineux.core.Extensions;
import com.mineux.core.rpc.RpcConfig;
import com.mineux.core.social.FriendConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Every module config there is, in one place: the settings tree ({@link Modules}, the
 * {@code cfg=} names in {@code modules.txt}) and the workstations ({@link Workstations}) both read
 * this list, so the two can no longer drift apart.
 * <p>
 * Core's own few come first ({@link #core}); after them every one added through
 * {@link Extensions#addConfig} -- the visuals in the full edition, a host's modules in a host
 * mod -- in the order they were added.
 * <p>
 * <b>Adding a module -- the rule, in this order:</b>
 * <ol>
 *   <li>Put it in a {@code modules.txt}-format file ({@link Extensions#addModuleFile}) with its
 *       {@code cfg=} name and its settings.</li>
 *   <li>Register its config with that {@code cfg=} name and its json file
 *       ({@link Extensions#addConfig}). That alone makes it per workstation: every workstation
 *       keeps its own copy, a module turned on in one stays off in the others, and a workstation
 *       saved before it existed gets its defaults (off).</li>
 * </ol>
 * Only something that belongs to the person rather than to a loadout stays out, and then by file
 * name in {@link #personal()} (a whole file, {@link DeskExtensions#addPersonal}) or
 * {@link #personalFields()} (single fields, {@link DeskExtensions#addPersonalField}), with the
 * reason beside it. {@code ConfigRegistryTest} fails for any config that is neither.
 * <p>
 * Entries are lambdas over the JDK's {@code Supplier}/{@code Runnable}, never an interface of
 * ours (an obfuscator a host may run over this code, such as Skidfuscator, breaks those).
 */
final class Configs {

    /** One config: its {@code cfg=} name, its file under the config dir, the live object, and how a write is put right and saved. */
    record Entry(String cfg, String file, Supplier<Object> live, Runnable commit) {}

    private static List<Entry> all;
    private static int seen = -1;
    private static Map<String, String> personal;
    private static Set<String> personalFields;

    private Configs() {}

    /** Every registered config, core's then the extensions', in a fixed order. */
    static synchronized List<Entry> all() {
        int added = Extensions.configs().size();
        if (all == null || seen != added) {
            List<Entry> list = new ArrayList<>();
            core(list);
            for (Extensions.ConfigEntry e : Extensions.configs()) list.add(new Entry(e.cfg(), e.file(), e.live(), e.commit()));
            all = Collections.unmodifiableList(list);
            seen = added;
        }
        return all;
    }

    /** The registered configs a workstation keeps a copy of: all but the {@link #personal} files. */
    static synchronized List<Entry> perWorkstation() {
        List<Entry> list = new ArrayList<>();
        for (Entry e : all()) if (!personal().containsKey(e.file())) list.add(e);
        return Collections.unmodifiableList(list);
    }

    /** The registered config behind a {@code cfg=} name, or null. */
    static Entry byCfg(String cfg) {
        for (Entry e : all()) if (e.cfg().equals(cfg)) return e;
        return null;
    }

    /**
     * Config files no workstation keeps, by name, and why: they belong to the person (or the
     * machine), not to a loadout, so they are the same on every workstation.
     */
    static synchronized Map<String, String> personal() {
        Map<String, String> map = new HashMap<>();
        map.put("ui.json", "how the desktop answers keys: the person's hands, not a loadout");
        map.put("accounts.json", "signed-in Minecraft accounts and their tokens");
        map.put("terminal.json", "the workstations themselves, art, history, binds");
        map.put("session.json", "the MineUX sign-in (greeter): the person's own session, never a loadout");
        map.putAll(DeskExtensions.personal());
        personal = Collections.unmodifiableMap(map);
        return personal;
    }

    /**
     * Single fields of per-workstation files that stay the person's, as {@code file:field}: a
     * switch leaves the live value alone. Records and counts that a restore would roll back,
     * window placement, and consent.
     */
    static synchronized Set<String> personalFields() {
        Set<String> set = new HashSet<>();
        // The friends network itself: going offline to your friends on a desktop switch is not a loadout.
        set.add("friends.json:enabled");
        set.add("friends.json:autoConnect");
        set.add("friends.json:sharePresence");
        set.addAll(DeskExtensions.personalFields());
        personalFields = Collections.unmodifiableSet(set);
        return personalFields;
    }

    static boolean personalField(String file, String field) {
        return personalFields().contains(file + ":" + field);
    }

    // ---- core's own -------------------------------------------------------------------------

    private static void core(List<Entry> list) {
        list.add(new Entry("friends", "friends.json", FriendConfig::get, () -> { FriendConfig c = FriendConfig.get(); c.ensureDefaults(); c.save(); }));
        list.add(new Entry("rpc", "rpc.json", RpcConfig::get, () -> { RpcConfig c = RpcConfig.get(); c.ensureDefaults(); c.save(); }));
        // The tray's group switches: which groups are off and what comes back with them. Per workstation, like the modules.
        list.add(new Entry("tray", "tray.json", TrayConfig::get, () -> { TrayConfig c = TrayConfig.get(); c.ensureDefaults(); c.save(); }));
    }
}
