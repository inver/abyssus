# Design

## Context

See proposal.md for motivation. `HandleInput` (plain Kotlin, no GL) turns key and mouse events into a tilt, and
`ControlLineGame.Keys` feeds it from libGDX's `InputProcessor`. The LWJGL3 backend has no joystick events, so a stick
must be polled. Menu screens take libGDX key codes through `GameUi.keyDown`. Everything runs on the LWJGL3 main
thread; no new thread is added.

## Goals / Non-Goals

**Goals:**
- Fly the plane and drive the menus with a T.16000M, without a new dependency.
- Keep the stick logic testable without a window or hardware.

**Non-Goals:**
- Per-stick profiles, choosing between several sticks, throttle, rudder, other devices' layouts, Abyssus Play.

## Decisions

1. **Raw GLFW joystick API, not gdx-controllers.** `glfwJoystickPresent`, `glfwGetJoystickAxes`, `glfwGetJoystickButtons`
   and `glfwGetJoystickHats` work for any HID stick and are already on the classpath through `gdx-backend-lwjgl3`.
   gdx-controllers is built on SDL gamepad mappings, which flight sticks often lack, and would add a dependency
   and native library. Alternative rejected for that reason.
2. **`JoystickSource` interface, polled once per frame.** It returns an immutable `StickState` (pitch axis in -1..1,
   hat direction, trigger and back buttons, or `null` when absent). `GlfwJoystickSource` implements it for the first
   present joystick id and is the only class that imports GLFW; `FakeJoystick` drives tests. The game polls in
   `render()` on the main thread, before `HandleInput.update`.
3. **T.16000M layout as the defaults of `JoystickSettings`.** Pitch is axis 1 (Y), the hat is hat 0, trigger
   button 0, back button 1, dead zone 0.05. GLFW reports forward as negative on Y, so the default pitch axis is
   inverted to make pull-back tilt up. These are expectations to confirm on hardware (task 5.1), not facts verified in
   this container; the settings screen exists so a wrong guess is fixed by the player, not by a rebuild.
4. **A third `HandleInput` device.** `stick(pitch)` applies a dead zone (0.05, rescaled so the output still reaches
   +-1) and sets the tilt directly like the mouse. A stick takes control only when the deadzoned value is non-zero
   or has changed from the last value, so noise at rest cannot steal control. When the source reports
   absent and the stick was in control, the device returns to keys and the tilt eases to neutral through the key ramp.
5. **Menus by translated key codes.** A hat edge (not level) and a button press edge are mapped to
   `Input.Keys.UP/DOWN/LEFT/RIGHT/ENTER/ESCAPE` and sent through the same `ui.keyDown` / `flow.pause()` path as the
   keyboard, so menu rules stay in one place. Edge detection lives in a small `StickEdges` class beside the source.
   During a flight only the back button is acted on (pause).

6. **`JoystickSettings` and `SettingsStore`.** An immutable value (pitch axis, invert, dead zone, confirm and back
   buttons) and a store modelled on `ScoreTable`: Jackson, a `version` field, atomic write through a temporary file,
   an unreadable or unknown-version file renamed to `.bad` with a `damaged` flag. Plain Kotlin, no GL, wired by
   constructor. Alternative: libGDX `Preferences`; rejected to keep one file format and one damage rule.
7. **Raw state, mapping afterwards.** `StickState` carries every raw axis, button and hat. The settings are applied by
   `HandleInput` and `StickEdges`, not by the GLFW reader, so the readout can show raw values for any axis.
8. **`ControlCapture`.** A pure class that waits for "the axis that moved furthest from where it started, past a
   threshold" or "the button that just went down", for the screen's assign actions. Escape cancels it.
9. **`Screen.Settings(from)` in `GameFlow`.** `GameUi` builds the screen, polls the source for the readout on the
   main thread, and saves through the store on leaving. The hat and keyboard arrows always navigate, so a bad
   assignment cannot lock the menus.

Testable without GL or hardware: `HandleInput`, settings mapping, dead zone, `StickEdges`, `JoystickSettings`, `SettingsStore`, `ControlCapture` and the flow's Settings screen. Only
`GlfwJoystickSource` needs hardware and is checked by hand.

## Risks / Trade-offs

- [Axis and button numbering differs between OS drivers] -> constants isolated in one place; hardware check on each
  OS available; a later change can add rebinding.
- [GLFW joystick state is only fresh after events are polled] -> poll inside `render()`, after libGDX has polled events.
- [Name entry on GAME OVER needs the keyboard] -> the trigger confirms the name field as Enter does; typing stays on the keyboard.
- [Assigning a button that does nothing, or an axis that is not the stick's pitch] -> keyboard, mouse and the fixed hat always navigate, Escape always leaves, and Reset to defaults restores the layout.
- [A settings file from a different stick names an axis this one lacks] -> an axis or button index outside the stick's range reads as zero or not pressed, and the readout shows it.
- [Another connected joystick (pedals, mouse-like HID) is picked first] -> the first present id whose reported axis count is at least 2 is used.
