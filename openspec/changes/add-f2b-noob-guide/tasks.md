# Tasks

Apply after `add-f2b-pattern-mode` and `add-f2b-pro-debrief`. All code is in `projects/app-game-control-line`. Run
single tests with `./gradlew :app-game-control-line:test --tests '<class>'`. Manual checks (M1, M2, ...) use
`./gradlew :app-game-control-line:run`, with `-Duser.home=<tmp>` in `JAVA_TOOL_OPTIONS`, so real scores are untouched.

## 1. Guide planner

- [ ] 1.1 Add `GuidePlanner`, with these parts:
  - **Placement:** build with `IdealTracks` at azimuth 0, then rotate so the first point lands on the gate. The gate
    is the plane's azimuth when the pause is done, plus `GUIDE_LEAD` (90°).
  - **Special guides:** take-off at the release point; the landing after the clover once the engine is off; nothing
    between the clover and that moment.
  - **Life cycle:** keep the guide until its manoeuvre is scored, then remove it.
  - **Rings:** the base and 45° rings.

  Verify with `GuidePlannerTest`:
  - Loops after the wingover: the first point is at the gate, a quarter lap ahead, and the loops lie between the base
    and the 45° parallel;
  - Not ready: passing the gate keeps the guide in place;
  - Skipping ahead: a later manoeuvre scored removes the guide, and the next one appears after the pause;
  - Engine out: the landing spiral appears only after the clover is scored and the engine stops;
  - Rings from the start;
  - every manoeuvre's guide has its first point within 0.1 m of its gate.
- [ ] 1.2 Add progress dimming: once the judge reports the manoeuvre started, a windowed nearest-point search that is
  monotone, with tags `GUIDE_DONE` and `GUIDE_AHEAD`. Add `segments()` with world-space segments and tags, including
  `GATE`, `RING_BASE` and `RING_45`. Verify with `GuidePlannerTest`:
  - Halfway through the loops: at the top of loop 2 of a synthetic flight, the first loop and the climb of the second
    are done and the rest is ahead;
  - progress never decreases while the plane passes points of an earlier repeat;
  - a Mid flight gets no segments.

## 2. Flow and drawing

- [ ] 2.1 Offer `NOOB` on `AssistSelect` after Pro and Mid, and add the Noob tab after Pro and Mid on the score table.
  Verify with `GameFlowTest`:
  - Choosing Noob: F2B → Noob → `Stunter` flies a Noob flight;
  - A Noob flight on the board: it enters only the Noob board, its tab highlights it, and its row opens the debrief;
  - Retry keeps Noob.
- [ ] 2.2 In `GameUi`, give Noob Mid's HUD, the assist entry text and the Noob tab. In `ControlLineGame`, update the
  `GuidePlanner` after each advance of a Noob session and append its segments to the lines, coloured and sized by tag.
  In `FieldRenderer`, blend segments with alpha below 1. Verify with manual checks:
  - M1: a Noob flight shows the rings and the take-off guide at start;
  - M2: after the wingover's pause, the loops appear with the gate a quarter lap ahead, and stay when passed;
  - M3: flying the loops dims the flown part;
  - M4: after the clover, the glide spiral appears when the engine stops;
  - M5: Mid and Pro show no guide;
  - M6: the Noob tab and its debrief work.

  If the gate is hard to see in M2, set `GUIDE_LEAD` to ⅛ lap and note it in the README.
- [ ] 2.3 Update `projects/app-game-control-line/README.md`: the three assist levels, what Noob draws and its colours,
  and that Noob is judged the same way. Verify with `scripts/check-docs.sh`.

## 3. Spec consistency and integration

- [ ] 3.1 If `add-f2b-pattern-mode` or `add-f2b-pro-debrief` is not archived yet, amend their deltas:
  - "Assist level" offers Pro, Mid and Noob;
  - "Score table screen" lists the Noob tab;
  - "Debrief from GAME OVER" says every F2B level.

  If they are archived, make the same wording edits in `openspec/specs/control-line-game-flow/spec.md` as part of
  archiving this change. Verify with `openspec validate --strict` on each touched change.
- [ ] 3.2 Run `./gradlew check` and `scripts/check-docs.sh`. Verify that both pass.

## Workflow follow-up

- Archive after `add-f2b-pattern-mode` and `add-f2b-pro-debrief` are archived.
