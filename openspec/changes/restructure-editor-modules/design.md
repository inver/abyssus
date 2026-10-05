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

### D2. What goes where

| Module / package | Contents (current location) |
|---|---|
| `editor-core` `…editor.document` | `SceneJson`, `JsonFormat`, `AbyssusDocumentFormat`, `DocumentParsing`, `AssetMetaReader`, `SceneDocument` (new, stage 2) |
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

### D4. `SceneDocument` (stage 2)

A small immutable read facade over a parsed `JsonNode` root:
`entities(): List<EntityView>`, `EntityView.component<C>()`, `lookAtTarget(entityId)`, `renderAsset(entityId)`,
`skybox()`. It is the only owner of the strings `ecs`, `entities`, `components`. `SceneEcsPaths` becomes private to it.
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
`net.nevinsky.abyssus.core.io` (and `…core.project` for the layout). After that `net.nevinsky.abyssus.core` itself
exists only in `gdx-model`. Renaming `gdx-model`'s packages is a larger mechanical change (51 importing files) with
a clear gain only if the module is published; recorded as Open Question 1.

### D7. Mechanics of a move

One slice at a time, in this order: document → components → content → pick → terrain → meta → ray. For each slice:
(1) split mixed files inside the plugin, (2) run the existing tests, (3) move main files and their tests with the IDE
rename so imports update, (4) run `./gradlew check`, (5) one commit. A slice that needs a class from a later slice
takes only the interface (placed in the earlier module) and leaves the implementation behind.

### D8. Testing

Tests that need no IDE fixture move to `editor-core/src/test` and run as plain JUnit; shared fixtures keep coming from
`core`'s `testFixtures` and the existing `abyssus.testData` root. Tests that use `BasePlatformTestCase`, Swing or
the tree stay in the plugin. New: a parity test per slice (same Untitled fixture, same output before and after the
move), `checkPackageCycles`, and a classpath test that `editor-core` has no `com.intellij` class (a resource scan of
its runtime classpath).

### D9. Stage 4 criteria (`editor-render`)

Extract only when a concrete user exists: a standalone viewer app, GL tests without the IDE test framework, or build
time dominated by the render code. If none holds at that point, record "not needed" and stop.

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
