"""Renders the tray's item icons from the game's own textures, for docs/showcase.

Reads the 26.2 client jar Loom keeps in the Gradle cache (nothing is downloaded), follows each
item's definition (assets/minecraft/items/<id>.json) to its model and textures, applies the
default tints the game uses for a plain stack (leather's dye, the firework star's grey), and
writes a nearest-neighbour upscale. Items whose model is a block cube (note_block) are drawn as
a simple isometric cube from the block's faces.

    python tools/showcase/render_icons.py [--jar PATH] [--out DIR] [--size 128] item ...

With no items it renders every icon the tray uses (ICONS below, kept in step with the code:
core Tray.java, Screens.Tray, Visuals.java and VisualRows.java).
"""
import argparse
import io
import json
import os
import sys
import zipfile

from PIL import Image

# Every item the tray shows, in the order the code declares them.
ICONS = [
    # core: the client group and its row
    "comparator", "note_block",
    # screens
    "black_stained_glass_pane", "ender_eye",
    # full edition groups
    "compass", "sunflower", "spyglass", "armor_stand",
    # hud rows
    "arrow", "bundle", "oak_sign", "writable_book", "painting", "prismarine_crystals",
    # world rows
    "clock", "barrier", "firework_star", "blaze_powder", "nether_star", "amethyst_shard",
    "snowball", "diamond",
    # camera rows
    "recovery_compass", "lead", "phantom_membrane", "glass_pane", "minecart", "glass_bottle",
    "chainmail_boots", "redstone", "map", "water_bucket", "item_frame", "leather",
    "wooden_sword", "stone_sword", "structure_void", "brush", "enchanted_book", "glow_ink_sac",
    # self rows
    "leather_chestplate", "leather_boots", "elytra", "rabbit_foot", "leather_helmet",
    "diamond_chestplate", "spectral_arrow", "tripwire_hook",
]

DEFAULT_JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar")


def argb(value):
    value &= 0xFFFFFFFF
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


class Assets:
    def __init__(self, jar):
        self.zip = zipfile.ZipFile(jar)

    def json(self, path):
        return json.loads(self.zip.read("assets/minecraft/" + path))

    def texture(self, ref):
        name = ref.split(":", 1)[-1]
        img = Image.open(io.BytesIO(self.zip.read("assets/minecraft/textures/" + name + ".png")))
        img = img.convert("RGBA")
        if img.height > img.width:  # an animated strip: its first frame
            img = img.crop((0, 0, img.width, img.width))
        return img

    def model(self, ref):
        """A model with its parents' textures and elements folded in."""
        name = ref.split(":", 1)[-1]
        data = self.json("models/" + name + ".json")
        textures = dict(data.get("textures", {}))
        parent = data.get("parent")
        chain = [name]
        while parent:
            pname = parent.split(":", 1)[-1]
            chain.append(pname)
            if pname.startswith("builtin/"):
                break
            pdata = self.json("models/" + pname + ".json")
            for k, v in pdata.get("textures", {}).items():
                textures.setdefault(k, v)
            parent = pdata.get("parent")
        # resolve #refs
        for _ in range(8):
            for k, v in list(textures.items()):
                if v.startswith("#") and v[1:] in textures:
                    textures[k] = textures[v[1:]]
        return chain, textures


def pick(node):
    """The model a plain stack in a menu uses: the fallback or first case of any switch."""
    t = node.get("type", "minecraft:model").split(":")[-1]
    if t == "model":
        return node
    if t == "condition":
        return pick(node["on_false"])
    if t == "select":
        if node.get("property", "").endswith("display_context"):
            for case in node["cases"]:
                when = case["when"] if isinstance(case["when"], list) else [case["when"]]
                if "gui" in when:
                    return pick(case["model"])
        return pick(node["fallback"]) if "fallback" in node else pick(node["cases"][0]["model"])
    if t == "range_dispatch":
        if "fallback" in node:
            return pick(node["fallback"])
        return pick(node["entries"][0]["model"])
    if t == "composite":
        return pick(node["models"][0])
    raise ValueError("no plain model in " + t)


def tint_of(source):
    if source is None:
        return None
    t = source.get("type", "").split(":")[-1]
    if t == "constant":
        value = source["value"]
    elif "default" in source:
        value = source["default"]
    else:
        return None
    if (value & 0xFFFFFFFF) == 0xFFFFFFFF:
        return None
    return argb(value)


def tinted(img, rgb):
    if rgb is None:
        return img
    px = img.load()
    out = img.copy()
    po = out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            po[x, y] = (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a)
    return out


def flat(assets, textures, tints):
    layers = []
    i = 0
    while "layer%d" % i in textures:
        tex = assets.texture(textures["layer%d" % i])
        tex = tinted(tex, tint_of(tints[i]) if i < len(tints) else None)
        layers.append(tex)
        i += 1
    base = Image.new("RGBA", layers[0].size, (0, 0, 0, 0))
    for layer in layers:
        if layer.size != base.size:
            layer = layer.resize(base.size, Image.NEAREST)
        base = Image.alpha_composite(base, layer)
    return base


def face(tex, size, origin, u, v, shade):
    """Maps a 16x16 face onto the canvas: texture (s, t) lands at origin + s*u + t*v."""
    n = tex.width
    # output p = O + s*U + t*V  ->  solve for (s, t)
    det = u[0] * v[1] - u[1] * v[0]
    a = v[1] / det
    b = -v[0] / det
    d = -u[1] / det
    e = u[0] / det
    c = -(a * origin[0] + b * origin[1])
    f = -(d * origin[0] + e * origin[1])
    scale = n  # s, t in 0..1 -> texels
    data = (a * scale, b * scale, c * scale, d * scale, e * scale, f * scale)
    out = tex.transform((size, size), Image.AFFINE, data, resample=Image.NEAREST)
    if shade < 1:
        px = out.load()
        for y in range(size):
            for x in range(size):
                r, g, bb, al = px[x, y]
                if al:
                    px[x, y] = (int(r * shade), int(g * shade), int(bb * shade), al)
    return out


def cube(assets, textures, size):
    top = assets.texture(textures.get("up", textures.get("top", textures.get("all"))))
    side = assets.texture(textures.get("north", textures.get("side", textures.get("all"))))
    side2 = assets.texture(textures.get("east", textures.get("side", textures.get("all"))))
    w = size * 0.86
    cx = size / 2
    y0 = (size - w * 1.07) / 2
    h = w / 2  # height of the top rhombus
    edge = w * 0.57  # vertical edge length
    left = (cx - w / 2, y0 + h / 2)
    right = (cx + w / 2, y0 + h / 2)
    topv = (cx, y0)
    front = (cx, y0 + h)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    # top: s runs from left corner to the top corner's... use the corner at `left`
    canvas = Image.alpha_composite(canvas, face(top, size, topv,
        (right[0] - topv[0], right[1] - topv[1]), (left[0] - topv[0], left[1] - topv[1]), 1.0))
    canvas = Image.alpha_composite(canvas, face(side, size, left,
        (front[0] - left[0], front[1] - left[1]), (0, edge), 0.8))
    canvas = Image.alpha_composite(canvas, face(side2, size, front,
        (right[0] - front[0], right[1] - front[1]), (0, edge), 0.62))
    return canvas


def render(assets, item, size):
    node = pick(assets.json("items/" + item + ".json")["model"])
    chain, textures = assets.model(node["model"])
    tints = node.get("tints", [])
    if "layer0" in textures:
        img = flat(assets, textures, tints)
        return img.resize((size, size), Image.NEAREST)
    # a block model: a cube from its faces (a supersampled draw, then cut down crisp)
    big = cube(assets, textures, size * 4)
    return big.resize((size, size), Image.NEAREST)


# Each tray group as the bar shows it: the group's icon, then its rows' icons in order.
GROUPS = {
    "client": ["comparator", "note_block"],
    "screens": ["black_stained_glass_pane", "black_stained_glass_pane", "ender_eye"],
    "hud": ["compass", "arrow", "bundle", "oak_sign", "writable_book", "painting", "prismarine_crystals"],
    "world": ["sunflower", "clock", "barrier", "firework_star", "blaze_powder", "nether_star",
              "amethyst_shard", "snowball", "diamond"],
    "camera": ["spyglass", "spyglass", "recovery_compass", "lead", "phantom_membrane", "glass_pane",
               "minecart", "glass_bottle", "chainmail_boots", "redstone", "map", "water_bucket",
               "item_frame", "leather", "wooden_sword", "stone_sword", "structure_void", "brush",
               "enchanted_book", "glow_ink_sac"],
    "self": ["armor_stand", "leather_chestplate", "leather_boots", "elytra", "rabbit_foot",
             "armor_stand", "leather_helmet", "diamond_chestplate", "spectral_arrow", "tripwire_hook"],
}


def strips(icons_dir, out_dir, size=64, gap=12):
    """A strip per group of just its modules' items (the group's own icon, the list's first, is the
    section heading's), on a transparent background: modules-<group>.png."""
    os.makedirs(out_dir, exist_ok=True)
    for name, items in GROUPS.items():
        rows = items[1:]
        n = len(rows)
        w = n * size + (n - 1) * gap
        img = Image.new("RGBA", (w, size), (0, 0, 0, 0))
        x = 0
        for item in rows:
            icon = Image.open(os.path.join(icons_dir, item + ".png")).resize((size, size), Image.NEAREST)
            img.alpha_composite(icon, (x, 0))
            x += size + gap
        img.save(os.path.join(out_dir, "modules-" + name + ".png"), optimize=True)
        print("strip", name, n, "modules")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", default=DEFAULT_JAR)
    ap.add_argument("--out", default=os.path.join("docs", "showcase", "img", "icons"))
    ap.add_argument("--size", type=int, default=128)
    ap.add_argument("items", nargs="*")
    args = ap.parse_args()
    assets = Assets(args.jar)
    os.makedirs(args.out, exist_ok=True)
    for item in args.items or ICONS:
        img = render(assets, item, args.size)
        img.save(os.path.join(args.out, item + ".png"), optimize=True)
        print(item)
    if not args.items:
        strips(args.out, os.path.join(os.path.dirname(args.out), "groups"))


if __name__ == "__main__":
    sys.exit(main())
