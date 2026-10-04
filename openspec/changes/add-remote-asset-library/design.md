# Design

## Context

See proposal.md for motivation. The plugin currently registers only the right-side Properties tool window. `ProjectLayout` locates `.abss` siblings and asset folders; `AssetFileCommand` stages file snapshots, checks conflicts, rolls back write failures and registers Undo. Its current implementation is intended for small terrain operations and holds file bytes in memory. No remote catalog client or service contract exists.

The native format validator is planned by `decouple-from-mundus`, not yet implemented. This design depends on that change, superseding the older compatibility wording still present in project context. Catalog UI can be developed independently, but native imports must not ship before the validator. Existing local inventory specs remain unchanged.

## Goals / Non-Goals

**Goals:** explicit network lifecycle, testable catalog/import models, bounded resource consumption, deterministic packages and reuse of the existing file command semantics.

**Non-Goals:** new renderer, generic model conversion, arbitrary remote filesystem synchronization or a backend implementation in this plugin repository. No generated server/client implementation is added during proposal.

## Decisions

### 1. Bottom tool window with explicit project destination

Register `Abyssus Asset Library` with `anchor="bottom"` using a factory patterned after the Properties factory and tool-window-owned disposal. The panel has a toolbar (search, type, refresh, endpoint settings), a scrollable thumbnail grid and a details area with version, license, size, included dependencies, Download and Import. Normal IntelliJ docking controls retain user placement. Use native IDE colors/components and bundle all strings.

Destination selection lists supported `.abss` projects visible in the IDE project, validates their native headers and preselects only when exactly one is eligible. Resolve roots using `ProjectLayout`; never infer a destination from a stale editor selection. No scene placement accompanies import. Download uses an output chooser, refuses an existing output file in v1 and saves via a sibling temporary file plus final move. Alternative: use the local asset tree as the remote catalog; rejected because it conflates remote items and project-owned files.

### 2. Contract-first public read-only API

The change's `openapi.yml` is a draft using OpenAPI 3.0.3 (reference: https://spec.openapis.org/oas/v3.0.3.html). Promote it to repository-root `openapi.yml` during apply and validate with a pinned OpenAPI validator in CI. Public operations use `security: []`. No upload/auth endpoint is defined.

Three JSON GET operations: filtered cursor listing, details with versions, and immutable version descriptor. The latter supplies a public ZIP URL, compressed and uncompressed sizes, SHA-256, native version and complete included-asset manifest entries. Downloads use that public URL; no fourth API operation is needed. All URLs are HTTPS except exact loopback HTTP for development. Enforce that rule after redirects too. The illustrative `.invalid` server is not a usable default. Store a configurable base URL per IDE project, outside `.abss`; an unset URL shows setup guidance.

Default catalog ordering is name ascending then asset ID. Cursor binds normalized query, type, limit and a catalog snapshot. A stale or mismatched cursor returns 400; refresh restarts at page one. Default limit 40, maximum 100. Details versions are ordered newest publication first then version ID; choose the advertised latest version once, then pin it during an operation. Version IDs are opaque immutable strings, not SemVer ranges. Error responses contain code/message/requestId, with documented 400/404/429/503 semantics and Retry-After seconds. Failed thumbnail requests use placeholders and do not fail the catalog. Alternative: offset pagination and mutable latest downloads; rejected to avoid shifting page results and ambiguous import identity.

### 3. Self-contained ZIP and manifest

Layout:

```text
manifest.json
assets/<folder>/meta.json
assets/<folder>/LICENSE.txt
assets/<folder>/<payload files and subfolders>
```

`PackageManifest` in the contract defines schemaVersion 1, root assetId/version, native format/version, included assets and an exhaustive regular-file inventory with paths, byte sizes and SHA-256. The manifest itself is covered by the archive digest and is excluded from its own file inventory. No unlisted payload files are allowed. Descriptor `includedAssets` and manifest `assets` must agree exactly; each entry identifies remote ID/version, folder, UUID, type, license and dependency UUIDs. Require one root matching descriptor identity, unique remote IDs/UUIDs/folders, and all included assets reachable from the root (cycles tolerated; no missing edges). Each asset has `LICENSE.txt`, preserving its license text after import.

The root and dependency metadata must carry `format: "abyssus"`, integer `formatVersion: 1` and the declared UUID/type. Payloads must exist for the supported loader fields, including model external resources. Native metadata bytes and payloads are copied without rewriting UUIDs. Support the currently recognized asset types in the contract. Validate dependency references using metadata knowledge shared with asset reachability; check material/texture references and model external resources beyond the currently limited local inventory walker, without changing local usage requirements. JSON reference checks are CPU-only, not renderer loads. Alternative: download dependencies recursively via network and convert raw assets on the client; rejected in v1 because closed packages make verification and import all-or-nothing.

### 4. Pure JVM models, background transfer and lifecycle

Put constructor-wired catalog DTOs/state reducers, `AssetPackageValidator` and `AssetImportPlanner` in `core` under a library package. Inject network transport, staging storage, clocks, limits and native metadata validation. No objects/companion objects in core. A JDK HTTP transport adapter in the plugin owns remote IO; no new networking framework or generated client required. Keep UI/controller/settings/disposal in plugin `assetlibrary`; leave `gdx-model` unchanged.

HTTP, ZIP streaming, hashing, image decoding and destination snapshots run on cancellable background workers. Swing changes, destination chooser and `AssetFileCommand.execute` run on EDT. Catalog request generation IDs prevent stale completion; search is debounced 300 ms and resets cursor/selection. Project disposal cancels tasks and cleans staging. One download/import at a time per panel. No network or archive work runs inside a write action. Library selection only decodes ordinary image previews; it does not load or execute shaders, models or scripts. Later scene use stays inside the existing guarded GL lifecycle.

Proposed v1 client limits: 128 MiB compressed download, 256 MiB total extracted bytes, 64 MiB per extracted file, 10,000 files, manifest up to 1 MiB, preview body up to 8 MiB and decoded dimensions up to 4096 per side. Descriptor sizes provide early rejection; actual bytes always enforce limits. Bound JSON response bodies to 2 MiB. Connection timeout 10 seconds; transfer inactivity timeout 30 seconds; explicit Retry rather than unbounded automatic retries. Store staging outside project roots, clean in finally and clean abandoned library-owned staging on next startup. Fail unsupported symlinks, absolute/drive/UNC/traversal paths, duplicate normalized paths, case collisions, nested ZIP entry tricks and extra top-level content. Resolve canonical parents and reject destination symlink traversal too.

### 5. Import transaction and conservative conflicts

The planner snapshots local metadata including unsaved document text, validates all local UUIDs and names, and stages every included file as `FileChange(Absent, Bytes)` plus created directories. Fail any included folder-name or UUID collision; no reuse, rename, replacement or UUID remapping in v1. Unreadable existing metadata blocks reliable checking. Recheck the complete asset inventory, UUID set, unsaved text and root identity on EDT immediately before executing the command; existing file preconditions alone cannot detect a new folder with a colliding UUID. File changes then retain the existing engine's commit checks. Creating an absent `assets` directory is included in the transaction.

Undo uses an extended reference guard scanning outside the imported set: references among imported assets must not block removing the whole set. Scene folder/name references, model/terrain/material UUID dependencies and unsaved text references from remaining files do block removal. Redo checks UUID conflicts again, in addition to folder/file preconditions; retain a caller-provided forward guard in the reusable command layer for this check. New filesystem state appearing externally after final checks is handled by create-new file/folder operations and failure rollback. Never follow destination symlinks or overwrite concurrent file creation. Reconcile these extensions with any transaction changes already applied elsewhere.

Cancellation is accepted until the first write; once writing starts, commit/rollback runs to completion and the UI shows that finalization is in progress. Refresh VFS/local tree once after success. Redo uses snapshots, without network requests. The existing engine offers application rollback, not crash-safe filesystem atomicity; do not claim protection from process termination mid-commit. Alternative: bypass command machinery with direct unzip into assets; rejected because it loses Undo and partial-failure handling.

## Risks / Trade-offs

- [No deployed service yet] -> configurable endpoint and a loopback fixture server permit complete testing; unset configuration is an explicit UI state.
- [Self-contained packages duplicate dependencies and repeated imports conflict] -> show included assets before import; fail clearly instead of silently modifying data. Deduplication/remapping is future scope.
- [Binary snapshots multiply memory usage] -> conservative extraction limits and one active import; profile the maximum package and lower limits if needed before shipping. Large-package disk-backed Undo is future scope.
- [Remote shaders can execute later when used in a scene] -> browsing and import do not instantiate them; keep ordinary renderer checks when users explicitly use the asset.
- [Local native format work is still pending] -> enforce prerequisite ordering and convert test copies only as part of that native change.
- [Filesystem process crash or failed rollback] -> report leftovers accurately; future journaled import can strengthen recovery.

## Migration Plan

1. Complete the native validator from `decouple-from-mundus`; retain its accepted document shape.
2. Promote and validate the contract, implement against a local fixture server, then add bottom tool window and guarded import transactions.
3. Configure a public service once its operator supplies a URL; do not ship a fictitious production endpoint.
4. No existing user file migration. Disabling the feature stops remote requests; already imported ordinary assets remain usable. Undo reverses an unused successful import.

## Open Questions

- Actual production service URL, hosting/storage technology and initial catalog contents belong to the separate server project. They do not change this contract or client scope.
