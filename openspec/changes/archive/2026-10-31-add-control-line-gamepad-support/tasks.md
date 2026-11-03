# Tasks

Run a single test class with `./gradlew :app-game-control-line:test --tests '<class>'` (all under
`net.nevinsky.abyssus.app.game.controlline`). Headless means no GL and no device: a fake `ControllerSource` stands in.

## 1. Profiles and the axis mapping

- [x] 1.1 Add the `controller` package with `ControllerProfile` (axis, rest, invert, dead zone, curve, pause button) and `AxisMapping.tilt`. Verify with `controller.AxisMappingTest`: a stick fully up gives 1; 0.08 from rest with a 10 % dead zone gives 0; 0.55 gives 0.5; a pedal with rest -1, dead zone 0 reads 0.5 at 0, 0 at rest and 1 at the end; invert gives -1; the cubic curve at 0.5 travel; values outside -1..1 are clamped; NaN is neutral.
- [x] 1.2 Add `ControllerProfiles` (the `controls.json` store through `AtomicJsonStore` and Jackson DTOs, keyed by GUID, version 1, damaged file renamed to `controls.json.bad`) with the gamepad and joystick default profiles. Verify with `controller.ControllerProfilesTest`: defaults for a gamepad (left stick vertical, rest 0, dead zone 10 %, Start for pause) and a joystick (no axis, no pause); changes survive a reload; two GUIDs keep separate profiles whatever their order; an unreadable file is renamed and `damaged` is set; an unknown version is refused like `ScoreTable` does.

## 2. The device source

- [x] 2.1 Add `ControllerSource`, `DeviceState` (GUID, name, gamepad or joystick, axes, buttons, first hat) and a `FakeControllerSource` in the test sources. Verify with `controller.FakeControllerSourceTest` that devices appear and disappear and their state changes between polls.
- [x] 2.2 Add `GlfwControllers` (`controller.glfw`), the only class that imports `org.lwjgl.glfw`: gamepads through `glfwGetGamepadState` with the Y axes negated so up is positive, other devices through raw axes, buttons and hat 0, GUID and name from GLFW. Verify with `controller.ControllerBoundaryTest` (no other source file imports `org.lwjgl.glfw`, and the module compiles with `./gradlew :app-game-control-line:compileKotlin`); the live behaviour is checked in 9.1.

## 3. The handle's third device

- [x] 3.1 Give `HandleInput` a PAD device: `pad(tilt)` takes control when the tilt is not 0, holds the pad's value (analog, no ramp) while it controls, `padLost()` neutralises it and falls back to the keys, a key or the mouse takes control back and `reset()` returns to the keys. Verify with the new cases in `flow.HandleInputTest`: stick then key, key then stick, a resting stick never overrides W, unplug mid-hold goes neutral, `reset`.

## 4. Menu navigation

- [x] 4.1 Add `PadNavigator` (edge detection, 0.4 s then 0.12 s auto-repeat, the D-pad or left stick of a gamepad or the first hat of a joystick as Up/Down/Left/Right, confirm as Enter, back as Escape, silent while flying or detecting). Verify with `controller.PadNavigatorTest`: a press gives one key; a held direction repeats at 0.4 s then every 0.12 s; the stick acts past 0.5 only; releasing stops it; confirm and back keys; nothing while flying or detecting.
- [x] 4.2 Add `GameUiHost.navigate(keycode)` over a pure `navigateKey`: the key first, then Tab / Shift-Tab for an Up/Down no screen handled and Space for an Enter nothing handled (a focused checkbox). Verify with `controller.PadKeysTest` (the fallback rules with fake senders: a taken key is not repeated, Down and Up become Tab and Shift-Tab, an untaken Enter becomes Space, Left, Right and Escape have none); the presenter's real focus moves need GL and are walked in 6.3.

## 5. Detect

- [x] 5.1 Add `DetectSession` (axis or button, snapshot at the start, 0.5 travel, largest axis wins, rest value recorded, 10 s deadline, cancel). Verify with `controller.DetectSessionTest`: an axis pushed fully up becomes the handle axis with its starting value as rest; two axes moved, the larger wins; a half-travel nudge picks nothing; a button press is taken; the deadline and cancel leave the profile unchanged; the device's back button cancels an axis detect but is taken by a button detect.

## 6. The settings screen

- [x] 6.1 Add `Screen.Settings` to `GameFlow` (`settings()` from the main menu, `back()` to it) and the Settings action. Verify with new cases in `flow.GameFlowTest`: Settings opens from the main menu, Back and Escape return, the screen needs no device.
- [x] 6.2 Publish the settings screen's values and accept its writes and actions in `GameUiModel` and `GameUiContract` (device rows and name, axis, live tilt bar and text, invert, dead zone, curve, pause button, detect status, damaged message; the writes `invert`, `deadZone`, `curve`; the actions `settings`, `devicePrevious`, `deviceNext`, `detectAxis`, `detectPause`, `cancelDetect`, `resetProfile`). Verify with `screens.SettingsUiModelTest`: no device says none was found; the live bar follows the device with the dead zone, invert and curve applied; a write is validated, applied and saved; Reset restores the default; the actions start and cancel a detect.
- [x] 6.3 Author the `settings` screen, the main menu's Settings button (between Scores and Quit) and the bindings in `assets/screens/meta.json`, and add Settings steps to `ScreenWalk`. Add a pad-navigation step to `ScreenWalk` that drives `GameUiHost.navigate` (Down moves focus on an arrows screen and on a tab screen, Enter presses a button, Space-by-Enter toggles Invert, Left/Right change a slider and a plane). Verify with `screens.ControlLineUiAssetTest` (asset and contract agree), `screens.GameUiModelTest`, and `screens.GameScreensCharacterizationTest` with `-Dabyssus.glTests=true` after re-recording `screens.golden` with `-Dabyssus.recordScreens=true`; where there is no display, leave this open and list the steps (9.2).

## 7. Wiring the game

- [x] 7.1 Add `ControllerService` and wire it into `ControlLineGame.render()` before `followFlow()`: poll the source, notice hot-plug, set the handle's tilt, send navigation keys through `GameUiHost.navigate` on menu screens, pause on the pause button during a human flight and publish the settings screen's values; give it no effect in a Watch AI demonstration. Verify with `controller.ControllerServiceTest` (fake source): the stick sets the tilt and takes control; a resting stick leaves W in control; unplug mid-flight keeps the flight running and neutralises the handle; plug-in restores the saved profile; the pause button pauses only a human flight; the D-pad moves the main menu focus; navigation is silent while flying and detecting; a demonstration ignores the stick.
- [x] 7.2 Fly with a fake stick through the game's own flight lifecycle. Verify with `ControllerFlightLifecycleTest` (like `ScaleFlightLifecycleTest`): a fake stick pushed up tilts the handle and the plane climbs, the pause button pauses, and the flight ends and enters the board as with the keys.
- [x] 7.3 Document the controls in `projects/app-game-control-line/README.md` (a Controller section: supported devices, the default gamepad mapping, the settings screen, Detect, the file `controls.json`, Linux device access, identical devices share a profile, name entry and the on-screen keyboard are not supported) and the Controls table, and update `docs/ai` where it lists the game's screens. Verify with `scripts/check-docs.sh`.

## 8. Display settings

- [x] 8.1 Add `DisplaySettings` (`display.json`, version 1, `fullscreen`, `.bad` rename on an unreadable file, atomic write) and `DisplayOptions` with `NoDisplayOptions`. Verify with `display.DisplaySettingsTest`: no file means windowed; a change survives a reload; an unreadable file is renamed and `damaged` is set; an unknown version is refused.
- [x] 8.2 Add `GdxDisplay` (`setFullscreen` saves and marks the switch pending, `applyPending()` applies `setFullscreenMode` or `setWindowedMode(1280, 720)`, `fullscreen` reports the window's real mode when there is one), call `applyPending()` at the top of `ControlLineGame.render()`, and start `Main` in the saved mode through the configuration. Verify with `display.GdxDisplayTest` over a fake window (a request applies once on the next `applyPending`, nothing applies without one, a failed switch leaves the request unapplied and the checkbox on the real mode) and `./gradlew :app-game-control-line:compileKotlin`; the real window switch is checked in 9.3.
- [x] 8.3 Publish and accept the `fullscreen` binding and the display damaged message in `GameUiModel` and `GameUiContract` (`fullscreen` writable, `hasDisplayDamaged`, `displayDamaged`), retitle the screen Settings and add the Display section (a Fullscreen row with a checkbox, a Controller heading) to `assets/screens/meta.json`, update `ScreenWalk` and re-derive `screens.golden` for it. Verify with `screens.SettingsUiModelTest` (the checkbox value follows the option, a write toggles it and saves, a non-boolean write is rejected, it works with no controller, the damaged message shows), `screens.ControlLineUiAssetTest`, and `GameScreensCharacterizationTest` with `-Dabyssus.glTests=true` (9.2).
- [x] 8.4 Document Fullscreen in `projects/app-game-control-line/README.md` (the Settings screen, `display.json`, starts in the saved mode). Verify with `scripts/check-docs.sh`.

## 9. Checks that need a device or a display

- [x] 9.1 With a real Logitech gamepad and a flight stick (`./gradlew :app-game-control-line:run`): the pad is listed by name on Settings; the left stick flies the plane and the live bar follows it; Detect picks the stick's axis and records its rest value; the D-pad and A move through every menu; Start pauses; unplugging mid-flight leaves the flight running; plugging back in uses the saved profile. Record what was seen in the change's notes.
- [x] 9.2 With a display (`-Dabyssus.glTests=true`): `GameScreensCharacterizationTest` and `GameUiGlTest` pass with the re-recorded `screens.golden`.
- [x] 9.3 On the platform in hand (`./gradlew :app-game-control-line:run`): Settings, Fullscreen on switches the window to fullscreen at once, off returns to a 1280 x 720 window, the game restarts in the mode last chosen, and the checkbox works with the keyboard, the mouse and a gamepad. Record what was seen in the change's notes.

## 10. Integration

- [x] 10.1 Run `./gradlew :app-game-control-line:test`, then `./gradlew check` and `scripts/check-docs.sh`, and fix what they report (including `checkShippedClasspath` and the screens golden).
