# MineUX: in-game test checklist

None of this has been run in the game yet. Everything below passes headlessly
(`./gradlew build` for both editions, the unit tests, the shader compile tests for Vulkan and
OpenGL, `tools/mixin_targets.py`, HoistCheck/PureCheck/SwitchCheck, and
the live API, chat and relay tests against mineux.com). This list is what only a person in the
game can check. Tick each box. When one fails, note the step and copy the `latest.log` lines
around it.

**Runs:** `./gradlew runClient` is the full edition, `./gradlew runClient -Pedition=lite` is lite.
Run one client at a time. Then do one pass with each built jar
(`build/libs/mineux-<mc>-<version>.jar` and `...-lite.jar`) in a plain Fabric instance. Do every
section on **OpenGL** first. Then switch to **Vulkan** in video settings, restart, and repeat
sections 4 and 5. A row marked *(full)* is full edition only.

**Accounts:** a MineUX account from https://mineux.com (register, then verify by mail). For the
sharing and social sections you need a second account and a second PC (or a friend).

## 1. Greeter (both editions)

- [ ] First launch: the ly-style greeter comes up before the title screen. The stream animation
      plays behind the box. The line under the box names the website. The password-reset
      pointer and the spam-folder note are there.
- [ ] `Tab`, `Shift+Tab` and the arrow keys move the focus. `Space` ticks "remember user name"
      and "stay signed in". `Enter` acts on the focused row.
- [ ] A wrong password says "wrong user name or password", and an unknown user gets the same
      words.
- [ ] A right sign-in fades to black (about 0.75 s). The home desk then boots: boot sequence,
      bar, terminal and fetch.
- [ ] Relaunch with "stay signed in" ticked. There is black and, after about 0.5 s, a
      "resuming ...'s session" line, then the desk. The greeter box is never drawn.
- [ ] Relaunch with "stay signed in" off: the box is back, with the user name filled in when
      "remember user name" is ticked.
- [ ] **Continue as guest** reaches the desk. `whoami` says guest.
- [ ] `F1` in the greeter shuts the game down with the tube-off ending.
- [ ] `config/mineux/session.json` holds no password, and on Windows only your account can
      read it (file properties, Security tab).
- [ ] `dotfiles ly animation=doom`, then `ascii`, then `none`: each shows the next time the
      greeter opens (`logout`). `dotfiles ly stream_color=#00FF88` changes the stream and the
      desk's tint.
- [ ] Offline (network unplugged) with a remembered session: the box comes up, and the next
      online launch still resumes.

## 2. Desktop, home desk and Esc desk (both editions)

- [ ] Title screen = home desk: the bar along the top, a terminal, and fetch with the campfire.
      The prompt is `user@mineux`. The shell is `muxsh` (`neofetch` says so). Its `mineux` row
      shows your MineUX account or guest.
- [ ] Bar, right end: the bell, the friends count, **your MineUX name (gold) or "guest"**,
      clock, FPS, ping (in a world) and memory. A click on the name prints `whoami` in the
      desk's terminal, with a clickable login or logout.
- [ ] `help` lists every command, `login` and `logout` among them. Tab completion works.
- [ ] `worlds`, `play 1`, `servers`, `join <n>`, `disconnect`, `vanilla`.
- [ ] In a world, **Right Shift** opens the desktop as an overlay: the game keeps running and
      mobs still move. Right Shift or `close` puts it away.
- [ ] Esc in a world opens the Esc desk. Its credit line reads "made with love, by goblinpiss".
- [ ] SUPER defaults to Alt: SUPER+Enter opens a terminal, SUPER+Q closes it, SUPER+F
      floats, SUPER+arrows move focus, SUPER+Shift+arrows move windows, SUPER+G groups,
      SUPER+P pins (a pinned pane stays over the game with the desktop closed), SUPER+K/L
      change opacity.
- [ ] `super ctrl` changes the key. `dotfiles` opens `hyprland.conf`. An edit plus
      `hyprctl reload` takes effect, and `hyprctl configerrors` lists mistakes.
- [ ] `pipes.sh` and `bonsai` take a terminal over, and Ctrl+C ends them. `art next` and
      `art list` work.
- [ ] `reboot` and `shutdown` ask first, then go down with the tube-off ending.
- [ ] `accounts`: the Minecraft account switcher lists accounts. `accounts login` opens the
      Microsoft sign-in, and switching account changes the name in the prompt.
- [ ] `lookup <player>` opens a window with stats, and `discord <player>` works.
- [ ] The Discord status shows up on your Discord profile when Discord is running (`rpc` in the
      `client` tray group).

- [ ] **Desk scale.** At GUI scale 2, 3 and auto (Options, Video), the home desk, the Esc desk
      and the Right Shift overlay look the same size, with crisp text; the HUD follows the
      game's GUI scale. A click on a tray icon, a window drag and a scroll land where the cursor
      is. `desk scale 3`, then `auto`, `1`, back to `2`: the desktop changes size at once, and
      the settings window (`desk`) does the same. The friends app's skin preview and the tray's
      item icons sit in their boxes at every scale. The greeter and the screens menu follow it.

## 3. Workstations (both editions)

- [ ] `ws` lists them. `ws new test`, `ws open test`, `ws mv test t2`, `ws rm t2`. SUPER+1..9
      switch workspaces, in game too with the desktop closed.
- [ ] Each workstation keeps its own windows.
- [ ] *(full)* Each workstation keeps its own visual settings: turn the crosshair on in one,
      switch, and it is off in the other. A workstation saved before a module existed gets
      that module off.
- [ ] SUPER+S toggles the special workspace.

## 4. Tray and settings windows

- [ ] The tray on the bar shows the groups `client` and `screens` (both editions), plus
      *(full)* `hud`, `world`, `camera` and `self`, each with an item icon.
- [ ] Hovering a group fades its rows in, one icon each. A group with anything on is lit.
- [ ] A click on a row opens the group's Glass settings window on that row's page, and every
      row of the group has a page. A right click on a row flips its switch.
- [ ] A click on a group icon opens its window on the first row. A right click on a group icon
      turns the whole group off, and a second right click brings back exactly what was on.
      This is remembered per workstation (`tray.json`).
- [ ] `gui topbar text` makes a click list the module in a terminal instead, and
      `gui topbar window` sets it back.
- [ ] Sliders, choices, colours and switches in a settings window save at once and survive a
      restart.
- [ ] On Vulkan: settings marked `vulkan=no` are hidden, and the log says how many.

## 5. Visual groups *(full)*

Turn each one on from its tray row, check what it does, then turn it off and check that it is
gone.

**hud**
- [ ] crosshair (shape, colours, opens out as you move, hit marker, kill burst, charge ring)
- [ ] hotbar (glass dock, health/armour/food/air bars, xp strip)
- [ ] scoreboard (glass card, either side)
- [ ] chat (timestamps, repeats folded, glass background, slide-in)
- [ ] main menu: `skin scene` puts the home desk over an animated scene (aurora, nebula,
      rain, bokeh, silk, grid, picture), with the parallax, spotlight, account card and accent colour.
      `skin terminal` brings back the black.
- [ ] theme textures: menus, buttons and hud sprites tinted toward the accent colour. Resources
      reload once the setting has stood still.

**world**
- [ ] ambience (time, weather, fog, sky colour, light on your screen only)
- [ ] no render
- [ ] totem pop (rings, shockwave, halo, runes, rays, spiral; never through walls)
- [ ] hit effects (particles, sound, lightning or burst on a kill)
- [ ] sky shaders (each preset)
- [ ] world shaders (each preset and strength)
- [ ] particles
- [ ] item physics

**camera** (camera and hand)
- [ ] zoom (hold the key, mouse slows), clear weather
- [ ] freelook, camera tweaks (distance), smooth camera, motion blur (the hand and hud stay
      sharp), auto f5 (third person while a container is open), no fov, no bob, no hurt cam,
      stretched
- [ ] view model, hand view, swing animations (every style), swing speed, no swing, inspect,
      glint colour, hand shaders (chroma, invert, outline, pixelate, prism)

**self**
- [ ] body view, move animations, jump effect
- [ ] player model (built-ins, and a `.bbmodel`/`.obj`/`.glb` in `config/mineux/models`). Its
      load status shows as a **notification card** over the hotbar, not in chat.
- [ ] skin changer: "browse files" opens a file picker, and its outcome shows as a
      notification card and on the window's stat line, not in chat. A bad file shows a red
      card. Also try another player's skin.
- [ ] armour shaders (all 8 materials)
- [ ] cosmetics (cape, wings, hat, aura, kill effect, pet)
- [ ] cursor trail, fake cursor (on the game's own screens)

## 6. Screens (both editions)

**Solo**
- [ ] `screens` lists sources. `screens add m1` puts your monitor in the world, and
      `screens add` with a window id puts a window there.
- [ ] B opens the menu and N the freecam to arrange screens. Ctrl+Alt+Home goes back to the
      game.
- [ ] The picture is live (fps per `screens/fps`). Density and curve change the screen's size
      and bend.
- [ ] clickable: a click switches to that window and puts the cursor where you clicked.
      Nothing is ever typed into it. draggable with clickable off picks the screen up.
- [ ] `screens remove 1`. Leaving the world stops every capture.
- [ ] Works on Vulkan as well as OpenGL.

**Two players** (two PCs, the same multiplayer server, not singleplayer or LAN; A and B are
MineUX friends, both signed in)
- [ ] Signed-in player A: `screens share 1` asks first, and `share 1 confirm` shares. The
      screen is marked LIVE while shared. Sharing is off by default. The share button's
      tooltip says only friends with screens enabled see it.
- [ ] Friends only: with "Enable screens" off on both profiles B sees nothing; A turns it on
      for B (B's profile of A now says "A has screens on") and B still sees nothing; B turns it
      on too and B (with `screens/share`, friends' screens, on) sees A's screen live within
      `watchRange`, and A sees B's.
- [ ] A sees a viewer count. With nobody watching, A sends no frames.
- [ ] Either of them switching "Enable screens" off makes the screens disappear on both sides
      at once; so does removing the friend.
- [ ] A third signed-in player C who is not a friend sees nothing.
- [ ] A guest sees nothing and is told screens are shared with friends only and need a MineUX
      sign-in (share button, `screens`). Singleplayer and LAN say that sharing works only on
      multiplayer servers.
- [ ] `screens unshare 1` makes the screen disappear on B's side.
- [ ] A `logout`s while sharing: the relay connection ends, A's screens go away for B, and A
      sees the friends-only sign-in note.

## 7. Social: friends, DMs, irc, bell (both editions)

- [ ] As a guest: friends, irc and the bell each show the guest note, and nothing errors.
- [ ] Signed in: the house account `goblinpiss` is already a friend. The friends count on the
      bar counts friends online.
- [ ] `friends window` opens the list with faces. A friend's profile shows the 3D skin
      looking at the cursor, the permission switches (Enable screens among them, with whether the
      friend has screens on for you), and message/join/bring/mute/remove.
- [ ] The add-friend line sends a request. The second account accepts it, and both lists
      update live.
- [ ] `msg <friend> hi` and `dm <friend>`: the conversation opens in irc. A DM bell row or
      card opens the conversation.
- [ ] `irc`: the `#public` tab (and no other room with nothing registered), lines sent and received, Ctrl+C quits.
- [ ] The bell: unread count, `notifications read`, `notifications clear`, and changelog posts.
- [ ] Join: a friend on a visible server shows a join button, and it joins.
- [ ] **Account change:** `logout` signs out and the greeter comes up (Esc returns to the game
      in a world). The friends stream, irc and the bell empty at once. Sign in as the second
      account and they fill again with that account's friends, conversations and bell, with
      nothing left over from the first. `login` as a guest opens the greeter too.

## 8. Lite edition only

- [ ] The tray has `client` and `screens` only. `help` and the settings tree name no visual
      module, and no visual mixin errors appear in the log.
- [ ] The greeter, desktop, workstations, Screens and social all work as in sections 1-3, 6
      and 7.
