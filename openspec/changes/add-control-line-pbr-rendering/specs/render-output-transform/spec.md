# Spec Delta

## Purpose

Gives rendering hosts an opt-in way to turn linear scene light into display colour (exposure, tone map, sRGB encode) so a
host can light physically and still show a correct image, without changing hosts that do not ask for it.

## ADDED Requirements

### Requirement: Output transform is opt-in and off by default

The shared rendering library SHALL offer an output transform made of an exposure, a tone map and an sRGB encode. A host
that does not turn it on SHALL get exactly the colours its shaders wrote before the transform existed, to within 1/255 per
channel. Turning it on for one host SHALL NOT change any other host.

#### Scenario: Editor view unchanged

- **WHEN** the scene view draws a lit PBR model, a terrain and foliage with the transform never enabled
- **THEN** each pixel equals the pixel drawn before this capability existed, to within 1/255 per channel

#### Scenario: Game turns it on

- **WHEN** a host enables the transform with an exposure of 1
- **THEN** every shader that host draws with ends in the same exposure, tone map and encode

### Requirement: Transform is applied once, in linear light

The transform SHALL be the last step of a fragment that is written to the screen, after lighting, fog and emissive, and SHALL
be applied exactly once to it. A pass that already writes display colour (a sky whose shader tone maps and encodes itself)
SHALL be drawn as it is, without the transform. A pixel SHALL NOT be tone mapped or encoded twice by overlapping passes (sky,
terrain, models, foliage, lines). The tone map SHALL map 0 to 0, be monotonic, and keep a mid-grey of 0.18 within 25% of 0.18 at
exposure 1 before the encode. The encode SHALL be the sRGB transfer function.

#### Scenario: Black and ordering

- **WHEN** linear values 0, 0.05, 0.18, 1 and 16 are transformed at exposure 1
- **THEN** the results are 0 for 0, strictly increasing, and all at most 1

#### Scenario: Exposure scales light

- **WHEN** the exposure is doubled for a fixed linear value below the tone map's shoulder
- **THEN** the linear value before the tone map doubles

#### Scenario: Display-referred sky is not transformed

- **WHEN** a sky that writes display colour is drawn and then the terrain and models are drawn over it with the transform on
- **THEN** a pixel of the sky that no later pass covers equals the same pixel drawn with the transform off

### Requirement: Colour inputs are linearised under the transform

When the transform is on, a colour that is authored as display colour (albedo and emissive textures, terrain layer
textures, and the scene's fog colour) SHALL be converted from sRGB to linear before it is used in lighting; material and
vertex colour factors follow the glTF convention and are used as they are, and data textures (normal, metallic-roughness,
occlusion, splat weights) SHALL NOT be converted. With the transform off nothing is converted.

#### Scenario: Albedo of 0.5 stored

- **WHEN** a PBR surface with a stored albedo of 0.5 is lit by a unit sun straight on, with the transform on
- **THEN** the lit linear albedo used is 0.2140 (the sRGB-decoded 0.5), to within 0.002

#### Scenario: Data textures untouched

- **WHEN** a metallic-roughness texel of 0.5 is sampled with the transform on
- **THEN** roughness and metallic use 0.5 as stored

### Requirement: Library stays host-neutral

The transform SHALL be a plain setting and shader include with no editor, physics, IntelliJ or ray-backend type, SHALL be
constructed and passed in rather than held as a singleton, and SHALL NOT be part of any native document.

#### Scenario: Boundary checks pass

- **WHEN** the module checks run
- **THEN** the render boundary test, `checkNoSingletons` and `checkNoEditorCode` pass

#### Scenario: No file field

- **WHEN** a `.scene`, `.abss` or asset `meta.json` is read or written
- **THEN** no output-transform field appears in it
