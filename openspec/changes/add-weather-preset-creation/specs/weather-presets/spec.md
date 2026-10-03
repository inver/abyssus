# Spec Delta

## ADDED Requirements

### Requirement: Create a weather preset from a sky

The Abyssus tree SHALL offer "New Weather Preset from Sky..." on a `SKYBOX_PROCEDURAL` asset that has a `clouds`
object. The user SHALL choose a folder name under the same rules as other new assets. Create SHALL write a new
`WEATHER_PRESET` asset holding the sky's resolved bands, as one undoable command, select it, and SHALL NOT change the
sky, any scene or the project file.

#### Scenario: Keep a tuned sky's weather

- **WHEN** a copy of `Untitled`'s `skybox_physical` has `"clouds": {"enabled": true, "preset": "builtin:storm",
  "high": {"type": "cirrus"}}` and the user creates `weather_mystorm` from it
- **THEN** `assets/weather_mystorm/meta.json` has `"type": "WEATHER_PRESET"`, a new `uuid`, the storm preset's low and
  mid bands and the sky's cirrus high band, and it is selected in the tree and the Properties panel

#### Scenario: New preset starts unused

- **WHEN** the preset has just been created and no sky names it
- **THEN** it is listed as a weather preset and marked unused, and `skybox_physical`, `Main Scene.scene` and
  `Untitled.abss` are byte-for-byte unchanged

#### Scenario: Undo

- **WHEN** the user undoes the creation and the new folder is unchanged
- **THEN** the folder is removed and the tree no longer lists it

#### Scenario: Invalid or colliding name

- **WHEN** the user enters an empty name, a name with a path separator, `..`, a `:` (which built-in names use), or a
  folder name that already exists
- **THEN** creation is refused with a reason and no file or folder changes

#### Scenario: No clouds to copy

- **WHEN** a `SKYBOX_PROCEDURAL` asset has no `clouds` object, or the selected row is not a procedural sky
- **THEN** the action is not shown

#### Scenario: Unreadable preset reference

- **WHEN** the sky's `clouds.preset` names a missing folder
- **THEN** the dialog says that only the sky's own bands will be copied, and Create writes those
