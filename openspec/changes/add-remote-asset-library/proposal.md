# Proposal

## Why

Users can inspect local project assets but cannot discover reusable assets from a remote library. A bottom Asset Library tool window and a public API contract will let them browse, download and import ready-to-use assets without leaving the editor.

## What Changes

- Add a bottom-docked Abyssus Asset Library with search, type filtering, paginated thumbnail results, details, version selection, licenses and download/import progress.
- Define a new anonymous, read-only HTTP API in `openapi.yml`, covering catalog listing, details and immutable versioned package descriptors. The service itself is developed separately.
- Download self-contained ZIP packages containing a selected asset and its complete dependency closure, verify checksums and native metadata, and import into an explicitly selected Abyssus project as one undoable operation.
- Refuse conflicting folder names or UUIDs before writing; support cancellation and retry without partial imports. Refresh the local asset tree after success; do not automatically place objects in scenes.
- Target the native document format planned in `decouple-from-mundus`; breaking legacy compatibility remains acceptable. This change adds a package manifest, not a new scene/project format.
- Out of scope: server implementation/deployment, authentication, payments, publishing/upload, ratings, automatic updates, asset conversion, collision merging, offline catalog browsing and automatic scene placement.

## Capabilities

### New Capabilities

- `remote-asset-library`: bottom panel, public catalog API, remote browsing and download state.
- `remote-asset-import`: self-contained package validation, destination selection, conflicts and undoable project import.

### Modified Capabilities

None. The existing `abyssus-project-assets` listing and read-only inventory behavior continue unchanged; imports are an explicit separate operation.

## Impact

- Plugin registration, a new `assetlibrary` UI/controller package, project-scoped settings and disposal, bundle messages and asset transaction integration.
- Plain JVM catalog/package models, validation and import planning; no IntelliJ or GL dependency in `core`. Existing `gdx-model` stays unchanged.
- Read native asset `meta.json` fields `format`, `formatVersion`, `version`, `uuid`, `type`, `additional` and referenced payload files. Imported metadata/payload bytes are preserved. Read `.abss` only to validate the destination; write neither `.abss` nor `.scene`. Stage new asset files through `AssetFileCommand`, because these are binary/file additions rather than scene JSON edits.
- Draft contract resides in this change's `openapi.yml`; implementation promotes it to repository-root `openapi.yml` with contract validation in CI. Endpoint configuration is required until an actual public service URL is available.
- Implement after the native document validator from `decouple-from-mundus` exists. Reconcile any changed integration paths from `design-review-refactor` rather than restoring older code.
- Update README, CHANGELOG and architecture/file-format/testing guidance during implementation.
