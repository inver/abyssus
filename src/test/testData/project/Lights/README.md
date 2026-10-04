# Light fixtures

`scenes/Mundus Lights.scene` is a native Abyssus scene (`format: "abyssus"`, `formatVersion: 1`) with one directional
light (root id `1`) and one spot light (root id `4`). The file name and scene name are kept because tests and specs
refer to them by path. The scene began as a recorded sample of a third-party editor's output (provenance only: the
editor is not a dependency and its format is not supported); it was converted once to the native format with the
content kept: entity ids, names, positions, look-at links and archetypes are unchanged. No importer ships.

Each root has a direction handle and a direction line entity. Both roots use archetype `12`: Parent, Name, Type,
Position, Render, Pickable, Light, Dependencies. The default root `ParentComponent` value is omitted from the saved
components, but `ParentComponent` remains in the archetype. Handle archetype: `6`; line archetype: `14`.

Renderables use inert native kinds that the loader preserves and draws nothing for: `direction-handle-marker`,
`direction-line-marker` and `light-marker`. Both saved `LightComponent`s are empty objects, so the fixture does not
establish persistence of color, intensity or range.

`scenes/Creation Baseline.scene` is the original committed `Untitled` scene (native), copied as a stable baseline for
creation tests while the original project is being edited in a live IDE.
