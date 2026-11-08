# MineUX

MineUX is a Fabric mod for Minecraft 26.2 that puts a Linux-style desktop in the game: a tiling
window manager, terminals with their own shell, a home desk in place of the title screen and an
Esc desk in place of the pause menu.

It comes in two editions:

- **MineUX** (full): the desktop plus the visual modules (HUD, world, camera, hand and self
  visuals, cosmetics).
- **MineUX Lite**: the desktop without the visual modules.

## What it does

- **Right Shift** opens the desktop over the game while you are in a world. The game keeps
  running and nothing pauses.
- **Workstations**: each one keeps its own windows and its own module settings. A module that is
  on in one workstation stays off in the others.
- **Visuals** (full edition): crosshair, hotbar, chat, sky and world shaders, camera and hand
  effects, capes and other cosmetics. They only change what you see. Nothing extra is sent to
  the server.
- **Screens**: shows your monitors and windows as screens in the world. A shared screen is seen
  only by MineUX friends who have screens turned on for each other. Sharing starts off every
  time.

## Online features are optional

The friends list, DMs, chat, notifications and Screens sharing need a
[mineux.com](https://mineux.com) account. You can also play as a guest. Guests send nothing to
mineux.com, and the rest of the mod works without an account. See the
[privacy policy](https://mineux.com/privacy.html).

## Building

You need:

- JDK 25
- the Rust toolchain for MSVC (`stable-x86_64-pc-windows-msvc`), to build Screens' native
  capture library in `native/`

Then run:

```
./gradlew build                  # both jars, the tests and the source checks
./gradlew build -Pedition=lite   # the same, with lite as the dev-run edition
./gradlew runClient              # a dev run of the full edition (-Pedition=lite for lite)
```

The jars land in `build/libs/`: `mineux-<mc>-<version>.jar` is the full edition and
`...-lite.jar` is lite. The build runs `cargo build --release` in `native/`. If cargo is
missing, it uses an existing `native/target/release/screens.dll` instead.

## Licence

MineUX is source-available under the
[PolyForm Noncommercial License 1.0.0](LICENSE.md). You may use, change and share it for
noncommercial purposes. Commercial use is not allowed. See [LICENSE.md](LICENSE.md) for the full
terms.

## Contributing

Issues and pull requests are welcome. Every contribution comes in under the contributor agreement
in [CONTRIBUTING.md](CONTRIBUTING.md). Read it before you open a pull request.

## Credits

Microsoft account sign-in: based on idkhack's MicrosoftLogin by riverxia.
