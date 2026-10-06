# Design

## Context

After `restructure-gradle-modules`, `projects/plugin-abyssus-physics` is a separate plugin that depends on Abyssus
(`localPlugin(project(":plugin-abyssus"))`). It registers three things against Abyssus's extension points:

- `componentSchemas`: `/schemas/physics.schema.json`, generated from `PhysicsComponents` at build time;
- `sceneOverlay`: `PhysicsOverlayProvider`;
- `sceneSimulation`: `PhysicsSimulationProvider`, which starts the bundled `play-host/` JVM or a game's `play.json`
  classpath.

It bundles `physics.jar` alone, without dependencies, and gets libGDX, `runtime` and Jackson from Abyssus's
classloader. `checkNoJolt` keeps `com.github.stephengold` and `net.nevinsky.abyssus.physics.jolt` out of its sources.

Abyssus reads only `name` and `mainCamera` from an `.abss` and never writes it, apart from `SceneFormatListener`
formatting. `editSceneJson` already edits any native document, `.abss` included. The Properties panel has no view
for a project node today.

## Goals / Non-Goals

**Goals:**
- One plugin, with physics behaviour identical to today for a project with `physicsEnabled: true`.
- The switch is re-read on every `.abss` change, so toggling it, Undo, or a text edit takes effect in open views
  without reopening them.

**Non-Goals:**
- No change to `lib-physics`, the play protocol, `play.json` or the play host.
- The extension point contracts are not reshaped for third parties beyond the "offers Play for this project" gate.

## Decisions

### `ProjectSettings`: a pure reader plus a project service

- `ProjectSettingsReader` lives in `plugin-abyssus`'s `dto/` package and is free of the platform: JSON in,
  `ProjectSettings(physicsEnabled: Boolean, problems: List<String>)` out. It is unit-tested on `SceneJson` trees.
  Validation goes through `AbyssusDocumentFormat` first. An unsupported document counts as physics off.
- `AbyssusProjectSettings` is a project service. It caches settings per `.abss` path, invalidates them through the
  VFS and document listeners already used by `ComponentSchemas`, and publishes `ProjectSettingsListener` on change.
  Reads run on any thread from the cache. Listeners are called on the EDT.
- `setPhysicsEnabled(abss, on)` calls `editSceneJson(project, abss, "Turn Physics On/Off") { root ->
  root.put("physicsEnabled", on) }`. That gives one undoable command, and `SceneJson` keeps key order and number
  text. When the key is missing it is appended last. Turning physics off writes `false` rather than removing the
  key, so the file states the choice explicitly.

**Alternative:** IntelliJ project settings (`.idea/`). Rejected during exploration because the choice must travel with
the game project and with the copies opened for manual checks.

### Physics keeps its extension-point shape; gating lives at three seams

The physics sources move into `plugin-abyssus` under their package (`net.nevinsky.abyssus.physics.plugin`), and their
registrations move into Abyssus's `plugin.xml`.

- **Schemas:** `componentSchemas` registrations are static resources. The physics schema stops being an extension
  registration. `ComponentSchemas.snapshotFor(scene)` adds the bundled `/schemas/physics.schema.json` as a built-in
  contribution only when the scene's project has physics on, and re-snapshots on `ProjectSettingsListener`. With
  physics off, the components fall back to the existing unknown-component path (read-only JSON), which the specs
  already describe.
- **Overlay:** `PhysicsOverlayProvider` returns no overlay, and Abyssus offers no "Show Physics" toggle, when the
  project is off. `SceneOverlayProvider` gains `isAvailable(projectDir): Boolean`, default `true`. The toolbar asks it
  each time the toolbar updates (an `AnAction.update` on the BGT reading the cached settings).
- **Play:** `SceneSimulationProvider` gains the same `isAvailable(projectDir)`, default `true`, which is what
  "offers Play for the scene's project" means in the extension-points delta. On a `ProjectSettingsListener` change to
  off, the Scene view stops a running play through the existing stop path, the same one an edit uses.

**Alternative:** a separate `<depends optional="true" config-file="physics.xml">` module that is loaded or unloaded
with the switch. Rejected because dynamic loading per project is not possible: plugin modules are per IDE, not per
project.

### Bundling: no Jolt, one classloader

`:plugin-abyssus` adds:

- `implementation(project(":lib-physics")) { isTransitive = false }`, so jolt-jni's jar never enters `lib/`.
  `lib-runtime` and its dependencies are already there.
- The `exportPhysicsSchema` and `physicsSchemaResource` tasks, the `playHostLibs` configuration and the
  `prepareSandbox` copy into `play-host/`, moved verbatim.
- `checkNoJolt` over `projects/plugin-abyssus/src/main`, wired into `check`.

`BundledPlayHostTest` and `PlayLaunchTest` move with the code and keep `-Dabyssus.playHost`.

**Alternative:** depending on `lib-physics` transitively and excluding jolt-jni. Rejected because the non-transitive
form is what the physics plugin proves today, and an exclusion silently breaks if a new Jolt artifact is added.

### Retiring the old plugin ID

Abyssus's `plugin.xml` declares `<incompatible-with>net.nevinsky.abyssus.physics</incompatible-with>`, so the IDE
refuses to run both and asks the user to remove the old plugin. The `Abyssus Physics` notification group and
`AbyssusPhysicsBundle` keep their IDs, so settings users made for those notifications survive.

### Project properties view

`PanelState` gains a `Project(abss)` case. `ProjectDetailsView` (Swing) shows the name and a checkbox bound to
`AbyssusProjectSettings`. The checkbox state comes from the cache and is refreshed by `ProjectSettingsListener`, which
covers Undo and text edits. All user text goes into `AbyssusBundle.properties`. Swing runs on the EDT, the write goes
through `editSceneJson`, and nothing touches GL.

## Risks / Trade-offs

- [Existing physics users see physics vanish after upgrading] → CHANGELOG entry, and the read-only JSON fallback keeps
  their data safe. Ticking Physics once restores everything. The two committed physics projects are updated in this
  change.
- [jolt-jni sneaks into the plugin through a new dependency] → `checkNoJolt` covers sources, and a build check asserts
  that the plugin zip's `lib/` has no `jolt-jni` jar (task 2.2).
- [Overlay or toolbar reads settings on a hot path] → reads go to an in-memory cache keyed by `.abss` path. Parsing
  happens only on file change.
- [`.abss` writes conflict with `SceneFormatListener`] → `editSceneJson` is already the sanctioned writer for native
  documents and coexists with the listener for `.scene` files today. A test edits an open `.abss`.

## Migration Plan

Users uninstall Abyssus Physics (the IDE prompts them to because of `incompatible-with`) and tick Physics in each
project that uses it. Rolling back means reverting the change and reinstalling the old plugin. `physicsEnabled` keys
left in `.abss` files are ignored by older Abyssus builds, which ignore unknown `.abss` members.
