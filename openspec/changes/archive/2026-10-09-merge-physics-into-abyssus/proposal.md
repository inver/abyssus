# Proposal

## Why

Physics ships as a second IntelliJ plugin, Abyssus Physics, only so it can be left uninstalled. That costs a second
plugin build, a classloader boundary ("bundle only your own code"), a `localPlugin` sandbox and two installs for
users. Whether a game uses physics belongs to the game, not to one person's IDE, so the choice should travel with the
project.

## What Changes

- **BREAKING:** the Abyssus Physics plugin (`net.nevinsky.abyssus.physics`) is retired. Its code is merged into
  Abyssus (`projects/plugin-abyssus`), and the `projects/plugin-abyssus-physics` module is removed. Abyssus declares the old plugin ID incompatible, so the two never load
  together.
- What moves into Abyssus:
  - the physics overlay;
  - Play through the play process;
  - typed editors for the three physics components, supplied to the existing component editor;
  - the bundled `play-host/` folder.

  Overlay and Play keep their public extension points. The retained `componentSchemas` extension point stays
  registered, but restoring general project/contributed schema support is outside this change. Physics editing
  and collider/constraint overlay drawing must be implemented: the current editor is built-in-only and the
  current overlay geometry produces no segments.
- New per-project switch: a top-level boolean `physicsEnabled` in the project `.abss`. A missing key or `false` means
  off. With physics off:
  - physics components are not offered and existing ones are read-only;
  - "Show Physics" and the built-in physics Play controls are absent; other available providers retain their controls.
- New project properties: selecting the `.abss` node in the Abyssus view shows the project in the Abyssus Properties
  panel, with a Physics checkbox that writes `physicsEnabled` as one undoable edit.
- `projects/plugin-abyssus/src/test/testData/project/Physics/Physics.abss` and `projects/app-game-control-line/project/ControlLine/ControlLine.abss` gain
  `"physicsEnabled": true`.
- Jolt still never loads in the IDE process. `lib-physics` is bundled without its dependencies, and the no-Jolt
  source check now covers all of Abyssus.
- Out of scope:
  - per-scene switches;
  - a global IDE setting;
  - switches for other subsystems;
  - turning physics on automatically for projects that already hold physics components;
  - any change to how a game or the play host uses physics. They ignore `physicsEnabled`;
  - restoring the general schema annotation/export pipeline or contributed/game component editing.

Native files:
- **Reads:** `.abss` `physicsEnabled` (boolean), only after the document passes `AbyssusDocumentFormat` validation.
  An unsupported `.abss` counts as physics off and is never written.
- **Writes:** `.abss` `physicsEnabled`, only through `editSceneJson`, and only when the user toggles the switch.
- No version change: `physicsEnabled` is an optional member of native version 1. Readers that ignore it, such as the
  game runtime, keep working.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `abyssus-document-format`: adds the optional `.abss` `physicsEnabled` switch, which is off when missing and written
  only when the user toggles it.
- `object-properties-panel`: a selected project file shows project properties with the Physics switch.
- `abyssus-extension-points`: play controls appear when a provider offers Play for the scene's project, not merely
  when one is installed; overlay contributions also follow provider availability.
- `physics-components`: editing depends on the project's switch instead of an installed plugin.
- `scene-physics-overlay`: "Show Physics" exists only with physics on.
- `scene-play-mode`: built-in physics Play exists only with physics on, and turning physics off ends its simulation.

## Impact

- Code:
  - `projects/plugin-abyssus`: complete the existing project-settings reader/service integration, add a project
    view alongside existing scene properties in the Properties panel,
    the physics sources and the message bundle from `plugin-abyssus-physics`, `plugin.xml` registrations, and gating
    in `ComponentSchemas` and the Scene view toolbar;
  - `projects/lib-core-editor`: allow constructor-injected component kinds while preserving built-in defaults
    and its plain JVM boundary;
  - the `plugin-abyssus-physics` module is removed.
- Build:
  - `:plugin-abyssus` gains `lib-physics` (non-transitive), the `play-host` sandbox copy and
    `checkNoJolt`;
  - `settings.gradle.kts` drops `:plugin-abyssus-physics`.
- Fixtures: `projects/plugin-abyssus/src/test/testData/project/Physics/Physics.abss` and `projects/app-game-control-line/project/ControlLine/ControlLine.abss` gain one key.
- Docs:
  - `AGENTS.md`: the hard rule "Extension plugins bundle only their own code" is rewritten as a rule about how
    Abyssus bundles `lib-physics`, the layout and commands lose `plugin-abyssus-physics`, and the no-Jolt rule stays;
  - `docs/ai/architecture.md`, `docs/ai/file-formats.md` (the `.abss` section: Abyssus now writes one member);
  - the `lib-physics` README, `README.md` (the plugin description mentions physics) and `CHANGELOG.md` (the
    retired plugin).
- Users with Abyssus Physics installed must uninstall it. Projects that used physics need Physics ticked once.
- The module restructuring is already present in the current checkout; it is no longer an outstanding prerequisite.
- Source reconciliation (2026-10-09): task 1.1 exists with reader tests. The service is in `filetype/`, reads saved
  VFS bytes without format validation, and only invalidates on VFS changes; task 1.2 remains incomplete. Neither
  committed physics project has the switch yet. No implementation tests were rerun for this planning refresh.
