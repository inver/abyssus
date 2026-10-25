# Spec Delta

## MODIFIED Requirements

### Requirement: Regenerate existing terrain

Apply SHALL replace only the selected existing terrain's base heights and generation recipe. When the terrain is
sculpted, Apply SHALL keep the sculpting by default, so the resulting heights are the new base plus the existing delta,
and the panel SHALL offer Discard sculpting, which makes the resulting heights exactly the new base and removes the
sculpt files. A stored base SHALL be replaced by the recipe's base. Resolution, metadata text, identity, world size,
textures and scene references SHALL remain unchanged. The panel SHALL show existing resolution as read-only and say
that kept sculpting is added on top of the new heights. Invalid existing terrain data or unsafe sculpt state SHALL
prevent regeneration with an explanation.

#### Scenario: Regenerate the fixture terrain
- **WHEN** the user applies a preview to Untitled's `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b`
- **THEN** its heights are replaced at its existing `180` by `180` resolution and its metadata, including size `1600` and uv `60.0`, is unchanged

#### Scenario: Shared terrain
- **WHEN** two entities reference the regenerated terrain
- **THEN** both display the new heights while their transforms and all other entities' positions remain unchanged

#### Scenario: Regenerate a sculpted terrain
- **WHEN** a sculpted terrain is regenerated with a new seed and sculpting is kept
- **THEN** its heights equal the new noise plus the same delta, the delta file is unchanged and the manifest fingerprints the new composed heights

#### Scenario: Discard sculpting
- **WHEN** a sculpted terrain is regenerated with Discard sculpting
- **THEN** its heights equal the new noise alone and no sculpt files remain

### Requirement: Restore generation recipes safely

Recipes SHALL save the generation settings, dimensions, generator version and fingerprint of the applied base heights.
Reopening a matching recipe SHALL restore settings. For a terrain with a valid sculpt state, a recipe SHALL be matched
against the base heights regenerated from it, not against `terrain.data`. Missing, malformed, unsupported or mismatched
recipes SHALL leave terrain usable and show a reason; draft defaults SHALL be offered without overwriting files. A changed
terrain or metadata after preview SHALL require a new preview before Apply.

#### Scenario: Reopen generated terrain
- **WHEN** the user selects a generated terrain again with matching data and recipe
- **THEN** the applied settings are restored and Regenerate preview reproduces the heights

#### Scenario: Reopen a sculpted terrain
- **WHEN** the user selects a sculpted terrain whose manifest and recipe are valid
- **THEN** the recipe's settings are restored, the panel shows it is sculpted, and Regenerate preview shows the new base plus the kept delta

#### Scenario: External height edit
- **WHEN** heights change outside Abyssus after preview generation
- **THEN** Apply is rejected without overwriting the external edit and a new preview is required

### Requirement: Undoable and failure-safe terrain writes

Regeneration, creation, sculpting strokes, Reset sculpting and Forget sculpting SHALL each be one undoable operation
available from their initiating UI. Undo SHALL restore prior heights, recipe and sculpt files, or remove the newly
created asset; Redo SHALL restore the same bytes and identity. Failed commits SHALL restore pre-operation files and
report a reason. Undo or Redo SHALL reject overwriting subsequent conflicting file changes or references to a newly
created asset.

#### Scenario: Undo regeneration without a previous recipe
- **WHEN** a terrain without a recipe is regenerated and then undone
- **THEN** its exact previous height bytes return and the newly introduced recipe is removed

#### Scenario: Undo and redo creation
- **WHEN** a newly created unreferenced terrain is undone and redone
- **THEN** Undo removes its asset folder and Redo restores the same UUID, metadata, heights and recipe

#### Scenario: Failed regeneration commit
- **WHEN** writing the recipe or a sculpt file fails after the height write begins
- **THEN** the previous height bytes, recipe and sculpt files are restored and the user sees the failure reason

#### Scenario: Conflict during creation undo
- **WHEN** another action references a created terrain before the user attempts to undo its creation
- **THEN** Undo does not delete the referenced asset and explains the conflict
