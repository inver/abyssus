# Design

## Context

Source reconciliation: 2026-10-09, including the current working tree. See `proposal.md` for motivation.

- `projects/plugin-abyssus-physics` is still a separate plugin using `localPlugin(project(":plugin-abyssus"))`.
  Sources and tests use `net.nevinsky.abyssus.plugin.physics`. Its build is
  `projects/plugin-abyssus-physics/plugin-abyssus-physics.gradle.kts`.
- Its descriptor registers `componentSchemas`, `sceneOverlay` and `sceneSimulation`. The schema registration names
  `/schemas/physics.schema.json`, but that resource and the schema exporter/tasks are absent. The unused
  `schemaExport` configuration is still declared. Physics classes expose `PHYSICS_COMPONENTS`, not a schema API.
- `ComponentSchemas.editorFor(sceneFile)` returns one built-in-only `ComponentEditor`. There is no schema snapshot
  or merge API. Physics and game components currently remain read-only.
- `PhysicsOverlayGeometry.segments` currently returns no geometry; collider and constraint code is commented out.
  Its tests explicitly assert this behavior. Moving these files alone cannot satisfy the overlay specs.
- `ProjectSettingsReader` in `dto/` validates native project text before reading `physicsEnabled` and has five unit
  cases. `AbyssusProjectSettings` and `ProjectSettingsListener` now live in `filetype/`. The service caches saved
  bytes and invalidates on VFS changes, but duplicates parsing without format admission. Its platform tests require
  saving before notification. This is partial implementation of task 1.2.
- The properties panel already supports `PanelState.UISceneState` and scene ray controls. Project selection has
  no dedicated state yet. Neither Physics nor Control Line's committed `.abss` contains `physicsEnabled`.
- `lib-core` owns the runtime and ECS loading; there is no `lib-runtime` module. The play host is already bundled
  outside the physics plugin's `lib/`, with transitive physics dependencies and native jars in `play-host/`.

## Goals / Non-Goals

**Goals:** Deliver the existing change's editable physics, visible overlay and external-process Play contract in one
plugin, gated independently for each native Abyssus project. Complete the partial settings implementation and restore
the missing editing/overlay behavior rather than assuming it can be moved intact.

**Non-Goals:** General schema loading/export, game component editing, changes to physics runtime behavior or the play
protocol, and changes to the existing scene properties/ray controls. `lib-core-editor` gains no dependency on physics.

## Decisions

### Complete the existing settings service in `filetype/`

Keep `ProjectSettingsReader` as the pure JSON admission/decoding seam, and make the service delegate to it. Read the
current editor document when present, otherwise saved VFS text. Cache by `.abss` path; invalidate for document edits,
Undo/Redo, VFS changes and file removal/rename. Unsupported or missing project documents mean physics off. Distinguish
unsupported native format from a malformed optional boolean so the panel can make unsupported files read-only.

Publish `ProjectSettingsListener` updates on the EDT and expose thread-safe cached settings for action updates. Resolve
the native project from `ProjectLayout.abssFor(sceneFile)`; an IntelliJ project may contain several Abyssus projects.
Parse on change/cache population, never each render frame. Report a malformed boolean once per unchanged problem,
using localized plugin text; repeated reads must not repeat the message or repair the file.

Retain `setPhysicsEnabled` through `editSceneJson`. Append a missing key, write explicit `false` on disable and preserve
all unrelated text. Equal-value toggles create no edit. Unsupported documents cannot be written. Explicitly toggling
a malformed boolean may replace that field; merely reading it leaves it untouched.

Alternative: `.idea/` settings or saved-only reads. Rejected because the choice travels with the game and open views
must follow unsaved text and Undo immediately.

### Inject typed physics kinds into the current component editor

Extend `ComponentEditor` with constructor-supplied `ComponentKind` contributions, defaulting to none. Implement a
platform/GL-free physics kind/codec adapter under `plugin.physics`, using the non-Jolt physics component classes and
the existing field/codec contracts. Defaults and validation follow `physics-components`; preserve unknown payloads
and unrelated numbers through the existing component diff/write path. Support registered short and fully qualified
physics component names without adding duplicate components or renaming existing keys.

`ComponentSchemas.editorFor(sceneFile)` chooses a built-in-only editor while physics is off, and an editor with the
three physics kinds while on. Subscribe to settings updates and publish `ComponentSchemasListener` so the Properties
panel refreshes. Recheck the scene's settings at edit time, as existing `SceneComponentEdits` obtains the editor there.
Keep `lib-core-editor` plain JVM, constructor-wired and independent of `lib-physics`; plugin composition supplies kinds.
Pure adapter logic remains headlessly testable even though its composition lives in the plugin module.

Remove the obsolete physics schema registration and unused `schemaExport` configuration. Do not plan to move
`exportPhysicsSchema`, `physicsSchemaResource` or call `snapshotFor`: none exists. Keep the public `componentSchemas`
bean/extension point as it is; broader schema behavior gaps remain outside this change.

Alternative: restore the complete schema annotation/export/merge pipeline. Rejected as unnecessary scope for the
three fixed physics components and incompatible with a simple transfer of current sources.

### Restore overlay geometry and gate providers per scene project

Implement collider/constraint JSON decoding against the physics component defaults without schema dependencies or
Jolt. Replace the current empty-segments expectation with headless coverage of shapes, anchors, motion colors, selected
passes, scaled offsets and preview/simulated transforms. Use the native entity/component representation and accept
registered short or fully qualified keys. Resolve terrain metadata from the scene's native project, rather than
`project.projectFilePath!!`; inject lightweight readers instead of allocating a runtime `BaseCtx` for drawing.

Add `isAvailable(project: Project, file: VirtualFile): Boolean`, defaulting to `true`, to both provider interfaces in
`sceneview/SceneExtensions.kt`. Both arguments are needed: physics providers are stateless extension instances, and
need the IDE project's settings service plus the scene's native project. Preserve existing `create` and `start` methods.
Review JVM compatibility for already compiled third-party providers and retain an always-available fallback.

`SceneOverlayHost`, the Scene view and Play provider selection must reevaluate availability when project settings
change. Hide only the unavailable provider's actions and suppress its draw calls; other available providers remain.
Create/activate contributions when physics turns on without reopening the view. Stop an active physics simulation
through the existing stop path when its provider becomes unavailable, including startup and paused states. Use
generation guards to discard late callbacks/poses. A third-party available Play provider can still offer controls
when built-in physics is off.

Action state and settings notifications run on the EDT, or read the cache from BGT updates. Geometry calculations
are pure CPU work; drawing runs only inside `GdxRuntime.withContext` on the safe AWT canvas render thread.

Alternative: load/unload an optional plugin module per native project. Rejected because plugin modules are IDE-wide.

### Merge packaging without loading Jolt in the IDE

Move physics sources/tests into `projects/plugin-abyssus`, keeping package `net.nevinsky.abyssus.plugin.physics`,
`AbyssusPhysicsBundle` and the `Abyssus Physics` notification group. Register Overlay/Simulation in Abyssus's descriptor,
and declare the old plugin ID incompatible. Remove the old Gradle module and all build/CI references to its tasks.

Add `implementation(project(":lib-physics")) { isTransitive = false }` to the main plugin. `lib-core` and its dependencies
are already available there. Transfer `playHostLibs`, the sandbox `play-host/` copy and `-Dabyssus.playHost` test wiring.
Jolt/native jars belong only in `play-host/`, which starts in a separate JVM; never in IDE-loaded `lib/`.

Move `checkNoJolt` to the main plugin, updating forbidden names to
`com.github.stephengold` and `net.nevinsky.abyssus.lib.physics.jolt`. Add a zip packaging assertion for IDE-loaded
`lib/` that permits the intentional external-process contents of `play-host/`.

Alternative: a transitive physics dependency with exclusions. Rejected because non-transitive bundling is the proven
boundary and does not silently admit a new native dependency.

### Add project properties alongside scene properties

Add `PanelState.Project` in the plugin's existing properties model and a Swing `ProjectDetailsView`. Show the project
name, Physics checkbox and problems from the admitted current `.abss`. Update through the settings listener on the
EDT; the toggle delegates to the existing writer. Preserve `UISceneState`, scene ray controls, asset and entity views.
Keep all plugin-facing text in `AbyssusBundle.properties`; text produced in editor-core uses injected `EditorMessages`.

## Risks / Trade-offs

- [Editing/overlay restoration is larger than a source move] → Dedicated headless implementation tasks and tests;
  preserve the feature contract rather than checking off the existing no-op implementation.
- [Unsaved settings or multiple native projects leak state] → Per-file settings, document/VFS invalidation and
  platform tests for two native projects in one IDE project.
- [Jolt enters IDE-loaded libraries] → Source ban plus zip assertions scoped to `lib/`; test the bundled host separately.
- [Old providers fail after interface growth] → Verify an already compiled provider that implements only the old
  methods, as well as source providers using the default availability implementation.
- [Existing users see physics disabled after upgrading] → Changelog and migration instructions; keep components raw
  and unchanged until enabled. Opt in only the two committed physics projects.

## Migration Plan

Update `projects/plugin-abyssus/src/test/testData/project/Physics/Physics.abss` and
`projects/app-game-control-line/project/ControlLine/ControlLine.abss` with `physicsEnabled: true`, preserving other text.
Document the switch and `.abss` writer in the same implementation change. Users uninstall Abyssus Physics when the
IDE reports the incompatibility and tick Physics in their native projects. Rollback restores the separate plugin;
unknown `physicsEnabled` members are ignored by older builds and the standalone runtime.
