# Proposal

## Why

Control Line is played with W / S or the mouse, which tilts the handle in coarse steps or by window position. A
control-line handle is a physical lever, and a flight stick such as the Thrustmaster T.16000M is the natural way to
fly it: its pitch axis is analog, centred and precise. The game has no joystick support today.

## What Changes

- Reading a connected flight stick through the windowing layer's raw joystick API, so no new dependency is added and
  no gamepad mapping database is involved (the T.16000M is a plain HID stick, not a gamepad).
- Flying: the stick's pitch axis sets the handle tilt directly, with a dead zone. Pulling the stick back tilts the
  handle up. A third input device joins keys and the mouse, under the existing "last used wins" rule.
- Menus and flight: the stick's hat moves through menus and changes the plane, the trigger confirms, and a second
  button goes back in menus and pauses while flying.
- A stick that is absent, or is unplugged mid-game, changes nothing: keys and mouse keep working.
- The Controls table in the game's README documents the stick.
- A **Joystick settings** screen, opened from the main menu: it shows the detected stick's name and a live readout of
  its axes, buttons and hat, lets the player choose the pitch axis (by moving the stick), invert it, set the dead zone
  and assign the confirm and back buttons (by pressing them), and resets to defaults. The defaults are the
  T.16000M's layout.
- The settings are saved in `<user-home>/.abyssus-control-line/joystick.json`, versioned and written atomically like
  `scores.json`; a file that cannot be read is renamed to `joystick.json.bad` and the defaults are used.

Out of scope: choosing between several connected sticks, per-stick profiles, throttle (the engine stays at full thrust until its fuel time ends), the twist axis (the game has no
rudder), other sticks and gamepads beyond what happens to share the same axis layout, the Abyssus Play host
(`ControlLinePlay` receives IDE key and mouse events only), and rumble.

No native Abyssus document (`.scene`, `.abss`, asset `meta.json`) is read or written, so there is no format
validation or versioned format change.

## Capabilities

### New Capabilities

- `control-line-joystick-settings`: the settings screen, the assignable controls, and how the choices are saved.

### Modified Capabilities

- `control-line-game-flow`: the Main menu (which gains Settings), Plane select and Flying requirements gain stick
  input, and the flying input rule gains a third device that follows the saved settings.

## Impact

- `projects/app-game-control-line`: `input/HandleInput` (a stick device), a new stick reader in `input/` with a
  GLFW implementation and a fake for tests, a `settings/` package (the settings, their store and control capture),
  `GameFlow` (a Settings screen), `GameUi` (building it), `ControlLineGame` (polling once per frame, forwarding menu
  keys), tests, and the README's Controls table.
- No new Gradle dependency (`gdx-backend-lwjgl3` already brings LWJGL's GLFW binding).
- `lib-*` modules, the plugins and Abyssus Play are untouched.
