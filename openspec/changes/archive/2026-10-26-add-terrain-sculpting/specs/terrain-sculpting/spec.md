# Spec Delta

## Purpose
Lets users shape an existing terrain by hand in the Scene view with brush tools, keeping the sculpting separate from the terrain's base heights so it can be reset or re-applied over regenerated noise.

## ADDED Requirements

### Requirement: Sculpt Terrain mode

The Scene view toolbar SHALL offer Sculpt Terrain while the selected entity is a terrain entity whose terrain asset
is readable and valid. In this mode a brush strip SHALL offer the tool, the radius in world units, the strength from 0
through 1 and the options of the chosen tool. Leaving the mode, or selecting another entity, SHALL end it. A circle
of the brush radius SHALL be drawn on the surface under the mouse and nowhere when the mouse is not over the selected
terrain. Other mouse buttons and the wheel SHALL keep navigating the view.

#### Scenario: Enter the mode
- **WHEN** entity `1` (`Terrain`) of `Main Scene` is selected and the user turns on Sculpt Terrain
- **THEN** the brush strip appears with raise/lower chosen, and move and rotate gizmos are hidden

#### Scenario: Not available
- **WHEN** the selected entity is a model, or its terrain data is unreadable or its sculpt state is externally modified
- **THEN** Sculpt Terrain is disabled with the reason

#### Scenario: Off the terrain
- **WHEN** the mouse points at the sky or at another terrain entity
- **THEN** no circle is drawn and a press does not sculpt

### Requirement: Brush tools

Each tool SHALL change heights only within the brush radius, with the effect strongest at the centre, falling smoothly
to nothing at the radius and scaled by the strength. A rotated or scaled terrain entity SHALL be sculpted under the
circle that is drawn. The tools SHALL be:

- **Raise/Lower**: raises heights; Shift lowers them.
- **Smooth**: moves heights toward the average of their neighbours.
- **Flatten**: moves heights toward the height under the point where the stroke began.
- **Set height**: moves heights toward a height typed into the strip.
- **Noise**: adds deterministic noise of a chosen amplitude and feature size, the same for the same world position.
- **Slope limit**: lowers peaks and raises pits until no slope under the brush exceeds a chosen maximum angle, within the strength.
- **Stamp**: places a project image asset as a heightmap at the clicked point with chosen size, height scale, rotation and a blend of Add, Max or Min. One click is one stamp.
- **Thermal erosion**: moves material downhill where a slope exceeds a chosen talus angle, with a chosen iteration count, deterministically.

#### Scenario: Raise a hill
- **WHEN** the user presses on flat terrain with Raise, radius `10` and strength `1`
- **THEN** heights rise at the press, most at the centre, and heights 10 or more world units away are unchanged

#### Scenario: Shift lowers
- **WHEN** the user holds Shift and drags with Raise over a hill
- **THEN** the heights under the brush go down

#### Scenario: Flatten a pad
- **WHEN** the user begins a Flatten stroke on a slope and drags across it
- **THEN** heights under the brush move toward the height at the stroke's start, and no height beyond the radius changes

#### Scenario: Deterministic noise
- **WHEN** the same Noise stroke is performed on two copies of a terrain, or undone and performed again
- **THEN** the resulting heights are identical

#### Scenario: Stamp image missing
- **WHEN** the chosen stamp image asset cannot be read
- **THEN** Stamp is disabled with the reason and no height changes

### Requirement: Live preview and one undoable stroke

Heights under the brush SHALL update in every open Scene view showing the terrain while the user drags, without
writing files. Release SHALL commit the stroke as one undoable operation. A stroke that changes no height SHALL write
nothing. Undo SHALL restore the exact previous bytes of every file the stroke wrote, or remove files it introduced; Redo
SHALL restore the stroke's result. Leaving the mode or switching entity mid-stroke SHALL commit or discard the stroke
as a whole, never partially. A failed commit SHALL restore all files and report the reason. Undo or Redo SHALL be
rejected if the files changed externally since the stroke.

#### Scenario: Two views
- **WHEN** two Scene views show the terrain and the user drags in one
- **THEN** both show the new surface while dragging

#### Scenario: Undo a stroke
- **WHEN** the user raises a hill and chooses Undo
- **THEN** `terrain.data` returns to its exact previous bytes and any sculpt files the stroke introduced are removed

#### Scenario: Empty stroke
- **WHEN** the user presses and releases Smooth on a perfectly flat area
- **THEN** no file changes and no undo step is added

#### Scenario: Failed commit
- **WHEN** writing the delta file fails after `terrain.data` was written
- **THEN** every file returns to its pre-stroke bytes and the user sees the failure

### Requirement: Delta layer storage

Sculpting SHALL be stored as an additive delta in world units, one value per terrain vertex, in the sculpt file with
the same resolution as the terrain, described by a manifest holding the schema version, the fingerprints of the delta
and of the composed heights, and whether the base is regenerable from the recipe or stored. `terrain.data` SHALL always
hold the composed heights (base plus delta), and `meta.json` SHALL NOT change. A terrain with none of these files SHALL
load and display unchanged. Heights outside the recipe's minimum and maximum SHALL be allowed after sculpting.

#### Scenario: Recipe terrain
- **WHEN** the user sculpts a terrain whose recipe matches its heights
- **THEN** the sculpt file and manifest are written, `terrain.data` holds base plus delta, no base file is written and the recipe is not rewritten

#### Scenario: Sculpt files removed
- **WHEN** the manifest and sculpt files are deleted
- **THEN** the terrain still loads and displays its saved composed heights, and sculpt state reads as none

### Requirement: Stored base for terrains without a usable recipe

When the terrain has no recipe, or the recipe is malformed, unsupported or does not match the terrain's size, resolution
or heights, the first committed stroke SHALL write the heights from before the stroke as the base file and mark the base
as stored. The base file SHALL be a square grid of big-endian floats in the terrain height format. The recipe file
SHALL NOT be changed or deleted by this.

#### Scenario: Imported terrain
- **WHEN** the user sculpts a terrain that has no recipe
- **THEN** the base file holds the pre-stroke heights, the delta holds the stroke, and one Undo removes the base, delta, manifest and restores `terrain.data`

### Requirement: Reset sculpting

The terrain panel SHALL show whether the terrain is sculpted and offer Reset sculpting. Reset SHALL set the terrain's
heights to the base (regenerated from the recipe, or read from the stored base), remove the delta, manifest and stored
base files, and be one undoable operation. Reset SHALL be unavailable when there is no sculpt state or the base cannot
be produced, with the reason shown.

#### Scenario: Reset a recipe terrain
- **WHEN** the user resets a sculpted terrain with a matching recipe
- **THEN** its heights equal a fresh generation from the recipe's settings, and the sculpt files are gone

#### Scenario: Reset a stored-base terrain
- **WHEN** the user resets a terrain sculpted from an imported base
- **THEN** its heights return to the pre-sculpt bytes

### Requirement: Unsafe sculpt state

If the composed heights, the delta or the stored base no longer match the manifest, or a file is missing, malformed,
of another resolution or from an unsupported schema, the terrain SHALL stay usable and show the reason, and sculpting
and Reset SHALL be unavailable. The panel SHALL offer Forget sculpting, which removes the sculpt files and keeps the
current heights as an unsculpted terrain, as one undoable operation.

#### Scenario: External height edit
- **WHEN** `terrain.data` is changed outside Abyssus after a sculpt
- **THEN** Sculpt Terrain is disabled with a reason, and Forget sculpting keeps the externally edited heights

### Requirement: Dependents follow the new heights

After a stroke the terrain entity's drawing, picking, shadows and shared references SHALL use the new heights.
Foliage on the terrain SHALL follow the surface (copies keep their terrain-local x and z and take height from the new
terrain). Ray-traced views and Play SHALL show the committed heights.

#### Scenario: Foliage follows
- **WHEN** a hill is raised under painted foliage
- **THEN** the copies on the hill sit on the new surface at their previous x and z

#### Scenario: Shared terrain
- **WHEN** two entities reference the sculpted terrain
- **THEN** both show the new heights and no transform changes
