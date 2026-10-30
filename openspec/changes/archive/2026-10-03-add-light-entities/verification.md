# Verification

## Automated checks

The initial `./gradlew check` passed before the original Untitled fixture was edited in a live IDE.
The final full run compiled the changes and ran 492 plugin tests: 10 failed, 24 skipped. Existing assertions failed
because `src/test/testData/project/Untitled/scenes/Main Scene.scene` now has 9 entities instead of 7, moved model
positions, and a moved/rotated camera target. The edited fixture was preserved. The new light creation tests use
`Lights/scenes/Creation Baseline.scene`, a clean copy of the committed Untitled scene, to avoid depending on live edits.

The final targeted run passed all 75 tests across the codec, component editor, creation, undoable edits, menus, panel, Scene editor and properties tests.

Failures from that final run:

- `net.nevinsky.abyssus.AbyssusViewTest.testNodeTree`: junit.framework.AssertionFailedError: expected:<[ambientLight, fog, skybox: skybox_physical, ecs  7 entities]> but was:<[ambientLight, fog, skybox: skybox_physical, ecs  9 entities]>
- `net.nevinsky.abyssus.ecs.SceneEcsLoaderTest.mainSceneLoads`: java.lang.AssertionError: expected:<7> but was:<9>
- `net.nevinsky.abyssus.projectView.SceneTransformEditTest.testMoveKeepsIndentationAndEveryOtherLine`: junit.framework.AssertionFailedError: [44, 45] expected:<1> but was:<2>
- `net.nevinsky.abyssus.projectView.SceneTransformEditTest.testNothingIsWrittenWhenTheEditChangesNothing`: junit.framework.AssertionFailedError
- `net.nevinsky.abyssus.plugin.sceneview.SceneContentTest.mainSceneHasThreeModelsAndOneTerrain`: java.lang.AssertionError
- `net.nevinsky.abyssus.plugin.sceneview.SceneContentTest.mainSceneHasTheFixtureCamera`: java.lang.AssertionError: expected:<Vec3(x=0.0, y=0.0, z=0.0)> but was:<Vec3(x=0.0, y=5.520455, z=0.0)>
- `net.nevinsky.abyssus.plugin.sceneview.SceneMarkersTest.theViewCameraHasNoMarkerTarget`: java.lang.AssertionError
- `net.nevinsky.abyssus.plugin.sceneview.SceneMarkersTest.aCameraDrawsABodyAndAFrustum`: java.lang.AssertionError: expected:<28> but was:<54>
- `net.nevinsky.abyssus.plugin.sceneview.SceneRendererCameraTest.lookingThroughACameraUsesItsPositionDirectionAndLens`: java.lang.AssertionError: expected:<0.8857895> but was:<0.96025884>
- `net.nevinsky.abyssus.plugin.sceneview.SceneTransformWriterTest.movingAnEntityChangesOnlyItsLocalPositionX`: org.junit.ComparisonFailure: expected:<[1.35894]2> but was:<[0.912396]2>

`scripts/check-docs.sh` passed (126 paths in 6 files), `git diff --check` passed, and
`openspec validate add-light-entities` passed.

## Review fixes

- Support readable empty scenes with no ECS or entity object yet.
- Keep direct range-only light objects direct, including removing range when reset to 100.
- Do not reuse a noncanonical archetype key if converting it to an integer would make its reference invalid.
- Reject JSON that cannot bind to the scene DTO without writing.
- Publish selection immediately so the new light is selected even before its tree row exists.

Regression tests reproduced these issues before their fixes.

## Manual IDE verification — pending

Project copy: `/private/tmp/abyssus-add-light-entities-ide`. Launch command:

```sh
./gradlew runIde -PideProject=/private/tmp/abyssus-add-light-entities-ide
```

The original fixture is never used as the sandbox IDE project by this workflow.
The sandbox IDE was launched with this copy; its log confirms that the project path was forwarded to the running IDE.
The desktop IDE cannot be driven with the available automation tools; task 5.2 remains unchecked.

1. Open `scenes/Main Scene.scene`, switch to **Scene View**, then choose **Add Light > Sun** in its toolbar.
   Confirm its marker is at the orbit target, model shading changes, and the new light is selected in both the
   Scene view and Abyssus Properties.
2. With `skybox_physical` selected as the procedural sky, choose **Add Light > Directional** and confirm that the
   sky's sun follows that light's direction.
3. In the Abyssus tree, right-click the scene row and choose **Add Light > Spot**. Confirm its position is
   `(0, 5, 0)` and it is selected.
4. In Abyssus Properties, set the spot's **Range** to `30`. Confirm the lit area shrinks.
5. Undo the range edit, then Undo Add Light. Confirm the spot entity and marker disappear.
6. Reopen the scene text and inspect the created entities: Name, Type, Position and nested Light data, with a valid
   four-component archetype. Undo should restore the exact text before creation.
