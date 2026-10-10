# Tasks

Use actual module task paths. Single-class filters belong to the owning module, for example
`./gradlew :plugin-abyssus:test --tests '*FoliagePanelTest'`; never use root `:test --tests`.
GL tests require `-Dabyssus.glTests=true` and a display. Sandbox checks use copied native projects, never committed
ControlLine or shared fixtures. The archived predecessor's seven manual checks remain unverified.

## 1. Verify prerequisites and record performance baseline (R7)

- [ ] 1.1 Verify current foliage classes and main capabilities against the archived predecessor, without assuming
  archive implies manual completion. Run `openspec list --specs`, `openspec validate add-foliage-runtime --strict`,
  `./gradlew :lib-core:test :lib-core-editor:test`; record any existing failures separately. Confirm the module graph
  in `settings.gradle.kts` and the fixture/Field ids and species totals in design.md.
- [ ] 1.2 Capture the unmodified Control Line pilot-view baseline on a recorded desktop machine: revision, release/debug
  mode, OS/JDK/GPU/driver, viewport, vsync/quality, camera path and 215-entity scene. Warm up 30 s and record three 120 s
  runs, p50/p95/p99 CPU frame time, separate supported GPU pass timings, allocations/GC, peak memory and draw/uploads.
  Record measurement limitations and choose an explicit budget before grass tuning in `project/environment/README.md`.
  Verify the report distinguishes submission from GPU execution and contains repeatable workload instructions.

## 2. Core component and source fidelity (R1)

- [ ] 2.1 Add `FoliageComponent` to core's `ComponentRegistry` using current Jackson binding. Preserve its carried
  source payload and patch only changed `assetName` on serialization through `JsonProcessor`/editor `EcsWriter`.
  Verify `:lib-core:test` `FoliageComponentTest` covers short/qualified names, missing/null names, invalid name carried
  raw, duplicate registration protection and no hidden asset-file reads.
- [ ] 2.2 Share the lossless tree reader behind editor `SceneJson` with core runtime scene parsing, retaining facade
  behavior and constructor wiring. Verify `NativeJsonTreeTest`, existing `SceneJsonTest`/document-preservation tests
  and `EcsRoundTripTest` retain nested unknown data, ordering, `2.50`, `-0.0`, `1.0E-4` and omitted defaults. Verify
  `:lib-core:checkNoSingletons`, `:lib-core-editor:checkNoSingletons` and no core dependency on editor-core.
- [ ] 2.3 Add pure core loaded-scene foliage listing from render type/name, engine entity and foliage name, with an
  injected bounded warning sink. Verify `SceneFoliageListingTest` lists entity `1` of the Foliage fixture, omits a model
  owner/missing name with specified diagnostics and performs no filesystem or GL calls. Keep folder/kind validation
  in the asset-read phase rather than a component deserializer.
- [ ] 2.4 Update `projects/lib-core/README.md`, `projects/lib-core-editor/README.md` and `docs/ai/architecture.md` for
  registry/writer/lossless-reader ownership and runtime listing; run `scripts/check-docs.sh` on each changed document.

## 3. Shared runtime bake policy and collider metadata (R2)

- [ ] 3.1 Reuse `FoliageMatrices`; verify `FoliageChunkMatricesTest` unchanged and add `FoliageMatricesTest` for alignment
  0/1, rotation, non-uniform scale and transformed local offsets. Document its instance/thread ownership; introduce
  no duplicate transform implementation.
- [ ] 3.2 Add a shared immutable core runtime-read policy for metadata/terrain/bake/diagnostics, used by game preparation
  and physics. Native-admit before binding; never generate or write. Verify `FoliageRuntimeLoadTest` headlessly for
  matching copy counts, stale density, invalid/missing masks with readable bake, removed layer/unresolvable model index,
  missing model, mismatched/unreadable terrain, truncated bake and formatVersion 2. Assert bounded warnings and unchanged
  input bytes/mtime; add a classpath check excluding IDE/editor generator dependencies.
- [ ] 3.3 Add neutral collider payload decoding in core without physics/Jolt imports. Keep absent collider distinct
  from malformed/unknown shape and retain nested extension payload. Verify `FoliageColliderReadTest` defaults,
  BOX/SPHERE/CAPSULE binding, invalid active sizes/offsets, unknown shape and malformed payload isolation; verify
  `FoliageFingerprintTest` excludes every collider field.
- [ ] 3.4 Update core README and `docs/ai/file-formats.md` for collider payload/defaults and ABFO v1 limitations:
  saved indices/positions/yaw/scale with current model mapping/heights/alignment. Run `scripts/check-docs.sh`.

## 4. Collider authoring and metadata-only Undo (R4)

- [ ] 4.1 Add collider validation/diffs to editor-core, sharing core's neutral reader and injected localized reasons.
  Verify `FoliageSettingsTest` active finite positive dimensions/finite offset, DETAIL incompatibility and OBJECT-to-DETAIL
  transition; `FoliageMetaEditsTest` add/change/remove collider, omitted defaults, nested unknown keys/order/number text.
- [ ] 4.2 Add a collider-only Apply path using `editSceneJson` that requires no scatter/bake preview and changes no
  binary transaction, mask or bake. Keep the existing composite command for mixed generation edits. Verify platform
  `FoliagePanelTest`: supported metadata with a stale or missing bake, open unsaved metadata, Apply/Undo/Redo exact
  metadata restoration, unchanged bake/mask bytes/mtime and stale notice, no additional reload-from-disk Undo.
- [ ] 4.3 Add OBJECT collider controls and None/Box/Sphere/Capsule labels/reasons to bundles, preserving imported DETAIL
  payload and requiring explicit incompatible-kind removal. Verify platform panel cases plus `PluginBundleTitlesTest`;
  disposal, Cancel, source change and read-only document cases write nothing and discard stale publication. Keep UI on
  EDT and use existing owned workers/revision checks for preparation; do not scan assets or call Jolt in controls.
- [ ] 4.4 Update user README, properties README and editor-core README for collider-only editing/Undo and refusal
  behavior; verify `:plugin-abyssus:patchPluginXml` and `scripts/check-docs.sh`. Interactive coverage is 8.3.

## 5. Physics assets, compounds, contacts and lifecycle (R3)

- [ ] 5.1 Add cached CPU-only `PhysicsAssets` foliage reads using the shared runtime policy. Verify `PhysicsAssetsTest`
  readable/stale/unverifiable/missing bake, wrong terrain and unsupported metadata without GL, and rendering/physics
  agree on the same prepared copy set and placement inputs.
- [ ] 5.2 Add pure collider affine validation/decomposition before JNI allocation, using `FoliageMatrices` and transformed
  model-local offset. Verify `FoliageColliderTransformsTest` expected rotation/height/scale/offset, largest-axis sphere/
  capsule, representable orthogonal non-uniform boxes, and rejection of shear, reflection, zero scale and non-finite
  effective values. Unsupported moving owners receive one reason. No false exact-geometry claim for rounded primitives.
- [ ] 5.3 Add scoped static compound construction per layer/chunk, validating every candidate before allocation.
  Check the attached Jolt 6.1.1 API before selecting ownership calls. Verify native `ShapeFactoryTest` sub-shape positions
  and primitive sizes within 1e-4, explicit failure rollback and ownership transfer with no double release.
- [ ] 5.4 Add whole-layer admission in numeric entity-id then metadata-layer order: cap 200,000 colliding copies and
  reserve chunk bodies after ordinary entities within the existing 10,240-body capacity. Verify `FoliageAdmissionTest`
  two 150,000-copy layers, synthetic 300,000-copy bake refused before JNI, available-body exhaustion and deterministic
  order. Do not raise native world capacities silently.
- [ ] 5.5 Integrate compounds into `PhysicsWorld` with separate body-id ownership/contact lookup, preserving ordinary
  body records. Verify native `PhysicsFoliageTest`: settling box, no collider, imported DETAIL collider, invalid collider,
  moving owner, stale bake and missing bake; contacts name foliage terrain owner even without its own ordinary collider.
- [ ] 5.6 Release foliage on owner removal, failed-layer construction and close, including foliage-only owners whose
  removal previously would early-return. Verify `PhysicsFoliageLifecycleTest` removes all chunks, leaves other bodies
  valid, rolls back injected failure, creates/steps/closes 100 worlds and rejects closed-world operations.
- [ ] 5.7 Add a temporary-copy PhysicsFoliage fixture and `PlayHostTest` settling-box case using the bundled external
  host for 5 s simulated time. Verify `:lib-physics:test`, `:plugin-abyssus:checkNoJolt` and `checkNoJoltInZip`; Jolt
  must not initialize in IDE-loaded code. Document fixture and physics policy in physics README and docs/ai/testing.md;
  run docs check. Interactive coverage is 8.3.

## 6. Game rendering before content conversion (R5)

- [ ] 6.1 Add FieldScene foliage listing and register `FoliageLoader` in `fieldAssets`, retaining existing BaseCtx wiring.
  Verify `FieldSceneModelsTest`/`FieldFoliageTest` and a loading-state test treat failed optional foliage as terminal;
  unsupported/missing foliage cannot leave the game on its loading screen.
- [ ] 6.2 Add renderer-owned per-placement drawable state from immutable prepared assets/shared model dependencies.
  Release it before shared assets on replacement/disposal. Verify opt-in `FieldRendererGlTest` draws two transformed
  owners of one asset, restores correct color visibility independent of draw order, and repeated scene replacement
  yields no GL errors or surviving placement resources.
- [ ] 6.3 Add independent light-pass OBJECT visibility and DETAIL receivers through existing shaders. Verify opt-in
  `FieldShadowsGlTest` an off-screen tree shadows visible terrain and grass never contributes depth; compare a rendered
  frame and assert GL state/errors. Preserve color-camera buffers after shadow rendering.
- [ ] 6.4 Document render ownership, asset readiness, shader/pass behavior and unsupported ray foliage in the game
  README and docs/ai/architecture.md; run docs check. Run `:app-game-control-line:test` without GL; manual checks are 8.1–8.2.

## 7. Reproducible field migration (R6, R7)

- [ ] 7.1 Record an immutable source manifest from current Field vegetation: ids/models/transforms, species totals,
  terrain assignment, input hashes, generator seed and fixed identifiers/timestamps. Verify current counts 52/40/33/
  48/42 sum to 215, site/outer bounds and exclusion geometry; document manifest/provenance and actual RGBA splat blend
  interpretation in environment README. No runtime dependence on this manifest.
- [ ] 7.2 Add `tools/FieldFoliage.kt` through an isolated `fieldFoliageTools` source set that compiles just the new tool,
  with editor-core only on its tool classpath and excluded from game runtime/coverage. Add `generateFieldFoliage` with
  explicit project/output argument. Verify `:app-game-control-line:compileFieldFoliageToolsKotlin` and tools-only tests
  without compiling unrelated stale tools; inspect runtime dependencies for no editor-core/IntelliJ.
- [ ] 7.3 Generate grass model/texture and two foliage assets from the manifest, including deterministic UUIDs/provenance,
  inverse-terrain mapping, mask support exclusion under site and transformed crown/28 m clearance. Sample the actual
  sequential splat weights for documented hard/worn thresholds; use bounded density fitting with deterministic tie
  breaks. Verify `FieldFoliageGenerationTest` native formats, valid bake fingerprints, provenance, copy/candidate limits,
  fit failure without publication and no grass footprint within 25 m or outer copy under site.
- [ ] 7.4 Stage and validate all outputs, then mutate Field.scene only through `HeadlessEditing`, retaining unrelated
  text. Use source manifest on both original and migrated inputs. Verify `FieldFoliageMigrationTest` two original-copy
  and two migrated-copy runs have identical per-file checksums, conflict input publishes nothing, non-vegetation bytes
  remain unchanged and no dependence on workspace-wide `git status`. No automatic commit or fixture editing in the IDE.
- [ ] 7.5 Apply reviewed outputs to the bundled project after rendering is available. Update `AirfieldEnvironmentTest`:
  no tree/bush entities, attachments on `0`/`300`, species totals within 25%, transformed OBJECT crowns clear 28 m,
  grass footprint clear 25 m, no outer copies under site, all native/provenance checks and zero foliage colliders.
  Existing `linesAndPlanesAreUnchanged` must pass. Update environment/game README and generator instructions; docs check.
- [ ] 7.6 Repeat the baseline workload from 1.2 with migrated content, then tune and record fixed grass density/draw
  distance in the tool. Verify three measured runs meet the agreed CPU/GPU/memory budget with responsive flight input;
  report any unsupported profiler/hardware checks without claiming measured GPU time from CPU wall-clock submission.

## 8. Manual checks on copies

- [ ] 8.1 Run `./gradlew :app-game-control-line:run` after automated migration checks. Fly the Trainer and compare pilot/
  overhead views: northeastern/eastern trees, irregular grass off asphalt/hard regions, no overlap artifacts, OBJECT
  shadows and responsive controls. Record machine/configuration against the budget from 1.2 and 7.6.
- [ ] 8.2 Copy `projects/app-game-control-line/project/ControlLine` to a temporary location and open it with
  `./gradlew :plugin-abyssus:runIde -PideProject=<copy>`. Both foliage assets render with current bakes, terrain transforms
  move their copies, and the archived predecessor's manual gaps are not silently marked complete.
- [ ] 8.3 In a disposable physics-enabled project copy, give an OBJECT layer a capsule, Apply with metadata open, Undo/
  Redo and confirm bake timestamp/content stay unchanged. Drop a dynamic box onto a copy and Play; it settles on the
  collider. Stop, remove the collider and repeat; the box reaches terrain. Close/reopen view/project while preparation
  is pending; no late UI update, native IDE loading or retained work is visible.

## 9. Integration

- [ ] 9.1 Run `./gradlew check -Pabyssus.requireShaders=true`, explicitly compile/run the new migration tool tests,
  and run `scripts/check-docs.sh`. Verify core/editor/physics singleton rules, NoPlatformClasspathTest, cancellation
  checks, package-cycle checks, no-Jolt source/zip checks and `PluginBundleTitlesTest`. Run opt-in foliage GL cases and
  `:plugin-abyssus:verifyPlugin` against the declared IDE range. Record unavailable hardware/manual checks and failures
  outside this change instead of fixing them silently. Ordinary check alone does not exercise generator source sets.

## Workflow follow-up

Archive after implementation/review with `/openspec-archive-change add-foliage-runtime`; retain any explicitly approved
unperformed manual checks as warnings. No runtime rebaking or generic ECS render system is implied by this change.
