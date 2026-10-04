# Proposal

## Why

The plugin has platform and GL tests, but no automated interactions with its installed UI in a running IDE. Existing design screenshots produce images without baseline pixel comparisons, leaving tree presentation, panel layout, dialogs, focus, and Undo integration dependent on manual checks.

## What Changes

- Add a separate UI test suite using the official JetBrains `intellij-ui-test-robot` client and robot-server plugin, extending the existing `runIdeForUiTests` setup.
- Exercise the Abyssus tree, asset/entity/component properties panels, Rename Scene dialog, and skybox chooser through real mouse and keyboard input.
- Add committed screenshot baselines for tree, panels, and dialogs, explicit baseline updates, and expected/actual/diff artifacts when comparisons fail.
- Use disposable copies of the Untitled fixture and an isolated IDE sandbox; check file preservation and Undo through the UI.
- Repair the existing UI workflow with Java 21, a dedicated test task, bounded startup/shutdown, and failure artifacts. Initially use one pinned Linux visual environment.
- Document local runs, the canonical screenshot environment, and baseline review/update procedures.

## Capabilities

### New Capabilities

None. This is test tooling for existing behavior; `.openspec.yaml` declares `skip_specs: true`.

### Modified Capabilities

None. Coverage targets existing `abyssus-project-view`, `object-properties-panel`, `abyssus-scene-skybox`, and `scene-component-editing` requirements. Tests follow the implemented state and applicable deltas of open changes rather than restoring superseded UI behavior.

## Impact

- Build/test configuration: `build.gradle.kts`, new `src/uiTest/` sources and resources, screenshot baselines, and `.github/workflows/run-ui-tests.yml`.
- Test-only Remote Robot and remote-fixtures dependencies, with compatible client/server versions pinned after verifying IC 2025.2.4. No additions to plugin runtime dependencies.
- Existing `DesignScreenshotTest` remains a component/design diagnostic; the new suite supplies real IDE regression comparisons.
- Tests read `.abss.name`, scene `id`, `name`, `fogEnabled`, `skyboxName`, `skyboxEnabled`, `ecs.entities` component fields, and asset `meta.json` fields/previews. UI edits in disposable copies cover scene `name`, `fogEnabled`, `skyboxName`, and `NameComponent.name`; tests do not introduce any file writer. File format, unrelated keys, number text, and key order remain unchanged.
- Documentation: `AGENTS.md` and `docs/ai/testing.md` for new commands and environment requirements.
- Out of scope: 3D viewport screenshots, GL/gizmo automation, new plugin features, exhaustive dialog coverage, multi-platform screenshot baselines, migrating to Starter/Driver, and replacing existing unit/platform/GL tests.
