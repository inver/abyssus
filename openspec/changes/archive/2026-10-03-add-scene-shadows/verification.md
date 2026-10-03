# Apply verification — 2026-10-03

Progress: 16/24 tasks complete (1.1–1.4, 2.1–2.2, 3.1–3.3, 4.1–4.4, 5.1–5.2, 5.4).

Passed:
- Mundus serialization inspected at local commit `128175e064a915e043f024a565f935d4c6883292` and against the saved Lights fixture; extension mapping recorded in design Decision 1.
- Strict OpenSpec validation of `add-scene-shadows` and `add-light-entities` during coordination.
- `./gradlew :test --tests '*ComponentCodecsTest' --tests '*SceneRenderParamsTest'`
- `./gradlew :test --tests '*ComponentEditorTest' --tests '*SceneComponentEditsTest'` (29 tests)
- `./gradlew :test --tests '*EntityPropertiesPanelTest'` (13 tests)
- `./gradlew :test --tests '*SpotConeTest' --tests '*SceneLightingTest'`
- `./gradlew :test --tests '*SceneRenderGlTest' -Dabyssus.glTests=true` (25 tests; the full class passed after isolating its test scene inputs from pre-existing fixture lights and intersecting model placements).
- `./gradlew :test --tests '*ShadowLayoutTest' --tests '*ShadowProjectionTest'` (6 tests).
- `./gradlew :gdx-model:test`.
- `./gradlew :gdx-model:test -Dabyssus.glTests=true` (23 tests including >65k indices, posed bones, alpha cutouts/blended exclusion, radial depth and independent default/PBR atlas lighting).
- `./gradlew :test --tests '*SceneRenderGlTest' --tests '*Shadow*Test' -Dabyssus.glTests=true` (35 tests, no skips; includes model-to-terrain and terrain-to-model casting, independent second-light fill, projection/layout, framebuffer fallback/restoration and current-pose bounds).
- Targeted scene casting, light-removal/context-rebuild and ShadowResourcesGlTest checks passed after wiring immediate hidden-context abandonment (4 tests).
- Depth and color cutout tests cover diffuse texture/material alpha, no blending and cutoff boundaries. Imported MASK materials with non-unit alpha retain AlphaTest without acquiring blending, so they remain shadow casters. Review found and fixed first-pose bounds clipping; per-bone boxes now track current poses for fitting/culling.
- `scripts/check-docs.sh` (127 paths), gdx-model source-path check, and `git diff --check`.

Task 2.3: README updated and docs checked; runIde checks 3/4 remain pending.
Task 5.3: safe disposal, immediate CPU-only abandonment, rebuild, light removal and fallback implemented; automated GL checks passed. Its required runIde checks 2/5 remain pending, so the task remains open.

The render test inputs were repaired only inside `SceneRenderGlTest`; the shared `Untitled` scene fixture's pre-existing height edit and its scene contents were preserved. The two render assertions now use an explicit light-free baseline and spaced model placements without camera/light marker colliders.

The stack trace's `EyeTree.actionBounds` failure is guarded by skipping rows whose `getRowBounds` is null. Its empty-tree regression and the full RowActionsTest class passed again in final `check` (5 tests).

Strict final OpenSpec validation and docs checks pass. Both `./gradlew check` and final `./gradlew check --continue` completed with the same 10 pre-existing fixture-dependent failures (514 tests, 30 GL skips). `core` and `gdx-model` checks passed, including `core:checkNoSingletons`. Task 6.6 remains unchecked because the repository check is not green. RunIde integration checks remain pending. RunIde checks use a copy of Untitled, never the test fixture. Mundus extension round-trip compatibility remains unverified.

## Manual IDE checks still required

No desktop automation capability is available in this session. Automated AWT GL tests do not establish the complete IDE interactions below. Leave tasks 2.3, 5.3 and 6.1–6.5 unchecked until recorded.

Prepare a writable copy of `src/test/testData/project/Untitled` outside the fixture, then launch `./gradlew runIde -PideProject=/absolute/path/to/copy`. Use only the copy.

1. Open Main Scene's Scene View. Add point and spot lights and an opaque occluder over terrain; move receivers around all six point axes and spot cone edges. Confirm model/model and model/terrain shadows, second-light filling and retained ambient/HDR lighting. Inspect grazing surfaces, point-face seams and tile borders.
2. Drag/rotate a light and model with gizmos; Undo each drag. Open a copy of Animated/Main.scene and observe poses. Check that shadows follow the displayed preview without lag.
3. Select a spot in Properties, set angle 60 degrees and softness 25 percent. Save, close and reopen; verify the controls and beam. Inspect scene text for unrelated number/key-order preservation, then Undo each edit.
4. Try invalid/nonfinite values, angles 0/180 and softness outside 0–100 percent. Check sharp zero-softness and fully soft 100-percent beams. Reset 45 degrees/20 percent and confirm only extension keys disappear. If Mundus is available, re-save another copy and record whether the extension survives; compatibility remains unverified.
5. Exceed one directional, two point and three spot shadow budgets while respecting the five-local illumination ceiling. Delete lights, switch scenes, resize, minimize and hide/show the view. Confirm continued lighting and no stale shadows or unsafe-context failures. Record frame timings and input responsiveness on a representative scene.

## Repository check failures

These assumptions disagree with the current user-edited Untitled fixture. The fixture and unrelated tests were preserved; only the two previously approved GL test inputs were repaired.

| Test | Current mismatch |
|---|---|
| AbyssusViewTest.testNodeTree | Expected 7 entities, current scene has 9 |
| SceneEcsLoaderTest.mainSceneLoads | Expected 7 entities, current scene has 9 |
| SceneTransformEditTest.testMoveKeepsIndentationAndEveryOtherLine | Current starting position changes two coordinate lines rather than the pinned one |
| SceneTransformEditTest.testNothingIsWrittenWhenTheEditChangesNothing | Pinned position is no longer the fixture position |
| SceneContentTest.mainSceneHasThreeModelsAndOneTerrain | Pinned placement no longer matches |
| SceneContentTest.mainSceneHasTheFixtureCamera | Expected camera Y 0, current Y 5.520455 |
| SceneMarkersTest.theViewCameraHasNoMarkerTarget | Current fixture includes extra light markers |
| SceneMarkersTest.aCameraDrawsABodyAndAFrustum | Expected 28 lines, current camera/light markers draw 54 |
| SceneRendererCameraTest.lookingThroughACameraUsesItsPositionDirectionAndLens | Pinned camera direction differs from current position/look-at |
| SceneTransformWriterTest.movingAnEntityChangesOnlyItsLocalPositionX | Pinned position differs from latest fixture edit |

The fixture changed again during this continuation; all tests added here use explicit scene inputs where exact geometry matters. The existing Gradle libGDX version edit (1.14.2) was also preserved.

## Shadow quality repair — 2026-10-03

The user supplied an aircraft screenshot showing blocky cast edges and triangular surface artifacts. Two new GL regressions reproduced numerical depth corruption and self-shadowing on a sloping plane before the fixes, then passed afterward.

- RGBA8 storage quantizes channels to 255 steps; depth packing and all three receivers now use matched base-255 digits rather than base-256 digits. The precision test checks several depths through an actual framebuffer within 0.00001.
- Four bilinear PCF samples interpolate sixteen comparisons. Slope derivatives are evaluated before per-fragment coverage branches; a strict identical-scene image comparison reproduced border flickering and passed after correcting that ordering. Receiver-plane gradients correct the depth expected at each actual sample center, reducing self-shadow acne without a large detaching bias. Tile-center clamping prevents sampling adjacent lights.
- Small selected light sets use available atlas space: 4096 pixels for one view, 2048 for two to four, 1365 for five to nine, 1024 for larger sets. The grid grows only when required and resets when no shadow lights remain, preserving tile positions after removals. Actual resolution drives directional snapping and bias.
- Depth passes disable dithering and sRGB output, then restore both states; packed depth is data rather than display color.
- All model GL tests passed after the changes, including depth precision, sloping-surface acne, default/PBR per-light isolation, skinning and cutouts. Targeted scene GL checks passed for atlas layout, projection, resources and casting in both directions.
- The updated aircraft render is saved in `build/screenshots/shadows/main-scene.png` and was visually inspected. The complete scene GL regression suite is being rerun; manual runIde checks remain open.
