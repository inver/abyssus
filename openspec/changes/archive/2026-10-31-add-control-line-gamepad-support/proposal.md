# Proposal

## Why

Control Line is flown with the keyboard and the mouse, but a control-line handle is one analog axis, which a gamepad,
flight stick or wheel gives far better than two keys. A player with a Logitech pad or stick has no way to use it, and
earlier changes put gamepads out of scope, so nothing in the game or its specs knows a device exists.

## What Changes

- A **controller input layer** reads the connected joystick, gamepad or wheel through GLFW's joystick API (already on the
  game's classpath, no new dependency) behind a small source interface, so tests feed it a fake.
- A third handle device, **GAMEPAD**, beside the keys and the mouse: the chosen axis sets the handle's tilt with the
  device's dead zone, invert, rest value and curve. Whichever device was used last controls the handle.
- A **per-device profile**, kept by the device's GUID, holds the handle axis, its rest value, invert, dead zone, curve and the
  pause button. Gamepads get a default profile; any other device is set up on the settings screen.
- A **Fullscreen** option on the same Settings screen (its Display section): a checkbox that switches the window between fullscreen
  and windowed at once and is remembered for the next start, in `~/.abyssus-control-line/display.json`. The screen becomes the
  game's general Settings screen, with a Display section above the Controller section.
- A **Settings** screen, opened from the main menu: it lists the detected devices, shows the handle axis live, and has
  **Detect** buttons that pick the axis or button the player moves or presses, plus invert, dead zone and curve controls
  and Reset. Settings are kept in `~/.abyssus-control-line/controls.json`.
- **Menu navigation by device**: the D-pad or hat, or the stick, moves focus and changes the plane and the score tab;
  a confirm button presses the focused control, a back button goes back. The device sends the same keys the keyboard
  does, so every screen works with no screen-by-screen code.
- A bindable **pause button** pauses a flight; in flight the stick drives the handle and does not navigate.
- A device that is unplugged or plugged in mid-flight leaves the flight running, with the handle back at neutral.

Files: `controls.json` and `display.json` are new user files of the game (version 1), not native Abyssus documents, and each is read
with the usual safe fallback (an unreadable file is renamed to `.bad` and the defaults used). The bundled
`assets/screens/meta.json` (a native document, `format: "abyssus"`, `formatVersion: 1`) gains one screen, `settings`, and
its published bindings and actions; no format version changes. No scene, `.abss` or other asset field changes.

Out of scope: typing a name with a device (name entry saves the offered name), an on-screen keyboard, force feedback,
rumble, a resolution or monitor choice, a fullscreen key such as F11, borderless or exclusive fullscreen variants, vsync and quality
settings, more than one device controlling a handle at once, remapping the keyboard or mouse, gamepad input in Abyssus's
Play mode, and the F2B Watch AI (it ignores every input device).

## Capabilities

### New Capabilities
- `control-line-controller-input`: reading a device, its per-device profile, the handle axis (rest value, dead zone, invert,
  curve), the pause button, menu navigation, hot-plugging and the settings file.

- `control-line-display`: the Fullscreen setting, its immediate effect, its saved state in `display.json` and its start-up.

### Modified Capabilities
- `control-line-game-flow`: a Settings entry and screen (a Display section with Fullscreen above the Controller section), and the Flying requirement (the handle's devices and the pause
  button). The Settings entry is added as its own requirement, not by changing "Main menu", which
  `add-control-line-scale-mode` also changes.

## Impact

- `projects/app-game-control-line`: a new `display` package (the fullscreen option, its `display.json` store and the libGDX window
  switch), `Main` (starts in the saved mode), a new `controller` package (source interface, GLFW wrapper, profile and mapping math,
  navigator, settings store), `input/HandleInput` (a third device), `FlightRunner` and `ControlLineGame` (polling each
  frame, the pause button, hot-plug), `GameFlow` (a Settings screen), `screens` (`GameUiModel` and the contract: the
  settings screen's values and actions), and `README.md`.
- `projects/app-game-control-line/project/ControlLine/assets/screens/meta.json`: the `settings` screen, a Settings button on the
  main menu and the new bindings. `screens.golden` is re-recorded.
- No change to `lib-core`, `lib-physics`, the plugin, the Play module or any file format version. The shipped-game
  boundary is unchanged: GLFW is already shipped with the LWJGL3 backend.
- Overlap: `add-control-line-scale-mode` also edits the main menu and `screens/meta.json`; whichever is archived second
  reconciles the Main menu requirement text.
