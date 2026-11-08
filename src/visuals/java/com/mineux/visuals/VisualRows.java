package com.mineux.visuals;

import com.mineux.core.TrayGroup.Row;

import java.util.List;

/**
 * The modules of each visual tray group, in the order the bar and the settings window show
 * them: every {@code dir} of {@code visuals.txt} with a {@code toggle=}, plus the two without
 * one that hold settings of their own (the main menu, clear weather). {@code VisualsFileTest}
 * holds this to the file: each switch in exactly one group, each row a dir of the file.
 */
final class VisualRows {

    private VisualRows() {}

    static final List<Row> HUD = List.of(
        new Row("crosshair", "crosshair", "minecraft:arrow"),
        new Row("hotbar", "hotbar", "minecraft:bundle"),
        new Row("scoreboard", "scoreboard", "minecraft:oak_sign"),
        new Row("chat", "chat", "minecraft:writable_book"),
        new Row("menu", "main menu", "minecraft:painting"),
        new Row("menu/theme", "theme textures", "minecraft:prismarine_crystals"));

    static final List<Row> WORLD = List.of(
        new Row("world/ambience", "ambience", "minecraft:clock"),
        new Row("world/norender", "no render", "minecraft:barrier"),
        new Row("world/totems", "totem pop", "minecraft:firework_star"),
        new Row("fx", "hit effects", "minecraft:blaze_powder"),
        new Row("world/sky", "sky shaders", "minecraft:nether_star"),
        new Row("world/shader", "world shaders", "minecraft:amethyst_shard"),
        new Row("world/particles", "particles", "minecraft:snowball"),
        new Row("world/items", "item physics", "minecraft:diamond"));

    static final List<Row> CAMERA = List.of(
        new Row("view/zoom", "zoom", "minecraft:spyglass"),
        new Row("camera/freelook", "freelook", "minecraft:recovery_compass"),
        new Row("camera/tweaks", "camera tweaks", "minecraft:lead"),
        new Row("camera/smooth", "smooth camera", "minecraft:phantom_membrane"),
        new Row("camera/blur", "motion blur", "minecraft:glass_pane"),
        new Row("camera/autof5", "auto f5", "minecraft:minecart"),
        new Row("camera/nofov", "no fov", "minecraft:glass_bottle"),
        new Row("camera/nobob", "no bob", "minecraft:chainmail_boots"),
        new Row("camera/nohurt", "no hurt cam", "minecraft:redstone"),
        new Row("camera/aspect", "stretched", "minecraft:map"),
        new Row("view", "clear weather", "minecraft:water_bucket"),
        new Row("hand/viewmodel", "view model", "minecraft:item_frame"),
        new Row("hand/handview", "hand view", "minecraft:leather"),
        new Row("hand/animations", "animations", "minecraft:wooden_sword"),
        new Row("hand/swingspeed", "swing speed", "minecraft:stone_sword"),
        new Row("hand/noswing", "no swing", "minecraft:structure_void"),
        new Row("hand/inspect", "inspect", "minecraft:brush"),
        new Row("hand/glint", "glint colour", "minecraft:enchanted_book"),
        new Row("hand/shaders", "hand shaders", "minecraft:glow_ink_sac"));

    static final List<Row> SELF = List.of(
        new Row("self/body", "body view", "minecraft:leather_chestplate"),
        new Row("self/moves", "move animations", "minecraft:leather_boots"),
        new Row("cosmetics", "cosmetics", "minecraft:elytra"),
        new Row("self/jump", "jump effect", "minecraft:rabbit_foot"),
        new Row("self/model", "player model", "minecraft:armor_stand"),
        new Row("self/skin", "skin changer", "minecraft:leather_helmet"),
        new Row("self/armor", "armour shaders", "minecraft:diamond_chestplate"),
        new Row("trail", "cursor trail", "minecraft:spectral_arrow"),
        new Row("cursor", "fake cursor", "minecraft:tripwire_hook"));
}
