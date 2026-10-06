# Design

## Context

Read `proposal.md` for S1–S6. Constraints that decide the shape:
- Every document edit stays on `editSceneJson`: one undoable command, formatting and number text kept. The module
  split must not add a second writer. What moves is the *mutation of a JSON tree* (pure functions on `JsonNode`);
  the `WriteCommandAction` shell stays in the plugin.
- Libraries stay plain JVM wired by constructors, no `object`/`companion object` (the `checkNoSingletons` rule applies
  to `editor-core` too).
- libGDX calls only inside `GdxRuntime.withContext` on the canvas thread: `GdxRuntime` and the per-canvas context stay
  where the canvas is (plugin, or `editor-render` if stage 4 happens).
- The plugin is the only module that may import `com.intellij.*`.

## Goals / Non-Goals

**Goals:** a module graph where the IDE dependency is an arrow in the build file, not a convention; no package cycles;
one place that knows the scene JSON layout; headless tests for editing logic; a smaller plugin.
**Non-goals:** new features; changing what is written to files; micro-optimizing build time (the faster incremental
build is a side effect, not a target).

## Decisions

### D1. Target module graph

```
gdx-model <- core <- runtime <- physics
                 \       \
                  \       +-- editor-core <- plugin (root)  <- physics-plugin
raytracing ---------------/        ^
                                   +-- editor-render (stage 4, optional)
```

`editor-core` depends on `core`, `runtime`, `raytracing`, `gdx-model` (for placements and the ray bridge's asset
types) and Jackson. It never depends on the plugin. `physics-plugin` stays compile-only on `runtime`; it gains
compile-only on `editor-core` only for the overlay types it already uses.

Native admission remains in `core.format`: `AbyssusDocumentFormat`, `DocumentKind`, `FormatProblem`,
`FormatRejection` and `UnsupportedDocumentFormat` are shared by core filesystem loaders, runtime ECS boundaries,
the plugin and the headless API. The completed `share-native-document-validation` change established this ownership.
Moving the implementation to `editor-core` would make `core` and `runtime` depend on their consumer and create
module cycles. Keep the implementation and its core tests in `core`; move the existing editor-facing type aliases
to `editor.document` with the parsing consumers. Aliases continue to name the same shared types; validation rules
and rejection reasons stay unchanged. Neither `core` nor `runtime` depends on `editor-core`.

### D2. What goes where

| Module / package | Contents (current location) |
|---|---|
| `core` `…core.format` (retained) | `AbyssusDocumentFormat`, document/rejection types and core validation tests |
| `editor-core` `…editor.document` | `SceneJson`, `JsonFormat`, editor-facing format type aliases, `DocumentParsing`, `AssetMetaReader`, `SceneDocument` (new, stage 2) |
| `…editor.components` | `ComponentCodec`, built-in kinds, `SchemaCodec`, `ComponentEditor`, `ComponentReader`, `LightEntities`, schema merge |
| `…editor.content` | `Vec3`, `Quat`, placements, `SceneContent`, `SceneRenderParams`, `PlacementMapper` |
| `…editor.pick` | `ScenePicker`, `SceneQueries`, `TerrainRestHeight`, `OrbitCamera`, gizmo math (`GizmoDrag`, `GizmoHit`, `GizmoHandles`), `SceneInteraction`, `ScenePreview`, `SceneTransformWriter` |
| `…editor.terrain` | generation, noise, recipe, new-terrain file logic |
| `…editor.meta` | `AssetMetaEditor`, field descriptions, `MetaRows`, `AssetReferenceChoices`, `PanelState` (the part without Swing) |
| `…editor.ray` | `RaySceneSnapshots`, `RayViewFeed`, `RayViewRuntime`, `RayFeasibilityLoop`, `RayModeState`, `RayMaterialOverrides`, `RaySkyBaker`, `RayBackendSelector` and the other plain `Ray*` files |
| plugin (root) | `editSceneJson`, project view pane, tree nodes, actions, tool window factories, dialogs and forms (Swing), `SceneViewPanel`, `SceneFileEditor`, `GuardedGLCanvas`, file types, listeners, `AbyssusCore`, VFS wiring |

Files that mix both (for example `PanelState`, `SceneFileEditor`, `AbyssusProjectViewPane`) are split first inside the
plugin: pure part, then IDE shell. A pure part that still needs a Swing type is not moved.

### D3. Keep the JSON-based editor model; do not merge it with the Ashley engine

Games and Play need typed components and a running engine; the editor needs the document's own key order, unknown
keys, omitted defaults and number text. The two needs conflict, so an `Engine`-backed editor would have to re-solve
round-tripping. The decision is to keep both and add the missing layer between: the typed `SceneDocument` read view
(D4) built on the same component codecs that `runtime` owns, which already guarantees both agree on defaults. Revisit
only if the codec layer grows a second copy of component rules.

**ADR (accepted 2026-10-06, stage 2):** the editor keeps the JSON document as its model; games and Play keep the
Ashley `SceneEngine`; both bind components through the shared `runtime` codecs. The entity layout
(`ecs[.entities].<id>.components`) is defined once, by the private `SceneEcsPaths` in
`editor/document/SceneDocument.kt`: readers go through `SceneDocument` / `EntityView`, writers through
`SceneEntityTree` (address lookup, entity insertion, archetype matching) on a tree that `editSceneJson` has already
admitted. No other main source names `components` or the entity map.

### D4. `SceneDocument` (stage 2)

A small immutable read facade over a parsed `JsonNode` root:
`entities(): List<EntityView>`, `EntityView.component<C>()`, `lookAtTarget(entityId)`, `renderAsset(entityId)`,
`skybox()`. It owns the editor's entity traversal and addressing layout (`ecs`, `entities`, `components`);
the shared validator in `core.format` retains the reserved-field paths it needs for native admission.
`SceneEcsPaths` becomes private to `SceneDocument`.
Writers still mutate the tree directly, but obtain paths from the same class (`SceneDocument.locate(entityId)`), so
the layout has one definition. Tests: build it from the Untitled fixture and compare with `SceneContent` output
before the switch (same entities, same values), then switch callers one at a time.

### D5. Breaking the cycles (stage 1)

- `Vec3`, `Quat`, `PlacementTransform` and the placement records move to `…content` (a leaf package).
- `SceneFileEditor` asks `projectView` for four things (Add Light actions, selection topic, `componentTargetOf`,
  `selectEntityInAbyssusView`). Replace them with `SceneViewHost`, an interface the view receives by constructor and
  the pane implements. The editor does not import `projectView`.
- `properties` reads the view through `SceneFacts` (read-only: current content, selected entity, ray mode); it stops
  importing 20 `sceneview` types.
- `terrain` ↔ `properties`: terrain form code asks `properties` for one helper; move that helper to `terrain`.
- `dto` ↔ `filetype`: `SceneJson` and `JsonFormat` go to `…editor.document`, which `dto` depends on one way.
- Guard: `checkPackageCycles` scans `import` lines per module, builds the package graph at the second path segment, and
  fails on a cycle; an allowlist file lists known cycles during the transition and must be empty at the end of stage 1.

### D6. The shared root package (S5)

Move `AbyssusProjectLayout`, `FileLoader`, `GeometryUtils` and `JsonProcessor` from `net.nevinsky.abyssus.core` to
`net.nevinsky.abyssus.core.io`, including the layout constants. `core.project` contains loaders that use the IO
helpers; placing the constants there would introduce `io → project → io`, so the constants share the IO leaf. After that `net.nevinsky.abyssus.core` itself
exists only in `gdx-model`. Renaming `gdx-model`'s packages is a larger mechanical change (51 importing files) with
a clear gain only if the module is published; recorded as Open Question 1.

### D7. Mechanics of a move

One slice at a time, in this order: document → components → content → pick → terrain → meta → ray. For each slice:
(1) split mixed files inside the plugin, (2) run the existing tests, (3) move main files and their tests with the IDE
rename so imports update, (4) run `./gradlew check`, (5) one commit. A slice that needs a class from a later slice
takes only the interface (placed in the earlier module) and leaves the implementation behind.

Overlap order recorded before extraction (2026-10-06), from
`rg -l 'sceneview|RaySceneSnapshot|ComponentEditor|PanelState' openspec/changes/*/tasks.md`:

| Change | Order relative to stage 3 |
|---|---|
| `repair-ray-editor-regressions` | Lands first; implementation and tasks already complete. |
| `repair-native-test-regressions` | Lands first; implementation and tasks already complete. |
| `add-scene-asset-entities` | Lands first; implementation complete, remaining manual verification stays with that change. |
| `add-scene-raytracing` | Existing implemented tasks land first; remaining work rebases onto extracted paths. |
| `add-scene-raytracing-settings` | Existing implemented tasks land first; remaining work rebases onto extracted paths. |
| `add-project-fps-counter` | Rebases onto extracted paths before implementation. |
| `add-sky-clouds` | Rebases onto extracted paths before implementation. |
| `add-scene-view-antialiasing` | Rebases onto extracted paths before implementation. |
| `add-cloud-scene-lighting` | Rebases onto extracted paths before implementation. |
| `add-realistic-water` | Rebases onto extracted paths before implementation. |
| `merge-physics-into-abyssus` | Rebases onto extracted paths before implementation; extension packaging follows D1. |
| `restructure-editor-modules` | This change owns extraction. |

The scan lists planning artifacts, not separate Git branches: “rebases” means subsequent implementation uses the
new module/package paths. Already implemented work is present in this checkout. Re-scan before stage 3 for newly
overlapping work. `add-remote-asset-library` and `add-weather-preset-creation`, named in the proposal, also adopt the
new paths when implemented even though their task text does not match this scan.

### D8. Testing

Tests that need no IDE fixture move to `editor-core/src/test` and run as plain JUnit; shared fixtures keep coming from
`core`'s `testFixtures` and the existing `abyssus.testData` root. Tests that use `BasePlatformTestCase`, Swing or
the tree stay in the plugin. New: a parity test per slice (same Untitled fixture, same output before and after the
move), `checkPackageCycles`, and a classpath test that `editor-core` has no `com.intellij` class (a resource scan of
its runtime classpath).

The document slice also runs `:core:test` and `:runtime:test` to retain admission at filesystem and raw ECS
boundaries. Its editor parsing tests use the shared validator through the moved aliases; the headless refusal
parity test compares the same rejection reason with the plugin. Verify that the build introduces no dependency
from `core` or `runtime` to `editor-core` and no second validator implementation.

### D9. Stage 4 criteria (`editor-render`)

Extract only when a concrete user exists: a standalone viewer app, GL tests without the IDE test framework, or build
time dominated by the render code. If none holds at that point, record "not needed" and stop.

### D10. Remove pre-existing library and game cycles

The user authorized removing all scanned package cycles (2026-10-06), including the baseline cycles outside the
editing engine. Keep this behavior-preserving: place the Assimp conversion pipeline under `core.assimp` beside its
model consumers; let runtime schemas own reserved built-in names and share numeric JSON spelling through `runtime.json`;
place `FlightReport` with flight and `HandleInput` in input so game flow and flight depend in one direction.
Verify these moves with the existing `:gdx-model:test`, `:runtime:test` and `:games:control-line:test` suites and the
cycle guard with their allowlist entries removed. The end-of-stage-1 allowlist remains entirely empty.

### D11. Messages and singletons in `editor-core` (decided 2026-10-06, before stage 3)

- **Messages.** `AbyssusBundle` is an IntelliJ `DynamicBundle`, so `editor-core` cannot use it. The keys
  `editor-core` uses move to `editor-core/src/main/resources/messages/AbyssusEditorBundle.properties`. `editor-core` code
  takes an injected `EditorMessages` (`message(key, vararg params)`). The plugin supplies `EditorBundle`, a
  `DynamicBundle` over that same resource (the `editor-core` jar is on the plugin classloader); headless callers use
  `ResourceEditorMessages`, a plain `ResourceBundle` over it. Both read one file, so the plugin and a headless
  caller give the same reason text. The resource is not named `EditorBundle`: the platform ships a
  `messages.EditorBundle`, and a second file of that name shadows it on a shared test classpath. IDE-only text stays in `AbyssusBundle.properties`.
- **Singletons.** `checkNoSingletons` applies to `editor-core`. Behavior singletons (`object X` with functions,
  companion factories such as `SceneContent.of`) become classes wired by constructor or created at the call site,
  in commits before the slice's move. Pure constant holders (a companion or object with only `const val`s or an
  `EMPTY` value) may be listed in `abyssusSingletonExcludes`.

## Risks / Trade-offs

- **Churn and merge conflicts** with every open change touching `sceneview/`. Mitigation: order (land or rebase first),
  slice commits, mechanical IDE renames, no logic edits inside a move commit.
- **A pure part that secretly needs the IDE** (a `Disposable` in a field, a `Project` argument). Mitigation: split
  inside the plugin first; a slice that needs more than a small interface is deferred and noted.
- **Rename of packages loses `git blame` continuity.** Use `git mv` and keep moves separate from edits.
- **Interface growth.** `SceneViewHost` and `SceneFacts` can become a second god object. Keep each under about six
  methods; split by consumer if they grow.
- **Extra Gradle module cost** (configuration, publishing the plugin zip with another jar). The plugin zip already
  bundles `runtime`/`core`; `editor-core` joins them, and `physics-plugin` must not bundle it (it takes it from
  Abyssus's classloader, as it does `runtime` today).
- **Over-engineering risk.** Stage 4 and the `SceneDocument` facade are the two parts that could be too much; both
  have an explicit stop condition.

## Migration Plan

Per slice as in D7. Rollback is reverting the slice's commit; no data or file-format migration is involved.

## Open Questions

1. Rename `gdx-model` root packages (`core.mesh` → `model.mesh` and so on)? Default: not now.
2. Module name `editor-core` vs `scene-editing`? Default: `editor-core`.
3. Does the Play host or the Control Line game want `editor-core` (for example to validate a scene on load)? If yes,
   it argues for stage 2 being earlier. Default: no.
