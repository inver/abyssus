# Design

## Context

See proposal.md - Why. The evidence for each duplication, with file and line, is in the exploration recorded for this
change and re-checked at the proposal revision; the load-bearing ones are: seven identical loader `prepare` bodies and
ten identical `discard(model: Unit) = Unit` overrides in `lib-core`; an inline copy of `lib-render`'s
`sunShadowBias` in `SceneShadows.kt:113`; two `ShadowLightKind` enums bridged by `valueOf(kind.name)`; and the shader
light/unit budgets ("5" and "2") declared in `lib-render`, `lib-core` and `lib-core-editor` independently.

Constraints that shape the approach:

- The module boundaries in `AGENTS.md` are hard gates: `lib-core`, `lib-core-editor`, `lib-render` and `lib-physics`
  stay plain JVM, constructor-wired, and free of IntelliJ/Swing/AWT; `lib-core` and `lib-physics` have no behavioral
  `object`/`companion object` (`checkNoSingletons`); there are no package cycles.
- `docs/reviews/design-review-2026-10.md` already records what a previous refactor fixed; a finding closed here is
  marked there rather than re-litigated.
- `reduce-architecture-duplication` owns the shared shader source, the `lib-render` terrain pass and the tree
  refresh; this change must not re-open those.
- Document reads already funnel through `SceneJson` (the `NativeJsonTree` parser) plus `AbyssusDocumentFormat`;
  `DocumentParsing` binds a `SceneDto`. No new parser may be introduced.

## Goals / Non-Goals

**Goals:**

- One definition per repeated algorithm or constant, in the module that owns it.
- The two rules this refactor protects: the shader light/unit budget contract, and the native-document read path
  (validation before binding, number text and key order preserved).
- Splits that make a class testable without the IDE/GL, keeping every public API and rendered output unchanged.

**Non-Goals:**

- Behavior, appearance or format changes; new features; backends; the `reduce-architecture-duplication` scope; the
  `L1`/`L8` review leftovers; a generic plugin framework or service container.

## Decisions

### D1. Where a shared helper lives

Three homes, chosen by dependency direction, not convenience:

| Kind of helper | Home | Why |
|---|---|---|
| Editor logic later used by the editor or the plugin (number text, angle wrap, azimuth, atomic JSON store, asset-meta builder, SHA-256 hex) | `lib-core-editor` (`util`/`document`) | GL-free and Swing-free, so it is unit-testable headless; the plugin already depends on it |
| Runtime rendering primitives and the light budget (shadow helpers, GL target renderer, light packing) | `lib-render` | Both the plugin and the game already depend on it; `lib-core` must not depend back on it |
| Values that describe a shader ABI shared with `lib-gdx` (`ShadowLightKind`, face keys) | `lib-gdx` (the enum/attribute that already declares them) | The enum already lives there; the plugin imports it, the game does not need a second copy |

Helpers that pure math or JSON needs and `lib-core` owns stay in `lib-core`. Nothing is moved "up" into the plugin.

### D2. The validated-tree read seam

The ~15 plugin sites do `SceneJson().parse(text).also { AbyssusDocumentFormat().requireSupported(it, kind) }`. They are
not interchangeable with `DocumentParsing.parse` (which binds `SceneDto`) and must keep the tree for number text and
key order. So add one small seam, alongside `DocumentParsing`, that does exactly parse + validate and returns the
`JsonNode` (and the existing `runCatchingKeepingCancellation { … }.getOrNull()` swallow form). It reuses
`SceneJson`/`NativeJsonTree` and `AbyssusDocumentFormat`; it does **not** introduce a parser and does **not** replace
`DocumentParsing` for the DTO path. Alternative rejected: redirecting all sites into `DocumentParsing` - it would lose
the tree and change write behavior.

### D3. Loaders: Template Method plus one Strategy

`AssetLoader.discard` gets a default `{}`, so the ten no-op overrides go. A package-internal base implements
`prepare(name) = metaLoader.loadBaseMeta(name)?.let(::loadPrepared)` once and leaves `loadPrepared` abstract; the seven
loaders keep only their real work. `FoliageLoader` and `RuntimeFoliageLoader` collapse to one loader taking a read
policy (Strategy), matching the existing `FoliageRuntimeLoad`. This is package-internal; `AssetLoader`'s public shape
and `AssetStorage`'s contract do not change.

### D4. One shader-budget contract

The array sizes and texture-unit numbers are a contract between the bundled GLSL and the Kotlin that binds it. Define
them once in the module that owns the shader contract (`lib-render` for the light profile; `lib-gdx` for the atlas
attribute) and derive `WaterDrawable`, `TerrainMesh`, `RenderLights` and `SceneLighting` limits from it.

Per-kind maxima and array capacity are different quantities. `TerrainPass.MAX` is the capacity of each of the three
uploaded light arrays (the editor fragment shader declares all three as `[5]`), so it is the maximum of the profile's
per-kind maxima (2, 5, 5) - not a directional budget; 5 beside `maxDirectional = 2` is consistent, and a smaller
capacity could not carry the profile's five point or spot lights. Derive the capacity as that maximum and assert it
against the shader's declared array sizes. The water shader declares its own directional array (`[5]`), so
`WaterDrawable.MAX_DIRECTIONAL` is a shader capacity, not a copy of the profile's directional maximum; the game terrain
variant has no light arrays (one sun uniform), so no capacity follows from its profile. Where a corrected value does
change what a shader sees, the correction is deliberate and proven by the GL parity run, never silent: this is the one
place the change may alter behavior.

### D5. GL target rendering as a Template Method

`ShadowResources.render` and `WaterResources.render` become one `GlRenderTarget.render(clear, block)` with the
viewport/clear overridable and `ShadowGlState` doing save/restore; `CloudShadowMap` uses the same state helper. Every
GL call stays on the AWT render thread inside `GdxRuntime.withContext`, guarded by `GuardedGLCanvas.glSafe`; the pure
parts (frustum test, clear choice) stay outside.

### D6. How far each class splits

Splits are by existing seam, and each extract is constructor-wired with the old class keeping its public API as a thin
facade:

- `AssetStorage` → off-GL executor factory, thread/call-depth guard, dependency resolver, hook registry; the pipeline
  stays and keeps owning upload/build/discard and notifications. No GL moves.
- `SceneRenderer` → a content/environment pass and a sky pass over the already-extracted `SceneViewState`; snapshot
  packing moves with the pass that reads it.
- `SceneViewPanel` → toolbar construction and a GL-lifecycle/frame-driver delegate.
- `PhysicsWorld` → body factory, stepper, pose writer (Jolt stays in this module).
- `SceneInteraction` → a `sealed Gesture` state plus a per-branch ray helper (this is the KISS half of the split).
- `FlightGearImport` → collection, geometry, staging collaborators; it reuses the shared asset-meta builder.
- `UiCanvasView`/`UiEditorController` → a document/selection model, a gesture controller, and a thin view.

Each split names the pure classes it produces, and those stay free of Swing/GL/platform types so a headless test can
drive them.

### D7. Service lookup and boundaries

`service<…>()` calls inside domain classes are replaced by constructor parameters, resolved at IntelliJ entry points
(`AnAction`, `FileEditorProvider`, `ToolWindowFactory`, `@Service` constructors). Scope is the call sites enumerated
by the analysis; it is not a blanket rewrite. `AssetLoading`'s public `decoder`/`hdrFiles`/`toneCurve`/`hdrPreview`
become one `HdrPreviewService`. Asset-kind strings become `MetaType`. `GeometryUtils`'s helper becomes a top-level
function and its `checkNoSingletons` exclusion is removed; empty `SceneEnvironment` is deleted.

### D8. Sequencing and verification

Mechanical DRY first (lowest risk, existing tests cover it), then the renderer helpers (GL parity required), then the
class splits, then the boundary work. Every phase keeps a green `./gradlew check` at the commit boundary. GL-dependent
steps are additionally gated on `-Dabyssus.glTests=true` on a display; a skipped GL test is never counted as passing.

## Risks / Trade-offs

- A shared helper changes an edge case the copies disagreed on (bias, color channel defaults, number text) → land the
  helper with a test that pins the retained value; keep a deliberate behavior fix separate from the mechanical moves.
- The D4 budget unification changes what a shader sees → prove every editor/game terrain and shadow case with the GL
  parity run before the old constants are deleted.
- Class splits touch large, GL-heavy files → split behind the existing public API in revertible commits, with the
  DLL/GL tests as the gate; the extracted pure classes are unit-tested headless first.
- `service<…>` removal changes construction order at startup → change one entry point at a time and keep
  `AbyssusCore` the single composition root.
- Broad refactor risks a review that cannot diff → keep tasks grouped by module and by the risk level above, so each
  group is independently reviewable and revertible.

## Migration Plan

1. R3 mechanical helpers and R6 removals; R2 loader base. Existing unit tests are the gate.
2. R1 shadow/water/GL-target helpers; R4 budget contract; GL parity on a display.
3. R5 class splits, one class per commit, tests unchanged between them.
4. R6 service-lookup and `MetaType` boundary work.
5. Docs (`docs/ai/architecture.md`, package/module READMEs, `AGENTS.md`, the review status) and
   `./gradlew check -Pabyssus.requireShaders=true`.

Rollback is per commit: every step is a local edit or an additive helper, and no native document is rewritten, so
reverting a step needs no migration.

## Open Questions

- Whether the `AssetStorage` split can take the off-GL executor factory out without touching the reflection-based
  creation on the current JDK - resolved when the split is implemented, and it does not change the task breakdown.
