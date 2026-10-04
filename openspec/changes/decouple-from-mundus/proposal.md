# Proposal

## Why

Abyssus should be an independent libGDX editor whose behavior and data format it owns. Product descriptions, serialized Java class names and compatibility rules currently tie its development to another editor despite having no build or runtime dependency on it.

## What Changes

- Present Abyssus as an independent IntelliJ-based libGDX scene editor in the README, plugin description and active developer documentation.
- **BREAKING:** require `format: "abyssus"` and integer `formatVersion: 1` in project `.abss`, scene `.scene` and asset `meta.json` documents. Keep the file extensions, folder layout and existing gameplay/asset fields.
- **BREAKING:** use the existing short component names as stable Abyssus schema identifiers; remove `ecs.componentIdentifiers`, whose keys are serialized Java classes. Retain optional `archetype`, `archetypes` and `metadata` as Abyssus data, with archetype lists referring to short component identifiers.
- **BREAKING:** replace `RenderComponent.renderable.class` with `kind: "asset"` for model/terrain references; unknown native renderable kinds remain preserved without rendering. Do not recognize or emit upstream Java class aliases.
- Reject unmarked, foreign, unsupported-version and legacy-identifier documents with an explanation, without importing, converting, formatting or editing them automatically. Existing user files remain untouched.
- Treat spotlight beam fields, terrain encoding, procedural/HDR sky assets and omitted defaults as the Abyssus contract, with no upstream compatibility prerequisite.
- Update repository fixtures and tests to native documents, preserving their game content where possible. Keep explicit legacy rejection test inputs.
- Reconcile active change artifacts and replace outdated compatibility constraints in AGENTS.md and OpenSpec configuration during implementation.
- Preserve accurate inherited-code attribution and license headers in dedicated third-party documentation; keep archive/history intact. This change removes product and format coupling, not historical provenance.

**Fields read/written:** new root `format` and `formatVersion`; removed `ecs.componentIdentifiers`; replaced renderable `class` with `kind`; existing `ecs.entities`, component payloads, `archetypes`, `metadata`, sky/fog/camera fields, asset `type`, `uuid`, `version`, `lastModified` and `additional` keep their meanings. Asset `version` remains distinct from document `formatVersion`. Native edits continue to preserve unrelated text and use the existing undoable writer.

**Out of scope:** legacy import/migration, a new project wizard, a wholesale scene schema redesign, renaming extensions/modules, replacing libGDX/Ashley/Assimp, rewriting inherited model code, physics or play mode, and deleting attribution or git history.

## Capabilities

### New Capabilities

- `abyssus-document-format`: Native document identity/version, rejection boundaries, independent product identity and supported format documentation.

### Modified Capabilities

- `scene-ecs-components`: Native renderable kinds and ECS round trips replace the upstream-format guarantee.
- `scene-component-editing`: Native default/unknown-data preservation and spotlight storage replace upstream compatibility requirements.
- `scene-light-creation`: New entities use short native identifiers without Java-class bookkeeping.

## Impact

- Project/scene readers, `SceneRenderParams`, asset metadata readers/loading, `SceneFormatListener`, mutation entry points and ECS codecs/loader/writer/light creation.
- `core` owns a pure format validator shared by the plugin and eventual `runtime`; module boundaries remain plain JVM and constructor-wired.
- Main spec purposes containing outdated product/format wording need editorial updates during apply; behavioral changes are captured by the listed deltas. No main specs or configuration are edited during this proposal.
- `design-review-refactor` is in progress and `extract-scene-runtime` plans the same readers/codecs. Land this format change after those changes, or amend their remaining artifacts first if implementation order changes. Reconcile all other open changes that promise upstream compatibility or create unmarked documents.
- Native fixture/test updates, user/developer docs, marketplace description, release notes and a third-party provenance document. No new dependencies.
