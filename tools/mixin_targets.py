"""Checks every visuals mixin against the decompiled Minecraft sources, headless.

    python tools/mixin_targets.py [C:/dev/MinecraftSourcesGenerator/sources/26.2]

For each class in src/visuals/java/com/mineux/visuals/mixin: the @Mixin target class exists;
every method = "..." names a method of it; every INVOKE target = "Lowner;name(...)" names a
method of its owner (and, with ordinal=N, is called at least N+1 times in the injected method);
every @Shadow, @Accessor and @Invoker names a member that is there; every @ModifyConstant
intValue is in the method. Exit 1 and a list on any miss.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIXINS = os.path.join(ROOT, "src", "visuals", "java", "com", "mineux", "visuals", "mixin")
SOURCES = sys.argv[1] if len(sys.argv) > 1 else r"C:\dev\MinecraftSourcesGenerator\sources\26.2"


def source_of(binary):
    """net/minecraft/x/Y$Z -> text of Y.java, or None."""
    outer = binary.split("$")[0]
    path = os.path.join(SOURCES, *outer.split("/")) + ".java"
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def imports(text):
    out = {}
    for m in re.finditer(r"^import\s+([\w.]+)\s*;", text, re.M):
        out[m.group(1).split(".")[-1]] = m.group(1).replace(".", "/")
    return out


def has_method(src, name, binary=None, depth=0):
    """Whether the class, or one it extends, declares `name`."""
    if name in ("<init>", "<clinit>"):
        return True
    if re.search(r"[\s>]" + re.escape(name) + r"\s*\(", src) is not None:
        return True
    if binary is None or depth > 8:
        return False
    simple = binary.split("/")[-1].split("$")[-1]
    ext = re.search(r"class\s+" + re.escape(simple) + r"\b[^{]*?\bextends\s+([\w.]+)", src)
    if not ext:
        return False
    sup = ext.group(1).split(".")[-1]
    parent = imports(src).get(sup) or binary.rsplit("/", 1)[0] + "/" + sup
    psrc = source_of(parent)
    return psrc is not None and has_method(psrc, name, parent, depth + 1)


def method_body(src, name):
    """The text of every declaration of method `name` (naive brace matching)."""
    bodies = []
    for m in re.finditer(r"\n\s*(?:public|private|protected|static|final|synchronized|\s)*[\w<>\[\], .?]+\s" + re.escape(name) + r"\s*\([^;{]*\)\s*(?:throws [\w., ]+)?\{", src):
        i = m.end()
        depth = 1
        while i < len(src) and depth:
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
            i += 1
        bodies.append(src[m.end():i])
    return "\n".join(bodies)


def main():
    misses = []
    checked = 0
    for fname in sorted(os.listdir(MIXINS)):
        if not fname.endswith(".java"):
            continue
        with open(os.path.join(MIXINS, fname), encoding="utf-8") as f:
            text = f.read()
        imp = imports(text)
        m = re.search(r"@Mixin\((\w+)\.class\)", text)
        if not m:
            misses.append(f"{fname}: no @Mixin")
            continue
        target = imp.get(m.group(1))
        src = source_of(target) if target else None
        if src is None:
            misses.append(f"{fname}: target class {m.group(1)} ({target}) not in the sources")
            continue
        # Constant strings the methods use (RENDER_LAYERS = "...").
        consts = {k: "".join(re.findall(r'"([^"]*)"', v)) for k, v in
                  re.findall(r"static final String (\w+)\s*=\s*((?:\"[^\"]*\"\s*\+?\s*)+);", text)}

        def value(expr):
            expr = expr.strip()
            if expr in consts:
                return consts[expr]
            return "".join(re.findall(r'"([^"]*)"', expr))

        for ann in re.finditer(r"@(Inject|Redirect|WrapOperation|WrapWithCondition|ModifyArg|ModifyVariable|ModifyConstant)\((.*?)\)\s*\n\s*(?:private|public|protected)", text, re.S):
            body = ann.group(2)
            mm = re.search(r"method\s*=\s*(\{[^}]*\}|[^,\n]+(?:\n\s*\+[^,\n]+)*)", body)
            methods = []
            if mm:
                raw = mm.group(1)
                if raw.startswith("{"):
                    methods = re.findall(r'"([^"]*)"', raw)
                else:
                    methods = [value(raw)]
            for meth in methods:
                name = meth.split("(")[0]
                checked += 1
                if not has_method(src, name, target):
                    misses.append(f"{fname}: {target} has no method {name}")
            tm = re.search(r"target\s*=\s*((?:\"[^\"]*\"\s*\+?\s*)+|\w+)", body)
            if tm:
                t = value(tm.group(1))
                inv = re.match(r"L([\w/$]+);(\w+)\(", t)
                if inv:
                    owner, name = inv.group(1), inv.group(2)
                else:
                    # No owner given: it is called somewhere in the injected method.
                    owner, name = target, t.split("(")[0]
                osrc = source_of(owner)
                checked += 1
                called = any(re.search(r"[.\s]" + re.escape(name) + r"\s*\(", method_body(src, x.split("(")[0])) for x in methods)
                if osrc is None:
                    misses.append(f"{fname}: invoke owner {owner} not in the sources")
                elif not inv and not called:
                    misses.append(f"{fname}: nothing calls {name} in {methods}")
                elif inv and not has_method(osrc, name, owner):
                    misses.append(f"{fname}: {owner} has no method {name}")
                om = re.search(r"ordinal\s*=\s*(\d+)", body)
                if om and methods:
                    calls = len(re.findall(r"[.\s]" + re.escape(name) + r"\s*\(", method_body(src, methods[0].split("(")[0])))
                    if calls < int(om.group(1)) + 1:
                        misses.append(f"{fname}: {methods[0]} calls {name} {calls} times, ordinal {om.group(1)}")
            cm = re.search(r"intValue\s*=\s*(\d+)", body)
            if cm:
                for meth in methods:
                    if not re.search(r"\b" + cm.group(1) + r"\b", method_body(src, meth.split("(")[0])):
                        misses.append(f"{fname}: {meth} has no constant {cm.group(1)}")
        for sm in re.finditer(r"@Shadow(?:\s*@Final)?\s+(?:private|protected|public)?\s*(?:abstract\s+)?(?:final\s+)?[\w<>\[\]?.]+\s+(\w+)\s*[;(]", text):
            checked += 1
            if not re.search(r"\b" + re.escape(sm.group(1)) + r"\b", src):
                misses.append(f"{fname}: shadowed {sm.group(1)} not in {target}")
        for am in re.finditer(r"@(Accessor|Invoker)\(\"(\w+)\"\)", text):
            checked += 1
            if not re.search(r"\b" + re.escape(am.group(2)) + r"\b", src):
                misses.append(f"{fname}: {am.group(1)} {am.group(2)} not in {target}")
    # The config lists exactly the mixin classes there are.
    import json
    with open(os.path.join(ROOT, "src", "visuals", "resources", "mineux.visuals.mixins.json"), encoding="utf-8") as f:
        listed = set(json.load(f)["client"])
    files = {n[:-5] for n in os.listdir(MIXINS) if n.endswith(".java")}
    for name in sorted(files - listed):
        misses.append(f"{name} is not listed in mineux.visuals.mixins.json")
    for name in sorted(listed - files):
        misses.append(f"mineux.visuals.mixins.json lists {name}, which does not exist")
    for miss in misses:
        print("MISS", miss)
    print(f"{checked} targets checked, {len(misses)} missing")
    return 1 if misses else 0


if __name__ == "__main__":
    sys.exit(main())
