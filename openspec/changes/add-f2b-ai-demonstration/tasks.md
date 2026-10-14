# Tasks

## 1. Fixed-step control boundary

- [ ] 1.1 Add `FlightControlSource`, read-only flight observations and the human input adapter; inject the source into `FlightSession` while preserving fixed-step order and interpolation. Verify `FlightControlSourceTest` cases for neutral startup, finite bounded tilt, one call per physics step and identical human input behavior; run existing `HandleInputTest`.
- [ ] 1.2 Expose only the physics feedback needed for control, with copies or immutable values and no interpolated pose. Verify `FlightControlSourceTest` observation timestamps and regular/irregular frame chunks at equal simulated time; run `./gradlew checkPackageCycles` and existing `ControlLinePlayTest`.
- [ ] 1.3 Document the control boundary and thread ownership in the game README; verify documented source paths and `scripts/check-docs.sh`.

## 2. Physical control and landing feasibility

- [ ] 2.1 Implement the pure feedback tracker with spherical tangent/error calculations, curvature feed-forward, rate damping, output limits and inverted attitude handling. Verify `F2bPilotTest` cases for upright/inverted sign, zenith crossing, repeated-path progress, finite output and low-speed startup.
- [ ] 2.2 Add the production bot's takeoff, base-flight, fuel-wait and glide/flare phases using the unchanged Stunter settings. Verify `F2bAiLandingTest` with real physics from rest through natural 360 s engine cutoff to upright `LANDED` by 420 s within 18.5 m horizontally of the pilot; include actual touchdown/impact measurements in failure output.
- [ ] 2.3 Add runtime-compatible target paths and entry/exit tracking for wingover, loops and inverted laps. Verify `F2bAiFiguresTest` real-physics cases with correct repetitions, independent judge recognition and tension; no synthetic samples or pose writes after normal initialization.
- [ ] 2.4 Add squares, triangles, eights, hourglass and clover target tracking using attainable curvature and monotonic crossing progress. Verify each case in `F2bAiFiguresTest`, including overhead and inverted transitions, using the normal judge and committed aerodynamic settings.
- [ ] 2.5 Document controller limits, baseline aircraft, landing semantics and measured tuning choices in the game README; verify the documented values against passing figure/landing tests and run `scripts/check-docs.sh`. If a physical constraint blocks completion, record evidence and resolve it before proceeding; do not weaken the acceptance contract.

## 3. Full routine scheduling

- [ ] 3.1 Implement `F2bRoutinePlanner` across the entire schedule, measured repeats, required pauses, final fuel wait and landing; keep judge access read-only and assistance display independent. Verify `F2bRoutinePlannerTest` ordered transitions, pause thresholds, crossings, no-progress recovery and immutable observations.
- [ ] 3.2 Add `F2bAiRoutineTest` driving the production session/controller with the bundled Stunter from stationary startup: require all 15 marks positive, correct schedule order/repeats, no early-entry penalty, natural engine cutoff, `LANDED` by 420 s and touchdown inside 18.5 m. Run `./gradlew :app-game-control-line:test --tests '*F2bAiRoutineTest'`; include phase, last sample and actual judge failure reasons in diagnostics.
- [ ] 3.3 Verify the whole-run acceptance under regular and irregular frame chunks and a pause/resume interruption, with tolerances on continuous measurements but identical successful outcomes. Add these cases to `F2bAiRoutineTest`; verify a modified-aircraft failure remains a real failure in `F2bAiFailureTest`.
- [ ] 3.4 Document the complete-run verification command and the distinction between synthetic judge tests and actual bot flights in the game README; verify commands with `:app-game-control-line:test` and `scripts/check-docs.sh`.

## 4. Game flow and demonstration lifecycle

- [ ] 4.1 Add human/AI identity and a guarded Watch AI action on F2B plane selection; retain selected aircraft and assist level through pause/results/retry. Verify `GameFlowTest` cases for F2B launch, Free/no-stunt rejection, default human launch and fresh AI retry.
- [ ] 4.2 Wire the production controller in `ControlLineGame`, suppress human flight input during AI, preserve navigation and dispose/reset correctly. Verify `AiFlightLifecycleTest` pause freezing, resume continuity, held keys/mouse ignored, menu cleanup, retry reset and return to human controls; rerun `ControlLinePlayTest`.
- [ ] 4.3 Bypass qualification/name-entry/persistence for AI while retaining actual results and the in-memory debrief. Verify `GameFlowTest` and `AiFlightLifecycleTest` high-scoring AI, crash results, debrief round-trip and unchanged score/recording files, including populated boards and retries.
- [ ] 4.4 Reconcile overlapping pending `add-f2b-pattern-mode` flow deltas (Flying, GAME OVER, Retry) and its F2B score-board requirement with AI exclusions; preserve the noob guide and pro debrief contracts. Verify `openspec validate add-f2b-pattern-mode --strict` and `openspec validate add-f2b-ai-demonstration --strict`, plus a requirement comparison showing neither archive order restores human-only controls or AI board eligibility.
- [ ] 4.5 Document Watch AI flow, AI-only input, transient results and baseline limitations in the game README and add an Unreleased changelog entry. Verify against passing flow/lifecycle tests and `scripts/check-docs.sh`.

## 5. Authored screens

- [ ] 5.1 Extend `GameUiContract` and `GameUiModel` with the Watch AI action and typed identity/visibility bindings; narrowly edit the existing UI asset for plane selection and AI labels on HUD, pause, results and debrief. Verify `ControlLineUiAssetTest` admission/actions/bindings and `GameUiModelTest` visibility at every relevant screen, retaining native format version and unrelated JSON values.
- [ ] 5.2 Add keyboard/mouse Watch AI navigation and demonstration screen steps to `ScreenWalk`/`GameScreensCharacterizationTest`; update only intentional expected text. Verify `./gradlew :app-game-control-line:test -Dabyssus.glTests=true --tests '*GameScreensCharacterizationTest'` and screenshot inspection from `GameScreenshotsGlTest`; include the existing human flow to detect regressions.
- [ ] 5.3 Update the game README's Screens section with the new authored action and bindings; verify `ControlLineUiAssetTest` and `scripts/check-docs.sh`.

## 6. Integration verification

- [ ] 6.1 Run the complete game suite with `./gradlew :app-game-control-line:test`; verify the new real-physics full routine and existing flight, scoring, guide, debrief and Play tests all pass.
- [ ] 6.2 Manual M1: launch `./gradlew :app-game-control-line:run`, choose F2B/Mid/Stunter/Watch AI by mouse, watch startup through all figures and landing, and confirm AI identity, normal judging, all 15 positive marks, touchdown within the circle and no name prompt. Repeat launch by keyboard; record observations. Leave unchecked without an actual visual run.
- [ ] 6.3 Manual M2: during Watch AI move the mouse and press flight keys, pause mid-figure, resume, inspect results/debrief, then retry and return to a human flight; confirm exclusive bot control, no pause jumps, fresh retry and restored human controls. Inspect Pro and Noob presentation and unchanged boards. Leave unchecked without an actual visual run.
- [ ] 6.4 Run `openspec validate add-f2b-ai-demonstration --strict`, `./gradlew check` and `scripts/check-docs.sh`; report any unrelated failures separately and retain unchecked tasks for verification that was not performed.
