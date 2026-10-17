# Proposal

## Why

The `lib-render` and `lib-core-editor` extractions landed, but they left near-copies behind: the plugin re-implements
shadow math, depth submission and GL state handling that now lives in `lib-render`, `lib-core`'s loaders repeat the
same `prepare`/`discard` boilerplate seven and ten times, and the same small helpers (SHA-256 hex, native asset-meta
maps, document header and field reads, number formatting, angle wrap) are written again wherever they are needed. Several classes
have also grown past a single responsibility (`AssetStorage` 1094 lines, `SceneRenderer` 967, `SceneViewPanel` 725,
`PhysicsWorld` 700). Each copy is a place a future change can miss, and the shader light/unit budgets are already
declared independently in four modules (`lib-core`, `lib-render`, `lib-core-editor` and the plugin), where they can silently drift and change rendering.

## What Changes

- **R1. Share what `lib-render` already owns.** Replace the plugin's inline `sunShadowBias` and the duplicated
  shadow-camera up-vector/texel-snapping idiom with the `lib-render` helpers, keep the plugin-only receiver+caster
  fit; delete the plugin's duplicate `ShadowLightKind` and the `AtlasLightKind.valueOf(kind.name)` string bridge;
  route water's depth pass through `DepthSubmission`; extract one Template-Method "render into a GL target" used by
  `ShadowResources` and `WaterResources`.
- **R2. Collapse lib-core loader boilerplate.** One Template-Method loader base implements `prepare` and provides a
  default `discard`; the two foliage loaders become one loader parameterized by a read policy (Strategy).
- **R3. One home for the shared small helpers.** SHA-256 hex, native asset-`meta.json` construction through one typed
  `AssetMetaDocument` DTO (no hand-built `ObjectNode` trees), typed document reads (DTO in, DTO out; the tree only on
  the lossless edit paths, design D9), number-text formatting, angle wrap/azimuth, the white/pixel texture factory, shader face keys and the
  duplicated GL constants move to a single definition and are imported everywhere.
- **R4. One shader-budget contract.** The directional/point/spot and texture-unit budgets that `TerrainPass`,
  `WaterDrawable`, `TerrainMesh`, `LightProfile` and `SceneLighting` currently declare independently come from one
  contract defined in `lib-core`, so they cannot drift. Any real mismatch found is fixed deliberately with GL parity evidence.
- **R5. Split the overloaded classes.** `AssetStorage`, `SceneRenderer`, `SceneViewPanel`, `PhysicsWorld`,
  `SceneInteraction`, `FlightGearImport`, and the `UiCanvasView`/`UiEditorController` pair lose their secondary
  responsibilities to constructor-wired collaborators; behavior and public APIs are preserved.
- **R6. Restore the project's own boundaries.** Service lookups move out of domain classes and back to IntelliJ entry
  points; `AssetLoading` exposes one narrow preview interface instead of four public `val`s; asset-kind strings become
  `MetaType`; the `GeometryUtils` and empty `SceneEnvironment` indirections are removed (and `GeometryUtils`'s
  `checkNoSingletons` exclusion goes with it).
- Resource defects met on the way are fixed in place: the leaked `Pixmap` in `WaterDrawable`'s blank texture and the
  three independent VFS-change listeners that should be one.

**No `**BREAKING**` user-facing change.** Every public API, Gradle task, plugin id and rendered output is preserved;
this is a structural refactor.

Out of scope: the architecture duplication owned by `reduce-architecture-duplication` (shared shader source, the
`lib-render` terrain pass, the tree refresh coordinator, asset-storage revision extraction), the `L1` test-only
Ashley code and the `L8` `DtoEntry.equals` tree-refresh question from `docs/reviews/design-review-2026-10.md`, new
features, new backends, and any change to what a scene looks like on screen.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a code-level refactor with no spec-level behavior change, so `.openspec.yaml` sets `skip_specs: true`.
`openspec/specs/` continues to describe the same required behavior; the tasks below assert it by re-running the
existing tests and GL parity checks.

Native documents: this change reads and writes **no** new or changed `.abss`, `.scene` or `meta.json` fields. It adds
no format version, no migration and no write path: existing edits still go through `editSceneJson`, and document
validation stays exactly `AbyssusDocumentFormat` with `format: "abyssus"` and integral `formatVersion: 1`. Where a
helper is shared, it preserves the current number text, key order, omitted defaults and unknown-payload rules. Moving
writers from `ObjectNode` building to DTO mapping must leave every produced file byte-identical.

## Impact

- `lib-core`: the loader hierarchy and `AssetStorage`; the shared helpers (SHA-256, blank texture, GL state,
  framebuffer checks); the shader-budget contract; the `GeometryUtils`/`SceneEnvironment` removals.
- `lib-core-editor`: shared number-text and asset-meta helpers, the typed read seam, and the
  `FlightGearImport`/`SceneInteraction` splits.
- `lib-render` and `lib-gdx`: the shadow helpers and the `ShadowLightKind` enum become the single source the plugin
  and the game both use.
- `plugin-abyssus`: `sceneview` (shadows, water), `projectView`, `properties`, `gameui`, `foliage`, `dto`; the
  `AssetStorage` collaborators, the `service<…>` call sites, and the action/selection boilerplate.
- `app-game-control-line` / `lib-physics`: the game-local angle/azimuth helper and atomic JSON store, the `PhysicsWorld` split and the Jolt sub-shape builder.
- Docs: `docs/ai/architecture.md`, the affected package/module `README.md`s and `AGENTS.md` where a class moves or a
  helper gets a new home; `docs/reviews/design-review-2026-10.md` gains a status line for the findings this closes.
- Verification: existing unit tests are the acceptance gate, plus `-Dabyssus.glTests=true` for the shadow/water/terrain
  renderer refactors and `./gradlew check -Pabyssus.requireShaders=true` at the end.
