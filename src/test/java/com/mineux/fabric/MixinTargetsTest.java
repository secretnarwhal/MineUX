package com.mineux.fabric;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every mixin of MineUX's names something that is there in this Minecraft: its target class, each
 * injected method by name, each accessor's field, and each {@code INVOKE} target's owner and
 * method. A mixin whose target went away fails the game at startup ({@code defaultRequire = 1});
 * this fails the build instead, with no game started. Read off the mixin sources, checked by
 * reflection against the game on the test classpath; listed in mineux.mixins.json, each one.
 */
final class MixinTargetsTest {

    private static final Pattern TARGET = Pattern.compile("@Mixin\\(([\\w.]+)\\.class\\)");
    private static final Pattern METHOD = Pattern.compile("method\\s*=\\s*\"([^\"(]+)(\\([^\"]*)?\"");
    private static final Pattern ACCESSOR = Pattern.compile("@Accessor\\(\"([^\"]+)\"\\)");
    private static final Pattern INVOKE = Pattern.compile("target\\s*=\\s*\"L([\\w/$]+);(\\w+)\\(");
    private static final Pattern IMPORT = Pattern.compile("import ([\\w.]+);");

    @Test
    void everyMixinTargetExists() throws IOException, ClassNotFoundException {
        Path dir = Path.of("src", "main", "java", "com", "mineux", "fabric", "mixin");
        String config = Files.readString(Path.of("src", "main", "resources", "mineux.mixins.json"));
        List<String> bad = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                if (!config.contains("\"" + name + "\"")) bad.add(name + ": not listed in mineux.mixins.json");
                String src = Files.readString(file);
                Matcher t = TARGET.matcher(src);
                if (!t.find()) {
                    bad.add(name + ": no @Mixin target");
                    continue;
                }
                Class<?> target = resolve(src, t.group(1));
                Matcher m = METHOD.matcher(src);
                while (m.find()) {
                    checked++;
                    if (!hasMethod(target, m.group(1))) bad.add(name + ": " + target.getName() + " has no method " + m.group(1));
                }
                Matcher a = ACCESSOR.matcher(src);
                while (a.find()) {
                    checked++;
                    if (!hasField(target, a.group(1))) bad.add(name + ": " + target.getName() + " has no field " + a.group(1));
                }
                Matcher i = INVOKE.matcher(src);
                while (i.find()) {
                    checked++;
                    Class<?> owner = Class.forName(i.group(1).replace('/', '.'), false, getClass().getClassLoader());
                    if (!hasMethod(owner, i.group(2))) bad.add(name + ": " + owner.getName() + " has no method " + i.group(2));
                }
            }
        }
        assertTrue(checked > 8, "found the injection points: " + checked);
        assertTrue(bad.isEmpty(), bad.toString());
    }

    private Class<?> resolve(String src, String simple) throws ClassNotFoundException {
        Matcher imports = IMPORT.matcher(src);
        while (imports.find()) {
            String full = imports.group(1);
            if (full.endsWith("." + simple)) return Class.forName(full, false, getClass().getClassLoader());
        }
        return Class.forName(simple, false, getClass().getClassLoader());
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name)) return true;
        }
        return false;
    }

    private static boolean hasField(Class<?> type, String name) {
        for (Field f : type.getDeclaredFields()) if (f.getName().equals(name)) return true;
        return false;
    }
}
