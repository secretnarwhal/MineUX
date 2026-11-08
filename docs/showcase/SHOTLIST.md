# Shot list

The screenshots `modrinth.md` still needs, in the order the page shows them. The Modrinth page
loads its images from mineux.com (`server/site/showcase/`, served at
`https://mineux.com/showcase/<file>`), so they show whether or not the GitHub repo is public.

Each shot to take is marked in `modrinth.md` with a comment naming its URL, such as
`<!-- screenshot: https://mineux.com/showcase/hud.png - ... -->`. For each one:

1. Save the shot as `server/site/showcase/<file>` (the name in the comment).
2. In `modrinth.md`, swap the comment for
   `![what it shows](https://mineux.com/showcase/hud.png)`.
3. Deploy the site once you have them all: `bash server/deploy/deploy.sh`.

If the README should show a shot too, also put a copy in `docs/showcase/img/`. The README uses
relative paths.

**Every shot:** PNG, 16:9, at least 1920x1080, the game window only (no taskbar, no other mods'
UI), no personal names, servers or chat. Run the full edition (`./gradlew runClient`) as a guest,
in a creative singleplayer world. A cherry grove or a coast at midday looks good: `/time set noon`
and `/weather clear`. Turn on only the modules a shot is about (right-click a tray group to turn
it all off).

## Already taken (2026-11-08, dev client, guest, GUI scale 3, main 181b09c logo)

These are in `img/`, and the ones the Modrinth page uses are also in `server/site/showcase/`
(crops and all, flat: `tray-strip.png`, `glass-window.png`; icons as `icon-<item>.png`, group
strips as `group-<group>.png`). Retake any of them if you like.

| File | What it shows |
|---|---|
| `img/greeter.png` | the greeter: login, password, continue as guest, the stream animation |
| `img/home-desk.png` | the home desk after `clear`: the bar, the 3D Diagonal logo, fetch with the campfire |
| `img/terminal-ls.png` | the home desk terminal after `ls`: the desk's buttons, the commands, the modules |
| `img/tiling.png` | three tiled terminals: the home desk, `bonsai`, `pipes.sh` |
| `img/floating.png` | the same with a floating terminal running `neofetch` |
| `img/crops/tray-strip.png` | the bar with the world group hovered, its rows faded in |
| `img/crops/glass-window.png` | the world group's Glass window on "sky shaders" |
| `img/crops/*-960.png` | 960x540 copies of the above for the README |

## To take, in order

1. **`desktop-over-game.png`**: in the world, press Right Shift. The bar and one terminal
   (`neofetch`) over the running game, with mobs or water moving behind.
2. **`esc-desk.png`**: Esc in the world. The Esc desk over the game.
3. **`terminal-programs.png`**: three tiles running `pipes.sh`, `bonsai` and the art pane
   (`art next` until a nice piece).
4. **`lookup.png`**: `lookup <player>` beside a terminal. Use a well-known public test name,
   not a friend's.
5. **`workstations.png`**: `ws new build`, a different window layout in each, and the bar
   showing both workstations. Catch the slide between them if you can.
6. **`tray-glass.png`**: in the world with the desktop open, only the world group's Glass
   window, on "sky shaders" with the aurora sky behind it.
7. **`hud.png`**: hud group on: custom crosshair, the glass hotbar with its bars, the glass
   scoreboard (needs a scoreboard: `/scoreboard objectives add k dummy` and
   `/scoreboard objectives setdisplay sidebar k`) and the glass chat with a few timestamped lines.
8. **`sky-aurora.png`**: world/sky "aurora" at night (`/time set midnight`) over a landscape,
   F1 to hide the HUD.
9. **`world-shader.png`**: world/shader "goldenhour" at the same place by day, F1.
10. **`hand.png`**: first person with an enchanted sword. View model moved, glint colour set,
    hand shader "prism".
11. **`cosmetics.png`**: third person (F5). Cape and wings on, armour shader "crystal" on
    armour, mid-jump for the ring.
12. **`screens.png`**: a window as a large curved screen in the world. Use a plain window with
    a picture (`powershell -File tools/showcase/source_window.ps1 docs/showcase/img/home-desk.png`
    opens one, then `screens` to find its id and `screens add <id>`), never your real desktop.
13. **`screens-freecam.png`**: the screens freecam (N) looking at that screen from the side.
14. **`friends-irc.png`**: as a guest, `friends window` and `irc` side by side, showing their
    guest notes. Never post in #public.

Optional: a Vulkan shot of one of the above (Video Settings, restart) to show both backends work,
and the lite edition's tray (`./gradlew runClient -Pedition=lite`) with only `client` and
`screens`.
