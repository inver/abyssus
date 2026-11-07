## Purpose

Defines how Control Line's raster game finishes a frame: it lights in linear HDR, adds bloom and ambient occlusion, and shows
the result through one output transform with a vignette and grade. It falls back to the plain path where the context cannot run this.

## ADDED Requirements

### Requirement: Frames are finished by one post pass

On a context that can run it, the game SHALL draw the lit surfaces of a frame (terrain, models, foliage, cables, lines) in linear
HDR, unclamped above 1, with 4x multisampling. It SHALL show them through one final pass that applies bloom, ambient occlusion,
exposure, the shared tone map, vignette, grade and sRGB encode, in that order, exactly once per pixel. A sky that writes display
colour SHALL show as it is where no surface covers it. The game's menus and HUD SHALL be drawn after the pass and SHALL NOT be bloomed,
occluded, vignetted or tone mapped.

#### Scenario: Bright emissive blooms

- **WHEN** the hangar is drawn from its camera with bloom on
- **THEN** the pixels around a lit tube lamp, outside the lamp's own outline, are brighter than with bloom off
- **AND** pixels far from any bright surface differ by no more than 2/255

#### Scenario: Effects off match the plain path

- **WHEN** the bundled Field is drawn with bloom, ambient occlusion, vignette and grade all off
- **THEN** each pixel away from silhouettes (whose antialiasing may differ between the HDR frame and the host framebuffer) is within 2/255 per channel of the same frame drawn through the plain path

#### Scenario: Sky shows as it is

- **WHEN** the Field is drawn with its procedural sky and the post pass on
- **THEN** a sky pixel that no surface covers equals the same pixel drawn with the post pass off, within 1/255

#### Scenario: UI is not post-processed

- **WHEN** a menu is drawn over the hangar with the vignette on
- **THEN** the menu's pixels equal those drawn with the vignette off

### Requirement: Ambient occlusion darkens creases

With ambient occlusion on, the game SHALL darken surfaces in proportion to how enclosed they are by nearby geometry in view
(wall–floor joints, under the table, between roof ribs). Open, flat surfaces SHALL keep their brightness. Bright emissive surfaces SHALL NOT be darkened.
The effect SHALL NOT bleed across depth discontinuities by more than a few pixels.

#### Scenario: Corner darker than open floor

- **WHEN** a floor meeting a wall is drawn with ambient occlusion on and off
- **THEN** the floor pixels next to the wall are darker with it on, and floor pixels far from the wall differ by no more than 2/255

#### Scenario: Lamps are not occluded

- **WHEN** the hangar's tube lamps are drawn with ambient occlusion on
- **THEN** the lamp pixels are within 2/255 of the same pixels with it off

### Requirement: Post settings and fallback

Bloom, ambient occlusion and vignette SHALL each be a game setting with a documented default. The display settings menu
SHALL offer a quality choice that switches bloom and ambient occlusion off together. The settings SHALL NOT be stored in any native document.
On a context that lacks what the post pass needs (OpenGL 3.0 and floating-point colour targets), or if creating its targets fails, the game
SHALL draw as it did before this capability, with the output transform in each shader, SHALL log the reason once, and SHALL NOT fail.

#### Scenario: No float targets

- **WHEN** the game starts on a context without floating-point colour targets
- **THEN** the field and the hangar are drawn with the per-shader output transform and one log line states why

#### Scenario: Low quality

- **WHEN** the player picks the low post quality in the display settings
- **THEN** the next frame is drawn without bloom and ambient occlusion, and the choice survives a restart through the game's own settings file

#### Scenario: Window resize

- **WHEN** the window is resized during a flight
- **THEN** the next frame is drawn at the new size without stretching and without failing

### Requirement: Post pass stays within the frame budget

The post pass SHALL keep the steady frame time of the pilot view and of the hangar within the budget recorded in the design,
measured by the repository's performance harness apart from loading. The harness SHALL report the post pass as its own span.

#### Scenario: Post span reported

- **WHEN** the performance harness draws the pilot view
- **THEN** its report lists a `post` span next to the sky, shadow, terrain, models and lines spans
