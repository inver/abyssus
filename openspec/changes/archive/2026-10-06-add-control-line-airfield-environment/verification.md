# Verification

## Revised version (2026-10-06)

### Application

`Field.scene` was edited once through `editSceneJson`, by the env-gated `AirfieldPatchApplicationTest`, with the
patch from `layout.py` (its expected SHA-256 matched). Compared with the pre-edit copy:

- Entity 0 (`Field`) now uses `terrain_airfield_site` at (-150, 0, -150).
- Entities 100-204 (the previous scenery) were removed and 333 entities appended: the outer ground and 332 instances
  (125 trees, 90 bushes, buildings, houses, 12 railway segments, street lamps, preparation props and the pad).
- Entities 1-5 (pilot, sun, planes) and every top-level setting (lighting, fog, sky) are identical. The other number
  text and keys are unchanged.

### Passed

- `./gradlew :games:control-line:test`: 97 tests, no failures. This includes the new `AirfieldEnvironmentTest`:
  - every airfield model parses with `AssimpModelLoader` and has its textures inside its folder, with nothing embedded;
  - every airfield asset is native and records its licence and source;
  - every scenery footprint lies at least 28 m from the pilot (nearest: a model stand at 30.2 m);
  - the line lengths are 21, 18 and 15 m.

  It also includes `BundledProjectTest`: no load warnings, physics bodies only for Field, Pilot and the three planes
  (no scenery colliders), and the parked planes rest on the new site terrain.
- `SunShadowCameraTest`: the shadow camera centres on the pilot, covers the facilities, street and railway, puts
  casters between the ground and the sun, snaps to texels, and uses a sub-texel bias.
- `./gradlew :games:control-line:run`: the game starts and renders the field without shader or GL errors in the log.
  It was stopped after 30 s.
- `scripts/check-docs.sh`: 188 paths OK.
- `openspec validate add-control-line-airfield-environment --strict`: valid.
- No `core`, `gdx-model`, plugin-main or `scene-shadows` code changed. The plugin's only new file is the gated test.

### Repository failures outside this change

`./gradlew check --continue` fails only in plugin `:test` (882 tests, 69 failed, 40 skipped) and
`:physics-plugin:test` (11 tests, 5 failed). These come from the unfinished asset-loading refactor on this branch:
`PhysicsOverlayGeometryTest` hits `NoSuchMethodError` on the old `AssetMetaLoader` constructor, and `AbyssusViewTest`
and others expect the old asset row fields. All other `check` tasks pass, including the singleton, Jolt and
`runCatching` guards.

### Visual checks still open

This environment cannot capture the screen, so nothing rendered has been looked at.

1. Task 4.2 / 6.3: run `./gradlew :games:control-line:run`, choose each plane and take off. Check that trees,
   buildings, the pilot and the plane cast soft shadows on the ground and on each other, with no acne or
   peter-panning, and that the dry, worn midday look reads from the pilot camera. Look for a seam at the site edge
   (task 2.2). Check that the frame rate holds: about 330 instances, trees at 27-42k triangles, drawn twice with the
   shadow pass.
2. Task 6.2: copy `games/control-line/project/ControlLine` to a temporary folder and run
   `./gradlew runIde -PideProject=/absolute/path/to/copy`. Compare a top-down and an oblique view with
   `project/pics/img.png` (pad and rings, landmark directions and distances, vegetation density), then move an object
   and check undo and redo.

## Previous version


### Application

The generated airfield is active in the bundled `Field.scene`, with 92 tree
instances, 12 building instances and a surrounding terrain. There are 111
entities in total. Only the original field asset reference and Z coordinate
changed; reversing those substitutions and removing the appended block
reproduces the original scene byte for byte. Pilot, plane, collider, lighting
and sky data are unchanged.

The user requested application after review of the direct edit. A temporary
invocation of `editSceneJson` could not run because unrelated existing test
sources failed compilation. That invocation was removed. The approved direct
application used the actual compiled `AbyssusDocumentFormat` to validate both
scene documents and all 19 asset metadata documents before writing.

### Passed

- `./gradlew :compileKotlin`: successful.
- `./gradlew :games:control-line:test --rerun-tasks`: 88 tests, no failures or
  errors, all 18 tasks executed. The scene is active during this fresh run.
- Asset graph check: model and terrain references resolve; terrain texture
  UUIDs resolve; all updated local documentation links exist.
- Surface check: the field's 201 x 201 local heights are all 1 and its Y
  offset is -1, so the flying surface is at world height 0.
- `git diff --check -- games/control-line`: successful.
- `openspec validate add-control-line-airfield-environment --strict`: successful.

### Repository failures outside this change

`./gradlew check --continue` failed in `:checkNoRunCatching` and plugin `:test`
(867 tests completed, 151 failed, 40 skipped). Report:
`/private/tmp/abyssus-airfield-check.log`. These failures concern existing
plugin cancellation checks, asset/property editing, scene content and other
plugin tests; this change edits no plugin implementation or tests.

`scripts/check-docs.sh` still reports the same six pre-existing broken paths
in `docs/ai/architecture.md`, `docs/ai/conventions.md` and `docs/ai/file-formats.md`.

### Visual checks still open

1. Copy `games/control-line/project/ControlLine` to a temporary directory and
   open that copy with `./gradlew runIde -PideProject=/absolute/path/to/copy`.
   Inspect tree/building scale, material appearance and clearance around the pad.
2. Editor undo/redo applies to future edits made through the editor. This
   approved offline content application has no editor undo command.
3. Run `./gradlew :games:control-line:run`, start a flight and inspect scenery
   from the pilot camera. Gameplay is covered by the fresh headless tests;
   rendered appearance has not been checked in this session.
