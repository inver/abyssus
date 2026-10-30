# Proposal

## Why

Editor reads guard native document identity, but filesystem and raw ECS loaders bypass that guard. Close this gap as the approved prerequisite of `refactor-solid-dedup`, whose metadata phase assumes validation before binding.

## What Changes

- Share the pure validator through `core` and retain editor rejection kinds and messages.
- Validate root `format: "abyssus"` and integral `formatVersion: 1` before project, scene and saved/unsaved metadata binding.
- Reject `ecs.componentIdentifiers` and renderable `class` at raw ECS boundaries, while treating unknown extension payloads as opaque.
- Preserve accepted binding, read-only behavior, unknown data, and isolation of rejected siblings.
- Document the dependency in the refactor's planning artifacts.

No migration, format version change, writer, HDR changes, ray preference changes, or metadata binder deduplication.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `abyssus-document-format`: admission is consistent inside and outside the editor, including raw ECS helpers.

## Impact

Core project/scene/asset readers; runtime ECS loader/writer/render codecs; plugin validator imports and unsaved metadata reading; regression tests and developer docs. No new dependency, singleton, IntelliJ or GL access in shared code.
