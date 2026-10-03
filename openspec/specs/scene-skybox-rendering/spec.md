# scene-skybox-rendering Specification

## Purpose

Lets users see the skybox a scene uses as the background of the read-only scene view, instead of a
flat clear color.

## Requirements

### Requirement: Skybox is displayed as the background

When a scene has `skyboxEnabled` true and `skyboxName` names a skybox asset folder in the project's
`assets` folder, the view SHALL draw that skybox behind everything else, following the camera's
orientation but not its position. A `SKYBOX` asset is built from the six face images listed in the
asset's `meta.json`; a `SKYBOX_PROCEDURAL` asset is drawn as described by the `scene-procedural-sky` capability; a
`SKYBOX_HDR` asset is drawn from its equirectangular image as described by the `scene-hdr-skybox` capability,
sampled per pixel at full image resolution, multiplied by a fixed exposure of 1.0, mapped by the ACES filmic curve
fitted by Narkowicz (`x(2.51x + 0.03) / (x(2.43x + 0.59) + 0.14)`, clamped to 0..1) and encoded with gamma 1/2.2.

#### Scenario: Skybox shown

- **WHEN** a scene enables its skybox and names `skybox_default`
- **THEN** the background is the skybox, and orbiting the camera changes which part is visible

#### Scenario: Procedural sky shown

- **WHEN** a scene enables its skybox and names `skybox_physical`
- **THEN** the background is the procedural atmosphere, and orbiting the camera changes which part is visible

#### Scenario: HDR sky shown

- **WHEN** a scene enables its skybox and names `skybox_hdr`, and its environment is built
- **THEN** the background is the sky from `sky.hdr`, and orbiting the camera changes which part is visible

#### Scenario: HDR highlights survive

- **WHEN** an HDR sky has areas of radiance 1.0, 2.0 and 4.0 side by side
- **THEN** they are drawn as three different, increasing brightnesses (about 231, 245 and 252 of 255), not one flat
  white

#### Scenario: HDR mid-grey is not washed out

- **WHEN** an HDR sky area has radiance 0.18
- **THEN** it is drawn at 140 of 255, within 3

#### Scenario: HDR image is not stretched

- **WHEN** an HDR sky is shown and the camera looks at the horizon
- **THEN** the image's horizon runs straight across the view, and the image's full width wraps once around the
  vertical axis

#### Scenario: Skybox disabled or unnamed

- **WHEN** `skyboxEnabled` is false, or `skyboxName` is null (as in `Main Scene`)
- **THEN** no skybox is drawn and the background is the scene's clear color

#### Scenario: Skybox does not occlude content

- **WHEN** a skybox and models are both shown
- **THEN** models and terrain are always drawn in front of the skybox, regardless of camera distance

### Requirement: Skybox failures are isolated

A skybox whose folder, metadata or face images are missing or unreadable SHALL be skipped and
logged; the view falls back to the clear color and everything else still renders. The same holds for a
procedural sky whose shader files are missing or do not compile, and for an HDR sky that cannot be read or built as
described by the `scene-hdr-skybox` capability.

#### Scenario: Missing face image

- **WHEN** one of the six faces named in `meta.json` does not exist
- **THEN** no skybox is drawn and the rest of the scene renders

#### Scenario: Procedural sky cannot be built

- **WHEN** the named `SKYBOX_PROCEDURAL` asset's fragment shader file does not exist
- **THEN** no skybox is drawn and the rest of the scene renders

#### Scenario: HDR sky cannot be read

- **WHEN** the named `SKYBOX_HDR` asset's `.hdr` file is truncated
- **THEN** no skybox is drawn and the rest of the scene renders
