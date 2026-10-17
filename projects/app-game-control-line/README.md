# Control Line

A control-line flight game on Abyssus's `lib-core` and `lib-physics`. The plane flies on two lines from the handle the
pilot holds, and the handle moves only the elevator, through taut lines. Its field, pilot and planes, and every number
of a plane, are scene data in the bundled native project `project/ControlLine`, authored in Abyssus. Required
behaviour: `control-line-flight`, `control-line-scoring` and `control-line-game-flow` (OpenSpec change
`add-control-line-game`).

## Run

```
./gradlew :app-game-control-line:run
```

The `run` task points the game at the bundled project (system property `controlLine.project`). The main menu offers
**Free flight** (the arcade game below) and **F2B pattern** (practice for the real schedule, see [F2B pattern](#f2b-pattern)).
Free flight scores are kept in `<user-home>/.abyssus-control-line/scores.json` and F2B scores in
`<user-home>/.abyssus-control-line/f2b-scores.json`; a file that cannot be read is renamed to `scores.json.bad` or
`f2b-scores.json.bad`.

## Ray mode (development aid)

The game supports an opt-in ray-traced field view, chosen at launch with the `controlLine.fieldView=ray` system
property:

```
./gradlew :app-game-control-line:runRay
```

The `runRay` task runs the game with the `ray` source set (`lib-raytracing` + `lib-core-editor`) and `controlLine.fieldView=ray`.
Raster remains the default and the only shipped path: `checkShippedClasspath` continues to pass, and `installDist` /
`exportPlay` contain neither the ray-tracing modules nor any ray source-set class.

In ray mode the field image is produced by the ray tracer and shows the field's terrain, the field scene's models
(moving planes follow their simulation transforms), sunlight, sky and fog through the game's camera view. Input,
physics, flight, scoring and the HUD, menus and screens behave as in raster mode and stay raster-drawn.

**Documented limitations:**
- Ray mode does **not** draw foliage.
- Animated models render in their **bind pose** (no animation poses in ray mode).
- Ray mode is a **development aid**, not a distribution feature: it is never shipped, and there is no in-game graphics-menu
  toggle for it.

When no ray-tracing backend is usable (runtime or crate missing, device unsupported, or the probe refusing it), the game
starts the raster field and states the reason in the window title and the application log; it never fails to start and
never silently ignores the request. If ray rendering fails after start (quality limit, stalled frame or lost device),
the game falls back to the raster field and states the reason the same way.

## Controls

| Where | Keys and mouse |
|---|---|
| Menus | Up / Down and Enter, or the mouse; Escape goes back |
| Plane select | Left / Right change the plane, Enter flies it, Escape returns to the screen before it (the main menu, or the assist level for an F2B pattern) |
| Scores | Left / Right change the tab (Free flight, Pro, Mid, Noob), Escape or Back returns. On the F2B tabs, Up / Down select a flight and Enter or a click opens its debrief |
| Debrief | Up / Down: manoeuvre; Space: pause or resume; R: restart; 1 / 2 / 3: speed ½× / 1× / 2×; V: pilot view or outside view; Escape returns |
| Flying | W or Up: handle up; S or Down: handle down (full tilt in 0.15 s, back to neutral on release). Moving the mouse up or down sets the tilt by its distance from the window's centre; whichever was used last wins. Escape pauses |

In Free flight, laps score 10. Inside and outside loops score 50, an inverted lap 80, a wingover 100 and a figure 8 150, times the
combo multiplier (up to x5 for maneuvers within 10 s of each other). A landing with the engine stopped adds 200.

## F2B pattern

F2B pattern flies the schedule of FAI class F2B (Sporting Code Section 4, Volume F2, 2027 edition, rule 4.2) in 7 minutes,
with a judge that marks each manoeuvre from the flown track, as a human judge does. Required behaviour:
`control-line-f2b-pattern` and `control-line-f2b-judging` (OpenSpec change `add-f2b-pattern-mode`). The menu asks for
an **assist level**, then offers only planes of class `STUNT` (the bundled `Stunter`):

| Level | HUD |
|---|---|
| Pro | line tension, fuel left and the clock `m:ss / 7:00`; nothing about the manoeuvres |
| Mid | also the next manoeuvre with its K, the pause count out of 1½ laps, a warning while the plane flies level between manoeuvres outside 1 to 3 m, and a toast with each mark as soon as the manoeuvre ends (`Take-off 8.5 × 2 = 17.0`) |
| Noob | all of Mid's, and the ideal track of the next manoeuvre drawn in the sky (below) |

All three levels are judged the same way, from where the pilot actually flew; Noob flights go on their own Noob board.
Required behaviour of the guide: `control-line-f2b-guide` (OpenSpec change `add-f2b-noob-guide`).

### The Noob guide

During a Noob flight the game draws, in the 3D view:

- the **ideal track of the next manoeuvre** (bright cyan). When the 1½-lap pause after the last manoeuvre is done, the
  track appears with its first point at a **start gate** (a yellow bar from the ground to 1 m above the base) a
  quarter lap ahead of the plane, turned about the vertical so that it starts there. It stays where it is until the
  manoeuvre is scored, so a pilot who is not ready passes the gate and starts on the next lap. The eights and the clover
  are drawn with their way in and out;
- **progress**: once the judge sees the manoeuvre start, the part up to the point nearest the plane is dimmed (faint
  cyan); the search looks only a tenth of the track ahead of the last point, so it never jumps back or onto a later repeat;
- the **take-off** climb and its two laps at the base from the release point, and, once the clover is scored and the
  engine has stopped, the one-lap **landing** glide a quarter lap ahead;
- a faint ring at the base and a fainter one at the 45° parallel, all flight long.

The guide never steers: no autopilot, smoothing or slowed time. `GUIDE_LEAD` (a quarter lap) is the one constant to
reduce if the gate is hard to see. The planner is `pattern.GuidePlanner`, free of GL; `ControlLineGame` draws its segments.

### The schedule

Fifteen manoeuvres in this order, each marked 1 to 10 in steps of 0.1 and multiplied by its K (rule 4.2.12); the flight
score is the sum, shown with two decimals. K sums to 130, so the best score is 1300.00.

| # | Manoeuvre | K | # | Manoeuvre | K |
|---|---|---|---|---|---|
| 1 | Take-off | 2 | 9 | Two horizontal eights | 7 |
| 2 | Reverse wingover | 8 | 10 | Two square horizontal eights | 18 |
| 3 | Three inside loops | 6 | 11 | Two vertical eights | 10 |
| 4 | Two laps of inverted flight | 2 | 12 | Hourglass | 10 |
| 5 | Three outside loops | 6 | 13 | Two overhead eights | 10 |
| 6 | Two inside square loops | 12 | 14 | Four-leaf clover | 8 |
| 7 | Two outside square loops | 12 | 15 | Landing | 5 |
| 8 | Two inside triangular loops | 14 | | | |

The flight time is counted from the start of the take-off ground roll. The bundled `Stunter` has fuel for 360 s, so its
engine stops before 7:00 and the landing is flown with the engine stopped.

### Rules that score 0

A manoeuvre scores 0, with its reason on the score sheet, when it is:

- **omitted**: a later manoeuvre was flown, so the ones skipped score 0 (a manoeuvre already passed is not scored again);
- **not completed**: started and given up before its end;
- **wrong number of repeats**: three square loops where two are asked for;
- **too soon**: it starts less than 1½ laps (540° of azimuth) after the previous one ended;
- **after 7:00**: it ends after 7:00 (the flight goes on until it lands or crashes);
- **crashed**: a crash, or lines that go slack, scores 0 from the manoeuvre in progress on; the ones before keep their marks.

A landing scores 0 when it ends after 7:00 or as a crash. Flying between the clover and the landing is not judged.

### How a manoeuvre is marked

The code is in the `pattern` package and works on a *flight hemisphere* centred 1.5 m above the pilot's ground point (the
base), of the line length's radius; a place is a unit vector from the centre, so nothing is singular over the pilot.

1. `PathTokens` reads the flown path (resampled every 0.5 m) as straights, level stretches, corners, arcs, ground roll
   and touchdown. A turn is inside when the plane's up points to the inside of its bend, outside otherwise.
2. `PatternJudge` waits for a *pause* (level flight for 150° of azimuth or more), takes the burst of flying before it
   and asks `Matchers` which manoeuvre it is, from the shape alone: loops are steady arcs, squares and triangles are sharp
   corners joined by straights, eights alternate inside and outside arcs, the overhead eights and the clover pass the
   zenith. It counts the repeats; how well the figure was flown never changes what it is.
3. `IdealTracks` builds the ideal track the F2B description defines, anchored where the pilot flew it (the first
   vertical of loops, the centre of eights, the first climb of squares, the take-off's release point and so on).
4. `Grader` matches the flown points to the ideal track by progress and marks `clamp(10 − Σ deductions, 1, 10)`. Each
   criterion deducts `min(cap, perUnit × max(0, measured − tolerance))`, which never falls as the error grows. The
   weights are this game's own, in `MarkWeights` (Annex 4B, the Judges' Guide, is not part of the 2027 volume):

| Criterion | Measured | Tolerance | Per unit | Cap |
|---|---|---|---|---|
| Base height | worst \|height − 1.5 m\| on base segments and loop bottoms | 0.30 m | 2 / m | 3 |
| Track deviation | RMS distance to the ideal track (m) | 0.5 m | 1 / m | 4 |
| 45° parallel | top of a figure away from the 45° parallel (m) | 0.5 m | 1 / m | 2 |
| Line angle | verticals (0°) and the triangles' 30° legs (°) | 3° | 0.1 / ° | 2 |
| Corner radius | mean corner radius (m) | 3 m | 1 / m | 3 |
| Segment length | the squares' ⅛ lap and the triangles' base; the glide's 1 lap (laps) | 0.02 | 10 / lap | 2 |
| Superimposition | worst distance between repeats that should lie on each other (m) | 0.5 m | 1 / m | 2 |
| Symmetry and crossing | drift of the crossing point between passes (m) | 0.5 m | 1 / m | 2 |
| Take-off roll | outside 4.5 m to ¼ lap (m) | 0 | 0.2 / m | 2 |
| Landing touchdown | vertical speed (m/s) | 1 m/s | 1 / (m/s) | 2 |

The `Stunter` cannot turn tighter than about 2.7 m at full handle tilt (the stall, not the elevator, sets the floor), so
the corner tolerance is 3 m rather than the 1.5 m a sharper plane would justify; the ideal corners are 1 m.

The ideal tracks are this game's reading of the F2B descriptions: loops are circles of 22.5° radius between the base and
the 45° parallel; squares are ⅛ lap wide with vertical sides; triangles have legs 30° off vertical and the apex on the 45°
parallel; the horizontal eight is two tangent loops side by side, the vertical eight two stacked from the base to the
zenith, the overhead eight two loops meeting over the pilot; the clover is four ¾ loops joined through the zenith; the
hourglass is a base edge, two crossing legs and a top edge near the zenith; the take-off is a ground roll, a climb over
one lap to the base and two laps at the base; the landing is one lap of glide. The judge keeps each deduction and the
ideal and flown tracks of every manoeuvre for the [debrief](#debrief).

Game simplifications: a flight ends at touchdown, so the landing roll is not judged and "stopped" means touchdown; there
is no Starting (K 0) or preparation time; one judge, no attempts, rounds or classification; no noise or pull tests.

### Scores

`f2b-scores.json` is `{version: 1, boards: {pro: [...], mid: [...], noob: [...]}}`: the 10 best flights of each level,
best first, earlier flights first among equal scores. Each entry has `name`, `plane`, `score` (two decimals),
`flightTime`, `date` and the 15 `marks`. A missing board reads as empty. The score table screen has a tab per board next
to Free flight. An entry may also have a `flight`: the id of its recording (see below). Entries saved without one, and
readers that do not know the field, are unaffected.

### Debrief

After every F2B flight (Pro, Mid and Noob), GAME OVER offers **Debrief**, whether or not the flight entered a board. On the score
table's F2B tabs, Enter on a row opens that flight's debrief. Required behaviour: `control-line-f2b-debrief`
(OpenSpec change `add-f2b-pro-debrief`). The screen shows the 15-row score sheet with one manoeuvre selected, the field
facing that manoeuvre, its **ideal track** (green) and **flown track** (orange) with the base and the 45° parallel as
faint guides, the plane flying the manoeuvre along its recorded path, and the manoeuvre's deductions, largest first, each
with what was measured, its tolerance and the points it cost (`Base height 0.62 m off (±0.30): −0.6`); a manoeuvre
scored 0 shows its reason. V switches between the pilot's view and a view from outside the circle. The pilot's view
widens its field of view up to 100° to fit a figure, which is not enough for the vertical eights; use the outside view
for those. Lines of more than one pixel need a driver that supports them; some draw them one pixel wide, and the
colours still tell the tracks apart.

A replay shows the marks and ideal tracks as they were judged at the time: a later change to the judge does not change a
saved flight.

When an F2B flight enters a board, its recording is saved in `<user-home>/.abyssus-control-line/f2b-flights/<id>.json.gz`
(version 1; the flown path at 30 samples a second with the plane's attitude, and per manoeuvre its time span, ideal track,
mark and deductions or zero reason) and the entry's `flight` names it. A flight that enters no board is not saved. The
recording is deleted when its flight leaves the board, and at start recordings no entry names (and leftover `.tmp` files)
are deleted. A recording that is missing, or that cannot be read (it is renamed with `.bad` added), leaves the entry and
its marks in place: its debrief shows the sheet with "Replay not available". Each recording is about 0.3 MB.

## The project in Abyssus

Open a **copy** of `project/ControlLine` in Abyssus (`./gradlew :plugin-abyssus:runIde`), never the committed folder:
the tests assert on its scene. `PlaneComponent` and `PilotComponent` are registered game components and currently
remain read-only JSON in the editor. The committed `abyssus/components.schema.json` is retained data; it does not
enable Properties editing. A plane duplicated in the field scene appears on plane select at the next game start.

`play/ControlLinePlay.kt` (in the game's `play` source set, `src/play/kotlin`) provides the game's `PlayModule`: it flies the
selected plane (or the first by name), handles W / S or Up / Down input and publishes telemetry. The `play` source set
depends on `lib-game-link`; the game's main classpath and its distribution do not (`checkShippedClasspath`).
To launch the module in Abyssus, run `exportPlay` on a disposable copy of the project:

```
./gradlew :app-game-control-line:exportPlay -PplayProject=/path/to/copy/of/ControlLine
```

It writes the machine-local `<project>/abyssus/play.json` (git-ignored), whose classpath holds the game, its `play`
output and `lib-game-link`. Export again whenever the classpath changes, and once after updating to this layout. The
current Gradle file does not define `exportAbyssus` or `exportComponentSchema`; `abyssus/components.schema.json` is
committed data. Without a launch file, Abyssus selects its bundled physics-only host. See
[`lib-game-link`](../lib-game-link/README.md).

Play gives a module no project folder, so the game's Play ground is flat at height 0, where the field's flying
circle is.

## Crashes

A touch faster than 3 m/s (the speed the plane came down onto the ground at) or upside down is a crash. That impact
speed picks the crash (`flight/CrashSeverity.kt`) and the clip the crashed plane plays once, holding its last frame
through GAME OVER:

| Impact speed | Severity | Clip | What breaks |
|---|---|---|---|
| below 6 m/s | `LITTLE` | `crash_little` | the landing gear |
| 6 to 10 m/s | `MEDIUM` | `crash_medium` | the left wing and the tail |
| 10 m/s and faster | `FULL` | `crash_full` | gear, propeller, wings and tail |

Only the trainer's model has the clips; the other planes stay whole. Slack lines end a flight without a crash clip.
`render/CrashClipGlTest.kt` plays each clip on the built model (with `-Dabyssus.glTests=true`).

## Code

| Package (`net.nevinsky.abyssus.app.game.controlline.`) | Holds | GL |
|---|---|---|
| `components` | `PilotComponent`, `PlaneComponent`, `GameComponents`, `ControlLineComponents` (with the physics components) | no |
| `flight` | `Aero`, `LineRig`, `Ground`, `Flight` / `FlightSystem`, `FlightOutcome`, `FlightSession`, `FlightJudge` | no |
| `track` | `SphereTrack`, `Maneuvers`, `Laps` | no |
| `score` | `ScoreKeeper`, `FlightScoring`, `ScoreTable` | no |
| `pattern` | the F2B pattern: `Schedule` (`Figure`), `HemisphereGeometry`, `PathTokens`, `TrackBuilder`, `IdealTracks`, `IdealSampler`, `Matchers`, `Grader` / `MarkWeights`, `PatternJudge`, `ScoreSheet`, `PatternScoreTable`, and the debrief's `FlightRecording`, `RecordingStore`, `DebriefModel`, and the Noob guide `GuidePlanner` | no |
| `flow` | `GameFlow` (the screen state machine, with the mode and assist level), `FlightReport` | no |
| `input` | `HandleInput` (keyboard and mouse tilt) | no |
| `render`, `screens` | `FieldScene`, `FieldRenderer` (terrain through `lib-render`'s `TerrainPass`, variant `GAME_SINGLE_SUN`), `FieldShadows` (the sun's shadow map, drawn by `lib-render`'s `DepthSubmission`), `Cameras`, `GameUiHost` (the authored screens over `lib-core`'s `UiPresenter`), `GameUiModel` (what the screens show and the actions and keys they send; no GL), `GameUiContract` | yes |
| `play` (own source set, `src/play`) | `ControlLinePlay : PlayModule` | no |

### Foliage

The game window is an OpenGL 3.2 core context (`Main.kt` asks for it, and `ControlLineGame.create` gives the legacy GLSL of
the terrain and model shaders their version line and defines), because foliage is drawn with instanced rendering.
The sky is drawn with layered clouds (`FieldRenderer`, `GAME_CLOUDS`): the asset's shell clouds, eight noise layers per band at
the full window size, left the game GPU-bound at about 35 frames a second in that context.

A terrain entity's `FoliageComponent` is drawn by `FieldRenderer` from the committed bake, with no scatter or write.
`FieldScene.foliages` lists the foliage of the loaded scene (`SceneFoliageListing`; a component on an entity that draws
no terrain is reported once). Assets load through `lib-core`'s `RuntimeFoliageLoader` (in `BaseCtx` and `fieldAssets`),
prepared off the GL thread with the shared `FoliageRuntimeLoad` read policy, so a stale bake keeps its saved copies, a
missing model drops only its copies, and a missing, unsupported or unreadable asset fails and is skipped with one
warning: `FieldRenderer.loaded` counts a failed foliage as settled, so it never holds the loading state.

Ownership: the asset storage owns each built foliage asset and the shared model data. The renderer makes one
`FoliageDrawable` per terrain entity from that asset (its prepared copies and the shared models), so two terrains using
one asset keep their own transforms, instance buffers and culling, whatever the draw order. A placement is rebuilt when
its asset is, dropped when the asset is bound to another terrain than the entity draws, and disposed before the shared
assets on scene replacement and on `dispose`.

Passes: the sun's depth pass calls `updateCasters` with the shadow camera's frustum and draws the OBJECT copies it
keeps, so a tree outside the viewer's frustum still shades visible ground; DETAIL copies (grass) never enter the depth
pass but receive the shadow through the same shadow attribute and shaders as models. The caster buffers are separate
from the viewer's, filled by `update(camera, …)` after the light pass, so the light pass never changes what the viewer
draws. Ray traced views leave foliage out (the game has none). `render/FieldRendererGlTest` and `render/FieldShadowsGlTest`
check this on a GL context (with `-Dabyssus.glTests=true`); `FieldFoliageTest` checks the listing and the terminal
failure without a window.

The field's trees and bushes are foliage on the two terrain entities (`foliage_airfield_site`, `foliage_airfield_outer`) with
grass on the site, made by `tools/FieldFoliage.kt` from the recorded manifest (see `project/environment/README.md`). The
tool has its own source set (`fieldFoliageTools`, with the editing library on its tool classpath only); the game's
runtime classpath does not contain it.

Frames are capped at 60 a second (`Main.kt`): left to vsync alone, a 120 Hz screen gets frames of 8, 17 and 25 ms in turn
and the motion judders. The simulation runs 120 fixed steps a second, so `FlightSession` draws the plane (and moves the
lines' plane ends and the camera target with it) between its last two simulated poses instead of at the latest one.

Everything runs on the LWJGL3 main thread; only asset preparation uses a worker pool. `./gradlew
:app-game-control-line:test` runs every rule headless (Jolt natives only, no window). The pilot's entity is the handle's
kinematic body; the plane is put at takeoff with `PhysicsBody.setPose`.

## Assets and licences

- `model_stunter`: Yuriy Yatsenko's Classic F2B, exported from `models/classic-f2b/Classic_F2B.blend` by
  `models/classic-f2b/export_game_glb.py` (Blender 5.2: `Blender -b Classic_F2B.blend --python export_game_glb.py -- model.glb`),
  mirrored so the inboard wing is on +X, origin at the centre of gravity. Photo credits: `models/classic-f2b/README.md`.
- `model_racer`, `model_pilot`: generated by `tools/PlaneModels.kt` (`./gradlew
  :app-game-control-line:generatePlaneModels`).
- `model_trainer`: FlightGear's Cessna 172R (`c172r`), 3D model by David Megginson, licensed GPL-2.0-or-later
  (`model_trainer/COPYING`; the archive states no licence, so FlightGear's aircraft licence is assumed). It is a
  separately licensed asset, not Apache-2.0. `importers/TrainerModel.kt` (`./gradlew :app-game-control-line:importTrainer`)
  re-imports it through `lib-core`'s FlightGear import: the archive is checked against its SHA-256, scaled to a 1.0 m span,
  without the cabin interior or the spinning propeller disc, with its origin at the estimated centre of gravity. It
  parks level on its nose wheel (`importers/trainer.patch.json`, applied by `ScenePatchApplicationTest`).
  `importTrainer` then adds three crash clips (`importers/TrainerCrashAnimations.kt`; `animateTrainer` adds them to
  a freshly imported model): `crash_little` (the landing gear breaks), `crash_medium` (the left wing and the tail
  break) and `crash_full` (a nose-in that scatters gear, propeller, wings and tail). For them the wings are cut at
  their roots and the tail cone behind the cabin, the moving parts turn about their own pivots under one `Trainer`
  node, and no part goes below the parked wheels' ground. Each clip starts from the rest pose and ends with the wreck
  at rest. The game plays one when a flight crashes into the ground (see "Crashes").
- `terrain_field`, `texture_grass`, `texture_mown`, `texture_field_splat`: generated by `tools/FieldAssets.kt`
  (`./gradlew :app-game-control-line:generateField`) with `lib-core`'s terrain generator.
- `skybox_physical`: copied from the `Untitled` test fixture.
- The airfield (`*_airfield_*`) reconstructs the real control-line circle in Samara from reference images and site notes (the original reference files are not in this checkout). It has a 50 m asphalt pad with rings at the 15, 18 and 21 m line radii, a 300 m
  splat-mapped site terrain (`terrain_airfield_site`, level within 30 m of the pilot) and a 600 m outer ground. The
  scenery includes the WC and staff building, the preparation area, the street, the DK "Plamya", the blue-roof
  warehouse, the electrified railway, houses, trees and bushes. All of it is CC0: Poly Haven models and ambientCG photo
  textures, or generated from them. Each folder has a `source.json` with its source and licence. Nothing stands within
  28 m of the pilot. Sizes and positions are estimates, not survey data. See
  [the environment notes](project/environment/README.md) to rebuild it.

## Screens

Every screen, the HUD and the pause menu are the `screens` UI asset of the bundled project
(`project/ControlLine/assets/screens/meta.json`, registered in `ControlLine.abss`), drawn by `lib-core`'s `UiPresenter` through
`GameUiHost`. Open a **copy** of `project/ControlLine` in Abyssus to edit them in the Game UI tab; the committed folder is
asserted on by tests. The asset's contract with the game is `screens/GameUiContract.kt`: the bindings `GameUiModel`
publishes every frame (lists carry the fields of their rows) and the named actions the asset may emit. When you change
a screen, keep `ControlLineUiAssetTest` green (it admits the asset and checks both lists against the contract), and run the
GL tests (`-Dabyssus.glTests=true`): `GameScreensCharacterizationTest` compares the text of 23 steps through a whole session
with `characterization/screens.golden`, recorded from the original hand-written screens. Re-record it
(`-Dabyssus.recordScreens=true`) only when a screen is meant to change.

Look: a dark navy ground (`#0E1A23`), an orange accent (`#FF6A2B`) and muted blue-greys, from a design made in Claude's design canvas. The styles are in the asset: backgrounds `bg`, `dim`, `pane` (translucent HUD and table panels) and `card`; text `hero`, `title`, `big`, `mid`, `toast`, `body`, `hudLabel`, `hudValue`, `hudBig`, `tabLabel`; the roles the game publishes per row (`normal`, `highlight`, `muted`, `fail`, `selected`, `ideal`, `flown`); controls `primary`, `outline`, `tab`, `tabSelected`, `field`, `bar`. A style's `fontSize` is a font's line height (see `assets/screens/fonts/index.json`), and `uppercase` shows a label in capitals without changing its text. The renderer has no rounded corners or letter-spacing, so those parts of the design are left out. A screen's root fills the window: centre or edge-anchor content with a column inside a root panel, and give a label the height of its font's line (it is measured by cap height, which would clip descenders).

Fonts and art are generated and committed, so the game needs neither the sources nor the tools: `./gradlew :app-game-control-line:generateUiFonts` renders the bitmap fonts from the SIL OFL fonts Barlow Condensed and IBM Plex Mono in `tools/fonts` (with their licences) and, beside each, an `@2x` twin at twice the pixel size that `createGameUi` loads instead when the back buffer is at least 1.5 times the window (the styles' `fontSize` stays in logical pixels), and `generateUiArt` draws `art/menu-track.png`. To look at every screen, run the GL tests (`-Dabyssus.glTests=true`): `GameScreenshotsGlTest` writes a picture per step to `build/screenshots`, and `GameUiHiDpiGlTest` checks drawing and clicking on a window twice as dense as it is large.

Screens fit a 1280 × 720 reference rectangle and use the bundled theme; the keys of the game (Escape, Left / Right, Enter on
plane select and the boards, the debrief's keys) are handled by `GameUiModel.key` before the presenter sees them.

## Tuning

The flight model is in `flight/Aero.kt` (design decision 3 of `add-control-line-game`). These constants are not scene
fields, and `FlightTest` holds the flight to the `control-line-flight` spec with them:

| Constant | Value | What |
|---|---|---|
| `AIR_DENSITY` | 1.225 kg/m³ | |
| `ASPECT_RATIO` | 6 | every wing's span²/area: gives the mean chord and the span from the wing area |
| `STALL_ANGLE` | 15° | past it lift stops growing and drag doubles |
| `ELEVATOR_THROW` | 20° | the elevator at full handle tilt |
| `PITCH_STABILITY` | -0.6 per rad | the stabiliser's moment towards `TRIM_ANGLE` |
| `TRIM_ANGLE` | 2° | the angle of attack with the elevator neutral |
| `FIN_SIDE_FORCE` | 0.6 per rad | side force against sideslip, on the wing area, `TAIL_ARM` (3) chords behind the centre of gravity |
| `RUDDER_EFFECT` | 0.05 per rad | yaw moment of the rudder offset |
| `ROLL_DAMPING`, `YAW_DAMPING` | 0.5, 0.3 | as pitch damping, on the span |
| `LINE_DRAG_COEFFICIENT` | 1.0 | a line is a cylinder across the flow |

The planes' scene values, in `scenes/Field.scene` (unlisted fields keep `PlaneComponent`'s defaults: line diameter
0.4 mm, fuel 120 s, wing area 0.2 m², lift slope 4.5, zero-lift drag 0.03, elevator effect 0.5, pitch damping 12,
rudder and engine offset 2°):

| Plane | Class | Mass | Lines | Thrust | Fuel | Wing area | Other |
|---|---|---|---|---|---|---|---|
| `Racer` | SPEED | 0.5 kg | 21 m | 6 N | 60 s | 0.09 m² | lift slope 4.2, zero-lift drag 0.02, elevator effect 0.3, pitch damping 10 |
| `Stunter` | STUNT | 1.1 kg | 18 m | 5 N | 360 s | 0.325 m² | lift slope 8, elevator effect 3 |
| `Trainer` | TRAINER | 0.8 kg | 15 m | 5.5 N | 120 s | 0.2 m² | zero-lift drag 0.06 |

All three have no linear damping and friction 0.05 (they roll on their wheels). With the handle neutral they settle at:

| Plane | Above 1 m after | Speed | Lap | Tension | Elevation |
|---|---|---|---|---|---|
| `Racer` | 2.8 s | 39 m/s | 3.4 s | 34 N | 9° |
| `Stunter` | 4.8 s | 24 m/s | 4.9 s | 29 N | 8° |
| `Trainer` | 4.2 s | 24 m/s | 4.0 s | 28 N | 3° |

Full up loops the `Stunter` in about 0.5 s, over the top at 45° of elevation or more, and its corners at full tilt are about 2.7 m in radius (`PatternFlyabilityTest`). Its fuel and its lift slope and elevator effect were raised for the F2B pattern (the old values turned no tighter than about 9 m).
