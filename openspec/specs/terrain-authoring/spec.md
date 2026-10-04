# terrain-authoring Specification

## Purpose

Lets users create and regenerate ordinary terrain assets from reproducible noise settings, previewing heights before committing an undoable change.

## Requirements

### Requirement: Deterministic terrain generation

Terrain generation SHALL expose an integer seed, positive finite feature size in world units, finite minimum and maximum heights with minimum less than maximum, octaves from 1 through 8, persistence from 0 through 1, and lacunarity from 1 through 4. Equal settings, dimensions and generator version SHALL produce equal finite heights within the selected range. Randomize seed SHALL be a separate action.

#### Scenario: Repeat a preview
- **WHEN** the user regenerates twice with unchanged seed and settings
- **THEN** both heightmaps and generated heights are identical

#### Scenario: Invalid settings
- **WHEN** feature size is zero, heights are nonfinite, or the height range is reversed
- **THEN** generation is disabled with a reason and no files change

### Requirement: Preview before persistence

The terrain panel and creation dialog SHALL show a grayscale heightmap preview. Regenerate preview SHALL generate draft heights without writing files. Apply or Create SHALL commit only a completed preview matching the current settings; changing settings invalidates the previous preview. Cancel, selection changes and closing the dialog SHALL discard drafts without writes.

#### Scenario: Preview and cancel
- **WHEN** the user previews new heights for Untitled's terrain and cancels
- **THEN** its metadata and terrain data remain byte-for-byte unchanged

#### Scenario: Settings changed after preview
- **WHEN** the user changes the seed after preview generation
- **THEN** Apply or Create stays disabled until a preview matching the new settings completes

### Requirement: Regenerate existing terrain

Apply SHALL replace only the selected existing terrain's height data and generation recipe. Resolution, metadata text, identity, world size, textures and scene references SHALL remain unchanged. The panel SHALL show existing resolution as read-only. Invalid existing terrain data SHALL prevent regeneration with an explanation.

#### Scenario: Regenerate the fixture terrain
- **WHEN** the user applies a preview to Untitled's `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b`
- **THEN** its heights are replaced at its existing `180` by `180` resolution and its metadata, including size `1600` and uv `60.0`, is unchanged

#### Scenario: Shared terrain
- **WHEN** two entities reference the regenerated terrain
- **THEN** both display the new heights while their transforms and all other entities' positions remain unchanged

### Requirement: Create a terrain asset

New terrain SHALL be available on a recognized project's Assets node. Users SHALL choose a unique folder name, positive integer world size, integer resolution from 2 through 255 and generation settings. Create SHALL write a fresh terrain asset, select it in the properties panel and refresh the tree. It SHALL NOT add a scene entity or modify a scene or project file.

#### Scenario: Create in Untitled
- **WHEN** the user creates `hills` with world size `1600` and resolution `180` under Untitled's Assets
- **THEN** `assets/hills` contains readable terrain metadata, height data and a generation recipe, is selected and appears unused until referenced

#### Scenario: Invalid or colliding name
- **WHEN** the user enters an empty name, path traversal, path separators or a folder name already present
- **THEN** creation is rejected with a reason and no existing folder or file changes

#### Scenario: No owning project
- **WHEN** the selection has no recognized owning project
- **THEN** New terrain is unavailable

### Requirement: Mundus-compatible terrain output

Created assets SHALL use a fresh UUID, type TERRAIN and the existing terrain metadata fields. Heights SHALL be a square grid of big-endian floats in the existing terrain binary format. Noise parameters SHALL live only in a separate Abyssus recipe. New assets SHALL start with uv 1 and null splat references and load without the recipe present.

#### Scenario: Recipe removed
- **WHEN** the recipe is removed from a newly created terrain
- **THEN** its terrain still loads and displays with its saved heights

### Requirement: Restore generation recipes safely

Recipes SHALL save the generation settings, dimensions, generator version and fingerprint of applied heights. Reopening a matching recipe SHALL restore settings. Missing, malformed, unsupported or mismatched recipes SHALL leave terrain usable and show a reason; draft defaults SHALL be offered without overwriting files. A changed terrain or metadata after preview SHALL require a new preview before Apply.

#### Scenario: Reopen generated terrain
- **WHEN** the user selects a generated terrain again with matching data and recipe
- **THEN** the applied settings are restored and Regenerate preview reproduces the heights

#### Scenario: External height edit
- **WHEN** heights change outside Abyssus after preview generation
- **THEN** Apply is rejected without overwriting the external edit and a new preview is required

### Requirement: Undoable and failure-safe terrain writes

Regeneration and creation SHALL each be one undoable operation available from their initiating UI. Undo SHALL restore prior heights and recipe, or remove the newly created asset; Redo SHALL restore the same bytes and identity. Failed commits SHALL restore pre-operation files and report a reason. Undo or Redo SHALL reject overwriting subsequent conflicting file changes or references to a newly created asset.

#### Scenario: Undo regeneration without a previous recipe
- **WHEN** a terrain without a recipe is regenerated and then undone
- **THEN** its exact previous height bytes return and the newly introduced recipe is removed

#### Scenario: Undo and redo creation
- **WHEN** a newly created unreferenced terrain is undone and redone
- **THEN** Undo removes its asset folder and Redo restores the same UUID, metadata, heights and recipe

#### Scenario: Failed regeneration commit
- **WHEN** writing the recipe fails after the height write begins
- **THEN** the previous height bytes and recipe are restored and the user sees the failure reason

#### Scenario: Conflict during creation undo
- **WHEN** another action references a created terrain before the user attempts to undo its creation
- **THEN** Undo does not delete the referenced asset and explains the conflict
