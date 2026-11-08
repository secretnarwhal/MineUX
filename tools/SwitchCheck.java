import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The switch rules for code a host may build into itself and obfuscate (core, visuals, screens),
 * checked on the compiled classes.
 * <p>
 * <b>No string switch.</b> javac compiles {@code switch (text)} into {@code text.hashCode()}
 * and a switch on that hash, then a second switch on the case found; the obfuscator turns every
 * string case into a block of decryption code and a switch over enough of them pushes a method
 * past the JVM's 64KB limit, which writes the class out unobfuscated and broken. Pattern
 * switches ({@code typeSwitch}/{@code enumSwitch} bootstraps) and switches over our own enums
 * (an {@code ordinal()} or a {@code $SwitchMap$}) are refused too: the obfuscator renames enum
 * constants and their {@code values()}. Write an if chain over {@code Objects.equals}, or a
 * {@code Words} set, instead.
 * <p>
 * <b>Second mode, for an obfuscating build.</b> Given two jars, the plain and the obfuscated one, it checks
 * that Skidfuscator left the switches alone: its {@code flowSwitch} rewrites {@code switch (x)}
 * as a switch on a hash that is not one-to-one, so a value that is no case can run a case's code.
 * The obfuscated jar must keep at least as many untouched switches as the plain one has.
 * <p>
 * Usage: {@code java -cp "asm.jar;asm-tree.jar" tools/SwitchCheck.java build/classes/java/core [more dirs]}
 * or {@code ... tools/SwitchCheck.java plain.jar obfuscated.jar}. Exits 1 on a finding.
 */
public class SwitchCheck {

    public static void main(String[] args) throws IOException {
        if (args.length == 2 && args[0].endsWith(".jar") && args[1].endsWith(".jar")) {
            compareJars(args[0], args[1]);
            return;
        }
        List<String> found = new ArrayList<>();
        for (String arg : args.length == 0 ? new String[] {"build/classes/java/core"} : args) {
            Path root = Path.of(arg);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    ClassNode node = read(Files.readAllBytes(file));
                    for (MethodNode method : node.methods) scan(node, method, found);
                }
            }
        }
        for (String line : found) System.out.println(line);
        if (!found.isEmpty()) {
            System.out.println(found.size() + " switch(es) the obfuscator cannot take: use an if chain or a Words set");
            System.exit(1);
        }
        System.out.println("SwitchCheck: no string, pattern or enum switches");
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        return node;
    }

    private static void scan(ClassNode owner, MethodNode method, List<String> found) {
        String where = owner.name.replace('/', '.') + "." + method.name;
        AbstractInsnNode last = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() < 0) continue;
            boolean isSwitch = insn instanceof LookupSwitchInsnNode || insn instanceof TableSwitchInsnNode;
            if (isSwitch && last instanceof MethodInsnNode call) {
                if (call.owner.equals("java/lang/String") && call.name.equals("hashCode")) {
                    found.add(where + ": a switch on a String (line " + line(insn) + ")");
                } else if (call.name.equals("ordinal") && call.desc.equals("()I") && !call.owner.startsWith("java/")) {
                    found.add(where + ": a switch on " + call.owner.replace('/', '.') + "'s ordinal (line " + line(insn) + ")");
                }
            }
            if (insn instanceof InvokeDynamicInsnNode indy && indy.bsm.getOwner().equals("java/lang/runtime/SwitchBootstraps")) {
                found.add(where + ": a pattern or enum switch (" + indy.bsm.getName() + ", line " + line(insn) + ")");
            }
            if (insn.getOpcode() == Opcodes.GETSTATIC && insn instanceof org.objectweb.asm.tree.FieldInsnNode field
                && field.name.startsWith("$SwitchMap$")) {
                found.add(where + ": a switch over an enum (" + field.name + ")");
            }
            last = insn;
        }
    }

    private static int line(AbstractInsnNode insn) {
        for (AbstractInsnNode at = insn; at != null; at = at.getPrevious()) {
            if (at instanceof org.objectweb.asm.tree.LineNumberNode n) return n.line;
        }
        return -1;
    }

    // ---- an obfuscating build's mode: the plain and the obfuscated jar -------------------------

    private static void compareJars(String plainJar, String obfuscatedJar) throws IOException {
        int[] plain = count(plainJar);
        int[] obfuscated = count(obfuscatedJar);
        int ours = plain[0] + plain[1];
        System.out.println(ours + " switches in the plain jar; " + obfuscated[0] + " untouched in the obfuscated one ("
            + obfuscated[1] + " more keyed by the obfuscator's hash)");
        if (obfuscated[0] < ours) {
            System.out.println((ours - obfuscated[0]) + " switches were rewritten to switch on a hash of their value: flowSwitch must be off");
            System.exit(1);
        }
    }

    /** The switches in a jar: those keyed by anything at all, and those keyed by a static int-to-int call's answer. */
    private static int[] count(String jar) throws IOException {
        int[] found = new int[2];
        try (ZipFile zip = new ZipFile(jar)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = read(zip.getInputStream(entry).readAllBytes());
                for (MethodNode method : node.methods) {
                    AbstractInsnNode last = null;
                    for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn.getOpcode() < 0) continue;
                        if (insn instanceof LookupSwitchInsnNode || insn instanceof TableSwitchInsnNode) {
                            boolean hashed = last instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                                && call.desc.equals("(I)I");
                            found[hashed ? 1 : 0]++;
                        }
                        last = insn;
                    }
                }
            }
        }
        return found;
    }
}
