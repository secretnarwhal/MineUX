import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds, in the compiled core's bytecode, the methods Skidfuscator may freeze the game with.
 * <p>
 * Found 2026-10-10 (two builds in a row, both frozen). Skidfuscator's flow obfuscation guards
 * blocks with checks that can never fail: it takes a static method with primitive parameters and
 * an {@code int} result -- from the JDK ({@code Integer.bitCount}) or <b>from the obfuscated
 * code itself</b> (a colour mixer) -- runs it once while obfuscating, and writes
 * {@code if (method(key) != <what it got>) for (;;);} into other methods all over the jar. It
 * does not ask whether the method gives the same answer twice. One build took
 * a {@code poisson(int)} helper, which draws a random number, got 49, and wrote that into
 * a method run on the client thread: the first arm swing after joining a server drew 48
 * and the render thread never came back. Which methods are taken is drawn again on every run,
 * which is why the same source froze in one build and not the next, and why the deploy's
 * self-test -- which never reaches most guarded blocks -- says nothing.
 * <p>
 * So every method of that shape has to be a pure function of its arguments. This reports the
 * ones that are not: a call to anything but arithmetic or another clean method of the checked code,
 * a read of a field that is not a constant, a write, an object made. The fix is to take the
 * method out of the shape -- hand it the object it reads from (the {@code Random}, the config,
 * the window) as a parameter, or make it an instance method -- never to quieten this.
 * <p>
 * Usage: {@code java -cp "asm-9.9.jar;asm-tree-9.9.jar" tools/PureCheck.java build/classes/java/core}
 * Exits 1 when it finds any.
 */
public class PureCheck {

    /** JDK classes whose static methods are arithmetic on their arguments. */
    private static final Set<String> ARITHMETIC = Set.of(
        "java/lang/Math", "java/lang/StrictMath", "java/lang/Integer", "java/lang/Long", "java/lang/Float",
        "java/lang/Double", "java/lang/Character", "java/lang/Short", "java/lang/Byte", "java/lang/Boolean",
        "net/minecraft/util/Mth");

    private static final Map<String, ClassNode> CLASSES = new HashMap<>();
    /** Why a method is not pure, by owner.name+desc; an empty string for one that is. */
    private static final Map<String, String> VERDICTS = new HashMap<>();
    private static final Set<String> IN_PROGRESS = new HashSet<>();

    public static void main(String[] args) throws IOException {
        for (String arg : args.length > 0 ? args : new String[] {"build/classes/java/core"}) {
            Path root = Path.of(arg);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    ClassNode node = new ClassNode();
                    new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    CLASSES.put(node.name, node);
                }
            }
        }

        List<String> found = new ArrayList<>();
        for (ClassNode node : CLASSES.values()) {
            for (MethodNode method : node.methods) {
                if (!takeable(method)) continue;
                String why = verdict(node, method);
                if (!why.isEmpty()) found.add(node.name.replace('/', '.') + "." + method.name + method.desc + "  -- " + why);
            }
        }

        found.sort(null);
        for (String line : found) System.out.println(line);
        System.out.println(found.size() + " methods Skidfuscator could take for a check that are not pure");
        if (!found.isEmpty()) System.exit(1);
    }

    /** The shape Skidfuscator takes: static, one or more parameters, all primitive, an int back. */
    private static boolean takeable(MethodNode method) {
        if ((method.access & Opcodes.ACC_STATIC) == 0 || (method.access & Opcodes.ACC_ABSTRACT) != 0) return false;
        if (method.name.startsWith("<")) return false;
        if (Type.getReturnType(method.desc).getSort() != Type.INT) return false;

        Type[] params = Type.getArgumentTypes(method.desc);
        if (params.length == 0) return false;
        for (Type param : params) {
            if (param.getSort() == Type.OBJECT || param.getSort() == Type.ARRAY) return false;
        }
        return true;
    }

    /** Why this method's answer may differ between two calls with the same arguments; empty when it cannot. */
    private static String verdict(ClassNode owner, MethodNode method) {
        String key = owner.name + "." + method.name + method.desc;
        String known = VERDICTS.get(key);
        if (known != null) return known;
        // A method that calls itself: judged by the rest of what it does.
        if (!IN_PROGRESS.add(key)) return "";

        String why = "";
        for (AbstractInsnNode insn : method.instructions) {
            why = judge(insn);
            if (!why.isEmpty()) break;
        }

        IN_PROGRESS.remove(key);
        VERDICTS.put(key, why);
        return why;
    }

    private static String judge(AbstractInsnNode insn) {
        int op = insn.getOpcode();

        if (op == Opcodes.GETSTATIC) {
            FieldInsnNode field = (FieldInsnNode) insn;
            return constant(field) ? "" : "reads " + field.owner.replace('/', '.') + "." + field.name;
        }
        if (op == Opcodes.PUTSTATIC || op == Opcodes.PUTFIELD || op == Opcodes.GETFIELD) {
            FieldInsnNode field = (FieldInsnNode) insn;
            return "touches " + field.owner.replace('/', '.') + "." + field.name;
        }
        if (op == Opcodes.NEW || op == Opcodes.INVOKEDYNAMIC || op == Opcodes.MONITORENTER) return "makes an object";

        if (insn instanceof MethodInsnNode call) {
            String name = call.owner.replace('/', '.') + "." + call.name;
            if (op != Opcodes.INVOKESTATIC) return "calls " + name;
            if (ARITHMETIC.contains(call.owner)) return "";

            ClassNode target = CLASSES.get(call.owner);
            if (target == null) return "calls " + name;
            for (MethodNode method : target.methods) {
                if (!method.name.equals(call.name) || !method.desc.equals(call.desc)) continue;
                return verdict(target, method).isEmpty() ? "" : "calls " + name + ", which is not pure";
            }
            return "calls " + name;
        }
        return "";
    }

    /** A static final field of the checked code's: a number, a string, or a table nothing writes to after start-up. */
    private static boolean constant(FieldInsnNode insn) {
        ClassNode owner = CLASSES.get(insn.owner);
        if (owner == null) return false;
        for (FieldNode field : owner.fields) {
            if (field.name.equals(insn.name)) return (field.access & Opcodes.ACC_FINAL) != 0;
        }
        return false;
    }
}
