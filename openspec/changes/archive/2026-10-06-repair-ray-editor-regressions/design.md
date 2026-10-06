# Design

Existing failing suites provide the regression baseline; assertions remain unchanged.

1. `SceneRayEdits.edit` invokes its supplied mutation on the validated object inside `editSceneJson`; its return
   value still decides whether the one undoable command writes. Invalid, equal and superseded edits write nothing.
2. `RayViewFeed.frame` stores the settings/override signature in the same render-thread branch that increments
   revision and epoch. Converter ownership and stale-result rejection remain unchanged.
3. `RayTracing` retains explicit null integer fields rather than allowing Jackson to coerce them to zero. Defaults
   remain 256, 2097152, 1 and 0 for omitted fields. `SceneRaySettingsCodec` remains the validity authority; malformed
   settings do not block ordinary native scene parsing. No global mapper coercion policy changes.
4. `ExrLoader` exposes header-only dimensions and shares native header parsing/lifetime with decoding. The data
   window determines full image dimensions, not preview reduction. `HdrPreview` forwards this read. `hdrSkyInfo`
   catches cancellation-preserving failures and retains the selected filename with zero dimensions for an
   unreadable header. Header tests use filesystem-backed inputs, as the EXR decoder does; native allocations
   are freed in every outcome. File listing and header access stay on existing background paths.

Verification covers exact undo/redo, conflicts, two-view refresh, stopped/off workers, explicit null fallback,
original EXR dimensions, corrupt inputs and chooser publication. Full `check` and docs checking follow focused
verification. The current EXR implementation differs from the historic Radiance-only main sky spec; this change
does not silently rewrite that broader format contract.
