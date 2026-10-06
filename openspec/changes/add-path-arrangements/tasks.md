# Tasks

Paths are relative to the repository root. `runIde` checks use a copy of the Untitled project, never
`projects/plugin-abyssus/src/test/testData/project/Untitled` itself.

## 1. Runtime component

- [ ] 1.1 Add `PathArrangementComponent` (points, closed, asset, spacing, offset, sides, alignToPath, yawOffset, slots) to `projects/lib-runtime` `ecs/component`, register it in `BUILT_IN_COMPONENTS` and `EcsJson`, with KDoc saying copies are in world coordinates; verify with a new `PathArrangementComponentTest` in `:lib-runtime:test` (loads the design.md example without warnings, writes it back byte-identical, omits defaults) and `./gradlew :lib-runtime:checkNoSingletons`
- [ ] 1.2 Document the component in `docs/ai/file-formats.md` and `projects/lib-runtime/README.md`; verify with `scripts/check-docs.sh`

## 2. Layout and heights (lib-core-editor, pure)

- [ ] 2.1 Add `PathLayout` in `projects/lib-core-editor/.../editor/arrange` (stations by X/Z arc length, open and closed, sides, offset, yaw, the 1000-slot cap); verify with `PathLayoutTest` cases: open 40-unit line gives 5 stations, closed 20x20 square gives 8, short line gives 1, `BOTH` with offset 3 gives the four positions of the spec scenario, +Z line gives a quarter turn, `alignToPath` false gives `yawOffset` only, right side gets a half turn, more than 1000 is refused
- [ ] 2.2 Add `GroundHeights` over `TerrainTarget`s (downward ray through `ScenePicker.terrainDistance`, highest hit, fallback to interpolated line Y); verify with `GroundHeightsTest`: height on the fixture terrain at (20, 0) equals `TerrainData.heightAt` of its local point, a translated and a rotated terrain, off-terrain fallback halfway between Y 2 and 4 is 3

## 3. Scene edits (lib-core-editor, pure)

- [ ] 3.1 Add `ArrangementEdits.create` (arrangement `Path <id>` of type `GROUP`, copies `Path <id> #<slot>` with `ParentComponent`, `slots`) with the names in `AbyssusEditorBundle.properties`; verify with `ArrangementEditsTest` "a line of trees in Main Scene": entity 11 plus copies 12 to 16, entities 0 to 10 byte-identical, refused for a non-native scene
- [ ] 3.2 Add `ArrangementEdits.regenerate` (rewrite kept slots, add past the end, remove past the new end, change asset, keep names and other components, normalise empty slots to `-1`, refill rule on spacing / sides / closed / first point, no write when nothing changes); verify with `ArrangementEditsTest` cases matching the spec: drag end point to 60 adds two copies, shorten to 20 removes two, change model, copy keeps a game component, deleted entity 15 stays empty, spacing change refills, moving the last point keeps slot 0 empty, Regenerate with no change returns unchanged
- [ ] 3.3 Add `ArrangementEdits.detach` and call it from `SceneTransformWriter` (move, rotate, drop) and from `ComponentEditor` for `PositionComponent` fields of a linked copy; verify with `SceneTransformWriterTest` "moving an arranged copy detaches it", `ComponentEditorTest` "edit an arranged copy's position" and "a rename does not detach", and `HeadlessEditingTest` showing the headless transform edit of copy 14 equals the plugin's text
- [ ] 3.4 Add the `PathArrangementComponent` kind to `BuiltInComponentKinds` (settings editable with limits, `points` and `slots` read-only) and make a settings field edit run `regenerate` in the same edit; verify with `ComponentEditorTest` "change an arrangement's spacing" and "spacing of zero is refused", and `./gradlew :lib-core-editor:checkNoSingletons`
- [ ] 3.5 Describe the `arrange` package in `projects/lib-core-editor/README.md` and add arrangement, slot and detach to `docs/ai/glossary.md`; verify with `scripts/check-docs.sh` and `./gradlew checkPackageCycles`

## 4. Scene view interaction (lib-core-editor `pick`, pure)

- [ ] 4.1 Add `PathHandles` (point and middle handles projected to the screen, pixel-radius hit, segment hit for selecting the line, copies win over the line); verify with `PathHandlesTest`
- [ ] 4.2 Add `Gesture.DrawingPath` to `SceneInteraction` (click on terrain adds a point, sky click ignored, Backspace, Enter / double click with 2 or more points, click on the first point closes with 3 or more, Esc cancels, too few points keeps drawing) with an `onArrangementCreate` callback; verify with a new `SceneInteractionPathTest` using fake `SceneQueries`
- [ ] 4.3 Add `Gesture.DraggingPathPoint` (move a point, insert from a middle handle, Esc restores, release calls `onArrangementEdit`) and a preview model (path segments and slot markers for `LineSink` / `SceneMarkers`); verify with `SceneInteractionPathTest` drag, insert and Esc cases and `SceneMarkersTest` for the slot markers

## 5. Plugin

- [ ] 5.1 Add the terrain height source: view terrain targets when a Scene view is open, otherwise a project cache read with `TerrainLoader.prepare` on a pooled thread keyed by `AssetRevisions`; verify with a plugin test that the Properties-panel spacing edit of `Path 11` with no view open writes copies at fixture terrain heights
- [ ] 5.2 Add `AddPathArrangementAction` (toolbar and scene tree row, models only, disabled for unreadable or project-less scenes) that opens the Scene view in drawing mode, with texts in `AbyssusBundle.properties`; verify with `AddPathArrangementActionTest` (fixture model list, unreadable scene disabled) and `PluginBundleTitlesTest`
- [ ] 5.3 Wire drawing, point dragging, Remove Point (right-click menu, disabled at two points) and the arrangement selection highlight in `projects/plugin-abyssus/.../sceneview`, writing through `editSceneJson` with the new arrangement row selected afterwards; verify with a `SceneViewPanelTest` case that a finished drawing writes one undoable command and Undo restores the text
- [ ] 5.4 Add `RegenerateArrangementAction` on the arrangement tree row and in the toolbar while an arrangement is selected; verify with `RegenerateArrangementActionTest` (no change writes nothing; changed terrain heights rewrite only Y)
- [ ] 5.5 Update `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md` and `docs/ai/architecture.md` for the drawing gestures and the height source; verify with `scripts/check-docs.sh`

## 6. Integration checks

- [ ] 6.1 Run `./gradlew check`; verify it passes
- [ ] 6.2 runIde check 1: in a copy of Untitled, Add Path Arrangement > `tree`, click three points on the terrain, watch the markers follow, press Enter; verify the trees stand on the terrain along the line, `Path 11` is selected, and one Undo removes everything
- [ ] 6.3 runIde check 2: set `sides` to `BOTH`, `offset` to `3` in the Properties panel; drag the last point handle, then drag a middle handle; verify the two rows face each other and follow both edits
- [ ] 6.4 runIde check 3: move one copy with the gizmo, then drag a path point; verify the moved copy stays put, its slot stays empty, and changing `spacing` refills the slot without touching the moved copy
- [ ] 6.5 runIde check 4: draw a closed loop by clicking the first point; Esc during a second drawing; verify the loop has copies on every side and the cancelled drawing wrote nothing
