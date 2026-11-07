## ADDED Requirements

### Requirement: Scene lights light the models

The game SHALL light models, including the planes and the pilot, with the scene's point and spot light entities in addition to the sun and
the ambient or sky light. It SHALL use each light's colour, intensity, position, range and, for a spot, its direction, cone angle and edge softness,
read the way the editor's scene view reads them, so a light shows the same reach and cone in both. Inactive lights and lights the editor
skips as unusable SHALL be skipped. A light SHALL NOT light a surface beyond its range. When more lights reach one model than the game's
per-model budget, the strongest at that model SHALL be used, and the game SHALL NOT fail.

#### Scenario: Hangar lamps light the table

- **WHEN** plane select shows the hangar, whose lamp point lights hang over the work table
- **THEN** the table top under a lamp is brighter than with the scene's point lights left out

#### Scenario: Beyond range

- **WHEN** a point light of range 2 m hangs 5 m above a model's top
- **THEN** that model is drawn exactly as without the light

#### Scenario: Spot cone

- **WHEN** a spot light points straight down at the floor with a 45 degree cone
- **THEN** the floor inside the cone is brighter and the floor well outside it is unchanged

#### Scenario: Many lights

- **WHEN** a scene has more point lights reaching one model than the per-model budget
- **THEN** the model is drawn lit by the strongest of them and the frame is drawn without error

### Requirement: No directional light means no sun

A scene with no active directional light entity SHALL be drawn without a sun: no direct sun light and no sun shadow pass. Its surfaces are lit
only by ambient or sky light, emissive surfaces and its point and spot lights. A scene with a directional light SHALL keep its sun as before.

#### Scenario: Closed hangar

- **WHEN** the hangar, which has no directional light, is drawn
- **THEN** no sun shadow map is rendered (only its lights' own shadows) and a surface facing 45 degrees up is not brighter than the same surface facing down, apart from local lights

#### Scenario: Field keeps its sun

- **WHEN** the bundled Field, which has a directional light, is drawn at midday
- **THEN** sun-lit and shadowed surfaces are drawn as before this change

### Requirement: Lights cast shadows in a scene without a sun

In a scene without a sun, the game SHALL let its lights cast shadows. Its spot lights, up to four, the brightest first, SHALL cast
shadows from all models every frame. Its point lights nearest the scene camera, up to eight, SHALL cast shadows from the scene's
static models (not its planes or pilot), each in every direction out to its range; these shadows SHALL be drawn once while the scene
is shown. Other lights SHALL NOT cast shadows. A scene with a sun SHALL keep its sun shadow alone, as before. Turning the game setting
for these shadows off SHALL leave such a scene without shadows.

#### Scenario: The table shadows the floor

- **WHEN** the hangar is drawn from its camera with its spot lights over the work table
- **THEN** the floor under the table is darker than the same floor drawn with these shadows off

#### Scenario: The warehouse shadows itself

- **WHEN** the hangar is drawn from its camera
- **THEN** its wall behind the table, shaded by the warehouse's pillars and beams from its lamps, is darker than the same wall drawn with these shadows off
- **AND** the lamps' shadows were drawn once, not every frame

#### Scenario: The Field keeps its sun shadow only

- **WHEN** the bundled Field, which has a sun and no spot or point light, is drawn
- **THEN** its shadows are the sun's, as before this change

### Requirement: Interiors reflect themselves

When a scene has no sky, the game SHALL capture the scene's static surfaces once after its assets load, lit by its lights.
Models SHALL use that capture for their diffuse ambient and specular reflection in place of the flat ambient colour. The capture SHALL be
corrected to the scene's bounds, so a reflection on a large floor stays put as the camera moves. Planes and the pilot SHALL NOT appear in the
capture. A capture SHALL be taken again only when another scene is loaded. If the capture fails, the flat ambient colour SHALL be used and
the game SHALL NOT fail.

#### Scenario: Wet floor reflects lamps

- **WHEN** the hangar is drawn from its camera
- **THEN** the floor's metallic concrete shows brighter streaks in the direction of the reflected tube lamps than with the flat ambient colour

#### Scenario: Reflection stays put

- **WHEN** the camera moves sideways by one metre in the hangar
- **THEN** a lamp's reflection on the floor moves the way a reflection of a fixed lamp would and does not slide with the camera

#### Scenario: Sky-lit scene unchanged

- **WHEN** the Field, whose sky lights the scene, is drawn
- **THEN** no capture is taken and models are lit by the sky as before

## MODIFIED Requirements

### Requirement: Lit in linear light, shown through the output transform

The game SHALL compute lighting in linear space and SHALL write every drawn surface (terrain, models, foliage, control lines) through the
shared output transform once: in the final post pass where `control-line-post-processing` runs, else at the end of each shader. The sky SHALL
be shown as `render-output-transform` requires. The scene's sun and ambient values SHALL keep their meaning as multipliers of colour, and so
SHALL point and spot light intensities. The game's exposure SHALL be a game setting with a documented default under which the bundled field
reads as a bright midday scene without clipped sun-lit surfaces.

#### Scenario: No clipped midday surfaces

- **WHEN** the bundled field is drawn from the pilot camera at midday
- **THEN** sun-lit asphalt, dirt and roofs show distinct values (no more than 1% of their pixels at pure white) and shadowed parts of them stay above pure black

#### Scenario: Sky and ground agree

- **WHEN** the horizon is drawn with the fog on
- **THEN** the distant terrain fades to the sky's horizon colour without a visible seam

#### Scenario: Transform once with the post pass

- **WHEN** the field is drawn with the post pass on
- **THEN** no surface pixel is tone mapped or encoded in its shader, and away from silhouettes the final image matches the plain path within 2/255 when every post effect is off

### Requirement: Models are lit by the sky

When the field's sky opts in to lighting the scene, the game SHALL light PBR models with the sky's diffuse irradiance and
prefiltered specular reflection instead of the flat ambient colour, and SHALL add the sun's direct light, with its shadow, and the scene's
point and spot lights on top. A model with a metallic material SHALL show the sky in its reflection, rougher surfaces a blurrier one. When the
sky does not opt in or its lighting is not ready, models SHALL be lit by the scene's own capture as "Interiors reflect themselves" requires.
Until that capture is ready, or if it fails, they SHALL use the flat ambient colour, without a visible pop larger than the first fade.

#### Scenario: Metal reflects the sky

- **WHEN** a plane with a metallic, smooth material is drawn in the field
- **THEN** its surface shows the sky colour in the direction of the reflection vector and differs from the same plane under the flat ambient

#### Scenario: Roughness blurs reflection

- **WHEN** a smooth and a rough surface of the same metal are drawn side by side
- **THEN** the rough one's reflection is lower in contrast than the smooth one's

#### Scenario: Sky without lighting

- **WHEN** the field's sky does not opt in to lighting the scene
- **THEN** models are drawn with the scene's capture, or with the flat ambient colour until it is ready, and the game does not fail
