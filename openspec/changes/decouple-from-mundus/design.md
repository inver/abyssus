# Design

## Context

See `proposal.md` for scope and the accepted compatibility break. The build already has no upstream editor dependency. The remaining executable coupling is `RenderCodec`'s `MUNDUS_CLASS` dispatch and output, and `LightEntities`' creation of `com.mbrlabs.mundus...` entries in `componentIdentifiers`. `SceneEcsLoader` carries block extras; `SceneEcsWriter` writes them back. Short component map names already provide the registry keys.

Project and scene readers currently bind arbitrary JSON without checking document identity. `SceneFormatListener` automatically pretty-prints any recognized project/scene extension. `AssetFiles` and the shared metadata reader being introduced by `design-review-refactor` are the asset admission points, including unsaved metadata snapshots. Some actions parse JSON directly instead of using the DTO readers; validating only a reader would leave edit and preview bypasses.

The repository has multiple active changes and partially implemented refactors. This change targets the shared readers/codecs after `design-review-refactor` and `extract-scene-runtime`; current plugin paths below identify the code to move or adapt, not a requirement to duplicate it in both modules.

The user explicitly accepts incompatible files and asked to remove product/format association. This supersedes the existing upstream compatibility constraints. Retaining source attribution and archive history is part of the agreed boundary, not an endorsement of continued format compatibility.

## Goals / Non-Goals

**Goals:**
- One native version gate shared by IDE and headless readers, writers, previews and authoring.
- A bounded native schema change using existing gameplay fields and stable component keys.
- Refuse unsupported documents before side effects, with localized actionable explanations.
- Preserve native unknown data, default omission, undo and graphics behavior.

**Non-Goals:**
- An importer, automatic user-file migration or compatibility aliases.
- New project/scene creation UI, an ECS redesign or source-model replacement.
- Removing attribution from inherited code or changing historic artifacts.

## Decisions

### 1. Version the enclosing documents, keep the useful payload

Version 1 requires these root members on each `.abss`, `.scene` and asset `meta.json`:

```json
{"format":"abyssus","formatVersion":1}
```

The rest of the project, scene and asset payload stays structurally the same except the two legacy ECS fields below. Metadata `version` is not the document format version. A marker is necessary even for standalone scenes; `.abss` membership alone cannot identify them. Auxiliary terrain recipes already have their own version contract and are not project/scene/asset metadata documents, so retain that contract. glTF/GLB and other external model/image formats do not receive these markers.

Use only exact `format` and integral JSON version 1; refuse missing, null, string, fractional, negative and future versions. Do not silently fill missing markers. Native new-document producers put markers first, followed by the existing payload order. Existing native documents need not reorder them. Default omission follows the current component defaults after the refactor, with no external-editor condition.

Keeping `.abss`, `.scene` and the asset layout avoids unnecessary renames while a new admission rule makes the break explicit. Reusing only metadata's `version` was rejected because projects/scenes do not share it and its meaning is different.

### 2. Make serialization independent of Java packages

The component identifiers are the existing short names (`NameComponent`, `PositionComponent`, `RenderComponent`, etc.). They are schema identifiers, independent of where their implementation moves. Retain optional entity `archetype`, block `archetypes` lists of short names and `metadata`; no class lookup or component identifier table is needed. Remove `componentIdentifiers` handling/creation from native helpers and the fixture payloads.

An asset renderable becomes:

```json
{"renderable":{"kind":"asset","shaderKey":"pbr","asset":{"type":"MODEL","assetName":"tree"}}}
```

`RenderCodec` dispatches by `kind`, not reflective class names, and writes only native kinds. Replace `MUNDUS_CLASS` with a native kind constant at the module boundary allowed by the runtime/core conventions. Unknown native kinds remain in `RenderComponent.raw` and draw nothing; no dynamic class loading. Unknown component payloads remain raw. Low-level ECS load/write APIs must also reject legacy `componentIdentifiers` and renderable `class` fields even when handed only an ECS block.

Repository-only fixture preparation maps known asset delegates to `asset` and known non-rendered editor delegates to inert native kinds (`camera-marker`, `direction-handle-marker`, `direction-line-marker`). Keep entity ids, names, positions, look-at targets and asset files. These fixture mappings are not a shipped importer. Foreign serialized classes anywhere in renderable dispatch are refused, rather than kept as an undocumented alias.

### 3. Share pure validation and gate every entry point

Add an instance `AbyssusDocumentFormat` in `core` with pure validation over `JsonNode`, an explicit document kind and structured rejection reasons. Its ECS-payload validator checks the reserved legacy fields; extension payloads are otherwise opaque, so a user string inside custom data is not a reason to reject a scene. Keep this class free of Swing, IntelliJ and GL, and inject it through the existing loading/composition roots. Do not add a singleton.

Validate before binding in project/scene readers and `SceneRenderParams`, before project enumeration, and before metadata type/UUID/reference extraction in the shared metadata reader. Native asset snapshots still use `MetaTextSource`; a rejected metadata document is unavailable and yields no usable references, with a once-per-revision diagnostic. Keep supported siblings working. Project/scene failures use the existing tree/status presentation; asset failures use existing unavailable/placeholder presentation with the localized format reason. No modal dialog.

`editSceneJson` validates the current document immediately before any mutation. Determine document kind from the target path (project, scene, asset metadata); keep the post-edit native gate too so a plugin operation cannot remove markers or write a forbidden field. Terrain transactions, recipe-driven authoring and previews check native source metadata at their existing stale-state validation boundary. Runtime load/write boundaries and direct codec tests use the same ECS validator. Add Light availability uses the same validation as execution.

`SceneFormatListener` must validate before pretty-printing, including selection changes. Rejected files stay openable in the ordinary text editor, but automatic formatting and plugin edit commands leave both document and disk unchanged. The plugin does not prohibit the user's intentional text edits.

### 4. Use the existing threading and ownership model

Pure validation is stateless and can run on the caller's thread. Background project/asset reads validate during their existing read/prepare step; document-aware plugin reads use the existing read actions. EDT actions, writer validation and the formatting listener run on the EDT and check the current document, never a stale accepted snapshot. No validator touches GL. Drawable construction/drawing remain in `GdxRuntime.withContext` on the guarded AWT render thread. Unsupported metadata is rejected before GPU build.

Test plain validation, native codec dispatch and document producers headlessly. Platform tests cover document text, guarded formatting, command/Undo behavior and UI explanations. GL/manual checks cover only the preserved viewport behavior.

### 5. Update active guidance and retain provenance separately

Update README's introduction and plugin-description block, release notes, `docs/ai`, package READMEs, source descriptions, AGENTS.md and OpenSpec config to define Abyssus's contract. Preserve README's extraction markers. Move the detailed model-fork origin narrative into `docs/third-party/gdx-model-origin.md`, with the existing commit/source/license information, and link to it from the module README. Preserve copyright/SPDX headers, bundled notices and archives; do not claim the inherited implementation was independently written.

Update main-spec purposes with editorial wording during implementation; changed requirements are only the deltas here. Audit active proposals/designs/tasks/specs for conflicting compatibility guarantees and marker-free producers: runtime extraction, design refactor, asset editing/terrain generation, weather presets, custom components, water, clouds/lighting, physics/play, ray tracing and FPS. Amend remaining tasks and expected native fixture values; preserve completed task status unless changed behavior needs re-verification. Never rewrite archived decisions to make the new format appear historic.

## Risks / Trade-offs

- [Existing projects become unsupported] → Explicitly accepted. Document the break, leave originals unchanged and supply native examples; no migration is included.
- [A direct parse/edit/formatter bypasses the gate] → Test every ingestion/mutation family, including text-tab formatting, raw ECS helpers, terrain previews and unsaved asset metadata.
- [Markers alone mask legacy dispatch] → Reject reserved class/identifier fields even in marked scenes; do not ship alias constants.
- [Fixture conversion obscures rendering regressions] → Preserve content and native inert marker entities, compare entity/asset inventories and run the existing look-at, transform, asset loading and ray checks.
- [In-flight plans restore old requirements] → Reconcile them in implementation and validate affected changes before final checks. If one is archived meanwhile, update the current main spec instead of an absent delta.

## Migration Plan

1. Complete the prerequisite refactor/runtime work, or amend its remaining artifacts if the order must change. Re-read status at apply time; do not restart already completed tasks.
2. Add shared validation, update codec/producers and native test fixtures together, then gate IDE/headless readers and mutation paths.
3. Update active guidance, descriptions and provenance organization; publish the compatibility break in Unreleased notes.
4. Verify headless, platform and manual behavior and archive this change's deltas after implementation.

There is no user-file migration and no mass rewrite on startup. Rollback is a code revert plus repository fixture revert; it does not convert documents authored under native version 1 back to the previous format. Keep user-created native data when rolling back.
