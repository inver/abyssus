# Editor restructuring baseline (2026-10-06)

`./gradlew check --console=plain`: BUILD SUCCESSFUL (8 executed, 77 up-to-date in the initial run; refreshed run passed).

Counts are Kotlin main sources in the root plugin, grouped by the first package segment after `net.nevinsky.abyssus`.

| Package | Files | Lines |
|---|---:|---:|
| (root) | 7 | 661 |
| assetfiles | 4 | 410 |
| dto | 10 | 475 |
| ecs | 5 | 766 |
| filetype | 6 | 414 |
| format | 1 | 8 |
| language | 8 | 175 |
| log | 1 | 56 |
| projectView | 26 | 2822 |
| properties | 11 | 1828 |
| sceneview | 76 | 7917 |
| schema | 3 | 235 |
| terrain | 12 | 1452 |
| ui | 1 | 4 |

| Import source | Import target | Count |
|---|---|---:|
| (root) | dto | 3 |
| (root) | format | 2 |
| (root) | log | 1 |
| (root) | projectView | 1 |
| (root) | sceneview | 2 |
| (root) | terrain | 6 |
| assetfiles | dto | 1 |
| assetfiles | filetype | 2 |
| dto | filetype | 3 |
| dto | format | 4 |
| ecs | projectView | 1 |
| ecs | sceneview | 2 |
| filetype | dto | 1 |
| filetype | format | 2 |
| projectView | assetfiles | 10 |
| projectView | dto | 29 |
| projectView | ecs | 11 |
| projectView | filetype | 18 |
| projectView | properties | 4 |
| projectView | sceneview | 4 |
| projectView | schema | 2 |
| projectView | terrain | 6 |
| properties | dto | 9 |
| properties | ecs | 5 |
| properties | filetype | 9 |
| properties | format | 2 |
| properties | projectView | 7 |
| properties | sceneview | 20 |
| properties | schema | 1 |
| properties | terrain | 7 |
| sceneview | dto | 7 |
| sceneview | ecs | 1 |
| sceneview | filetype | 8 |
| sceneview | projectView | 8 |
| sceneview | terrain | 1 |
| schema | dto | 2 |
| schema | ecs | 2 |
| schema | filetype | 2 |
| terrain | assetfiles | 11 |
| terrain | dto | 1 |
| terrain | filetype | 3 |
| terrain | properties | 1 |

## Scene layout sites

Current sites containing `SceneEcsPaths`, `"ecs"` or `"components"` (the proposal count is historical):

- `src/main/kotlin/net/nevinsky/abyssus/SceneEcsPaths.kt`
- `src/main/kotlin/net/nevinsky/abyssus/ecs/scene/ComponentEditor.kt`
- `src/main/kotlin/net/nevinsky/abyssus/ecs/scene/LightEntities.kt`
- `src/main/kotlin/net/nevinsky/abyssus/ecs/scene/SceneEntities.kt`
- `src/main/kotlin/net/nevinsky/abyssus/filetype/AbyssusFileTypes.kt`
- `src/main/kotlin/net/nevinsky/abyssus/projectView/AddLightAction.kt`
- `src/main/kotlin/net/nevinsky/abyssus/projectView/ComponentActions.kt`
- `src/main/kotlin/net/nevinsky/abyssus/projectView/DtoTree.kt`
- `src/main/kotlin/net/nevinsky/abyssus/projectView/EntitySelection.kt`
- `src/main/kotlin/net/nevinsky/abyssus/properties/PanelState.kt`
- `src/main/kotlin/net/nevinsky/abyssus/sceneview/RaySceneSnapshot.kt`
- `src/main/kotlin/net/nevinsky/abyssus/sceneview/RayViewFeed.kt`
- `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneContent.kt`
- `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneRayEdits.kt`
- `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneTransformWriter.kt`

Shared native admission now lives in `core.format`; its reserved-field paths are intentionally retained there.

## Reproduction

## Package-cycle guard verification

`./gradlew checkPackageCycles --console=plain` passes with the captured transition allowlist.
Adding two temporary packages (`cycleProbeA` and `cycleProbeB`) importing each other makes the guard fail with
both new cyclic edges. The temporary files were removed; `./gradlew check --console=plain` then passed
(86 actionable tasks: 9 executed, 77 up-to-date), and `scripts/check-docs.sh` passed (199 paths in 6 files).
Configuration caching stored and reused the guard successfully.

The scan groups immediate child packages beneath each module's common `net.nevinsky.abyssus` package prefix;
root helpers and foreign packages are outside that grouping. It checks main Kotlin import lines separately per
module, without loading application code or resolving dependencies.

Scope conflict found before stage 1: the all-module allowlist also contains existing `gdx-model` (`core` ↔ `lib`),
`runtime` (`ecs` ↔ `schema`), and Control Line (`flight` ↔ `flow`) cycles. Task 1.4 requires an entirely empty
allowlist, but those refactors are outside the change's editing-engine scope. Stage 1 awaits a decision on retaining
those baseline entries while emptying the plugin entries, or widening the refactor scope.

Baseline: `./gradlew check --console=plain`.
Counts and edges: scan `src/main/kotlin/**/*.kt`, count files/lines by declared package, then count internal `import net.nevinsky.abyssus.*` lines by source and target package.
Scene layout sites: `rg -l 'SceneEcsPaths|"ecs"|"components"' src/main/kotlin`.
Overlap list: `rg -l 'sceneview|RaySceneSnapshot|ComponentEditor|PanelState' openspec/changes/*/tasks.md`.
