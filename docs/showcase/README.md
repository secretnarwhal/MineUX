# Showcase

The material for the Modrinth page and the GitHub README.

- [`modrinth.md`](modrinth.md): the Modrinth description, ready to paste into Modrinth's editor.
  Its images load from `https://mineux.com/showcase/<file>`: copies in `server/site/showcase/`
  (losslessly re-compressed, flat names: icons as `icon-<item>.png`, strips as
  `group-<group>.png`), shipped with the site by `bash server/deploy/deploy.sh`. They show
  whatever the GitHub repo's visibility. The README keeps relative paths into `img/`.
- [`SHOTLIST.md`](SHOTLIST.md): the screenshots still to take, in order, and the ones already in
  `img/`.
- `img/icons/`: every item the tray uses, 128x128, from the game's own 26.2 textures.
- `img/groups/`: each tray group as one strip: the group's item, then its rows' items in order.
- `img/`: screenshots (1920x1080), and `img/crops/` with smaller crops for the README.

Rebuild the icons and strips (reads the client jar Loom keeps in the Gradle cache, downloads
nothing):

```
python tools/showcase/render_icons.py
```

`tools/showcase/mux.ps1` captures and drives a dev client (PrintWindow, SendInput), and
`tools/showcase/source_window.ps1` opens a plain picture window for Screens shots.

## Feature to item

Each icon is the item the code itself shows for that feature on the bar's tray. Sections of the
page with no tray item (the desktop, the window manager, the terminal, workstations, the greeter
and account) have no icon.

| Tray group | Feature | Item | Declared in |
|---|---|---|---|
| **client** | the group | `minecraft:comparator` | `core/ui/term/Tray.java` |
| client | discord status | `minecraft:note_block` | `core/ui/term/Tray.java` |
| **screens** | the group, screens | `minecraft:black_stained_glass_pane` | `screens/Screens.java` |
| screens | friends' screens | `minecraft:ender_eye` | `screens/Screens.java` |
| **hud** | the group | `minecraft:compass` | `visuals/Visuals.java` |
| hud | crosshair | `minecraft:arrow` | `visuals/VisualRows.java` |
| hud | hotbar | `minecraft:bundle` | `visuals/VisualRows.java` |
| hud | scoreboard | `minecraft:oak_sign` | `visuals/VisualRows.java` |
| hud | chat | `minecraft:writable_book` | `visuals/VisualRows.java` |
| hud | main menu | `minecraft:painting` | `visuals/VisualRows.java` |
| hud | theme textures | `minecraft:prismarine_crystals` | `visuals/VisualRows.java` |
| **world** | the group | `minecraft:sunflower` | `visuals/Visuals.java` |
| world | ambience | `minecraft:clock` | `visuals/VisualRows.java` |
| world | no render | `minecraft:barrier` | `visuals/VisualRows.java` |
| world | totem pop | `minecraft:firework_star` | `visuals/VisualRows.java` |
| world | hit effects | `minecraft:blaze_powder` | `visuals/VisualRows.java` |
| world | sky shaders | `minecraft:nether_star` | `visuals/VisualRows.java` |
| world | world shaders | `minecraft:amethyst_shard` | `visuals/VisualRows.java` |
| world | particles | `minecraft:snowball` | `visuals/VisualRows.java` |
| world | item physics | `minecraft:diamond` | `visuals/VisualRows.java` |
| **camera** | the group | `minecraft:spyglass` | `visuals/Visuals.java` |
| camera | zoom | `minecraft:spyglass` | `visuals/VisualRows.java` |
| camera | freelook | `minecraft:recovery_compass` | `visuals/VisualRows.java` |
| camera | camera tweaks | `minecraft:lead` | `visuals/VisualRows.java` |
| camera | smooth camera | `minecraft:phantom_membrane` | `visuals/VisualRows.java` |
| camera | motion blur | `minecraft:glass_pane` | `visuals/VisualRows.java` |
| camera | auto f5 | `minecraft:minecart` | `visuals/VisualRows.java` |
| camera | no fov | `minecraft:glass_bottle` | `visuals/VisualRows.java` |
| camera | no bob | `minecraft:chainmail_boots` | `visuals/VisualRows.java` |
| camera | no hurt cam | `minecraft:redstone` | `visuals/VisualRows.java` |
| camera | stretched | `minecraft:map` | `visuals/VisualRows.java` |
| camera | clear weather | `minecraft:water_bucket` | `visuals/VisualRows.java` |
| camera | view model | `minecraft:item_frame` | `visuals/VisualRows.java` |
| camera | hand view | `minecraft:leather` | `visuals/VisualRows.java` |
| camera | animations | `minecraft:wooden_sword` | `visuals/VisualRows.java` |
| camera | swing speed | `minecraft:stone_sword` | `visuals/VisualRows.java` |
| camera | no swing | `minecraft:structure_void` | `visuals/VisualRows.java` |
| camera | inspect | `minecraft:brush` | `visuals/VisualRows.java` |
| camera | glint colour | `minecraft:enchanted_book` | `visuals/VisualRows.java` |
| camera | hand shaders | `minecraft:glow_ink_sac` | `visuals/VisualRows.java` |
| **self** | the group | `minecraft:armor_stand` | `visuals/Visuals.java` |
| self | body view | `minecraft:leather_chestplate` | `visuals/VisualRows.java` |
| self | move animations | `minecraft:leather_boots` | `visuals/VisualRows.java` |
| self | cosmetics | `minecraft:elytra` | `visuals/VisualRows.java` |
| self | jump effect | `minecraft:rabbit_foot` | `visuals/VisualRows.java` |
| self | player model | `minecraft:armor_stand` | `visuals/VisualRows.java` |
| self | skin changer | `minecraft:leather_helmet` | `visuals/VisualRows.java` |
| self | armour shaders | `minecraft:diamond_chestplate` | `visuals/VisualRows.java` |
| self | cursor trail | `minecraft:spectral_arrow` | `visuals/VisualRows.java` |
| self | fake cursor | `minecraft:tripwire_hook` | `visuals/VisualRows.java` |

Paths are under `src/<source set>/java/com/mineux/`. When a row's item changes in the code,
change it in `ICONS`/`GROUPS` in `tools/showcase/render_icons.py` too and rerun it.
