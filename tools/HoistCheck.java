import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

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
 * Finds, in the compiled core's bytecode, the one shape of code that Skidfuscator reorders
 * (found 2026-09-30, reproduced with every transform off, so it is in its IR and not a
 * setting):
 * <pre>
 *     String r = a.call1();   // a call whose result is kept in a local used once...
 *     int v = a.field;        // ...then a field read into a local...
 *     String q = a.call2();   // ...then another call...
 *     use(r, v, q);           // ...and r used only after all that
 * </pre>
 * Skidfuscator moves {@code call1} (and {@code call2}) down into {@code use(...)}, past the
 * field read, so {@code v} is read before {@code call1} runs -- wrong whenever {@code call1}
 * changes the field. A void call, or any call whose result is not a single-use local, between
 * the first call and the use stops it, as does a branch; this only reports what nothing stops.
 * <p>
 * It reports every such spot, whether or not the call really writes the field: the fix is the
 * same and cheap (read the field through a method, or use the call's result at once).
 * <p>
 * <b>Negation</b> (found 2026-10-03). Skidfuscator's pass drops a {@code float} or a
 * {@code long} unary minus: {@code -x} comes out as {@code x} ({@code int} and {@code double}
 * are untouched). It turned {@code clamp(v, -g, g)} into "always g" in one camera module and spun
 * the camera. Every {@code FNEG} and {@code LNEG} is reported; write {@code 0f - x} (or
 * {@code 0L - x}), which compiles to a subtraction.
 * <p>
 * <b>A field copied, then written</b> (found 2026-11-06 in the overlay's close). Skidfuscator
 * forwards a local that only copies a field into the local's uses, so
 * <pre>
 *     Screen s = hosted;   // the field kept in a local...
 *     hosted = null;       // ...the field written...
 *     use(s);              // ...and the local read after: obfuscated, this reads hosted again (null)
 * </pre>
 * Pass the value to a method that writes the field instead (or write the field after the use).
 * <p>
 * Usage: {@code java -cp "asm-9.9.jar;asm-tree-9.9.jar" tools/HoistCheck.java build/classes/java/core}
 * Exits 1 when it finds any.
 */
public class HoistCheck {

    public static void main(String[] args) throws IOException {
        List<String> found = new ArrayList<>();
        for (String arg : args.length > 0 ? args : new String[] {"build/classes/java/core"}) {
            Path root = Path.of(arg);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    ClassNode cn = new ClassNode();
                    new ClassReader(Files.readAllBytes(file)).accept(cn, 0);
                    for (MethodNode m : cn.methods) scan(cn, m, found);
                }
            }
        }
        for (String f : found) System.out.println(f);
        System.out.println(found.size() + " spot" + (found.size() == 1 ? "" : "s") + " Skidfuscator would reorder");
        System.exit(found.isEmpty() ? 0 : 1);
    }

    private static boolean isInvoke(AbstractInsnNode n) {
        int op = n.getOpcode();
        return op == Opcodes.INVOKEVIRTUAL || op == Opcodes.INVOKESTATIC || op == Opcodes.INVOKEINTERFACE
            || op == Opcodes.INVOKESPECIAL || op == Opcodes.INVOKEDYNAMIC;
    }

    private static boolean isStore(AbstractInsnNode n) {
        int op = n.getOpcode();
        return op >= Opcodes.ISTORE && op <= Opcodes.ASTORE;
    }

    private static boolean isLoad(AbstractInsnNode n) {
        int op = n.getOpcode();
        return op >= Opcodes.ILOAD && op <= Opcodes.ALOAD;
    }

    /** Anything that ends straight-line code: a branch, a switch, a return or throw, or a label something jumps to. */
    private static boolean breaksFlow(AbstractInsnNode n, Set<LabelNode> targets) {
        if (n instanceof JumpInsnNode || n instanceof TableSwitchInsnNode || n instanceof LookupSwitchInsnNode) return true;
        int op = n.getOpcode();
        if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN || op == Opcodes.ATHROW) return true;
        return n instanceof LabelNode && targets.contains(n);
    }

    private static void scan(ClassNode cn, MethodNode m, List<String> found) {
        if (m.instructions == null || m.instructions.size() == 0) return;
        AbstractInsnNode[] code = m.instructions.toArray();

        for (int i = 0; i < code.length; i++) {
            int op = code[i].getOpcode();
            if (op == Opcodes.FNEG || op == Opcodes.LNEG) {
                found.add(cn.name + "." + m.name + " line " + line(code, i) + ": a "
                    + (op == Opcodes.FNEG ? "float" : "long") + " negation, which the obfuscator drops -- write 0 - x");
            }
        }

        copiedThenWritten(cn, m, code, found);

        Set<LabelNode> targets = new HashSet<>();
        for (AbstractInsnNode n : code) {
            if (n instanceof JumpInsnNode j) targets.add(j.label);
            if (n instanceof TableSwitchInsnNode t) {
                targets.add(t.dflt);
                targets.addAll(t.labels);
            }
            if (n instanceof LookupSwitchInsnNode l) {
                targets.add(l.dflt);
                targets.addAll(l.labels);
            }
        }
        if (m.tryCatchBlocks != null) for (TryCatchBlockNode t : m.tryCatchBlocks) {
            targets.add(t.handler);
            targets.add(t.start);
            targets.add(t.end);
        }

        // How often each local is stored and loaded; the single-use ones are what gets moved.
        Map<Integer, Integer> stores = new HashMap<>(), loads = new HashMap<>();
        for (AbstractInsnNode n : code) {
            if (isStore(n)) stores.merge(((VarInsnNode) n).var, 1, Integer::sum);
            if (isLoad(n)) loads.merge(((VarInsnNode) n).var, 1, Integer::sum);
            if (n.getOpcode() == Opcodes.IINC) {
                // An incremented local is both; never a candidate.
                int var = ((org.objectweb.asm.tree.IincInsnNode) n).var;
                stores.merge(var, 2, Integer::sum);
            }
        }

        for (int i = 0; i + 1 < code.length; i++) {
            if (!isInvoke(code[i])) continue;
            AbstractInsnNode after = next(code, i);
            if (after == null || !isStore(after)) continue;
            int r = ((VarInsnNode) after).var;
            if (stores.getOrDefault(r, 0) != 1 || loads.getOrDefault(r, 0) != 1) continue;

            String field = null;
            int fieldAt = -1;
            boolean callAfterField = false;
            boolean blocked = false;
            for (int k = index(code, after) + 1; k < code.length; k++) {
                AbstractInsnNode n = code[k];
                if (breaksFlow(n, targets)) {
                    blocked = true;
                    break;
                }
                if (isLoad(n) && ((VarInsnNode) n).var == r) break;
                if (n instanceof FieldInsnNode f && (n.getOpcode() == Opcodes.GETFIELD || n.getOpcode() == Opcodes.GETSTATIC)
                    && field == null && endsInStore(code, k)) {
                    // A read that ends up in a local, directly or through arithmetic (w = a.f * 2).
                    field = f.owner + "." + f.name;
                    fieldAt = k;
                }
                int op = n.getOpcode();
                if ((op >= Opcodes.IALOAD && op <= Opcodes.SALOAD || op == Opcodes.ARRAYLENGTH) && field == null && endsInStore(code, k)) {
                    // An array element (or length) read into a local moves the same way.
                    field = op == Opcodes.ARRAYLENGTH ? "an array's length" : "an array element";
                    fieldAt = k;
                }
                if (isInvoke(n)) {
                    AbstractInsnNode st = next(code, k);
                    boolean kept = st != null && isStore(st) && stores.getOrDefault(((VarInsnNode) st).var, 0) == 1
                        && loads.getOrDefault(((VarInsnNode) st).var, 0) == 1;
                    // A call whose result is not a single-use local stays put, and nothing moves past it.
                    if (!kept) {
                        blocked = true;
                        break;
                    }
                    if (fieldAt >= 0) callAfterField = true;
                }
            }
            if (!blocked && field != null && callAfterField) {
                MethodInsnNode call = code[i] instanceof MethodInsnNode mi ? mi : null;
                found.add(cn.name.replace('/', '.') + "." + m.name + " line " + line(code, i) + ": "
                    + (call == null ? "a call" : call.name + "()") + "'s result is used after " + field + " is read into a local");
            }
        }
    }

    /** Whether the value read at {@code k} goes into a local before anything calls, writes a field or branches. */
    private static boolean endsInStore(AbstractInsnNode[] code, int k) {
        for (int i = k + 1; i < code.length; i++) {
            AbstractInsnNode n = code[i];
            if (n.getOpcode() < 0) continue;
            if (isStore(n)) return true;
            if (isInvoke(n) || n instanceof JumpInsnNode || n.getOpcode() == Opcodes.PUTFIELD || n.getOpcode() == Opcodes.PUTSTATIC
                || n instanceof TableSwitchInsnNode || n instanceof LookupSwitchInsnNode
                || n.getOpcode() >= Opcodes.IRETURN && n.getOpcode() <= Opcodes.RETURN || n.getOpcode() == Opcodes.ATHROW
                || n.getOpcode() >= Opcodes.IASTORE && n.getOpcode() <= Opcodes.SASTORE) {
                return false;
            }
        }
        return false;
    }

    /** A field read straight into a local, the same field written, then that local read again. */
    private static void copiedThenWritten(ClassNode cn, MethodNode m, AbstractInsnNode[] code, List<String> found) {
        for (int i = 0; i < code.length; i++) {
            int op = code[i].getOpcode();
            if (op != Opcodes.GETSTATIC && op != Opcodes.GETFIELD) continue;
            FieldInsnNode get = (FieldInsnNode) code[i];
            AbstractInsnNode after = next(code, i);
            if (after == null || !isStore(after)) continue;
            int local = ((VarInsnNode) after).var;
            int put = op == Opcodes.GETSTATIC ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD;
            boolean written = false;
            for (int k = index(code, after) + 1; k < code.length; k++) {
                AbstractInsnNode n = code[k];
                if (isStore(n) && ((VarInsnNode) n).var == local) break;
                if (n.getOpcode() == put && n instanceof FieldInsnNode f && f.owner.equals(get.owner) && f.name.equals(get.name)) {
                    written = true;
                } else if (written && isLoad(n) && ((VarInsnNode) n).var == local) {
                    found.add(cn.name.replace('/', '.') + "." + m.name + " line " + line(code, k) + ": " + get.name
                        + " is copied into a local, written, then the local is read -- the obfuscator reads the field again; "
                        + "pass the copy to a method instead");
                    break;
                }
            }
        }
    }

    private static AbstractInsnNode next(AbstractInsnNode[] code, int i) {
        for (int k = i + 1; k < code.length; k++) {
            if (code[k].getOpcode() >= 0) return code[k];
        }
        return null;
    }

    private static int index(AbstractInsnNode[] code, AbstractInsnNode n) {
        for (int i = 0; i < code.length; i++) if (code[i] == n) return i;
        return -1;
    }

    private static int line(AbstractInsnNode[] code, int i) {
        for (int k = i; k >= 0; k--) if (code[k] instanceof LineNumberNode ln) return ln.line;
        return -1;
    }
}
