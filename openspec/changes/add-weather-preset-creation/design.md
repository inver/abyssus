# Design

## Context

See `proposal.md` for the motivation and the two delta specs for behavior.

Current sources provide:
- `ProceduralSkyMeta.cloudsReference`: a nonblank text UUID pointing to a project `CLOUDS` asset.
- `CloudMeta`, `CloudBand`, `CloudType` defaults and `CloudTechnique` in `lib-core`; `AssetMetaBinder` binds cloud
  metadata. A sky loads its referenced cloud asset as a dependency and reads its built settings.
- `AssetMetaReader` in `lib-core-editor`, which admits native metadata before binding, and `SceneJson`, which
  preserves raw number text and key order.
- `checkFolderName` and `uniqueAssetUuid` in `lib-core-editor/terrain`. The validator already rejects colons,
  separators, reserved names, escaping paths and case-insensitive collisions.
- `AssetTransaction`, `FileChange`, `FileSnapshot`, `AssetReferenceGuard` and `AssetFileCommand.execute` in
  the plugin's `assetfiles` package; `NewTerrainFactory` demonstrates staging a new asset.
- `selectAssetInAbyssusView`, which refreshes the tree, selects the row and shows its properties.

There is a source/spec gap to address before writing a snapshot: specs and bundled cloud templates use lowercase
technique/type keys and a `wind` array. `CloudSettingsReaderTest` currently exercises uppercase enum names,
explicit `level` and `windX`/`windZ`, and rejects malformed bands as a whole. Do not treat the templates as proof
that the current runtime can bind a newly generated canonical asset.

## Goals / Non-Goals

**Goals:** compose existing readers and asset transactions; produce a cloud snapshot that the runtime actually
loads; keep the draft headless and testable without Swing, platform services, noise generation or GL.

**Non-Goals:** a new asset type or loading graph, a preset resolver/merge system, or capturing transient view state.

## Decisions

### 1. Resolve source documents by UUID and admit them before use

The plugin reads the owning project, sky and project asset metadata with unsaved document text (`textOf`) taking
precedence. Validate the `.abss` as a project document. Parse source metadata through `SceneJson` and admit it with
`AssetMetaReader`; verify the sky is `SKYBOX_PROCEDURAL` and the resolved source is `CLOUDS`.

Resolve the sky's textual `additional.clouds` against supported project asset metadata UUIDs, using the same
project scope as the existing listing. Supply admitted source trees to the headless draft rather than passing IDE
services into it. A missing, unreadable or wrong-type source, or a source with no valid band, produces a localized
reason and no transaction. There are no sky-local bands to copy as a fallback.

The action is visible for a supported procedural sky row with a nonblank textual reference. Source errors are
reported when invoked; a missing/nontextual reference or another row hides the action. Re-read and validate the
source when the user confirms Create so stale tree data cannot determine the snapshot.

*Alternative:* read the live built sky. Rejected: that requires rendering state, may lag unsaved text, and would
couple creation to GL and noise generation.

### 2. Share canonical cloud decoding and keep editing serialization in lib-core-editor

First prove the spec/template representation binds through `AssetMetaBinder` and its default `JsonProcessor`.
If it does not, correct the cloud reader in `lib-core` for lowercase keys, band level inferred from its container,
`wind: [x, z]`, type defaults and the existing per-band validity rules. Runtime loading and the draft must use the
same decoded `CloudMeta`; do not introduce an editor-only decoder that produces a different cloud setup.
Reader corrections must not rewrite any input file and must retain unknown native extension trees in the editor.

Place constructor-wired `WeatherPresetDraft` and its writer in
`net.nevinsky.abyssus.lib.core.editor.weather`. They receive document/JSON collaborators and source trees;
UUID generation and the clock remain injected at the staging boundary. They return either resolved settings and
new metadata text or a reason key, without file writes or platform dependencies. Use injected `EditorMessages`
for text produced in editor-core and `AbyssusBundle` for the plugin's dialog and command strings.

The writer creates root fields in this order:
`format`, `formatVersion`, `version`, `lastModified`, `uuid`, `type`, `additional`.
The type is `CLOUDS`. `additional` contains the stored technique (`shells` when omitted) and only valid, present
bands in low/mid/high order. Each copied band explicitly records `type`, `base`, `top`, `coverage`, `density` and
`wind`, using canonical lowercase keys and all resolved defaults. Serialize through `SceneJson`; retain source
number literals for unchanged supplied fields, and format newly materialized defaults deterministically.

Retain unknown native extension members from the source cloud tree in their existing relative order, replacing
only the new asset's identity/timestamp and intended known snapshot values. Default materialization applies only
to the new document. Do not copy the source UUID, a view override, renderer fields, explicit runtime `level`, or
3D noise resources into canonical band output. Known alternate runtime field spellings are decoded settings,
not opaque extension members; test their handling alongside canonical input without migrating source files.

*Alternative:* copy the metadata bytes. Rejected: it would reuse the UUID and preserve dependence on future defaults.

### 3. Stage immutable bytes and reuse the existing creation transaction

Follow the New Terrain pattern with a metadata-only staged asset:
1. Trim and validate the requested folder name with `checkFolderName`, including at staging time.
2. Choose a fresh UUID through `uniqueAssetUuid`; stage the draft with an injected creation timestamp.
3. Construct an `AssetTransaction` named with the localized New Weather Preset command. Its single `FileChange`
   is `assets/<name>/meta.json`, from `FileSnapshot.Absent` to immutable metadata bytes. Include the asset folder
   in `createdDirs`, and `assets` only if it must be created.
4. Set the undo guard to `AssetReferenceGuard(projectDir).blocker(name, uuid)`, covering saved and unsaved
   references. Execute through `AssetFileCommand(project, LocalAssetFileStore(projectDir)).execute`.
5. After successful VFS refresh, schedule `selectAssetInAbyssusView(project, abss, name)` as New Terrain does.
   Map collision, conflict, blocked, cancellation and failure results to localized messages.

This deliberately reuses the existing write path for creating a previously absent folder. `editSceneJson` remains
required for edits to existing scene/project/metadata documents; this operation edits none of them.
Undo removes only an unchanged, unreferenced created folder and refuses extra files or newer edits. Redo uses the
same transaction, bytes, UUID and timestamp, and refuses a new collision. No automatic sky assignment is staged.

*Alternative:* create directly through VFS. Rejected: it would duplicate rollback, expected-state and undo rules.

### 4. Threading and UI

Use EDT action updates, dialog presentation, validation and command execution, matching New Terrain. Capture VFS
and unsaved document text in a read action on the EDT. Draft resolution and serialization are synchronous CPU
operations over small metadata snapshots. Do not call `CloudsLoader.prepare`, generate noise, or touch libGDX/GL.
The dialog suggests `weather_<sky>` and enables Create only for a valid name and a copyable source.

## Risks / Trade-offs

- Canonical metadata is not covered by current binding tests → add runtime round-trip coverage and the targeted
  reader correction before the draft/action; follow the existing spec rather than changing it to match the gap.
- The source changes while the dialog is open → re-read it on Create and refuse if it is no longer copyable.
- Defaults change later → intentional explicit snapshot values preserve the chosen weather.
- New references or edits appear after creation → use the existing transaction verification and reference guard
  for Undo/Redo, including unsaved editor references.

## Migration Plan

No document migration or format-version change. Deployment registers the action once generated metadata passes
the runtime round-trip gate. Removing the action leaves created assets as ordinary native `CLOUDS` assets.
