# Spec Delta

## Purpose

Lets users compose Abyssus scenes with realistic seas and lakes viewed from above, using terrain-defined shorelines and persistent editable water surfaces.

## ADDED Requirements

### Requirement: Add water from presets

Users SHALL be able to add a Lake or Sea from the Scene view toolbar or a scene's tree menu. Each SHALL create a horizontal finite surface with a stable id and distinct starting size and wave settings. New surfaces SHALL be selected and independently editable; Lake SHALL start calmer and smaller than Sea.

#### Scenario: Add Lake
- **WHEN** a user adds Lake in the Scene view of a copy of `Untitled/scenes/Main Scene.scene`
- **THEN** a named Lake is added at the orbit target and selected without altering existing entities

#### Scenario: Add Sea from tree
- **WHEN** a user chooses Add Water > Sea on a scene row
- **THEN** a named Sea is created centred at the origin with larger extent and stronger waves than the Lake preset

#### Scenario: Multiple levels
- **WHEN** a user adds a Sea and a Lake and raises the Lake
- **THEN** both retain independent levels, sizes and appearance settings after reopening

### Requirement: Water persistence and compatibility

Water SHALL be saved only under `abyssus.waterSurfaces.<id>` in the scene file, with `name`, `preset`, `position`, `width`, `length`, `tint`, `clarity`, `waveAmplitude`, `waveLength`, `waveSpeed`, `foamAmount`, `foamWidth` and `enabled`. It SHALL be identified as Abyssus-only. Opening a scene SHALL write nothing; a scene without the extension SHALL have no water.

#### Scenario: Reopen water
- **WHEN** a saved scene with water is reopened in Abyssus
- **THEN** the same surfaces, ids and settings appear without a file rewrite

#### Scenario: Existing Mundus data survives
- **WHEN** water is added to the Untitled Main Scene
- **THEN** its existing keys, numbers, formatting, ECS archetypes and component identifiers remain unchanged, and neither the .abss nor asset meta.json files are modified

### Requirement: Water edits are undoable and isolated

Adding, editing, renaming, hiding or removing water SHALL be one named undoable scene edit, changing only the targeted extension values. Unknown extension fields SHALL be preserved. No-op and rejected edits SHALL write nothing. Removal SHALL not remove unrelated `abyssus` data.

#### Scenario: Undo creation
- **WHEN** a user adds a Lake then invokes Undo
- **THEN** the entire scene text is restored to its previous content and the Lake disappears

#### Scenario: Undo removal
- **WHEN** a user removes a water surface then invokes Undo
- **THEN** its id, settings, unknown fields and previous scene text are restored

### Requirement: Water selection and movement

A click on exposed enabled water SHALL select its row and highlight its extent when it is the nearest visible target. Terrain above water SHALL remain selectable. Move handles SHALL preview and commit water position, and Esc SHALL cancel without writing. Water SHALL offer neither rotation nor Drop and SHALL not count as ground for dropping other objects.

#### Scenario: Select water
- **WHEN** the user clicks exposed Lake water with no nearer object
- **THEN** that Lake is selected in the view and tree, highlighted and shown in Properties

#### Scenario: Terrain island
- **WHEN** the user clicks terrain above the water level
- **THEN** the terrain is selected instead of the water rectangle behind it

#### Scenario: Move water level
- **WHEN** a user drags a Lake's Y move handle and releases
- **THEN** its water level follows the preview and the completed move writes only its position as one undoable edit

#### Scenario: Cancel move
- **WHEN** a user presses Esc during a water move
- **THEN** the original position returns and the scene text is unchanged

### Requirement: Terrain defines shorelines

Water SHALL occupy its horizontal rectangular extent, remain hidden behind terrain above its level and reveal land and islands without modifying terrain heights. No custom boundary drawing SHALL be required. Sea SHALL be a large bounded surface rather than an infinite ocean.

#### Scenario: Flood a terrain basin
- **WHEN** a Lake spans a terrain basin and its level is raised
- **THEN** lower areas become submerged and the shoreline moves up the terrain while the terrain data remains unchanged

### Requirement: Animated waves and reflections

Above-water viewing SHALL show animated waves and wave-distorted reflections of sky and opaque or alpha-tested models and terrain above each surface. Reflections SHALL grow stronger at grazing angles and follow the displayed camera, animation pose, lights and transform previews. Editor grids, markers, highlights, gizmos and other water surfaces SHALL be absent from reflections.

#### Scenario: Reflected moving model
- **WHEN** a model above a Lake is animated or moved with a gizmo
- **THEN** its reflection follows the same displayed pose and position without a second animation advance

#### Scenario: Look along the surface
- **WHEN** the camera moves from looking down to looking along the water
- **THEN** reflection becomes stronger and wave motion distorts it continuously

### Requirement: Visible shallow water

Shallow water SHALL reveal submerged opaque or alpha-tested scene geometry with wave-distorted refraction. Increasing water depth SHALL attenuate that geometry toward the water tint; increasing clarity SHALL make it visible through greater depth. Above-water objects SHALL not appear smeared into submerged areas by refraction.

#### Scenario: Shallow and deep bottom
- **WHEN** a Lake covers a sloping terrain with a shallow shore and a deep centre
- **THEN** bottom detail is more visible near shore and fades toward the centre

#### Scenario: Increase clarity
- **WHEN** a user increases clarity
- **THEN** submerged terrain remains visible at greater depth without moving the shoreline

### Requirement: Shoreline foam

Water SHALL show animated foam concentrated where terrain meets shallow water. Foam width and amount SHALL be adjustable; zero amount SHALL disable foam. Open deep water and rectangular extent edges without terrain contact SHALL not generate shoreline foam.

#### Scenario: Foam follows level
- **WHEN** water level changes in a terrain basin
- **THEN** the foam band follows the new terrain shoreline

#### Scenario: Deep open water
- **WHEN** a Sea surface extends over deep water or empty space
- **THEN** no shoreline foam appears at its artificial extent edges

### Requirement: Bounded rendering and failure isolation

Water rendering SHALL use a bounded number of reflection views, keep every valid visible surface rendered and use a sky or tint reflection fallback beyond that budget. Invalid surfaces or unavailable water rendering resources SHALL not hide or freeze the remaining scene. Camera positions at or below a surface SHALL not require underwater effects or crash the view.

#### Scenario: Reflection budget exceeded
- **WHEN** more distinct visible water levels exist than full reflection capacity
- **THEN** a deterministic subset reflects scene geometry and remaining surfaces use the fallback without flickering allocation

#### Scenario: Invalid surface
- **WHEN** one saved surface has invalid size or settings
- **THEN** it is skipped with a logged reason while other surfaces and scene content remain usable and no automatic repair is written

#### Scenario: Context replacement
- **WHEN** a scene view is resized, hidden then shown, or its graphics context is replaced
- **THEN** rendering resumes without stale reflection images or invalid graphics resources

