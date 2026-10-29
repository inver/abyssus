# Proposal

## Why

A `.scene` mixes a handful of scene settings (ambient light, fog, skybox, ray tracing) with every entity of the scene.
In Control Line's `Field.scene` the 209 entities are almost all of 143 KB, so any settings change diffs, merges and
loads with the whole entity list. Entities belong in their own document, as a plain array.

## What Changes

- **BREAKING (file format):** a scene's entities leave the `.scene` and live in a sibling file
  `scenes/<Scene>.ecs.json` whose root is a JSON array. Each element is an entity with an explicit integral `id`, the
  optional header (`type`, `flags`, `disabledComponents`) and `components` (still an object keyed by component name).
- The `.scene` keeps `format`, `formatVersion`, `id`, `name`, ambient light, fog, skybox, `abyssus` and `rayTracing`.
  A `.scene` that still holds an `ecs` member is unsupported (no importer, no implicit migration).
- The ECS file is found by naming convention, not by a pointer. The `.scene` is the format authority: the array file
  has no markers of its own and is supported only beside a supported `.scene`.
- A missing ECS file is a scene with no entities; a malformed one (not an array, duplicate or non-integral `id`, an
  entity shape the existing rules reject) makes the scene unsupported for editing and leaves both files unchanged.
- Loading (`SceneLoader`, `SceneParser`, the unsaved-buffer path, `RuntimeSceneLoader`, Play) reads both documents.
- `editSceneJson` edits one or two documents of a scene as one undoable command, so a gizmo drag, an eye toggle, an
  entity delete or Rename Scene is a single Undo. A document an edit does not change is not rewritten.
- Rename Scene renames the ECS file together with the `.scene`.
- Migrate Control Line (`Field.scene` and its constant, tests and Play export) and the other `.scene` fixtures with a
  one-time script. Shipped games carry both files.
- Docs and specs are updated; the change is applied after `typed-scene-ecs` is archived and amends its deltas where
  they place `ecs` inside the scene.

## Capabilities

### New Capabilities

- `scene-ecs-file`: where a scene's entities live, the array shape, id rules, pairing with the `.scene`, missing and
  malformed file behavior, and two-document edits.

### Modified Capabilities

- `scene-loading`: a scene loads from its `.scene` plus its ECS file, from files and from unsaved text of both.
- `abyssus-document-format`: `ecs` in a `.scene` is rejected; the ECS array file's admission rules.
- `scene-ecs-components`: entities load from the ECS file instead of the scene's `ecs` block.
- `headless-scene-editing`: the library edits and validates a scene as a document pair without the IDE.

## Impact

- Code: `lib-core` (`dto`, `scene`, `ecs`, `io`, `format`), `lib-core-editor` (`document`, `host`, `scene`, `ecs`,
  `components`, `panel`, `ray`, `headless`), `plugin-abyssus` (`dto`, `filetype`, `projectView`, `sceneview`, Rename
  Scene), `lib-game-link` (play launch file and export), `app-game-control-line` (scene path, tests, packaging).
- Data: 10 `.scene` files (Control Line plus fixtures); old-shape files become unsupported.
- Docs: `docs/ai/file-formats.md`, `docs/ai/architecture.md`, `projects/lib-core/README.md`,
  `projects/lib-core-editor/README.md`, the Control Line README.
- Depends on: `typed-scene-ecs` (finish task 4.3 and archive first).
