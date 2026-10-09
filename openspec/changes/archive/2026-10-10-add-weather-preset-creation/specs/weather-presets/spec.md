# Spec Delta

## Purpose

Lets a user keep the clouds referenced by a procedural sky as a separate reusable cloud asset through an explicit,
undoable creation action in the Abyssus tree.

## ADDED Requirements

### Requirement: Create a weather preset from a sky

The Abyssus tree SHALL offer "New Weather Preset from Sky..." on a supported `SKYBOX_PROCEDURAL` asset with a
nonblank textual `additional.clouds` UUID reference. The dialog SHALL suggest `weather_<sky>` and accept a folder
name under the same rules as New Terrain. Creation SHALL snapshot the referenced project `CLOUDS` asset and select
the new asset in the tree and Properties panel.

#### Scenario: Keep a tuned sky's weather

- **WHEN** a copy of `Untitled` has `assets/clouds_storm/meta.json` with a native `CLOUDS` asset containing a low
  stratocumulus band, a mid altostratus band and a high cirrus band, and `skybox_physical` references its UUID
- **AND** the user chooses the action and creates `weather_mystorm`
- **THEN** `assets/weather_mystorm/meta.json` is created and the new cloud asset is selected in the tree and
  Properties panel

#### Scenario: No cloud reference to copy

- **WHEN** the selected row is not a supported procedural sky, or its `additional.clouds` is absent, null,
  blank or an object rather than text
- **THEN** the action is not shown

#### Scenario: Invalid or colliding name

- **WHEN** the chosen name violates the shared asset folder-name rules, including an empty name, `.`, `..`, path
  separators, `:`, reserved names, invalid characters or a collision with an existing file, folder or link
- **THEN** creation is refused with a reason and no file or folder changes, including case-insensitive collisions

### Requirement: Snapshot resolved cloud settings

The created metadata SHALL use native `format: "abyssus"`, integral `formatVersion: 1`, `version: 1`, creation time
`lastModified`, a fresh UUID and `type: "CLOUDS"`. It SHALL contain the source's stored technique and valid present
bands with every known band default explicit, using the existing cloud format. Source reads SHALL prefer unsaved
editor text. Unknown native extension data SHALL be retained in the snapshot.

#### Scenario: Snapshot defaults in canonical format

- **WHEN** the source has no technique and a low band `{"type":"cumulus"}`
- **THEN** the new asset records `"technique":"shells"` and a low band with `type: "cumulus"`, `base: 800`,
  `top: 2000`, `coverage: 0.4`, `density: 0.8`, and `wind: [4, 1]`, and has no mid or high band
- **AND** the runtime reads the new asset with those same settings

#### Scenario: Stored technique survives a view override

- **WHEN** the source specifies volumetric clouds and an open scene view overrides them to Layered
- **THEN** the new asset records `"technique":"volumetric"`

#### Scenario: Unsaved source edits are copied

- **WHEN** unsaved metadata text changes the sky's cloud UUID or a referenced source band's coverage
- **THEN** Create uses that current editor text and leaves both source documents and their disk bytes unchanged

#### Scenario: Source extension data is retained

- **WHEN** a supported source cloud document contains unknown extension members and unrelated numeric literals
- **THEN** those members, their relative key order and unchanged numeric text survive in the new snapshot

### Requirement: Refuse an unavailable weather source

Creation SHALL validate the owning native project, source sky and referenced cloud metadata before using them.
It SHALL refuse with a reason and write nothing if those documents are unsupported, the source UUID is unresolved,
the referenced asset is unreadable or not `CLOUDS`, or it has no valid band. It SHALL NOT substitute an empty preset
or interpret an inline cloud object.

#### Scenario: Missing or wrong-type source

- **WHEN** the sky references a UUID absent from its project, or a UUID belonging to a `MODEL` asset
- **THEN** creation reports that no copyable cloud asset is available and creates no folder

#### Scenario: Unreadable or unsupported metadata

- **WHEN** the owning project, sky or referenced cloud metadata is malformed or has unsupported native markers
- **THEN** creation reports the problem and leaves all documents and directories unchanged

#### Scenario: Source has no valid bands

- **WHEN** the referenced source has no bands or every band violates the existing cloud band rules
- **THEN** creation reports that there are no clouds to copy and creates no folder

### Requirement: Creation changes only the new asset

Creation SHALL be one undoable command and write only the new folder's `meta.json`. It SHALL NOT change the source
cloud asset, sky, scenes or project file. The new asset SHALL be listed with type `CLOUDS` and marked unused until
reached through a scene's sky reference.

#### Scenario: New preset starts unused

- **WHEN** `weather_mystorm` has just been created in a copy of `Untitled` and no sky names its UUID
- **THEN** it is listed as an unused cloud asset, and the source cloud metadata, `skybox_physical` metadata,
  `Main Scene.scene` and `Untitled.abss` are byte-for-byte unchanged

#### Scenario: Another sky uses the snapshot

- **WHEN** a second procedural sky is explicitly edited to reference the new asset's UUID and is used by a scene
- **THEN** it loads the snapshot's bands and technique and the snapshot is marked used

### Requirement: Guarded Undo and stable Redo

Undo SHALL remove the created folder only while its contents are unchanged and no saved or unsaved scene or asset
references it. Redo SHALL recreate the same metadata bytes, UUID and timestamp only if the target is still absent.
A refused Undo or Redo SHALL explain the conflict and leave files unchanged.

#### Scenario: Undo unchanged creation

- **WHEN** the user undoes creation of an unchanged, unreferenced snapshot
- **THEN** the folder is removed and the tree no longer lists it

#### Scenario: Undo refuses changed contents

- **WHEN** the new metadata was edited or an extra file was added to the created folder
- **THEN** Undo refuses to remove the folder and leaves its contents unchanged

#### Scenario: Undo refuses a new reference

- **WHEN** a saved or unsaved sky metadata document now references the snapshot's UUID
- **THEN** Undo refuses to remove the asset and leaves all files unchanged

#### Scenario: Redo preserves snapshot identity

- **WHEN** unchanged creation was undone and the target remains absent
- **THEN** Redo restores the same metadata bytes, UUID and creation timestamp

#### Scenario: Redo refuses a collision

- **WHEN** another file or folder occupies the snapshot's target after Undo
- **THEN** Redo reports the collision and overwrites nothing
