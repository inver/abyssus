# Design

## Project understanding

See proposal.md for motivation and scope. Assessment date: 2026-10-10. The source checkout is the baseline;
this review does not assume that archived tasks prove behavior.

The seven modules in `settings.gradle.kts` are `lib-gdx`, `lib-core`, `lib-raytracing`, `lib-core-editor`,
`lib-physics`, `plugin-abyssus` and `app-game-control-line`. There is no `lib-runtime` or separate physics plugin.
Runtime ECS loading/registration is in `lib-core`; `EcsWriter` and editing codecs are in `lib-core-editor`.
`JsonProcessor` binds components through Jackson and discards unknown modeled fields today. The earlier plan's
`BUILT_IN_COMPONENTS`, injected folder resolver and carried source-node serializers are not current implementations.

The editor renders on AWT/EDT inside `GdxRuntime.withContext` and safe canvas frames. Games render on the LWJGL3 GL
thread. `lib-physics` runs Jolt only in a game or the bundled child play process. The main plugin bundles its plain
physics classes without their transitive dependencies; Jolt jars/natives are under `play-host/`, outside IDE `lib/`.

`FoliageLoader` is already registered by `BaseCtx` and plugin asset loading; Control Line's separate `fieldAssets`
map lacks it. `FoliageMatrices.copy` is already the shared pure placement math. `FoliageDrawable` owns mutable instance
buffers, camera visibility and transforms; an asset-storage drawable cannot be shared concurrently by two terrain
placements. Its OBJECT shadow renderables reflect the last visibility fill.

The current `Field.scene` has 52 broadleaf, 40 young and 33 acacia trees, 48 low and 42 tall bushes. Terrain `0` is
300 m square at (-150, 0, -150); terrain `300` is 600 m square at (-300, 0, -300). Splat metadata and its generator
identify R dirt (including paths), G asphalt, B ballast and A concrete. These are blend weights, not a grass-only
semantic label. The committed source tools include older generators with stale imports, so new tools must compile
independently of those unrelated sources.

## Scope, assumptions, and questions

Sampled paths: component load → engine → editor writer; foliage meta/mask/bake prepare → dependency loading → instanced
draw → disposal; physics CPU asset read → shape/body allocation → contacts/removal/close; field scene → game color/depth
passes; environment placement/splat generators → committed field content. Inspected root/module Gradle files, plugin
registration, versions, CI, current specs and all eight change artifacts. Main-spec contradictions unrelated to foliage
and general plugin action/thread debt are outside this change.

Configured contract: Java 21, Kotlin 2.4.20, libGDX 1.14.2, LWJGL 3.4.3, Ashley 1.7.4, Jolt JNI 6.1.1,
IntelliJ Platform Gradle Plugin 2.19.0, compile IDE IC 2025.2.4, since-build 252 without an upper bound.
The descriptor has dynamic extension points; no explicit restart-only policy is declared. This change adds no new
extension point or native loading in the IDE and does not claim dynamic-unload compatibility from those declarations.

Desktop delivery covers Linux/Windows/macOS natives in configured builds; minimum GPUs, target device, team size and
deadline are unknown. The user left performance hardware/target unspecified. Establish measurements and record a
budget before tuning grass, rather than choosing a measured-looking FPS claim. Estimates below assume one experienced
Kotlin/game developer and available supported Jolt SDK sources; they are planning ranges, not commitments.

Checks performed for this review: fresh source/spec/build inspection, current field entity/count extraction, splat
channel verification from metadata/generator, and OpenSpec structure comparison. Prior `add-terrain-foliage` automated
check results are reused evidence only; no game/GL/native benchmark, sandbox UI, Plugin Verifier or implementation test
was run for these planning edits. Seven manual predecessor checks remain open in its archive.

## Assessment

| Area | Evidence and conclusion | Confidence |
|---|---|---|
| Architecture and modules | Observed: runtime ownership moved into core; writer remains editor-core. Reuse constructor-wired classes and avoid reverse dependencies or replacing the ECS architecture. | High |
| Rendering and API abstraction | Observed: game color/depth passes use existing OpenGL shaders and instancing. Mutable asset drawables require per-placement state; no demonstrated need for backend migration or a render graph. | High |
| Shaders, materials, passes, GPU | Observed: OBJECT/DETAIL renderables and instanced depth variants exist. Inference: reusing viewer-camera visibility for shadows can omit off-screen casters; grass adds alpha-test overdraw. Correct pass visibility and measure cost. | High for state ownership; performance unmeasured |
| Editor and content workflow | Observed: Apply presently expects a completed scatter preview and includes a derived bake transaction. Collider-only edits need a metadata-only path to satisfy unchanged-bake behavior, including stale/missing bakes and unsaved buffers. | High |
| CPU/GPU profiling | No representative foliage-game timings or hardware budget supplied. Existing vegetation models are relatively large per the environment docs; copy count alone is not a GPU budget. Baseline precedes density tuning. | Insufficient measurement |
| Builds, platforms, delivery | Observed: physics host already bundled; actual task paths differ from old plan. Tools source set is excluded from ordinary check and some legacy imports are stale. Compile/test the new tool explicitly; preserve packaging guardrails. | High |
| Quality and maintainability | Observed: source-number preservation is in editor `SceneJson`, not ordinary core binding. Runtime raw source retention, native failure cleanup, per-placement drawing and scene-context Undo need explicit test seams. | High |
| Debt, risk, scalability | Observed: PhysicsSystem has a 10,240-body capacity; logical copy cap does not bound chunk-body count. Inference: rotated copies under non-uniform entity scaling can produce shear, which rigid primitive poses cannot exactly represent. Refuse unsupported transforms and admission failures explicitly. | High |

## Goals / Non-Goals

Goals: reuse the existing render/physics boundaries; retain native source fidelity; share exact accepted copy placement;
ship the bake without a generator dependency; make content conversion repeatable and reversible. Non-goals are in the
proposal. Static collision captures terrain/copy state at world construction; changing an owner's pose while the world
runs is outside this change, and moving rigid-body owners receive no foliage collision.

## Decisions

### 1. Register the component in core; preserve source without a folder scan in deserialization

Add `FoliageComponent` to `ComponentRegistry` with both short and qualified-name admission. Capture its original
object and bound asset name at the component binding boundary, and serialize a copy of that source with only a changed
`assetName` patched. Keep absent/null values and opaque extension members distinct; an invalid modeled value remains
raw with the existing one-warning behavior. Do not widen generic game component codecs.

Extract the lossless tree-reading primitive behind `SceneJson` into a constructor-wired core utility and reuse it at
runtime scene parsing, retaining the editor facade. Add preservation tests for `2.50`, `-0.0`, exponent spelling,
unknown nested objects and key order. No core → editor dependency. `EcsWriter` stays in editor-core; core must expose
source-aware component serialization through its existing binding abstraction for that writer to consume.

A new pure core listing class accepts the engine and an injected warning sink; names come from `RenderComponent.type`
and `assetName`, without disk access or a new asset resolver. Project-folder/kind checks happen in the runtime asset
read phase. Separate warning ownership prevents load, listing and frame loops from logging the same error repeatedly.
Alternative: performing filesystem checks in a component deserializer was rejected because it adds hidden I/O and a
resolver that no longer exists.

### 2. Share CPU preparation and runtime bake policy, not generator code

Reuse `FoliageMatrices` as-is; do not introduce a duplicate transform class or needless extraction. A core read policy
shared by game preparation and `PhysicsAssets` returns immutable prepared foliage plus diagnostics, after native
admission. The editor retains its current regeneration policy. Runtime readers never call `FoliageScatter`.

ABFO v1 stores layer/model indices, x/z, yaw and uniform scale, not model folder identities or height/tilt. A matching
bake can agree with the committed editor view. A stale bake retains its saved copies under CURRENT heights, alignment
and valid model-index mappings; it cannot promise the editor's regenerated distribution or historical models. Drop
unresolvable indices/removed layers and warn. Failed mask/fingerprint checking marks a readable bake unverifiable,
with a warning, rather than dropping its usable copies. Terrain/meta failures still prevent placement. Warnings are
once per loaded asset revision, not per frame. Runtime drawing and collision use the same readable snapshot.

### 3. Collider metadata is neutral and tolerant; collider-only Apply is a document command

Add a core-owned collider payload/reader for BOX/SPHERE/CAPSULE, with defaults matching `ColliderComponent` and
finite positive active sizes plus finite offsets. Retain raw nested payload so an unknown shape or malformed collider
is refused separately from layer/model binding. No core import of physics or Jolt. Share this reader with editor
validation and physics; editor labels/reasons use the existing injected message bundles.

OBJECT controls expose None/Box/Sphere/Capsule. Imported DETAIL colliders are ignored in physics with a warning; the
editor offers no collider controls on DETAIL and does not silently erase unknown payload. Changing OBJECT → DETAIL
with an existing collider requires clearing the incompatible collider explicitly, with validation feedback.

Classify the full diff: collider-only Apply uses `FoliageMetaEdits` and `editSceneJson`, preserves bake/mask bytes and
mtime, never scatters, and works with stale/missing bakes if the metadata can be edited. Mixed generation edits retain
the existing preview and composite binary/document transaction. Unknown nested collider fields survive edits to known
fields; None removes only collider. Native admission, writability and captured document revision are rechecked before
the command. Cancel/disposal/external changes invalidate pending results. An existing properties controller owns this
work; no new broad service is necessary. Alternatives: always rebaking or changing the fingerprint are rejected.

### 4. Build bounded, owned compounds; report contacts using the terrain entity

Use one static body per nonempty (entity, layer, chunk) and one primitive sub-shape per accepted copy. Use
`FoliageMatrices` for the affine copy matrix; collider offset is in model units and is transformed with the full matrix.
Validate all entries before native allocation. Orthogonal, finite, positive-determinant transforms can be decomposed
into rigid pose and positive scale. Refuse singular, reflected or sheared transforms with a layer warning, rather than
misplacing a collider. Spheres/capsules use the largest decomposed scale axis and warn for non-uniform scale.
Terrain owners that declare DYNAMIC/KINEMATIC motion are refused; foliage bodies are not updated or moved later.

Admit whole layers in numeric entity-id order, then metadata layer order: enforce 200,000 colliding copies and reserve
chunk bodies within current PhysicsSystem capacity after ordinary entities. Native creation failure rolls back the
layer before admitting the next one. Do not increase the world's existing limits as an incidental change. Guard the
synthetic 300,000-copy test before JNI allocation.

Track foliage body IDs separately from the single ordinary body per entity. A contact lookup maps every foliage body
to its owning terrain entity, while preserving relative-speed/first-contact reporting; removal must not early-return
when an owner has foliage bodies but no ordinary collider. Stage native resources with explicit ownership transfer;
failed groups, removed owners and close destroy bodies and release owned shapes/references exactly once. Exercise
build failure and repeated worlds, not only successful close. Alternative per-copy bodies is rejected on broadphase
cost; silently clipping partial layers is rejected on determinism and clear warnings.

### 5. Rendering owns one drawable per terrain placement and separates pass visibility

Register `FoliageLoader` in Control Line's custom `fieldAssets` map. Load completion treats Ready and failed optional
foliage as terminal; a missing foliage asset must not trap the game's loading screen. The storage owns its built asset
and shared models; the renderer owns independent placement drawables constructed from immutable preparation/dependencies.
Release placement drawables before shared asset storage on scene replacement/disposal, without double disposal.

Fill color instances for the game camera. Fill OBJECT casters for the light pass independently, so a tree outside the
viewer frustum still casts onto visible ground; DETAIL never enters the depth pass. Restore color visibility/buffers
before color rendering. Reuse existing shaders and safe GL state restoration; do not create a second graphics backend.
Verify two placements of one asset, scene replacement and different light/view frusta. An automated rendered image and
GL error check prove correctness; CPU wall time around a draw is not GPU execution time.

### 6. Migrate from an immutable source manifest, with bounded and explicit output

Record current vegetation ids, models, transforms, per-species totals, source scene hash and terrain assignment in a
committed manifest under `project/environment/`. A seeded `FieldFoliage` tool uses that manifest on EVERY run. Source
hash validation applies to the original vegetation subset/unchanged flight data, not the whole already-migrated scene;
refuse conflicting edits instead of rebuilding from an empty scene.

Map world positions through the inverse TERRAIN matrix; assign copies over the site square to site, outside it to
outer. Site and outer layer counts sum to the recorded species total. Clear outer MASK TEXEL SUPPORT under the site,
not only texel centers; verify actual generated copies because interpolation may leak at boundaries. Mask zeroing
uses transformed model crown bounds and the 28 m radius. Fit density with fixed bounds, deterministic tie-breaking,
and a maximum iteration count; fail if ±25% cannot be achieved, never publish partial outputs.

Generate the three-crossed-quad alpha-tested grass model/texture through existing native writers with fixed UUIDs,
provenance, seeds and timestamps. Sample the documented sequential splat blend at terrain-local coordinates and zero
hard/worn regions using recorded thresholds. R includes dirt paths; no scratch-test deletion or unresolved semantic
channel question is required. Verify actual grass footprints stay off the 25 m pad and document threshold limitations.

Compile the new tools in an isolated tools source set with editor-core only on its tool classpath, since ordinary
check excludes generators and legacy tools have stale imports. Stage generated files, validate bakes/copy budgets and
source state, then apply scene mutation with `HeadlessEditing`. Allow a supplied temporary project output directory;
compare complete manifests/checksums after two runs, not global `git status` in an already-dirty worktree.
Integrate runtime rendering before rewriting shipped content. Retain downloaded vegetation model provenance/assets.

### 7. Execution contexts and platform contract

| Piece | Owner/context | Lifetime and validation |
|---|---|---|
| Core component/listing/lossless JSON | Caller; CPU only | Scene load; no file I/O in listing or generator/native code |
| Core foliage prepare | Asset worker; CPU only | Immutable snapshot, native admission and bounded diagnostics |
| Game drawable upload/color/depth/dispose | LWJGL3 GL thread | Per placement; no GL before context or after shared models dispose |
| Physics asset read/build/step/removal | Game/play-host world thread | Independent world snapshot; cancellation/failure cleanup |
| IDE collider controls | EDT | Properties view/controller; no native Jolt or new per-frame scans |
| IDE preparation | Existing owned worker | Snapshot current unsaved text under required model-read access; bounded work |
| IDE metadata write/publication | Write-safe EDT command | `editSceneJson`; native/revision/writability recheck; stale/disposed result discarded |
| Migration tool | Standalone CPU JVM | Explicit output; GL-free; canceled/failed staging never changes authored files |

The configured 252 baseline remains. Consult attached SDK before selecting new APIs. The general scheduling/ownership
requirements are supported by JetBrains [threading](https://plugins.jetbrains.com/docs/intellij/threading-model.html),
[service coroutine scopes](https://plugins.jetbrains.com/docs/intellij/coroutine-scopes.html) and
[disposal guidance](https://plugins.jetbrains.com/docs/intellij/disposers.html); these sources establish platform
contracts, not repository defects. IDE libGDX remains inside `GdxRuntime.withContext` on a safe AWT canvas frame.

## Prioritized roadmap

Effort is person-days for one experienced developer; confidence medium unless noted. Priorities reflect correctness and
release risk first, then repeatability and measured performance. Dependencies are sequenced, not parallel promises.

| ID / priority / phase | Action and evidence | Rationale | Expected impact | Effort | Risk: implementation / unresolved issue | Dependencies | Success metric |
|---|---|---|---|---|---|---|---|
| R1 / P0 / foundation | Align core registry/source preservation (decision 1; registry, JsonProcessor, SceneJson) | Old plan targets absent modules and assumes fidelity binding does not supply | Usable built-in data without losing extensions | 3–5, medium | Shared parser regression / data loss and unimplementable tasks | Native specs/predecessor source | Core/editor round trips keep lexical numbers, unknown keys and defaults; no reverse dependency |
| R2 / P0 / foundation | Shared committed-bake read policy and existing matrices (decision 2; FoliageLoader/Matrices) | Editor regeneration differs from shipped runtime | Consistent game/physics snapshot, isolated failures | 2–4, medium | Error-policy regression / missing foliage freezes or wrong stale mapping | R1 listing | Headless stale/missing/corrupt/mask-error/native-rejection tests; zero runtime writes |
| R3 / P0 / physics | Validate affine poses, cap copies AND chunk bodies, own contact/resources (decision 4; PhysicsWorld capacity/contact map) | Existing one-body records cannot cover compounds safely | Bounded collision with usable contacts and cleanup | 4–7, medium | JNI resource errors / native failure, silent wrong collision, missing contacts | R2, collider metadata | Native transform/contact/removal/failure tests, cap admission before allocation, repeated close |
| R4 / P0 / editor | Metadata-only collider Apply/Undo (decision 3; current controller apply path) | Collider changes must not depend on scatter or bake | Reliable authoring with live buffers | 2–3, medium | Undo grouping regression / accidental bake rewrite or lost edits | R1, collider metadata | Platform tests preserve bake bytes/mtime through Apply/Undo/Redo, stale/missing bake, external change/disposal |
| R5 / P1 / game | Per-placement drawables and separate light visibility (decision 5; drawable mutable buffers) | Sharing cached draw state or camera culling breaks placement/shadows | Correct runtime presentation and release | 2–4, medium | GL state/ownership / misplaced copies, missing shadows or loading hang | R2 | GL tests with two placements and off-screen caster; failed optional asset leaves loading |
| R6 / P1 / content | Source-manifest migration, bounded fit, staged output (decision 6; 215 current entities) | Reading removed entities cannot rerun deterministically | Reviewable committed field conversion | 3–5, medium | Content drift / nonrepeatable tool or clear-area violation | R5, R7 baseline | Two runs from original and migrated copies byte-equal; counts/clear areas/flight/provenance tests |
| R7 / P1 / measurement | Baseline current field before grass tuning | Hardware/CPU/GPU budget unknown | Defensible density and acceptance thresholds | 1–2, low until hardware chosen | Noisy measurements / unbounded overdraw and input latency | Current game; repeat after R5/R6 | Record hardware/build/scene/resolution, warmup, 3 runs, p50/p95/p99 CPU and separate GPU pass timing, memory/uploads; agree and meet budget |

Long-term options (not tasks in this change): generic ECS rendering only when another game needs it; wind/LOD or alternate
backends only after R7 demonstrates a bottleneck/platform need and maintenance capacity. No migration recommendation is
justified by absent measurements.

## Risks / Trade-offs

- [Risk] An archived predecessor has unverified interactive checks → preserve them; run the runtime's own GL/IDE gates.
- [Risk] Lossless reader extraction broadens a shared seam → keep APIs stable and run existing admission/text-preservation tests.
- [Risk] Non-uniform affine shear cannot be represented by a rigid primitive → refuse unsupported collision with a reason.
- [Risk] Native body/sub-shape allocation can fail below the copy cap → reserve capacities and rollback whole layers.
- [Trade-off] Stale bakes cannot recover historical model identities from ABFO v1 → document current-index mapping; no format bump.
- [Risk] Masks can bleed into overlap/clear areas → verify actual baked footprints and zero texel support conservatively.
- [Risk] Dense grass increases overdraw and expensive model copies → R7 baseline/budget gates tuning; no promised speedup.

## Migration Plan

Land core/read policy and tests first; collider metadata/UI and physics follow their dependencies. Add game rendering
and optional-failure handling before content conversion. Run the manifest tool only on temporary copies until checks
pass; then review complete generated output together with field test/docs updates. Keep source placement manifest and
existing model assets for rollback. Revert the content change to restore authored entities; a scene without foliage
continues working. No automatic migration, publishing or Git commit is performed by applying these tasks.

## Immediate next steps

Start R1/R2 against the current module graph and run R7's baseline before choosing grass settings. Implement R3/R4 at
separate native/platform test seams, then R5 before R6. Record hardware and acceptance budget in the environment docs;
these are explicit measurement gates, not unresolved design questions. Apply with `/openspec-apply-change add-foliage-runtime`
after this planning revision is approved.
