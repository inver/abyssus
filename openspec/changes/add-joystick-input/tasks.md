# Tasks

## 1. Settings model and store

- [ ] 1.1 Add `JoystickSettings` (pitch axis, invert, dead zone, confirm and back buttons) with the T.16000M defaults, and `SettingsStore` kept in `joystick.json` like `ScoreTable`. Verify with `SettingsStoreTest`: defaults without a file, round trip, atomic write, a damaged file or unknown version renamed to `.bad` with the `damaged` flag set.
- [ ] 1.2 Add `ControlCapture` (furthest-moved axis past a threshold, newly pressed button, cancel). Verify with `ControlCaptureTest`: picks the right axis, ignores jitter, picks the pressed button, cancel leaves the settings unchanged.

## 2. Stick reading

- [ ] 2.1 Add `StickState` (all raw axes, buttons and hat), the `JoystickSource` interface and `StickEdges` (hat and button press edges to key codes, using the settings' buttons), free of GLFW. Verify with `StickEdgesTest`: one event per press, none while held, none for an absent stick, an out-of-range button never fires.
- [ ] 2.2 Add `GlfwJoystickSource`, choosing the first present joystick with at least two axes. Verify it compiles (`./gradlew :app-game-control-line:compileKotlin`); behaviour is checked in 5.1.

## 3. Handle tilt from the stick

- [ ] 3.1 Add the stick device to `HandleInput`, reading the pitch axis, invert and dead zone from `JoystickSettings` (dead zone with rescale, take control only on real movement, return to keys when the stick disappears). Verify with new cases in `HandleInputTest`: half pull gives half tilt, an inverted axis tilts the other way, another axis chosen in the settings is the one that counts, jitter in the dead zone keeps the keys in control, last device wins, unplug returns to neutral through the ramp.
- [ ] 3.2 Update the Controls table in `projects/app-game-control-line/README.md` and the Code table row for `input` and `settings`. Verify with `scripts/check-docs.sh`.

## 4. Settings screen and game wiring

- [ ] 4.1 Add `Screen.Settings` to `GameFlow` and Settings to the main menu (after Scores), leaving by Escape or back. Verify with `GameFlowTest` cases: main menu offers Start, Scores, Settings, Quit; Settings opens the screen; back returns to the main menu.
- [ ] 4.2 Build the screen in `GameUi`: stick name and live readout, pitch axis, invert, dead zone, confirm and back assignment through `ControlCapture`, Reset to defaults, a message when the saved file was damaged, save on leaving. Verify headless with a test that drives the screen's model with `FakeJoystick` (assign axis 2, invert, reset, save and reload through `SettingsStore`).
- [ ] 4.3 Poll the source once per frame in `ControlLineGame.render()`, feed `HandleInput`, forward menu edges to `GameUi.keyDown`, and pause on the back button while flying. Verify with a test of the forwarding helper using `FakeJoystick` (main menu: hat down + trigger opens Scores; plane select: hat right + trigger flies `Stunter`).
- [ ] 4.4 Confirm that with no stick present nothing changes. Verify with the existing `HandleInputTest` and flow tests passing unchanged.

## 5. Hardware check and integration

- [ ] 5.1 With a T.16000M, run `./gradlew :app-game-control-line:run` and check: the settings screen shows the stick and live values, the default pitch axis direction (pull back = up) and centre is neutral, choosing another axis and inverting work, assigning buttons works, settings survive a restart, menu hat and trigger, back button pauses, unplugging mid-flight. Record the axis and button numbers seen on the OS used and correct the defaults if they differ.
- [ ] 5.2 Run `./gradlew check` and `scripts/check-docs.sh`.
