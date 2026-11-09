# MineUX

**A Linux desktop inside Minecraft.** A tiling window manager configured like Hyprland, terminals
running their own shell, a home desk in place of the title screen and an Esc desk in place of the
pause menu. Press **Right Shift** in a world and the desktop opens over the game while the game
keeps running.

The full edition adds client-side visuals: a custom HUD, sky and world shaders, camera and hand
effects, and cosmetics. **Screens** puts your monitors and windows in the world as screens. An
optional [mineux.com](https://mineux.com) account adds friends, DMs and chat. You can skip it and
play as a guest.

![The home desk: the bar, a terminal and the fetch with its campfire](https://mineux.com/showcase/home-desk.png)

---

## The desktop

**Right Shift** opens the desktop over the game. Nothing pauses: mobs keep moving, the server
keeps ticking, and Right Shift (or `close`) puts it away again. Windows you **pin** stay over the
game with the desktop closed.

The **home desk** replaces the title screen: a bar along the top, a terminal and the fetch with its
campfire. Singleplayer, multiplayer, realms, options and the rest are still there. Click one or
type it (`worlds`, `play 1`, `servers`, `join <n>`). `vanilla` opens a button the way vanilla has
it. **Esc** in a world opens the **Esc desk** instead of the pause menu.

The bar shows your workstations, the tray, memory, FPS, the clock, ping in a world, your MineUX
name (or "guest"), the friends count and the bell.

<!-- screenshot: https://mineux.com/showcase/desktop-over-game.png - a world with Right Shift pressed: the bar and one terminal over the running game, 1920x1080 -->
<!-- screenshot: https://mineux.com/showcase/esc-desk.png - Esc pressed in a world: the Esc desk over the game, 1920x1080 -->

## A window manager like Hyprland

Windows tile on their own, in **columns** or **dwindle** layouts. You can float, pin and group them
as tabs, fade them with SUPER+K/L, and keep a **special workspace** for scratch work (SUPER+S).
SUPER is **Alt** by default. `super ctrl`, `super win` or `super shift` changes it.

| Keys | Action |
|---|---|
| SUPER+Enter | new terminal |
| SUPER+Q | close the window |
| SUPER+F | float / tile |
| SUPER+P | pin over the game |
| SUPER+G | group windows as tabs |
| SUPER+arrows / SUPER+Shift+arrows | move focus / move the window |
| SUPER+1..9 | switch workstation, in game too |
| Alt+drag / Alt+right drag | move / resize |

It is all set in a real **`hyprland.conf`** (`dotfiles` opens it): gaps, borders and their
gradients, bezier curves and animations, binds. Saved changes apply at once. `hyprctl reload`,
`hyprctl keyword`, `hyprctl binds`, `hyprctl dispatch` and `hyprctl configerrors` work the way you
would expect.

![Three tiled terminals: the home desk, bonsai and pipes.sh](https://mineux.com/showcase/tiling.png)

![A floating terminal with neofetch over the tiled ones](https://mineux.com/showcase/floating.png)

## muxsh, the terminal

Every terminal runs **muxsh**, with tab completion, history and clickable output. `help` lists
every command.

- **`join <address>`** connects to any server straight from the terminal. **`join <name>`** or
  **`join <number>`** connects to one from your server list. It works on the home desk and inside
  a world (it leaves the world first), with no trip back to the main menu. `servers` lists the
  saved ones.
- **`quit`** closes the game, after asking. In a world, the world is saved first.
- **`reboot`** restarts the game, after asking. It closes the way Quit Game does (a singleplayer
  world is saved), then starts the same game again with the same Java, options and arguments.
- **`neofetch`**: your client in the usual neofetch layout, with the MineUX logo and a campfire.
- **`pipes.sh`** and **`bonsai`** take over a terminal until Ctrl+C. **`art next`** / **`art list`**
  change the desktop art.
- **`lookup <player>`** opens a window with that player's MCPVP and Minemen stats, rating cards and
  graphs. **`discord <player>`** finds a player's Discord ID.
- **`bind r "toggle rpc"`** runs any command on a key. **`say`** writes to the server chat or runs a
  /command.
- Every setting is a folder you can walk: `ls`, `cd`, `man`, `tree`, `toggle`, `open`.
- **`accounts`** switches between Minecraft accounts (Microsoft sign-in). **`quit`**,
  **`shutdown`** and **`reboot`** go out with a CRT tube-off.

![muxsh on the home desk: ls lists the desk's buttons, the commands and the modules](https://mineux.com/showcase/terminal-ls.png)

<!-- screenshot: https://mineux.com/showcase/terminal-programs.png - pipes.sh, bonsai and art in three tiles, the art pane showing a piece from `art list`, 1920x1080 -->
<!-- screenshot: https://mineux.com/showcase/lookup.png - `lookup <player>` window open beside a terminal (use a public test name, nobody personal), 1920x1080 -->

## Workstations

A **workstation** is a whole setup: its own windows and its own module settings. Turn the
crosshair on in one and it stays off in the others. Use `ws` to list them, and `ws new`, `ws open`,
`ws mv` and `ws rm` to manage them. Hold SUPER and press a number to switch, in game too with the
desktop closed.

<!-- screenshot: https://mineux.com/showcase/workstations.png - the bar showing two or three workstations, switching between two that have different windows (or the slide between them), 1920x1080 -->

## <img src="https://mineux.com/showcase/icon-comparator.png" width="32" alt=""> The tray and its Glass settings windows

Each tray icon on the bar is a group of modules, and each group is a Minecraft item. Hover a group
and its rows fade in, one item each. Click a row to open the group's **Glass** settings window on
that row's page. Right-click a row to flip it. Right-click the group to turn the whole group off,
and right-click again to bring back exactly what was on. Sliders, choices, colours and switches
save as you touch them, per workstation.

The client's own group is the **comparator**. It holds the Discord status
(<img src="https://mineux.com/showcase/icon-note_block.png" width="16" alt="">
note block): where you are and what is running, shown on your Discord profile.

![Hovering the world group: its rows fade in under the bar](https://mineux.com/showcase/tray-strip.png)

![The world group's Glass settings window, open on sky shaders](https://mineux.com/showcase/glass-window.png)

<!-- screenshot: https://mineux.com/showcase/tray-glass.png - in a world, desktop open, only the world group's Glass window open on "sky shaders" with an aurora sky behind it, 1920x1080 -->

---

## Visuals (full edition)

Four tray groups and 42 modules. They change only what **you** see. Nothing extra is sent to
the server. Each module starts off, so turn on what you want.

### <img src="https://mineux.com/showcase/icon-compass.png" width="32" alt=""> hud: your screen's overlay

<img src="https://mineux.com/showcase/group-hud.png" height="32" alt="crosshair, hotbar, scoreboard, chat, main menu, theme textures">

- **Crosshair**: any shape, size and colour (or rainbow). It opens out as you move, marks your hits,
  bursts on a kill and rings the attack charge.
- **Hotbar**: a glass dock with a sliding selection. Health, absorption, armour, food and air show
  as bars, and experience as a strip.
- **Scoreboard** as a glass card on either side. **Chat** with timestamps, repeats folded into
  one, a glass background and new lines sliding in.
- **Main menu**: the home desk over black, or over an animated scene (aurora, nebula, rain, bokeh,
  silk, grid).
- **Theme textures**: the game's menus, buttons and HUD sprites tinted toward your accent colour.

<!-- screenshot: https://mineux.com/showcase/hud.png - in a world: custom crosshair, glass hotbar with the bars, glass scoreboard and glass chat with timestamps, 1920x1080 -->

### <img src="https://mineux.com/showcase/icon-sunflower.png" width="32" alt=""> world: the world on your screen

<img src="https://mineux.com/showcase/group-world.png" height="32" alt="ambience, no render, totem pop, hit effects, sky shaders, world shaders, particles, item physics">

- **Sky shaders**: the sky drawn by a shader of its own, from 27 presets (aurora, galaxy, black hole,
  thunderstorm, nebula, synthwave, blood moon...). The fog takes the sky's horizon colour.
- **World shaders**: sunlight, contact shadows, god rays, low fog, bloom, colour grading and a
  vignette, from a preset (realistic, golden hour, misty, cinematic, dreamy, stormy, neon) or your
  own strengths.
- **Ambience**: your own time of day, weather, fog and sky colour, on your screen only.
- **Totem pop** effects that follow whoever popped (rings, shockwave, halo, runes, rays, spiral).
  **Hit effects** with particles, a sound, and lightning or a burst on a kill.
- **Particles** falling around you, **item physics** (items lie flat and tumble), and **no render**
  for overlays in the way.

<!-- screenshot: https://mineux.com/showcase/sky-aurora.png - world/sky "aurora" at night over a landscape, HUD hidden (F1), 1920x1080 -->
<!-- screenshot: https://mineux.com/showcase/world-shader.png - world/shader "goldenhour" over the same place in the day, 1920x1080 -->

### <img src="https://mineux.com/showcase/icon-spyglass.png" width="32" alt=""> camera: your camera and your hand

<img src="https://mineux.com/showcase/group-camera.png" height="32" alt="zoom, freelook, camera tweaks, smooth camera, motion blur, auto f5, no fov, no bob, no hurt cam, stretched, clear weather, view model, hand view, animations, swing speed, no swing, inspect, glint colour, hand shaders">

- **Zoom** (the mouse slows to match), **freelook**, third-person distance, **smooth camera** and
  **motion blur** (the hand and HUD stay sharp).
- **Auto F5** while a container is open. **No FOV** change, **no bob**, **no hurt cam**, a
  **stretched** picture and **clear weather**.
- **View model**: each hand moved, turned and scaled. **Hand view**: your arm holding the item as
  it is held in third person.
- **Swing animations**: old (1.7), block-hit, push, down, spin, smooth, stab, slice, windmill,
  combo, dance, charge, momentum and whirlwind. Also **swing speed**, **no swing** and a
  knife-style **inspect**.
- **Glint colour** for enchanted items, and **hand shaders**: chroma, invert, outline, pixelate,
  prism.

These only change what is drawn. Your real swing, its timing and its packets stay the game's own.

<!-- screenshot: https://mineux.com/showcase/hand.png - first person holding an enchanted sword: view model moved, glint colour set, hand shader "prism", 1920x1080 -->

### <img src="https://mineux.com/showcase/icon-armor_stand.png" width="32" alt=""> self: your own player as you see it

<img src="https://mineux.com/showcase/group-self.png" height="32" alt="body view, move animations, cosmetics, jump effect, player model, skin changer, armour shaders, cursor trail, fake cursor">

- **Cosmetics**: a cape, wings, a hat, an aura, a kill effect and a pet that follows you. They are
  drawn on your client only and nobody else sees them.
- **Body view** in first person, **move animations** (leaning into speed and turns, tucked in the
  air), and a **jump effect** ring.
- **Player model**: become something else in third person, or load your own `.bbmodel`, `.obj`,
  `.glb` or `.gltf`. **Skin changer**: another skin on you, as only you see it.
- **Armour shaders** in eight materials: glass, crystal, hologram, molten, void, chrome, neon wire
  and frost.
- A **cursor trail** and a **fake cursor** on the game's own screens.

<!-- screenshot: https://mineux.com/showcase/cosmetics.png - third person (F5) in the daylight world: cape and wings on, armour shader "crystal", jump ring, 1920x1080 -->

---

## <img src="https://mineux.com/showcase/icon-black_stained_glass_pane.png" width="32" alt=""> Screens: your monitors and windows in the world

`screens add m1` puts your monitor in the world as a live screen. `screens add <id>` does the same
for one window. Press **B** for the screens menu, or **N** for a freecam to arrange them. The
freecam is purely visual (nothing is sent to the server) and hides other players while you fly.
Density and curve set each screen's size and bend, up to a half circle around you.

Turn on **clickable**, and clicking a screen switches to that window with the cursor where you
clicked. Nothing is ever clicked or typed into it for you. **Ctrl+Alt+Home** brings the game back.
Capturing works on Windows. On other systems Screens only shows your friends' screens.

### <img src="https://mineux.com/showcase/icon-ender_eye.png" width="32" alt=""> Friends' screens

Share a screen live with your **MineUX friends** on the same multiplayer server:

- It is **friends only**, and **both** of you have to turn "Enable screens" on for each other.
  Anyone else sees nothing, and so does a guest.
- Sharing is **off every time** you start. `screens share 1` asks before it starts. A shared screen
  is marked **LIVE** and shows how many friends are watching.
- With nobody watching, no frames are sent. If either of you turns screens off or removes the
  friend, the screens disappear on both sides at once.
- Sharing works only on multiplayer servers, not in singleplayer or LAN.

<!-- screenshot: https://mineux.com/showcase/screens.png - a creative world with a window (not your desktop: a plain window with a picture) placed as a large curved screen, 1920x1080 -->
<!-- screenshot: https://mineux.com/showcase/screens-freecam.png - the screens freecam (N) looking at that screen from the side while arranging it, 1920x1080 -->

---

## The greeter and your MineUX account (optional)

MineUX starts at a **ly-style greeter**: sign in to your [mineux.com](https://mineux.com) account,
or **continue as guest**. The stream animation behind it (or doom fire, or ascii) and its colour are
yours to set in `dotfiles`. **F1** shuts the game down from the greeter.

![The greeter: sign in or continue as guest](https://mineux.com/showcase/greeter.png)

Signed in, you get:

- **Friends**: who is online and what they are playing. Each profile has a 3D skin that follows the
  cursor, switches for what that friend may do, and message, join, bring, mute and remove.
- **DMs** (`msg`, `dm`) and the **#public** chatroom, each as a tab in `irc`.
- **The bell**: messages and changelog posts. You can join a friend on a server they let you see.

<!-- screenshot: https://mineux.com/showcase/friends-irc.png - signed out (guest) is fine: `friends window` and `irc` open side by side showing their guest notes; never post in #public, 1920x1080 -->

---

## Editions

- **MineUX** (full): everything on this page.
- **MineUX Lite**: the desktop, the window manager, terminals, workstations, Screens and the
  account features, without the visual modules. Its tray has only `client` and `screens`.

Both run on Minecraft **26.2** with Fabric, on **OpenGL** and on **Vulkan**. Every visual runs on
both. The few settings that need OpenGL are hidden when you play on Vulkan.

## Privacy

The account is optional. **Guests send nothing to mineux.com**, and everything except friends, DMs,
chat, notifications and screen sharing works without an account. The visual modules only change
your own screen. Read the [privacy policy](https://mineux.com/privacy.html).

## Licence

All rights reserved. The source is
[published to read](https://github.com/secretnarwhal/MineUX). You may play the official builds and
build the code for your own use. Anything else needs written permission.
