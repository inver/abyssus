# Design

## Context

See proposal.md for motivation and scope. `FlightSession.advance` currently accepts `HandleInput` and writes its tilt before each 1/120 s physics step. `Flight` supplies real samples to `PatternJudge`; `ControlLineGame.startFlight` creates the session, judge and optional `GuidePlanner`. `GameFlow` owns selection, pause, retry and score qualification. Screens are authored in the bundled `screens/meta.json` and driven through `GameUiContract`/`GameUiModel`.

`IdealTracks` and `GuidePlanner` already describe the schedule geometry. Test-only `ScriptedPilot` controls climb angle with proportional/damping feedback; `PatternFlyabilityTest` proves some turns, not the complete pattern. Synthetic judge tests do not establish physical flyability. `Flight` stops thrust according to scene fuel time; Stunter has 360 s. A normal landing requires an engine-off upright touchdown meeting the existing impact criterion. Simulation ends at touchdown; a new rollout simulation is not part of this change.

## Goals / Non-Goals

**Goals:** Introduce a small interchangeable control boundary, a physics-driven autonomous controller with an independent judge, and a complete headless acceptance run through the production integration. Keep input and lifecycle behavior explicit and testable.

**Non-Goals:** Replacing the flight model or judge, adding bot logic to shared core/physics modules, or implementing a general agent framework. The bot is deterministic local flight control, not a network or language-model feature.

## Decisions

### 1. Session-owned control source with read-only feedback

Define a `FlightControlSource` in the game's `flight` package with a fixed-step method returning a finite tilt clamped to [-1, 1]. Supply a read-only observation of the last completed simulation state: position/direction relative to the pilot, velocity, attitude/up, angular velocity where needed, height above ground, tension, elapsed time, fuel and end state. Do not expose a mutable physics body or judge to the controller. The first step has a documented neutral startup observation.

Use an adapter for `HandleInput` preserving its update and last-device semantics. `FlightSession` receives the source by construction and evaluates it once per fixed step, after restoring simulation poses and before applying forces. Keep presentation interpolation out of observations. `ControlLineGame` constructs the selected source; `flight` must not import `pattern` or the new `ai` package. The concrete AI may consume pattern geometry. This keeps package dependencies acyclic.

Alternative: synthesize keyboard events. Rejected because input-device smoothing and frame timing would become part of AI control. Direct pose animation is also rejected because it bypasses the feature's actual-flight requirement.

### 2. Separate routine planning from tracking

Add GL-free `ai.F2bRoutinePlanner` and `ai.F2bPilot`, using math types and immutable observations only. Planner phases:

```text
Startup --> Takeoff --> Base/pause --> Figure --> Base/pause
                            ^           |
                            +-----------+
           final figure --> Wait for fuel --> Glide/flare --> End
```

Build figure targets from `IdealTracks` and shared pure path construction where appropriate. Do not make the bot depend on rendered guide segments or the selected assist level. Preserve entry/exit geometry, attitude changes, repeat counts and required pauses. Maintain phase and unwrapped travelled progress; use local monotonic path search with a bounded look-ahead so crossings and repeated loops cannot jump to a later segment. Use spherical unit vectors/tangents and the aircraft's up direction for signed turn demand, avoiding climb/azimuth singularities overhead.

Translate cross-track and heading error plus target curvature into desired pitch response; use angular-rate damping, bounded output and finite-value guards. Scale look-ahead by airspeed and limit curvature to physically achievable turns. Track inverted flight explicitly; a simple sign flip on climb error is insufficient for overhead figures. Initial gains can draw from ScriptedPilot, but it is not promoted unchanged.

The planner advances on measured flight progress, never elapsed-time-only canned commands or assigned judge marks. It can observe a read-only judge progress view injected by the composition root to wait for finalization between manoeuvres; this cannot change judge state. Keep its own target completion/repetition accounting. If recognition fails, retain the genuine zero/partial result; do not skip silently and declare success. Timeout/no-progress state records a controller diagnostic and targets controlled recovery/base flight while ordinary flight end rules remain authoritative.

Alternative: open-loop timed handle commands. Rejected because small phase errors accumulate across a six-minute flight. A trained policy adds dependencies and training uncertainty without an established need.

### 3. Landing uses the existing fuel and outcome rules

Complete airborne figures with enough time before the 360 s fuel cutoff, wait at the base if early, and begin the landing sequence from base flight when thrust ends. Use airspeed, height and descent feedback to manage the glide and flare while staying on the lines. Do not add an AI-only engine cutoff or change scene fuel. Full success means all 15 rows positive, `FlightEnd.LANDED`, time <= 420 s, upright touchdown and the line ends' horizontal distance <= line length + 0.5 m (18.5 m for Stunter). The distance is measured where the lines meet the plane (its leadouts), not at its centre of gravity: the Stunter's leadouts are 0.75 m inboard, so with taut lines its centre of gravity rests about 18.7 m from the pilot even parked at takeoff (measured during apply), which no control can change.

The early physical milestone must prove glide/flare and full-schedule feasibility against the committed aircraft. If the current model cannot satisfy it, report the concrete limiting measurement and revisit scope explicitly; do not loosen scoring, alter aircraft values or mark the task complete to make the demo pass.

### 4. Controller identity is independent of assist level

Add a human/AI control selection carried by the flying state (and therefore pause/results/retry), defaulting to human. `Mode.Pattern(level)` continues to select judge and assistance presentation; it does not encode pilot identity. Add `watchAi` as an explicit plane-select action, shown only for Pattern. Retain existing Enter-to-fly behavior and keyboard focus navigation to Watch AI.

Suppress flight key/mouse delivery to the human adapter during an AI flight while preserving Escape and screen navigation. Reset input at new-session boundaries. Pause advances neither bot nor physics; Resume reuses both. Retry constructs a new controller, judge and physics session. Main menu/disposal releases the session and bot state. Free flight and editor Play remain human-driven.

Alternative: a new assist level named AI. Rejected because guidance and who controls the plane are separate choices.

### 5. Results remain truthful and transient

In `GameFlow.flightEnded`, branch on control identity before score qualification. An AI attempt goes directly to GameOver with no rank. Preserve the actual `FlightReport` and recording for the existing in-memory debrief path. Neither name-entry nor table add/recording save is reachable for AI, including retries. Scores may still display existing human boards; no synthetic AI rows or new board format are introduced. Propagate AI identity through debrief presentation so its title remains clear without changing the recording format.

### 6. Authored UI, documents and threading

Add typed bindings for Watch AI visibility and demonstration identity plus the `watchAi` action; update plane selection, HUD, pause, results and debrief in the existing UI asset. Use its existing styles and layout conventions. Keep labels in the authored game UI asset, not plugin bundles. Runtime loads continue to use native-document admission. Source asset edits are narrow repository edits to an authored fixture, not runtime scene writes; no new scene writer is introduced. Preserve unrelated JSON exactly.

All runtime controller, flow and physics work remains on the standalone game's LWJGL3 main thread. Only existing asset preparation uses workers. No IDE/AWT execution path is added; the IDE's `GdxRuntime.withContext` requirement remains untouched. Pure planner/tracker/control-selection logic is headless, without Swing, GL or IntelliJ dependencies.

## Risks / Trade-offs

- [Full pattern has never been proved with real physics] -> Gate the work on real-controller per-figure and full-run tests; distinguish control errors from existing recognizer defects using recorded trajectory diagnostics. Any judge correction must preserve normal judging contracts and have independent regression cases.
- [Sharp corners or overhead tracking exceed physical authority] -> Track reachable rounded paths within existing judging tolerances; do not demand ideal 1 m corners when observed minimum is about 2.7 m.
- [Landing before 7:00 with natural fuel exhaustion is difficult] -> Prove engine-off glide/flare early; keep the 360 s fuel and seven-minute deadline as acceptance constraints.
- [Two pending changes overwrite the same flow requirements] -> Reconcile `add-f2b-pattern-mode` Flying, GAME OVER and Retry deltas with this change, and scope its F2B board eligibility to human flights. Preserve noob guide and debrief behavior. Perform these targeted artifact updates during apply, not this proposal.
- [Custom aircraft cannot complete the routine] -> Document the unmodified Stunter baseline; custom stunt planes run honestly and may fail. No hidden aircraft substitution.
- [Controller depends on render rate] -> Drive it only by fixed-step observations; test regular and irregular frame chunks at equal simulated time, plus pause/retry boundaries.

## Migration Plan

No document or score migration. Ship controller, flow and authored UI changes together. Keep existing persisted score/recording formats unchanged. Rollback removes the action and controller wiring without touching player data. Existing human screen characterization must remain valid aside from the intentional new Watch AI control and demonstrated identity text.

## Open Questions

- Feedback gains, look-ahead distances and flare thresholds are tuning values to settle with real-physics tests, without changing the acceptance contract.
