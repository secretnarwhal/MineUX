package com.mineux.core.ui.term;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineux.core.Extensions;
import com.mineux.core.TestModule;
import com.mineux.core.config.ConfigFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard on the rule in {@link Configs}: every module's config -- core's own and every one
 * added through {@link Extensions#addConfig} -- is per workstation unless it is named, with a
 * reason, as the person's. Fails for a {@code cfg=} in any module file, a registered config or a
 * config file anywhere in core or the visuals that is neither.
 */
class ConfigRegistryTest {

    @BeforeAll
    static void setUp() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        TestModule.install();
    }

    private static final Pattern CFG = Pattern.compile("\\bcfg=([A-Za-z0-9_.]+)");
    private static final Pattern CONFIG_FILE = Pattern.compile("new ConfigFile<>\\(\\s*[\\w.]+\\.class\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern RESOLVED_JSON = Pattern.compile("resolve\\(\"([A-Za-z0-9_\\-]+\\.json)\"\\)");

    private static Set<String> perWorkstationFiles() {
        Set<String> files = new HashSet<>();
        for (Configs.Entry e : Configs.perWorkstation()) files.add(e.file());
        return files;
    }

    private static Set<String> registeredFiles() {
        Set<String> files = new HashSet<>();
        for (Configs.Entry e : Configs.all()) files.add(e.file());
        return files;
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = Modules.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyCfgInEveryModuleFileIsPerWorkstationOrNamedPersonal() throws IOException {
        StringBuilder text = new StringBuilder(read("/mineux/modules.txt"));
        for (String file : Extensions.moduleFiles()) text.append('\n').append(read(file));
        List<String> bad = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        for (String line : text.toString().split("\n")) {
            if (line.startsWith("#")) continue;
            Matcher m = CFG.matcher(line);
            while (m.find()) seen.add(m.group(1));
        }
        assertTrue(seen.containsAll(List.of("rpc", "friends", "testmod", "testmod.part")), "found the cfg= names: " + seen);
        for (String cfg : seen) {
            if (cfg.equals("none")) continue;
            Modules.Binding binding = Modules.binding(cfg);
            if (binding == null) {
                bad.add(cfg + ": no binding (neither a config nor a config part)");
                continue;
            }
            Object owner = binding.owner().get();
            if (Workstations.keeps(owner)) continue;
            String top = cfg.contains(".") ? cfg.substring(0, cfg.indexOf('.')) : cfg;
            Configs.Entry entry = Configs.byCfg(top);
            if (entry == null) bad.add(cfg + ": not registered");
            else if (!Configs.personal().containsKey(entry.file())) bad.add(cfg + ": registered but not kept by workstations");
        }
        assertTrue(bad.isEmpty(), "cfg= names neither per workstation nor personal: " + bad);
    }

    @Test
    void extensionConfigsAreInTheList() {
        assertNotNull(Configs.byCfg("testmod"), "Extensions.addConfig reaches Configs");
        assertTrue(perWorkstationFiles().contains("testmod.json"), "an extension's config is per workstation");
        assertTrue(Configs.personal().containsKey("testrecord.json"), "DeskExtensions.addPersonal reaches Configs");
        assertTrue(Configs.personalField("testmod.json", "uses"), "DeskExtensions.addPersonalField reaches Configs");
    }

    @Test
    void everyRegisteredConfigIsPerWorkstationOrNamedPersonal() {
        Set<String> cfgs = new HashSet<>();
        Set<String> files = new HashSet<>();
        for (Configs.Entry e : Configs.all()) {
            assertTrue(cfgs.add(e.cfg()), "cfg name registered twice: " + e.cfg());
            assertTrue(files.add(e.file()), "file registered twice: " + e.file());
            boolean kept = perWorkstationFiles().contains(e.file());
            assertTrue(kept != Configs.personal().containsKey(e.file()), e.file() + " is either kept or personal");
        }
    }

    /** The file each entry names is the one its config class really reads and writes. */
    @Test
    void registeredFileNamesMatchTheConfigClasses() throws ReflectiveOperationException {
        for (Configs.Entry e : Configs.all()) {
            Object live = e.live().get();
            assertNotNull(live, e.cfg());
            Field file;
            try {
                file = live.getClass().getDeclaredField("FILE");
            } catch (NoSuchFieldException none) {
                continue;
            }
            file.setAccessible(true);
            Object cf = file.get(null);
            if (!(cf instanceof ConfigFile<?> configFile)) continue;
            assertEquals(e.file(), configFile.path().getFileName().toString(), e.cfg() + "'s file");
        }
    }

    /** Every json config file core or the visuals declare anywhere is registered or named personal. */
    @Test
    void everyConfigFileInCoreAndVisualsIsAccountedFor() throws IOException {
        Set<String> declared = new TreeSet<>();
        for (String set : new String[] {"core", "visuals", "screens"}) {
            Path root = Path.of("src", set, "java");
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(p);
                    Matcher a = CONFIG_FILE.matcher(src);
                    while (a.find()) declared.add(a.group(1));
                    Matcher b = RESOLVED_JSON.matcher(src);
                    while (b.find()) declared.add(b.group(1));
                }
            }
        }
        assertTrue(declared.contains("friends.json") && declared.contains("ui.json"), "the scan found configs: " + declared);
        List<String> bad = new ArrayList<>();
        for (String file : declared) {
            if (!registeredFiles().contains(file) && !Configs.personal().containsKey(file)) bad.add(file);
        }
        assertTrue(bad.isEmpty(), "config files neither registered nor personal: " + bad);
    }

    @Test
    void personalFieldsNameRealFields() throws ReflectiveOperationException {
        for (String key : Configs.personalFields()) {
            String file = key.substring(0, key.indexOf(':'));
            String field = key.substring(key.indexOf(':') + 1);
            Configs.Entry entry = null;
            for (Configs.Entry e : Configs.perWorkstation()) if (e.file().equals(file)) entry = e;
            assertNotNull(entry, key + ": a per-workstation file");
            Field f = entry.live().get().getClass().getField(field);
            assertTrue(!Modifier.isStatic(f.getModifiers()), key);
        }
    }

    /**
     * A workstation saved before a config existed has no copy of it: entering it gives the
     * module its defaults (off), not whatever was on in the workstation left.
     */
    @Test
    void missingFileRestoresDefaultsNotTheLiveValue() throws ReflectiveOperationException {
        for (Configs.Entry e : Configs.perWorkstation()) {
            Class<?> type = e.live().get().getClass();
            Object fresh = construct(type);
            Object live = construct(type);
            for (Field f : type.getFields()) {
                if (f.getType() == boolean.class && plain(f)) f.setBoolean(live, !f.getBoolean(live));
            }
            Workstations.load(e.file(), live, new JsonObject());
            for (Field f : type.getFields()) {
                if (f.getType() != boolean.class || !plain(f)) continue;
                boolean expected = Configs.personalField(e.file(), f.getName()) ? !f.getBoolean(fresh) : f.getBoolean(fresh);
                assertEquals(expected, f.getBoolean(live), e.file() + ":" + f.getName());
            }
        }
    }

    @Test
    void nestedSettingsAreCopiedIntoTheLiveObject() throws ReflectiveOperationException {
        TestModule live = new TestModule();
        Object part = live.part;
        JsonObject json = JsonParser.parseString("{\"part\":{\"on\":true}}").getAsJsonObject();
        Workstations.load("testmod.json", live, json);
        assertSame(part, live.part, "whatever holds testmod.part keeps reading the live one");
        assertTrue(live.part.on, "the part's own values come from the workstation");
    }

    @Test
    void personalFieldsSurviveASwitch() throws ReflectiveOperationException {
        TestModule live = new TestModule();
        live.uses = 7;
        live.enabled = true;
        JsonObject json = JsonParser.parseString("{\"uses\":1,\"enabled\":false}").getAsJsonObject();
        Workstations.load("testmod.json", live, json);
        assertEquals(7, live.uses, "a record is not rolled back");
        assertEquals(false, live.enabled, "the switch follows the workstation");
    }

    private static boolean plain(Field f) {
        int m = f.getModifiers();
        return !Modifier.isStatic(m) && !Modifier.isFinal(m) && !Modifier.isTransient(m);
    }

    private static Object construct(Class<?> type) throws ReflectiveOperationException {
        Constructor<?> c = type.getDeclaredConstructor();
        c.setAccessible(true);
        return c.newInstance();
    }
}
