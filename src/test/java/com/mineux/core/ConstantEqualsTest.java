package com.mineux.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No string literal and no ALL_CAPS constant is the receiver of {@code equals} or
 * {@code equalsIgnoreCase} in core, screens or visuals. A host may build those into itself and
 * obfuscate them with Skidfuscator, and Skidfuscator rewrites {@code CONST.equals(x)} into a hash
 * of {@code x}: a null {@code x}, false in plain Java, is a NullPointerException there (the home
 * desk's {@code LyConfig.load} on a dotfile without a window key). Write
 * {@code x != null && x.equals(CONST)}. {@code Boolean.TRUE}/{@code Boolean.FALSE} are not
 * strings and are allowed. Read off the sources with comments dropped and string contents blanked.
 */
final class ConstantEqualsTest {

    private static final List<Path> ROOTS = List.of(Path.of("src", "core"), Path.of("src", "screens"), Path.of("src", "visuals"));

    /** A string literal (blanked to {@code ""}) right before {@code .equals(}. */
    private static final Pattern LITERAL = Pattern.compile("\"\"\\s*\\.\\s*equals(?:IgnoreCase)?\\s*\\(");
    /** An ALL_CAPS name, its qualifier kept to tell {@code Boolean.TRUE} apart. */
    private static final Pattern CONSTANT = Pattern.compile(
        "(?<![\\w$.])((?:[\\w$]+\\s*\\.\\s*)*)([A-Z][A-Z0-9_]*)\\s*\\.\\s*equals(?:IgnoreCase)?\\s*\\(");

    @Test
    void noConstantReceiverOfEquals() throws IOException {
        List<String> bad = new ArrayList<>();
        int files = 0;
        for (Path root : ROOTS) {
            assertTrue(Files.isDirectory(root), root + " is missing; run the tests from the project directory");
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    files++;
                    for (String spot : spots(Files.readString(file))) bad.add(file + ":" + spot);
                }
            }
        }
        assertTrue(files > 100, "only " + files + " sources found under " + ROOTS);
        assertTrue(bad.isEmpty(), "a constant as the receiver of equals -- Skidfuscator hashes the argument, "
            + "so a null one throws; write x != null && x.equals(CONST):\n  " + String.join("\n  ", bad));
    }

    @Test
    void scannerFindsWhatItShould() {
        assertEquals(1, spots("boolean a = \"x\".equals(s);").size());
        assertEquals(1, spots("boolean a = \"x\" .equalsIgnoreCase( s);").size());
        assertEquals(1, spots("boolean a = MAXIMIZE.equals(window(v));").size());
        assertEquals(1, spots("boolean a = LyConfig.DOOM.equals(ly.animation);").size());
        assertEquals(1, spots("boolean a = com.mineux.X.SPECIAL\n    .equalsIgnoreCase(n);").size());
        assertEquals(0, spots("boolean a = Boolean.TRUE.equals(o) || Boolean.FALSE.equals(o);").size());
        assertEquals(0, spots("boolean a = s != null && s.equals(MAXIMIZE) && t.equals(\"x\");").size());
        assertEquals(0, spots("boolean a = foo.BAR_X.size() > 0 && Objects.equals(A, b);").size());
        assertEquals(0, spots("// \"x\".equals(s)\n/* MAX.equals(s) */ String t = \"MAX.equals(s) \\\" \".trim();").size());
        assertEquals(0, spots("char c = '\"'; String t = \"\"\"\n  KEY.equals(s)\n  \"\"\"; boolean a = s.equals(t);").size());
    }

    /** Each offending spot in {@code src} as {@code line: text}. */
    static List<String> spots(String src) {
        String code = blank(src);
        List<String> out = new ArrayList<>();
        Matcher m = LITERAL.matcher(code);
        while (m.find()) out.add(spot(code, m.start(), m.group()));
        m = CONSTANT.matcher(code);
        while (m.find()) {
            String qualifier = m.group(1).replaceAll("\\s", "");
            String name = m.group(2);
            if (qualifier.equals("Boolean.") && (name.equals("TRUE") || name.equals("FALSE"))) continue;
            out.add(spot(code, m.start(), m.group()));
        }
        return out;
    }

    private static String spot(String code, int at, String text) {
        int line = 1;
        for (int i = 0; i < at; i++) if (code.charAt(i) == '\n') line++;
        return line + ": " + text.replaceAll("\\s+", " ");
    }

    /**
     * The source with comments turned to spaces and every string, text block and char literal
     * emptied ({@code ""}, {@code ''}), newlines kept so line numbers still match.
     */
    static String blank(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                keepNewlines(src, i, end, out);
                i = end;
            } else if (src.startsWith("\"\"\"", i)) {
                int end = src.indexOf("\"\"\"", i + 3);
                while (end > 0 && src.charAt(end - 1) == '\\') end = src.indexOf("\"\"\"", end + 1);
                end = end < 0 ? n : end + 3;
                out.append("\"\"");
                keepNewlines(src, i, end, out);
                i = end;
            } else if (c == '"' || c == '\'') {
                int k = i + 1;
                while (k < n && src.charAt(k) != c && src.charAt(k) != '\n') k += src.charAt(k) == '\\' ? 2 : 1;
                out.append(c).append(c);
                i = Math.min(n, k + 1);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void keepNewlines(String src, int from, int to, StringBuilder out) {
        for (int k = from; k < to; k++) out.append(src.charAt(k) == '\n' ? '\n' : ' ');
    }
}
