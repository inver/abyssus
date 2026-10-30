# Proposal

## Why

After test-only repairs, ray editing, frame publication, explicit-null settings and HDR dimensions still fail their
existing regression assertions. The user authorized these four production repairs as a separate prerequisite.

## What Changes

- Restore the ray setting/material mutation callback inside the existing native document command.
- Retain the settings signature after invalidating older work, so unchanged frames can publish.
- Preserve explicit null ray limits through typed scene binding for codec validation and raster fallback.
  **BREAKING:** nullable core DTO limits use boxed JVM accessors; externally compiled consumers must rebuild.
- Read EXR header dimensions for chooser details and preview labels without decoding image pixels.

## Capabilities

No new or modified requirements: this restores existing required behavior. `skip_specs: true` avoids inventing
requirements for implementation repairs. Contracts are `object-properties-panel` and native document editing,
plus the approved `add-scene-raytracing-settings` delta (invalid external settings, exact undo and view refresh).
The native fields read/written remain `rayTracing`, `ecs`, `rayTracingMaterials` and HDR metadata `additional.file`.
Native validation precedes edits and binding; no document version or implicit migration is introduced.

## Impact

Plugin ray adapters, the core `RayTracing` DTO and EXR header access; focused tests and documentation. Production
dependency packaging, backend architecture, module splits, native rendering algorithms and unrelated game work
are excluded. This is a prerequisite for `refactor-solid-dedup` integration.
