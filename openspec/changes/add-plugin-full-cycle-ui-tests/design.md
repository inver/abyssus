# Design

## Context

See `proposal.md` for motivation and scope. `src/uiTest/kotlin/net/nevinsky/abyssus/uitesting` already supplies Remote Robot page objects, `UiTestBase`, `FixtureProject`, read-only model queries, failure captures and screenshot comparison. `uiTest` runs separately from `check`; `uiHelperTest` runs headlessly as part of `check`. The Linux/Xvfb container is the canonical interactive environment. The documented macOS host path cannot reliably perform mouse interactions or captures.

Current UI tests exercise a few isolated edits and often undo through the scene text editor. They do not prove redo or undo from the initiating tree/view. `ComponentActions.kt` creates entities through the ecs row but exposes no general entity removal. `ComponentEditor` and `FoliageComponentEdits` contain existing reference checks, but those checks are limited and must not be treated as a complete deletion policy.

The current Untitled scene is named `Ololo`, with ID `0`, entities `0`–`10`, `Model 0`, `Camera 4` and `tree` among its usable fixtures. Some main-spec examples still describe the earlier fixture of entities `0`–`8` and entity `9` as the next ID; this change corrects those examples through MODIFIED requirements (`scene-loading`, `scene-asset-placement`, `scene-light-creation` and `scene-component-editing`) so the archived specs agree with the committed fixture. Tests must compute max-ID + 1 from admitted fixture content; the creation algorithm and the fixture itself are unchanged.

## Goals / Non-Goals

**Goals:** Establish independent, repeatable editing lifecycles with visible and persisted assertions; add safe single-entity deletion; fix defects demonstrated by the suite; expose remaining coverage and environment gaps explicitly.

**Non-Goals:** A single enormous ordered test, universal fixture resets in the middle of a lifecycle, programmatic mutation masquerading as UI coverage, exact 3D pixel matching, changes to runtime module boundaries, or new deletion actions beyond Remove Entity.

## Decisions

### Extend the existing suite with independent workflow tests

Reuse Remote Robot and the canonical runner. Split scenarios by feature family, keeping each lifecycle in one test or an independently initialized case. A monolithic walkthrough hides later failures behind the first failure and makes fixtures/order brittle. Pure platform tests alone would miss focus, popup reachability, gestures and stale UI updates.

For each mutation assert the visible row/field/selection, current document and saved files. Save and close/reopen the relevant editor before checking persistence; do not claim an IDE restart was tested by merely reopening a tab. Compare unrelated text or file hashes rather than whole-file normalized JSON. Exact before-text comparison is required after undo; after semantic remove of newly added content, require restoration of unrelated data without requiring incidental whitespace to equal the pre-creation file unless the existing writer contract promises it.

For supported operations the lifecycle is create/add → edit → undo → redo → save/reopen → remove/unregister/undo-create → verify absence/restoration. "Reopen" means editor reopen in the same session unless a scenario says project reopen, which closes and reopens the IDE project (not an IDE restart) and needs a dedicated helper. Read-only views instead exercise open/select/refresh/reopen with no writes. View-only controls exercise enable/change/reset with scene bytes unchanged. Each dialog also gets cancel and representative invalid input. Exercise both tree and toolbar entry points when both exist, sharing assertions rather than duplicating every lifecycle combination.

### Coverage matrix defines completion

Every scenario ID in `plugin-ui-regression-coverage` maps to a test method and a result status in the inventory; a headless check fails on an unmapped ID. Promote this matrix to a maintained test inventory in `docs/ai/testing.md` during implementation. Record actual class/method names, current entry points, fixture, cycle stages, assertions and execution evidence. The class names below are proposed groupings, not claims that these tests already exist. Each implemented command and each distinct field editor type must map to a case; per-kind tables cover component and widget catalogs without multiplying every field/value combination. Planned features in other changes do not count as implemented coverage. A missing required case or skipped scenario remains an open task.

All rows use canonical Linux UI execution unless explicitly marked otherwise.

| Feature family / existing capability | Proposed scenario grouping | Required round trip and observations |
|---|---|---|
| Tree navigation, rename, eyes, unused filter, footer (`abyssus-project-view`) | Extend `AbyssusTreeUiTest`, `RenameSceneUiTest` | Expand/switch/reopen without duplicates; rename/cancel/undo/redo; visibility twice; filter on/off and stable footer; selection/expansion after edits and removal |
| Component catalogs and fields (`scene-component-editing`, `component-schemas`, `object-properties-panel`) | `ComponentLifecycleUiTest` | Add/edit/remove each offered modeled kind where meaningful, tree and Properties removal controls, Render asset choice, duplicate exclusion, default omission, invalid numbers/choices/references, read-only unknown/game components; exact undo/redo and reopen |
| Entity creation/removal (`scene-component-editing`, `scene-asset-placement`, `scene-light-creation`) | `EntityLifecycleUiTest` | New Camera and Render from ecs; model/terrain and each light creation choice from tree/toolbar; rename/edit/reopen/remove; remove guards, no asset deletion, focus-aware undo/redo and cleared stale selection |
| Scene/environment and asset properties (`abyssus-scene-skybox`, `scene-environment-lighting`, `scene-hdr-skybox`, `scene-procedural-sky`, `scene-sky-clouds`) | Extend `SkyboxChooserUiTest`, add `EnvironmentLifecycleUiTest` | Ambient/fog/sky visibility and editable settings; choose/filter/cancel/replace sky; editable procedural/cube metadata; HDR/cloud read-only behavior; preview readiness and errors; persist/reset and undo/redo |
| Scene interactions and display (`scene-view-tools`, `scene-object-transform`, `scene-view-toolbar`, `scene-camera-display`, `scene-fps-display`) | `SceneInteractionUiTest`, extend `SceneViewUiTest` | Pick/tree sync and clear; Q/W/E; move/rotate, Local/World, snap, Drop and Escape cancellation; camera/view changes, overlays, stats and available display settings; document changes only for authoring gestures; reopen and undo from Scene View |
| Water (`scene-water`) | `WaterLifecycleUiTest` | Add Lake/Sea, rename/edit/toggle/move, undo/redo/reopen/remove; reject invalid authoring data; water rows remain distinct from entities |
| Paths (`scene-path-arrangements`) | `ArrangementLifecycleUiTest` | Choose model, draw/finish/cancel path, edit points/settings, regenerate, detach a copy, undo/redo/reopen; verify copies/slots and tree counts; remove detached unreferenced copies and exercise ownership refusal |
| Terrain (`terrain-authoring`) | `TerrainLifecycleUiTest` | Preview/change/cancel/create, place, regenerate, invalid/colliding name, undo/redo with height/recipe files, reopen; remove scene instance before undoing creation where reference guards require it |
| Foliage (`terrain-foliage-authoring`, `terrain-foliage-painting`) | `FoliageLifecycleUiTest` | Create, layer add/edit/remove, preview/apply/cancel, attach, paint/erase/cancel stroke, stale draft/limits, collider options, undo/redo and reopen mask/bake; reset attachment before guarded creation undo |
| Weather snapshot (`weather-presets`) | `WeatherPresetLifecycleUiTest` | Create from procedural sky/cloud pair, reject collision/missing source, cancel, undo/redo; stable UUID/bytes and unchanged source metadata |
| Imports (`model-import`, `flightgear-aircraft-import`) | `ImportLifecycleUiTest` | Local synthetic model and minimal aircraft archive; choose source/settings, preview, cancel/corrupt input/collision; create with and without placement; verify folder/provenance and one-step undo/redo, then reopen |
| Game UI (`game-ui-authoring`, `game-ui-lists`, `game-ui-preview`) | `GameUiLifecycleUiTest` plus focused declaration/canvas cases | Create/register/unregister; screen lifecycle and every supported widget kind, duplicate/reorder/reparent/delete, properties/layout/style, bindings/resources including local image/font import, list template/item authoring, preview interactions/navigation/resize; invalid hierarchy/resource/stale edit paths; undo/redo/reopen and registry consistency |
| Physics, registered schemas, Play (`physics-components`, `scene-physics-overlay`, `scene-play-mode`) | `PhysicsPlayLifecycleUiTest` | Toggle project physics, add/edit/remove offered typed kinds, verify schema/read-only boundaries, overlay state, Play/pause/step/stop/edit-stops-play and failure recovery; unchanged source scene during simulation; host process cleaned up |
| Ray controls and settings already implemented | `RayControlsUiTest` | Off/default, supported setting edits/validation/undo/redo/reopen and unsupported-backend explanation in Linux; accelerated backend rendering verified separately on supported hardware, never silently marked passed in Linux |
| Typed asset properties and optical materials (`object-properties-panel`, `abyssus-scene-skybox`, per-entity material optics) | `AssetPropertyLifecycleUiTest`, `OpticalMaterialUiTest` | Terrain/cube/procedural field edits, omitted defaults, invalid combinations, stale commits, read-only states; transmission/IOR overrides, ranges, instance isolation, diagnostics; undo/redo and reopen |
| Shared field commit (all Properties editors) | `FieldCommitUiTest` | Enter and focus-loss commits, no double commit, reject-and-recover, no-op submissions; one Undo per accepted change |
| Continuous creator journeys | `CreatorJourneyUiTest` | JRN-01–10 without resets between stages: Undo of removal restores edited state; JRN-10 reopens the project |
| Native admission and preservation (all write surfaces) | `NativeAdmissionUiTest` | Prepared disposable malformed/future/legacy-marker scenes/project/meta rejected without writes; unknown payloads, key order, numeric text and omitted defaults survive representative lifecycle edits |

Rendering quality such as shadow softness and cloud animation is validated by existing/targeted GL tests or separately recorded hardware checks, not by treating toolbar clicks as proof of rendered output. The UI suite must still execute every available authoring/control path in this matrix. Hardware-only evidence is a separate task; lack of hardware does not silently satisfy it.

### Stable interaction and isolation

Extend page objects by focused feature rather than growing one file indefinitely. Locate Swing controls by accessible labels, stable component names or class plus scope, and tree rows by structural identity rather than fuzzy first matches. Re-resolve controls after refresh. Add production component names only where accessibility/class scoping is insufficient. Viewport tests establish deterministic camera framing and calculate coordinates from read-only view state or known projection data; gestures themselves use mouse/keyboard input, never call edit callbacks directly.

Use bounded condition polling for dialog readiness, parse/preview completion, tree changes and disk convergence. Do not use arbitrary sleeps or retry an entire failing scenario until it passes. Timeout diagnostics include the operation/stage and expected versus observed state.

Before fixture reset, close editors, stop Play and cancel dialogs/background previews so stale unsaved buffers cannot overwrite restored files or leave undo history targeting the next scenario. Restore only the disposable copy. Inputs for custom schemas, physics, foliage, UI and imports are staged before a scenario using repository fixtures or deterministic generated test data. They never count as the operation under test. Choose the needed fixture profile for each scenario while keeping the runner's isolated sandbox and source-hash protection. Include empty-folder cleanup in fixture isolation tests: current `FixtureProject.restore()` removes files but can leave newly created empty asset directories, which would cause false name collisions. Keep committed source fixtures byte-identical.

Remote queries may read selection/document/render readiness and perform existing navigation/refresh setup; they must not call product mutations, enable physics, set widget properties or synthesize a result. After setup, all workflow writes and undo/redo use the UI.

### Entity removal remains a pure edit with thin IDE glue

Add a constructor-wired pure entity-removal service under `lib-core-editor`'s component editing package. It operates on `SceneJson`/`SceneEntityTree` and returns existing-style changed/rejected results with injected messages. Reference scanning covers short/fully qualified built-in identifiers, parent/look-at/point-to-point endpoints, typed physics references exposed by registered fields, other registered `ENTITY_REF` fields, and arrangement slots/owned copies. Reuse schema field metadata instead of interpreting every integer as an entity ID. Preserve unknown components without inferring undocumented reference semantics.

Do not reuse the foliage helper's built-in-only deletion check as the whole policy. Do not delete connected entities automatically. Reject missing/stale targets and unsupported documents without an undo entry. Preserve wrapped archetypes, including unused definitions, rather than compacting them as a side effect.

Add a stateless `RemoveEntityAction` using `AbyssusTreeAction` and the existing entity target resolver. Register it in `plugin.xml`, localize its label/command/errors, and write only through `editSceneJson` against current document text. Existing selection/refresh listeners should clear removed selections in tree, Properties and Scene View; fix demonstrated stale selection behavior at its owner. Removal is immediate and undoable, with no new confirmation dialog. Undo/redo must be reachable with the tree focused; opening a text editor is not an acceptable substitute for this acceptance case. Add the smallest appropriate platform undo integration if current focus handling prevents it.

### Execution contexts and module boundaries

Remote Robot orchestration, disk assertions and fixture generation run in the test client JVM. Swing lookup/state reads run on the IDE EDT; test read queries use the existing platform read access where required. Product action updates follow their existing declared update context; document mutation, command registration and UI publication run on the EDT through the existing writer. Parsing/import/generation preparation remains off the EDT and is revalidated before commit. The pure removal edit has no Swing, AWT, GL or IntelliJ dependency and is tested on the JVM.

All libGDX work remains inside `GdxRuntime.withContext` on the AWT rendering thread with `GuardedGLCanvas.glSafe()`. Play physics natives remain exclusively in the external host. No test bridge or runtime dependency crosses into shipped game modules. New cleanup belongs to the existing owning editor/project lifecycle; do not add global test-only services to production.

### The canonical environment must render a scene

The canonical Linux/Xvfb container currently has no usable OpenGL: `docs/ai/testing.md` records that the Scene View shows its unavailable message and the toolbar is disabled above it, and `docker/ui-tests` ships only `libgl1`/`libglx-mesa0` with no software rasterizer. Tasks that need the toolbar or viewport (tree/toolbar entity creation, gestures, water/path/foliage interaction) depend on this stack. The proposed gesture scenarios (pick, Q/W/E, move/rotate with Local/World and snap, Drop, path drawing, foliage painting, water and entity moves, camera chooser, orientation widget) and the viewport-update checks cannot run there as the suite stands; running them on the documented macOS host is also not reliable.

Add a software GL stack to `docker/ui-tests` (Mesa's software rasterizer, e.g. `libgl1-mesa-dri` with `LIBGL_ALWAYS_SOFTWARE=1`, or an equivalent) and prove it with a Scene View smoke scenario that creates a real GL context and completes one pick before the gesture groups count as covered. Software rendering is deterministic enough for interaction and state assertions; exact 3D pixels remain out of the suite and stay with the GL tests and separately recorded hardware checks.

If a context still cannot be created, the affected scenarios are recorded as environment-blocked and left unchecked, never reported as covered — the same rule the accelerated ray evidence follows. Enabling the option is not by itself evidence that the environment renders.

### Defect fixes and verification evidence

When a scenario fails, retain diagnostics, distinguish fixture/locator/environment failures from product failures, and record the trigger, expected contract and affected file. For a confirmed product defect, keep the UI reproduction and add a focused lower-level regression when it tests the underlying rule. Fix the owner, rerun the reproduction, affected test group and relevant module checks, then run the complete canonical suite. Do not change a main spec merely to bless observed broken behavior. If a fix requires a new behavior decision, revise this change's delta before implementing it.

The archived `redesign-project-view-menus` change states entity menus are unchanged. Adding Remove Entity is a deliberate exception; note in the project-view README that Remove Entity supersedes that statement for entity rows, without editing the archive. Other unimplemented redesign proposals remain out of scope. Existing unrelated workspace work and failures must be preserved/reported, not folded into these fixes.

Keep real-IDE execution serial per sandbox/port. Expand CI capacity or split sequential groups with independent sandboxes only when measured duration requires it; do not immediately increase timeouts to hide hangs. Existing diagnostics and JUnit reports remain uploaded on failure. The complete suite must pass twice from fresh disposable state to provide initial evidence against order/reset failures; no open required matrix rows may be called complete.

## Risks / Trade-offs

- Broad UI coverage increases execution time and locator maintenance → focused independent scenarios, shared page objects, per-kind data tables and measured runner capacity.
- Generated terrain/foliage/preview work is asynchronous → small deterministic inputs and explicit ready conditions with bounded diagnostics.
- Unknown extensions can contain undocumented references → preserve opaque data; guarantee guards only for supported/registered relationships, without guessing from numbers.
- GPU backends differ from Linux software GL → provision software GL in the canonical container for interaction coverage with a smoke check, keep exact rendered pixels out (GL tests and separately recorded hardware checks), and record native rendering checks separately.
- Undo and project reset can leak state across tests → close owners before reset, verify empty-folder cleanup and repeat the full suite from fresh state.
- Entity deletion overlaps pending menu and refresh changes → integrate current code at apply time, document the exception to the archived menu redesign, and retain selection/refresh regression coverage.
- The main `scene-component-editing` spec has a stray `## ADDED Requirements` header that would make archive refuse this change's delta → repair it before archiving (task 3.6).

## Migration Plan

No data migration is needed. Implement helpers and lower-level removal behavior first, wire the action, expand UI groups, fix demonstrated defects, then enable the expanded suite through the existing CI workflow. Rollback removes the new action and its edit service; saved native documents remain version 1. Preserve regression tests for independent bug fixes if rolling back the action.
