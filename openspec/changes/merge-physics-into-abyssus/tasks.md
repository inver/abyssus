# Tasks

Requires `restructure-gradle-modules` to be applied first; paths are post-restructure. Single plugin tests:
`./gradlew :plugin-abyssus:test --tests '<class>'`. Manual checks use copies of `testData/project/Untitled` and
`testData/project/Physics`, never the fixtures themselves.

## 1. The project physics switch (headless)

- [x] 1.1 Add `ProjectSettingsReader` (`dto/`, platform-free). It validates with `AbyssusDocumentFormat`, then reads
  `physicsEnabled`. Verify with `ProjectSettingsReaderTest`:
  - `Untitled.abss` is off with no problems;
  - `Physics.abss` with the key is on;
  - `"physicsEnabled": "yes"` is off with one problem naming `physicsEnabled`;
  - an `.abss` with `formatVersion: 2` is off.
- [ ] 1.2 Add the `AbyssusProjectSettings` project service: a per-`.abss` cache, invalidation on VFS and document
  changes, `ProjectSettingsListener`, and `setPhysicsEnabled` through `editSceneJson`. Verify with
  `AbyssusProjectSettingsTest` (platform test):
  - turning physics on in a copy of `Untitled` adds `"physicsEnabled": true` last, and the rest of the text is
    byte-identical;
  - Undo restores the original text;
  - a text edit of the document fires the listener;
  - a non-boolean value reports once and leaves the file unchanged.
- [ ] 1.3 Add `"physicsEnabled": true` to `testData/project/Physics/Physics.abss` and
  `projects/app-control-line-game/ControlLine.abss`. Document `physicsEnabled` and the narrowed write rule in
  `docs/ai/file-formats.md` (`.abss` section). Verify with `ProjectSettingsReaderTest`, `./gradlew :lib-runtime:test
  :app-control-line-game:test` (they ignore the key) and `scripts/check-docs.sh`.

## 2. Merge the physics plugin into Abyssus

- [ ] 2.1 `git mv` the sources, resources and tests of `projects/plugin-abyssus-physics` into
  `projects/plugin-abyssus`, keeping package `net.nevinsky.abyssus.physics.plugin` and `AbyssusPhysicsBundle`. Move the
  `<extensions>` entries for `sceneOverlay`, `sceneSimulation` and the `Abyssus Physics` notification group into
  Abyssus's `plugin.xml`. Add `<incompatible-with>net.nevinsky.abyssus.physics</incompatible-with>`. Remove the module
  and its `settings.gradle.kts` entry. Verify with `./gradlew :plugin-abyssus:test --tests
  'net.nevinsky.abyssus.physics.plugin.*'`.
- [ ] 2.2 In `:plugin-abyssus`'s build:
  - add `implementation(project(":lib-physics")) { isTransitive = false }`;
  - move over `exportPhysicsSchema` / `physicsSchemaResource`, `playHostLibs` and the `prepareSandbox` copy, and the
    test task's `-Dabyssus.playHost`;
  - move `checkNoJolt`, scanning `src/main`, wired into `check`;
  - add `checkNoJoltInZip`, which fails if the built plugin zip's `lib/` holds a `jolt-jni` jar, wired into `check`.

  Verify with `./gradlew :plugin-abyssus:checkNoJolt :plugin-abyssus:checkNoJoltInZip`, with `BundledPlayHostTest`,
  and with `unzip -l` of the zip showing `play-host/` and no `lib/jolt-jni*`.
- [ ] 2.3 Rewrite `AGENTS.md`'s "Extension plugins bundle only their own code" rule: Abyssus bundles `lib-physics`
  without its dependencies, and Jolt loads only in a game or the play host. Remove `physics-plugin` and Abyssus
  Physics from the layout and commands. Update `docs/ai/architecture.md` (plugin and extension sections) and the
  `lib-physics` README. Add a CHANGELOG "Removed" entry for the Abyssus Physics plugin and a "Changed" entry for the
  Physics switch. Verify with `scripts/check-docs.sh`.

## 3. Gate physics on the switch

- [ ] 3.1 Remove the physics `componentSchemas` registration. Have `ComponentSchemas` add the bundled
  `/schemas/physics.schema.json` as a built-in contribution only for projects with physics on, and re-snapshot on
  `ProjectSettingsListener`. Verify with `PhysicsSchemaGateTest` (platform test):
  - in a copy of `Untitled`, "Add component" choices for `Model 0` exclude `RigidBodyComponent`;
  - after `setPhysicsEnabled(true)` they include all three physics components;
  - a `RigidBodyComponent` in the scene is read-only JSON while physics is off, and the file is unchanged.
- [ ] 3.2 Add `isAvailable(projectDir)` (default `true`) to `SceneOverlayProvider` and `SceneSimulationProvider`, and
  implement it in the physics providers from `AbyssusProjectSettings`. The Scene view toolbar hides Show Physics and
  the play actions when no provider is available, and stops a running play when the switch turns off. Verify with:
  - `SceneExtensionsAvailabilityTest`: a test provider returning `false` yields no play actions or overlay toggle, and
    a third-party provider that does not override the method stays available;
  - `PlayStateTest`: a new case where a settings change to off while playing stops play and restores the authored
    poses.
- [ ] 3.3 Update `abyssus-extension-points`-facing docs (`docs/ai/architecture.md`, extension points section) for
  `isAvailable`. Verify with `scripts/check-docs.sh`.

## 4. Project properties in the panel

- [ ] 4.1 Add `PanelState.Project` for a selected `.abss` node, and `ProjectDetailsView` with the project name and a
  Physics checkbox bound to `AbyssusProjectSettings`. The checkbox is read-only for an unsupported `.abss`, and all
  text goes in `AbyssusBundle.properties`. Narrow the "Nothing to show" case so project files no longer reach it.
  Verify with `PanelStateTest`:
  - selecting `Untitled.abss` yields `Project` with physics off;
  - selecting `Physics.abss` yields physics on;
  - a scene node still yields "Nothing to show".
- [ ] 4.2 Manual check 1 (`./gradlew :plugin-abyssus:runIde -PideProject=<copy of Untitled>`):
  1. Select `Untitled.abss`: Physics is cleared.
  2. Tick it: the `.abss` gains `"physicsEnabled": true`, and Show Physics and Play appear in the open `Main Scene`
     view.
  3. Undo: the controls disappear and the file text is restored.
  4. Type `"physicsEnabled": true` in the text editor: the checkbox ticks.
- [ ] 4.3 Manual check 2 (`-PideProject=<copy of Physics>`):
  1. Show Physics draws the green box on `Model 0`.
  2. Play shows `Model 0` falling.
  3. Untick Physics while playing: play stops, `Model 0` is back at Y `3.086434`, the controls disappear and
     `RigidBodyComponent` shows as read-only JSON.
  4. Install a build of the old Abyssus Physics plugin next to it: the IDE reports the incompatibility.
- [ ] 4.4 Update `README.md`'s plugin description (physics is built in and switched per project) and the
  `projectView` / `properties` package notes. Verify with `./gradlew :plugin-abyssus:patchPluginXml` and
  `scripts/check-docs.sh`.

## 5. Integration

- [ ] 5.1 Run `./gradlew check` and `scripts/check-docs.sh`. Both pass. Run `openspec validate
  merge-physics-into-abyssus --strict`.
