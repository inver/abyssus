# Design

## Context

Everything here is in `projects/app-game-control-line`, on the LWJGL3 main thread that renders the game. See proposal.md for
the motivation and the specs for behaviour.

- **Input today.** `HandleInput` keeps the handle's tilt from the keys (a 0.15 s ramp) and the mouse, "last device wins".
  `HumanControl` calls `input.update(PHYSICS_STEP)` each fixed step, so the tilt is sampled per step from whatever the
  frame left. `ControlLineGame.Keys` feeds it key and mouse events; menus get keys from `GameUiHost.keyDown`, which tries
  `GameUiModel.key` (Left/Right on plane select and score tabs, Up/Down/Enter on boards, Escape, A) and then
  `UiPresenter.keyDown` (Tab, Up/Down only on screens authored with arrows navigation, Enter on a button, Space on a
  button or checkbox, Left/Right on a focused slider, Escape).
- **Threads.** `GameFlow`, `FlightRunner`, `GameUiModel` and the host all run on the main thread, in `ControlLineGame.render`
  (`followFlow()` then `flights.advance()` then the UI). `MainTest`/GL tests are opt-in.
- **GLFW.** `gdx-backend-lwjgl3` brings `lwjgl-glfw` on the compile and runtime classpath, aligned to the build's LWJGL
  3.4.3 by the BOM, and the packages already ship its natives. Nothing in the game calls GLFW directly yet.
- **Stores.** Scores and F2B boards are Jackson DTOs written through `AtomicJsonStore` (temp file, atomic move,
  unreadable file renamed to `.bad`).
- **Authored UI.** `screens/meta.json` holds the screens; `GameUiContract` lists the screen ids, the published bindings
  and the actions, and `ControlLineUiAssetTest` fails when asset and contract differ. Only `playerName` is writable today.
  The toolkit has `slider`, `checkbox`, `progressBar`, `button` and `list`.

## Goals / Non-Goals

**Goals:**
- A device sets the handle and moves through every menu with no per-screen code.
- All logic (mapping math, profiles, navigation, detection, store, hot-plug) runs against a fake device in headless tests.
- GLFW is touched by one small class.

**Non-Goals:**
- No hand-written parser or device database of our own: GLFW's mappings tell a gamepad from a joystick.
- No resolution or monitor choice and no fullscreen key in this change.
- No name entry by device, no rebinding of the keys or mouse, no output (rumble) and no second device at once.

## Decisions

### 1. A `ControllerSource` interface with one GLFW implementation

`ControllerSource.poll(): List<DeviceState>` returns every present device: id (GUID), name, whether it is a gamepad, and a
snapshot of axes, buttons and hat 0. `GlfwControllers` is the only class that imports `org.lwjgl.glfw` (checked by a test
that scans the sources). For a gamepad (`glfwJoystickIsGamepad`) it reads `glfwGetGamepadState` and normalises it: the
six standard axes with **up positive** (GLFW reports a stick's Y as -1 up, so the Y axes are negated), the standard
buttons and the D-pad. For any other device it reads the raw axes, buttons and first hat. It is polled once per frame
(no GLFW callback, so there is no second thread and no event ordering to reason about); a device that was present and is
not any more, or the reverse, is the hot-plug.

*Alternative:* `gdx-controllers` (listeners, a new dependency with its own natives per OS). Rejected: GLFW is already
shipped and gives the same data; the interface keeps the choice reversible.

### 2. The pure core: profiles, mapping, detection, navigation (no GL, plain classes)

- `ControllerProfile` (data): `axis`, `rest`, `invert`, `deadZone`, `curve`, `pauseButton`.
- `AxisMapping.tilt(profile, value)`: the spec's formula. The share of travel is `d = (value - rest) / (1 - rest)` above the
  rest value and `(value - rest) / (rest + 1)` below it, so full travel either way is 1; then the dead zone
  (`(|d| - dz) / (1 - dz)`), the curve (`(1 - c) * x + c * x^3`) and invert.
- `DetectSession` (axis or button): holds the snapshot at its start, takes the axis that moved at least 0.5 from it
  (largest first) or the first pressed button, with a 10 s deadline.
- `PadNavigator`: edge detection and auto-repeat (0.4 s, then 0.12 s) turning a device's state into `Input.Keys`
  codes; stick past 0.5 acts as a direction; it emits nothing while flying or detecting.
- `ControllerProfiles` (store): `controls.json` as Jackson DTOs through `AtomicJsonStore` like `ScoreTable`, keyed by GUID;
  an unreadable file is renamed to `.bad` and `damaged` set.

All of these are plain Kotlin with injected time and a `ControllerSource`, so tests drive them with a fake.

### 3. `ControllerService` ties them to the frame

One object on the main thread, created by `ControlLineGame` and polled at the top of `render()` before `followFlow()`. Each
frame it reads the source, notices hot-plug, applies profiles, and produces: the pad's tilt for the handle (`HandleInput`
gets `pad(tilt)`), a list of navigation keys for the host (only on menu screens), a pause request (only on a human
flight) and the settings screen's live values. It holds the selected device for the settings screen and the active
`DetectSession`. A lost controlling device calls `HandleInput.padLost()`, which neutralises the tilt and falls back to KEYS.

### 4. `HandleInput` gets a third device

`Device.PAD` joins KEYS and MOUSE. `pad(tilt)` takes control when `tilt != 0f` (that is, outside the dead zone), so a
resting stick never overrides the keys; while PAD controls, the tilt is the pad's value (analog, no ramp) including 0. A key
or the mouse takes control back as today. `reset()` returns to KEYS. Because the pad value is sampled per frame and held
across fixed steps, `HumanControl` needs no change.

### 5. Menu navigation sends the keyboard's own keys

`GameUiHost` gets `navigate(keycode)`: it calls `keyDown(keycode)`; for Up/Down that nothing handled (a screen authored with
tab navigation) it sends Tab / Shift-Tab; for a confirm (`Enter`) that nothing handled (a focused checkbox) it sends Space.
So the main menu, plane select, score tabs and boards, the debrief and the settings screen work with no change, and a
screen's own authored `navigation` stays the only source of truth. The nav source is the D-pad or left stick of a
gamepad and the first hat of a joystick; confirm is A / button 0, back is B / button 1.

*Alternative:* a parallel focus system for devices. Rejected: it would duplicate the presenter and drift from it.

### 6. The settings screen is authored UI plus a small model

`GameFlow` gets `Screen.Settings` (`settings()` from the main menu, `back()` returns to it). `GameUiModel` publishes the
screen's values (the device list rows, the chosen device's name, axis, live tilt, invert, dead zone, curve, pause button,
detect status, a damaged message) and accepts three more writes (`invert`, `deadZone`, `curve`, validated and saved at
once); the actions are `settings`, `devicePrevious`, `deviceNext`, `detectAxis`, `detectPause`, `cancelDetect` and
`resetProfile` (back is the existing `back`). `GameUiContract` and `assets/screens/meta.json` gain the `settings` screen, the
main menu button and the bindings; `ControlLineUiAssetTest` keeps them in step. Device choice is a Previous / Next pair
(like plane select), not a list, because one device is the common case.

### 8. Fullscreen is a small `DisplayOptions` seam over libGDX

The Settings screen's checkbox writes the new `fullscreen` binding. `GameUiModel` takes a `DisplayOptions` (`fullscreen`, `setFullscreen`),
the way it takes `ControllerSettings`, and `NoDisplayOptions` stands in for tests and Play. `GdxDisplay` is the real one: `setFullscreen`
saves the choice through `DisplaySettings` (`display.json`, a Jackson DTO through `AtomicJsonStore`, `.bad` on an unreadable file) and
marks the switch pending; `ControlLineGame.render()` calls `applyPending()` at the top of the frame, before anything is drawn, which does
`Gdx.graphics.setFullscreenMode(displayMode)` or `setWindowedMode(1280, 720)`. Deferring to the next frame keeps a window-mode change out
of the middle of drawing the screen that asked for it. At launch `Main` reads `display.json` and, for fullscreen, sets the
configuration's fullscreen mode, so the window opens in the right mode with no windowed flash. libGDX reports the new size through
`resize`, which the game already handles. The writable binding is accepted with no controller connected.

*Alternative:* apply the switch inside the action. Rejected: the action runs while the presenter is drawing, and a window change there
re-enters the frame. *Alternative:* a Settings key such as F11 now. Out of scope here.

### 7. Detect and the back button

Detecting an axis can be cancelled by the device's back button; detecting a button cannot, since any button is a candidate,
so it ends on Escape, a click on Cancel, or the 10 s deadline. While a `DetectSession` is active `PadNavigator` is silent.

## Risks / Trade-offs

- [GLFW's mapping database may not list a given Logitech model] -> an unlisted device is a joystick and works through Detect;
  the spec requires nothing of the database.
- [Two identical devices share a GUID and so a profile] -> accepted and noted in the README; the device list still shows both.
- [Axis rest values and drift vary by device] -> the rest value is recorded at Detect, the dead zone defaults to 10 % and a
  resting stick never takes control from the keys.
- [Linux needs read access to the device node] -> without it GLFW sees no device and the game behaves as without this change;
  noted in the README.
- [A joystick's Y axis polarity is not standard] -> the Invert checkbox and the live bar on the settings screen show it at once.
- [GLFW joystick state is only meaningful with the window created] -> the source is polled from `render()` only, after the
  window exists; tests use the fake.
- [Switching the window mode behaves differently per platform (macOS Spaces, Linux window managers, HiDPI back-buffer scale)] -> the
  switch is libGDX's own `setFullscreenMode` / `setWindowedMode`, applied between frames; the hand check (8.3) runs it on the platform in
  hand, and the launch path uses the configuration so it avoids a runtime switch altogether.
- [A display that cannot do the current mode] -> `setFullscreenMode` can fail; the game keeps the window it has and the checkbox shows
  the real mode (`Gdx.graphics.isFullscreen`) rather than the request.
- [`add-control-line-scale-mode` also edits the main menu requirement, `screens/meta.json` and `screens.golden`] -> this change
  adds its Settings entry as its own requirement; when both are in flight, the later one to archive reconciles the Main menu text.

## Open Questions

- Which button a gamepad's default profile uses for pause on controllers without a Start button: Start (button 7 in the
  standard layout) is assumed; devices that lack it set it with Detect.
