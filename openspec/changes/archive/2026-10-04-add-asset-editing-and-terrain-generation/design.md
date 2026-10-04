# Design

## Context

See proposal.md for motivation and specs/ for acceptance behavior. AssetPropertiesPanel renders metadata with labels and rebuilds on relevant document/VFS events. EntityDetailsView has editor and validation patterns, but entity codecs do not describe asset fields. loadAssetMeta already uses unsaved text. editSceneJson accepts any JSON VirtualFile and preserves formatting through SceneJson; its name does not require a separate metadata writer.

TerrainDataReader reads z-major big-endian floats with inferred square resolution. The Untitled terrain has resolution 180, size 1600 and uv 60.0; the renderer caps resolution at 255. AssetCache loads once per name and SceneAssets has no revision invalidation. SceneFileEditor currently observes scene/project sources, not arbitrary asset files. AssetFiles also memoizes its UUID index, so invalidating only a drawable does not refresh changed texture references.

## Goals / Non-Goals

**Goals:** Keep draft generation separate from persistence; make all saved changes reversible; keep pure generation and edit logic reusable outside the IDE; propagate a consistent asset revision to rendering, picking and dependent geometry consumers.

**Non-Goals:** No GPU generation, scene placement, world object repositioning or broad component editor refactor. A grayscale heightmap is the initial preview; a transient 3D scene preview is not required. Scope exclusions are listed in proposal.md.

## Decisions

### Typed field descriptions with a pure edit model

Introduce constructor-wired asset field descriptions and an AssetMetaEditor in core, independent of Swing/GL/platform. They define paths, value kinds, effective defaults and validation; edits operate on a freshly parsed JSON tree and compare the expected previous value. Use injected JsonProcessor and preserve unknown fields. Plugin code supplies project reference choices and uses SceneJson through editSceneJson for accepted metadata edits. Refactor only the editor widgets needed to share number/text/choice/error behavior with EntityDetailsView; do not reuse ECS codecs for unrelated metadata.

Terrain validation: integer size > 0 within Int range; finite uv > 0; nullable UUID references resolving to readable TEXTURE or PIXMAP_TEXTURE assets. Cube faces accept decoded images inside the canonical asset folder, checking path boundaries and symlinks. Existing broken values remain visible until explicitly replaced. Atmosphere validation uses effective defaults and checks the complete resulting parameter set, including atmosphereRadius > planetRadius. Display omitted supported atmosphere fields as defaults; merely displaying or committing the same effective default does not materialize keys. Existing metadata edits do not update lastModified: only the requested property changes.

Alternative: generic editable rows would expose structural and identity fields without useful validation. Typed known fields plus read-only unknown fields gives coverage without treating every JSON scalar as a supported operation.

### Deterministic CPU heights and a versioned recipe

Add TerrainGenerationSettings, TerrainGenerator, TerrainHeightEncoder and TerrainRecipeCodec in core. All are ordinary injected classes with no singleton/companion additions. Wrap a pinned, licensed FastNoiseLite Java source in an injected noise sampler. Record source revision and a stable generator identifier (`opensimplex2-fbm-v1`) in the recipe; never silently substitute a different algorithm for an old identifier. Native/GPU noise adds unnecessary context and portability requirements; hand-rolling noise adds maintenance without improving this flow.

Generate OpenSimplex2 fBm using world-local coordinates x/(resolution-1)*size and z/(resolution-1)*size, frequency 1/featureSize, seed, octaves, persistence and lacunarity. Map the bounded noise interval to minHeight..maxHeight; clamp roundoff and reject nonfinite intermediates/output. Do not normalize by each map's sampled extrema, which would make height meaning depend on resolution. Noise orientation matches TerrainData's z-major layout. Encode Float heights with no header, using big-endian output. New defaults: resolution 180, size 1600, uv 1, seed 12345, feature size 200, min/max heights 0/120, octaves 5, persistence 0.5 and lacunarity 2. Existing terrains derive resolution and size from the current asset; resolution stays read-only.

Use `abyssus-terrain.recipe.json` in the terrain folder with schemaVersion, generator identifier/source revision, complete settings, size, resolution and SHA-256 of encoded height bytes. Nothing is added to meta.json. Missing recipes show defaults; malformed/unknown/mismatched recipes show a status and offer a fresh draft without writes. A size edit makes the stored recipe's geometry inputs stale until another Apply. Recipe loading does not prevent ordinary terrain loading.

### Draft state and conflict checks

Use a platform-free TerrainGenerationDraft model for settings, generation request tokens, completed preview and expected source fingerprint. Grayscale preview maps selected min/max heights to black/white. Every settings edit clears Apply eligibility; only the latest matching result is accepted. Randomize seed changes only the draft. Selection change, cancellation or disposal invalidates requests. Keep draft generation controls stable across unrelated panel refreshes; relevant external edits explicitly invalidate a draft rather than silently resetting it.

Reads, decoding, hashing, noise and height encoding run on the application pool with cancellation-preserving handling; bounded CPU loops check cancellation. Swing state, selection and event notifications run on the EDT. Before committing, recheck the metadata text, height bytes, recipe bytes and folder existence against the preview snapshot. An external edit or newer metadata document requires a new preview. Snapshot matching is rechecked inside the write command so no event gap can overwrite a newer edit.

### Binary transactions and Undo

Metadata-only edits continue through editSceneJson. Introduce a plugin-side AssetFileCommand for creation and binary/recipe replacement, because binary heights, absent files and folders cannot be represented by a JSON document edit. It prepares immutable before/after byte snapshots and registers a platform UndoableAction with file references. Give the properties panel an Undo data context for its selected files; creation uses an owning-project command context available after selection changes. Manual checks must verify Undo from the panel, not just a text editor.

Stage bytes before the write command. On the EDT in one named command, verify expected state, apply the VFS writes, register Undo only after success and publish one completed transaction notification. On an ordinary write failure, restore before snapshots and report the error; never expose a success selection or completion event for a partial asset. Undo/Redo verify that current bytes and paths match their expected snapshots. Creation Undo also scans current saved/unsaved scene references and asset references before removal; conflicts reject the operation instead of deleting changed or newly used content. Removing a created directory is allowed only when its contents exactly match the command's snapshot. Redo reuses the original UUID and bytes and refuses folder collisions.

This provides application-level rollback, not a cross-file filesystem transaction or crash journal. Use same-directory staging/replacement where available, avoid long writes on EDT (maximum heights are about 260 KB), and document an interrupted process as a remaining limitation. Cancellation before commit writes nothing; commit/rollback is treated as an indivisible operation.

### New terrain creation without placement

Add a localized New terrain action on a recognized project's Assets node using the existing project-view action pattern. Derive the owning project from the node rather than the current scene editor. Reject blank names, `.`/`..`, separators, absolute paths, invalid platform filename characters, canonical escapes and collisions (including case-insensitive filesystem collisions); recheck at commit.

Creation takes a user-chosen folder name with a fresh UUID inside metadata. Write version 1, creation-time lastModified, type TERRAIN and the fixture-established additional keys in established order: terrainFile `terrain.data`, size, uv 1.0, splatMap/base/R/G/B/A null. Verify against a pinned upstream Mundus terrain writer before shipping; any discrepancy must be resolved in the encoder and compatibility tests, without inventing new fields. New metadata is serialized using SceneJson; there is no existing document to preserve, which is another reason creation needs AssetFileCommand. Create the Assets directory if absent and undo it only if this operation created it and it remains empty.

After success refresh the project tree, select the newly created asset and display its properties. No scene file is touched. This preserves show-project-assets' read-only listing requirement and unused marking; no delta amendment is needed for that requirement.

### Explicit asset revisions and safe reload

Extend core loading APIs with explicit invalidation/revision inputs. Invalidate affected names, cancel/drop previous requests, clear failed revisions for retry and recreate AssetFiles when the UUID index may have changed. Retain unaffected drawables. Build reverse dependency information from terrain splat UUIDs; changes in texture metadata/images invalidate dependent terrains. Initially a metadata membership/UUID change may conservatively invalidate all dependent terrains in that project. Pure dependency/revision models are tested without GL.

Plugin document/VFS listeners observe owning project assets and coalesce transaction events. Capture immutable metadata text overrides on EDT (including unsaved referenced texture metadata), then pass a revision-scoped JsonProcessor/file-source adapter into core so pool reads do not reach IDE documents. On metadata save the same effective content must not trigger redundant work. Pool work prepares only; invalidation/disposal/build/upload occur inside GdxRuntime.withContext on the AWT render thread and only when GuardedGLCanvas.glSafe. Hidden views queue revisions until safe. Stale prepared work is discarded once and cannot publish under a newer revision.

SceneTerrains publishes new mesh and CPU TerrainData together. Picking, Drop (if the open change is applied) and shadow geometry revision (if available) consume that same replacement. No dependent consumer caches an old terrain after replacement. Scene sky changes use the same invalidation mechanism and preserve existing failure isolation. Ordinary external disk events support changed data, texture replacement, deletion/recreation and recipe status refresh; recipes alone do not reload a drawable.

## Risks / Trade-offs

- Binary and multi-file Undo complexity -> immutable snapshots, conflict rejection, injected failure tests and explicit panel Undo checks.
- Recipe drift after Mundus/external edits -> geometry inputs and height fingerprint; keep baked terrain usable and require a fresh preview.
- Concurrent scene-drop/shadow changes -> integration tests for latest geometry and no source edits in this planning phase; amend any actual contradictory open delta during implementation.
- Upstream metadata convention -> verify the pinned writer before shipping; fixture round trips alone prove only Abyssus compatibility.
- No 3D draft preview -> grayscale feedback first; the saved scene displays the applied surface.
- Frequent VFS/document events -> coalesce by effective revision and invalidate only affected assets; headless tests cover stale requests and separate projects.
- Third-party reproducibility -> pin source/license, golden output tests and explicit generator version rather than an unbounded dependency update.

## Migration Plan

No existing project migration. Existing assets become editable only for supported fields, and recipe files are created only after explicit Apply/Create. Unsupported assets continue to inspect as before. Rollback to an earlier plugin leaves terrain metadata/heights readable; recipe files can be ignored or removed. Review all artifacts before applying; implementation proceeds in the order in tasks.md.
