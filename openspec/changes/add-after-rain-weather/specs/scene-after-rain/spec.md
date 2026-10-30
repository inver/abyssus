# Spec Delta

## Purpose

Lets creators and players choose a consistent after-rain ground appearance with automatic puddles, while preserving the authored atmosphere and simulation.

## ADDED Requirements

### Requirement: Saved scene weather choice

Scene Properties SHALL offer Weather choices Default and After the rain. The choice SHALL use optional `abyssus.weather.mode` values `default` and `afterRain` in native version 1 scenes. Absent settings SHALL mean Default. Opening or selecting a scene SHALL write nothing; reopening SHALL restore its weather.

#### Scenario: Existing scene
- **WHEN** a copy of Untitled's Main Scene has no weather extension and is opened
- **THEN** Weather shows Default, the ground retains its existing appearance, and the scene text is unchanged

#### Scenario: Save after rain
- **WHEN** After the rain is selected and the scene is reopened
- **THEN** `abyssus.weather.mode` is `afterRain` and wet ground and puddles are shown again

### Requirement: Isolated undoable weather edits

Each weather change SHALL be one undoable scene edit preserving unrelated keys, formatting, number text, unknown extension data and existing water settings. Returning to Default SHALL remove the known mode field and only empty weather containers. No-op, stale conflicting or rejected edits SHALL write nothing.

#### Scenario: Undo and redo
- **WHEN** After the rain is selected in a copy of Untitled's Main Scene and then undone
- **THEN** the previous scene text and appearance return exactly, and Redo restores the wet choice

#### Scenario: Preserve extensions
- **WHEN** Default is selected in a scene containing weather extension members and lake settings
- **THEN** only the mode field is removed and those members and lake settings remain unchanged

#### Scenario: Concurrent edit
- **WHEN** the saved mode changes after the Properties control was populated and the user commits its stale value
- **THEN** the edit is refused with an explanation and the newer mode is preserved

### Requirement: Weather validation and failure isolation

Weather reads and edits SHALL validate native document identity first. Unsupported documents SHALL remain unchanged and offer no weather editing. Malformed weather containers or unknown modes in supported scenes SHALL produce an explanation, render Default and remain unrepaired; malformed containers SHALL block weather edits.

#### Scenario: Unsupported version
- **WHEN** a scene has `formatVersion: 2`
- **THEN** weather is not bound or edited and no conversion is written

#### Scenario: Invalid weather
- **WHEN** a supported scene contains `"weather": []` or an unknown mode
- **THEN** it remains usable with Default weather, reports the invalid setting and writes no repair

### Requirement: Automatic stable ground puddles

After the rain SHALL automatically place irregular shallow puddles on suitable gently sloping terrain and static ground models, including Control Line's asphalt circle. Placement SHALL be stable across cameras, frames, reopening and both hosts for the same scene geometry. It SHALL exclude steep faces, elevated roofs, foliage, animated models and existing lake/sea surfaces; users SHALL need no painting or per-puddle authoring.

#### Scenario: Asphalt and terrain
- **WHEN** After the rain is enabled on a copy of Control Line's Field scene
- **THEN** puddles appear on the asphalt flying circle and suitable surrounding ground, without rectangular water sheets or puddles on buildings, planes or vegetation

#### Scenario: Repeat placement
- **WHEN** the scene is reopened in the editor and game and the camera is moved
- **THEN** puddles occupy the same ground locations, although lighting and quality can differ between hosts

#### Scenario: Changed geometry
- **WHEN** a ground model is moved or terrain is reloaded
- **THEN** puddles follow the current eligible surface without retaining floating or obsolete patches

### Requirement: Wet appearance preserves atmosphere

After the rain SHALL darken eligible ground and show shallow reflective puddles with irregular softened edges, visible underlying texture and stronger reflections at grazing angles. The reflected sky SHALL match the displayed sky, including its clouds. Sky, lights, fog and cloud settings SHALL remain unchanged. Default SHALL restore the original ground appearance.

#### Scenario: Look along the pavement
- **WHEN** the pilot camera looks along the wet asphalt
- **THEN** puddles reflect the current sky more strongly than when viewed from above and painted markings remain identifiable

#### Scenario: Preserve the authored sky
- **WHEN** weather is switched between Default and After the rain
- **THEN** sky selection, cloud settings, sun direction, light intensities and fog stay the same and their documents are unchanged

### Requirement: Visual effects leave editing and simulation intact

Weather SHALL NOT change physics, wheel grip, takeoff, flight, scores, collision geometry, terrain heights or asset files. Puddles SHALL add no selectable entities or transform handles; picking and dropping SHALL continue to use the original ground. Editor Play SHALL use the scene's weather.

#### Scenario: Same flight
- **WHEN** identical initial conditions and control inputs run with Default and After the rain
- **THEN** flight states and scores match and only the rendered appearance differs

#### Scenario: Select through a puddle
- **WHEN** the user clicks or drops an object at a puddle-covered ground point
- **THEN** the existing ground is used and no puddle entity is selected or saved

### Requirement: Bounded rendering and recovery

Puddle work and reflection resources SHALL remain bounded independently of puddle count. Unavailable scene reflections SHALL fall back to the current sky or background; weather failures SHALL leave the scene interactive. Default SHALL require no weather rendering targets or passes. Resizing, hiding, reopening and graphics-context replacement SHALL recover without stale images or leaked targets.

#### Scenario: Many patches
- **WHEN** a large visible ground area contains many puddles
- **THEN** reflection views do not grow per puddle and all eligible visible patches retain a reflection fallback

#### Scenario: Resource failure
- **WHEN** reflection allocation fails
- **THEN** puddles use the sky/background fallback and normal scene rendering and controls continue

### Requirement: Explained ray mode fallback

While After the rain is active, an editor view requesting ray tracing SHALL display the complete raster scene and explain that wet weather currently uses raster rendering. The saved ray settings and requested mode SHALL remain intact; returning to Default SHALL allow normal ray admission to resume.

#### Scenario: Wet scene in ray mode
- **WHEN** a user selects After the rain while ray tracing is requested
- **THEN** the view shows wet ground through raster rendering with a visible reason rather than silently omitting the weather
