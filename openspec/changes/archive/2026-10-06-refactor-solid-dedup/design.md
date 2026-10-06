# Design

## Context

Prerequisite: `share-native-document-validation` supplies shared core admission and guards raw runtime ECS and unsaved metadata.
See `proposal.md` for the findings F1–F10. This is a behavior-preserving refactor, so the existing tests are the
safety net; each phase must leave `./gradlew check` green before the next starts.
On 2026-10-06 the user instructed continuation against the recorded failing baseline. The separately approved
prerequisites now restore a passing full check; the integration gates are complete (see tasks.md and verification.md).

Constraints:
- **Writes:** every scene or `meta.json` edit still goes through `editSceneJson`. No new write path.
- **Modules:** `core`, `runtime` and `physics` stay plain JVM libraries wired by constructors, with no `object` or
  `companion object` (`checkNoSingletons`). `gdx-model` is untouched. `physics-plugin` may not name Jolt.
- **Threading:** libGDX only inside `GdxRuntime.withContext` on the canvas's AWT thread. Splitting `SceneViewPanel`
  moves code between classes on that same thread; no code moves threads.
- **Format:** documents keep `format: "abyssus"` / `formatVersion: 1`; validation by `AbyssusDocumentFormat` stays
  before binding.

## Goals / Non-Goals

**Goals:** remove each duplicated block in the findings table; make adding a `MetaType`, a `FieldType` or a play frame a
one-place change; bring classes above ~450 lines with several reasons to change under that size by responsibility.

**Non-Goals:** behavior changes, `gdx-model`, performance work, a new DI framework, patterns without a duplicate to
remove.

## Decisions

### D1. One meta binding in `core` (F1)

Add `AssetMetaBinder` (constructor takes `JsonProcessor`) in `core/.../assets/`. It owns the `MetaType` → `additional`
class table (a `Map<MetaType, Class<*>>` built in the constructor, not a `when`) and `bind(name, tree): AssetMeta<Any>`.
`AssetMetaLoader` and the plugin's unsaved-meta loader both take it. `AssetMetaReader` keeps format validation and the
`MetaDocument` for listing and the panel; it does not duplicate the table.
*Why not a `MetaType` field:* `MetaType` is a plain enum without settings classes; putting classes on it would make it
depend on every asset package. The binder is injected, so a test can register a fake kind.
*Pure code:* the binder has no IntelliJ or GL imports and is tested headless.

### D2. Small helpers instead of new rules (F2)

`parseUuidOrNull(String?)` in `core` (uses `runCatchingKeepingCancellation`, or a plain `try` on
`IllegalArgumentException`, which is not cancellation) replaces the four direct calls. `checkNoRunCatching` moves
into the shared Gradle script (D7) and covers all JVM modules.

### D3. Strategy per `FieldType` (F3)

The prototype below was evaluated and rejected. Production retains exhaustive `FieldType` switches in
`SchemaJson`, `ComponentSchemaReader` and the editor mapping. No handler registry is introduced.

#### Task 3.1 result — 2026-10-06

The DECIMAL/VECTOR candidate is retained under `prototype/runtime/schema/` (outside production source sets).
Its two behavior tests passed with `./gradlew :runtime:test --tests '*FieldTypeHandlerPrototypeTest'` before moving
it out of the runtime module. Tests cover finite values, partial axes, Java conversion, defaults and limit/error text.

| Compared code | Nonblank code lines |
|---|---:|
| Existing SchemaJson decimal/vector encode/decode arms, including the vector closing brace | 9 |
| Existing ComponentSchemaReader inference and schemaValue arms | 4 |
| Existing ComponentEditor fieldsOf arms | 2 |
| Existing total | 15 |
| Candidate contract and two handlers | 34 |

Counts exclude comments/imports and give the candidate the benefit of excluding shared validation helpers entirely:
those could be relocated without growth. Registry wiring, inference adapters and remaining plugin display mapping
would add lines. Even replacing the candidate's vector limit loop with the existing limitedAxes helper would not
make it smaller. Task 3.1 therefore fails its explicit acceptance gate; retain the switches. No field-type production change remains.
The user then instructed continuation with the existing switches retained: tasks 3.3/3.4 are withdrawn, and the remaining phases continue.

### D4. Split by reason to change (F4, F5) — executed in `restructure-editor-modules` stage 3

The split below is the intended result; it is carried out together with the module move so each file is touched once.

- `ComponentEditor.kt` → `ComponentCodec.kt` (contract and `RuntimeCodec`), `BuiltInComponentKinds.kt` (field tables),
  `SchemaCodec.kt`, `ComponentEditor.kt` (create/update/remove). Public names stay; this is file moves plus visibility.
- `SceneViewPanel` → `SceneToolbar` (buttons, `syncControls`, camera choices), `SceneInputForwarder` (mouse/keys,
  play forwarding) and the panel as the canvas host and wiring. The panel remains the `SceneView`. Extracted classes
  take collaborators by constructor and hold no Swing state shared through fields: callbacks are lambdas.
  Testable logic (camera choices, button enablement) goes to Swing-free functions.
- `AssetPropertiesPanel` → row builders and `Thumbnail` out; the panel keeps layout and selection.

### D5. Named play frames (F6)

`FrameType(id)` enum; encode and decode look the id up once. Wire ids stay identical, so the protocol version does not
change. A round-trip test over every `FrameType` pins that.

### D6. Facade groups instead of one holder (F8)

`AbyssusCore` exposes `documents`, `assets`, `terrain` and `ray` groups (small classes constructed in it).
Entry points (actions, tool window factory, editor provider) take the group they use. The `service<AbyssusCore>()`
rule from `conventions.md` does not change: it is allowed only at those entry points. This reduces what each entry
point can reach, so tests can pass a group instead of the whole core. Fully qualified names inside the class are
replaced with imports.

### D7. One Gradle convention (F7)

Add `gradle/checks.gradle.kts` (applied with `apply(from = ...)`) that registers `checkNoSingletons(sources, excludes)`
and `checkNoRunCatching(sources)`. Modules call it with their paths. Task names stay the same, so the commands in
`AGENTS.md` and CI do not change. `checkNoJolt` stays in `physics-plugin`.

### D8. Verify before extracting (F9)

Task 9.1 diffs `MetalRayBackend` and `VulkanRayBackend` session code and records the result in this design. Extract a
shared base or helper only for blocks that are identical in behavior; otherwise leave them and say so. Same for
`PhysicsWorld`: split only along seams covered by separate tests (`PhysicsWorldTest`).

### D9. Verifier first (P1, P2)

Configure `intellijPlatform { pluginVerification { ides { recommended() } } }` and run `verifyPlugin` once before any
other platform work, so the baseline is known. For each internal-API hit in the project view pane, either replace it
with a public API or keep it in one file (`AbyssusProjectViewPane.kt`), comment why, and list it in an allowlist of
known problems in the verifier configuration so new hits fail CI. No replacement is forced when the public API cannot
build the same tree.

### D10. Constructors do not look up services (P3)

Entry points (actions, factories, providers, the pane) read services and pass them in. Secondary constructors that call
`service<...>()` are removed from `SceneReader`, `SceneDocumentCache`, `ProjectReader` and `AssetReadCache`; their
dependencies come from `@Service` constructors that take only `Project` (allowed) and read the shared holder lazily
inside the method that needs it. This merges with D6: each group of `AbyssusCore` is created on first access
(`lazy`), so the first call to one group does not build the others.

### D11. Threads and `update()` (P4, P5)

- The single-thread ray worker and the ray convert thread stay plugin-owned: they own native resources and need thread
  affinity, which a platform pool does not give. Both are closed in `AbyssusCore.dispose`.
- `executeOnPooledThread` in `SkyboxChooserDialog` becomes a `SwingWorker`-free pattern: build the thumbnail list off
  the EDT into an immutable result, then hand it to the EDT in one `invokeLater` that stores and repaints. No mutable
  field is written from two threads.
- Actions whose `update()` reads only data (no Swing component) switch to `ActionUpdateThread.BGT`; the scene text read
  stays behind the cached parse, with a read action where PSI or documents are touched. Actions that read the tree
  selection component stay on EDT. Each action is classified in task 10.5.
- New async work uses a coroutine scope injected into a service. No existing callback code is rewritten.

### D12. Localized action text and dynamic reload (P6, P8)

Action, tool window and notification names move to bundle keys (`action.Abyssus.RenameScene.text` and so on) while the
English text stays identical. Dynamic reload is checked by hand: install the built zip into a running sandbox IDE,
update it, and uninstall it without restart; if unloading fails, fix what the log names under the `DynamicPlugins`
category (static caches, a leaked disposable, the native libraries).

## Risks / Trade-offs

- **Churn conflicts** with open changes touching `AssetLoading`, `SceneViewPanel` and ray files. Mitigation: phases are
  independent commits; phases 4 and 5 go last and may be rebased.
- **Over-abstraction.** Strategy for field types adds indirection. Kept only because it removes five switches; if the
  prototype in task 3.1 is not smaller than what it replaces, stop and keep the switches.
- **Hidden behavior in copies.** The two `parse` copies could differ in subtle ways (the unsaved one drops errors
  silently). Task 1.1 writes a test first that pins both behaviors on the Untitled fixture and on a malformed meta.
- **The verifier may report a lot at first.** Task 10.1 only records the baseline; fixes are limited to what P2 names.
- **BGT `update()` can expose a hidden Swing access** and throw in a slow-operation assertion. Each switch is tested and
  the list of switched actions is explicit.
- **Docs and the OpenSpec specs** must change in the same change that makes them wrong; each phase's last task does so.

## Open Questions

- Should the extracted `SceneToolbar` live in `sceneview/` or a new `sceneview/toolbar/` package? Default: new package.
- (Resolved upstream) `AssetMetaEditor` stays in the plugin; the docs now say so.

## Implementation inventory — 2026-10-06

D6: the original core lookups occur in eight source files (nine expressions). SceneReader and ProjectReader use
documents; PropertiesToolWindowFactory uses documents/assets/terrain; ComponentActions and ProjectViewPane use assets;
SceneFileEditorProvider uses documents/assets/ray; NewTerrainAction uses documents/terrain. Groups are lazy and
compatibility getters forward to them. Cache and reader service constructors now defer lookup until a read.

D11 action classification: RenameSceneAction, NewTerrainAction, AbyssusTreeAction (including component actions) and
UnusedFilter read the selected Swing tree/pane and remain EDT. The three AddLightGroup preset actions read scene data
only and use BGT. AddLightActionTest exercises all three updates on the platform pool under a read action.

D8 backend comparison: owner-thread guards, one pending request, cache invalidation and accumulation have similar
contracts, but Metal delegates submit/poll/destruction to JNI while Vulkan owns fences, command buffers, descriptor
sets and mapped half-float readback. Vulkan additionally guards device loss and logs refusal. Those blocks cannot
be shared without changing behavior. The scene-instance mapping is identical (mesh, copied transform, white
multiplier and BLEND primary-ray flag); it now lives in `RaySceneRequest.instances()`. Existing `dirtyMeshes`,
`shadingUnchanged`, scene encoding and frame accumulation are already shared. No common native session base is added.

D8 physics seams: ShapeFactory already separates shapes, tested by flat hull refusal, convex hull acceptance and
terrain/static/falling cases. Body mutation is exercised by thrust, kinematic movement, reset and entity removal;
constraints by rope creation/removal and invalid limits; contacts by landing. These still share native ownership
and body maps. The fixed-step accumulator has its own `advanceRunsFixedStepsAndCarriesTheRemainder` test, covering
zero-step calls, carried fractions and overflow dropping. Only that seam is extracted to `FixedStepClock`; native
stepping, body write-back and contact collection remain in PhysicsWorld.

D9 verifier baseline: `./gradlew verifyPlugin` completed against IC-252.28539.97 and recommended IU builds
253.33813.55, 261.27258.48, 262.10968.63 and 263.6259.32. Abyssus is binary compatible on all five, but the Gradle
gate fails on internal API usages: IC reports seven (six generated ToolWindowFactory default bridges and the
EyeTree RenderingUtil.CUSTOM_SELECTION_BACKGROUND field). The same tree field appears on every target; IU-262 also
reports PluginManager.getPluginByClass in SceneFileEditorProvider.pluginName. Later IDEs additionally report
deprecated/experimental and scheduled-for-removal usage. Full reports are in `build/reports/pluginVerifier`.
The unqualified command also verified physics-plugin, which fails because the mandatory local Abyssus dependency
is unavailable to the verifier. CI now calls the root `:verifyPlugin`; extension dependency verification needs its
own resolution before it can be claimed green. Log: `/private/tmp/abyssus-solid-verifier.log`.

The documented ignored-problems mechanism lists compatibility errors, not arbitrary internal API reports.
`gradle/plugin-verification.gradle.kts` now finalizes root `verifyPlugin` with `checkPluginInternalApis`, matching
complete report descriptions against eight exact entries in `gradle/plugin-internal-api-allowlist.txt`. There are no
wildcards: a new caller of an accepted API still fails. Compatibility and override-only usages retain the verifier's
default failure policy; internal usages use this exact comparison. Missing verifier reports fail the comparison.
An injected unknown report line failed; removing it restored success, including configuration-cache reuse.
At this baseline stage task 10.3 remained unchecked pending both verifier and AbyssusViewTest verification;
the passing continuation below completes it.

Continuation: the remaining AddAsset and AddComponent entry points now select `assets.metaFiles`, and the
FlightGear construction uses imports rather than inline qualified names. The stable Tree snapshot isolates the
tree suite from interactive edits. All 21 AbyssusViewTest cases pass, along with the NewTerrain, ComponentActions
and SceneFileEditor group. Constructor inspection confirms the four reader/cache services defer lookups until use.

Root `:verifyPlugin` now passes all five targets with the exact allowlist and verdict/detail consistency check
(`/private/tmp/abyssus-solid-verifier-continuation.log`). Task 10.3 is complete. With user-authorized test-runtime
TinyEXR natives, all five SkyboxChooserDialogTest cases pass; task 10.7 is complete. Neither native test dependency
nor fixture isolation changes production behavior or plugin packaging.

CI task 10.2 is edited locally: supported action versions, Java 21, root `:verifyPlugin`, and removal of the obsolete
listProductsReleases call. A remote branch workflow has not been run; its verification checkbox stays open.
