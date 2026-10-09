# Tasks

## 1. Stick reading

- [ ] 1.1 Add `StickState`, the `JoystickSource` interface and `StickEdges` (hat and button press edges to key codes) in `input/`, free of GLFW. Verify with `StickEdgesTest`: one event per press, none while held, none for an absent stick.
- [ ] 1.2 Add `GlfwJoystickSource` with the T.16000M layout constants and the Y sign flip, choosing the first present joystick with at least two axes. Verify it compiles (`./gradlew :app-game-control-line:compileKotlin`); behaviour is checked in 4.1.

## 2. Handle tilt from the stick

- [ ] 2.1 Add the stick device to `HandleInput` (dead zone with rescale, take control only on real movement, return to keys when the stick disappears). Verify with new cases in `HandleInputTest`: half pull gives half tilt, jitter in the dead zone keeps the keys in control, last device wins, unplug returns to neutral through the ramp.
- [ ] 2.2 Update the Controls table in `projects/app-game-control-line/README.md` and the Code table row for `input`. Verify with `scripts/check-docs.sh`.

## 3. Game wiring

- [ ] 3.1 Poll the source once per frame in `ControlLineGame.render()`, feed `HandleInput`, forward menu edges to `GameUi.keyDown`, and pause on the back button while flying. Verify headless with a `ControlLineGame`-independent test of the forwarding helper using `FakeJoystick` (main menu: hat down + trigger opens Scores; plane select: hat right + trigger flies `Stunter`).
- [ ] 3.2 Confirm that with no stick present nothing changes. Verify with the existing `HandleInputTest` and flow tests still passing unchanged.

## 4. Hardware check and integration

- [ ] 4.1 With a T.16000M, run `./gradlew :app-game-control-line:run` and check: pitch axis direction (pull back = up), centre is neutral, menu hat and trigger, back button pauses, unplugging mid-flight. Record the axis and button numbers seen on the OS used and correct the constants if they differ.
- [ ] 4.2 Run `./gradlew check` and `scripts/check-docs.sh`.
