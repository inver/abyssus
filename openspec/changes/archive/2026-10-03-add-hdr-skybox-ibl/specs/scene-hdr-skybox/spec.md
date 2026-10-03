# Spec Delta

## Purpose

Lets a scene use a captured high dynamic range environment - a Radiance `.hdr` image in a `SKYBOX_HDR`
asset - both as its background and as the source of the environment textures it is lit by.

## ADDED Requirements

### Requirement: HDR skybox asset

A folder under the project's `assets` whose `meta.json` has `"type": "SKYBOX_HDR"` SHALL be an HDR sky. Its image
SHALL be chosen as follows: a value in `meta.json` `additional` that names an existing file ending in `.hdr` (any
case) in the folder; otherwise the only `.hdr` file in the folder; otherwise, when there are several, the first by
file name, with a warning written to the IDE log naming the file used. A folder with no `.hdr` file SHALL NOT
produce a sky.

#### Scenario: Fixture sky is recognized

- **WHEN** the `Untitled` project contains `assets/skybox_hdr` with `"type": "SKYBOX_HDR"` and the file `sky.hdr`
- **THEN** the asset is an HDR sky and `sky.hdr` is its image

#### Scenario: The .hdr is found whatever the field is called

- **WHEN** an `SKYBOX_HDR` asset's `additional` holds no file name, or names `sky.hdr` under an unexpected key
- **THEN** `sky.hdr` in the asset folder is used

#### Scenario: Several .hdr files

- **WHEN** an `SKYBOX_HDR` folder holds `b.hdr` and `a.hdr` and `additional` names neither
- **THEN** `a.hdr` is used and a warning naming it is written to the IDE log

#### Scenario: Folder without a .hdr

- **WHEN** an `SKYBOX_HDR` folder contains no file ending in `.hdr`
- **THEN** no sky is built, the problem is written to the IDE log, and the rest of the scene renders

### Requirement: Supported Radiance images

The view SHALL read Radiance images whose header starts with `#?RADIANCE` or `#?RGBE`, whose `FORMAT` is
`32-bit_rle_rgbe` or absent, and whose resolution line is `-Y <height> +X <width>`, with scanlines either flat or in
the run-length encoding Radiance writes for widths from 8 to 32767. It SHALL ignore other header lines, including
`EXPOSURE`. The image SHALL be equirectangular: its width SHALL be twice its height, and its height SHALL be at most
4096 pixels. An image wider than 4096 pixels SHALL be halved in each dimension, by averaging, until it is at most
4096 wide.

#### Scenario: Run-length encoded image

- **WHEN** `sky.hdr` is a 64 x 32 run-length encoded Radiance image
- **THEN** it decodes to the same pixel values as the same image written flat

#### Scenario: Large image is reduced

- **WHEN** the image is 8192 x 4096
- **THEN** the sky is built from a 4096 x 2048 image whose pixels average the 2 x 2 blocks of the original

#### Scenario: Wrong shape

- **WHEN** the image is 1000 x 1000
- **THEN** no sky is built and the IDE log says the image is not 2:1 equirectangular

#### Scenario: Unsupported orientation or format

- **WHEN** the resolution line is `+Y 32 +X 64`, or `FORMAT` is `32-bit_rle_xyze`
- **THEN** no sky is built and the IDE log names the unsupported header line

### Requirement: Environment built from the image

From the decoded image the view SHALL build, on the GPU, a specular environment cube of 256 x 256 pixels per face
with 6 mip levels (256 down to 8), level `n` prefiltered for GGX roughness `n / 5`, and a diffuse irradiance cube of
32 x 32 pixels per face holding the cosine-weighted average of the sky around each direction. Both SHALL use 16-bit
floating point color, so values above 1.0 are kept. The image's horizontal centre SHALL face world `-Z`, its left
and right edges `+Z`, and its top row straight up (`+Y`).

#### Scenario: Uniform sky

- **WHEN** every pixel of the image has the radiance (2.0, 2.0, 2.0)
- **THEN** every texel of every mip level of the specular cube, and of the irradiance cube, is (2.0, 2.0, 2.0)
  within 1%

#### Scenario: Orientation

- **WHEN** the image is red in its middle column and blue at its left and right edges
- **THEN** the specular cube's `-Z` face is red at its centre and its `+Z` face is blue at its centre

#### Scenario: The build does not stall the view

- **WHEN** an HDR sky is being built
- **THEN** the view keeps drawing frames, doing one build step per frame, and the scene is drawn without the sky,
  lit by its own ambient color, until the build is complete

### Requirement: HDR skybox failures are isolated

An `SKYBOX_HDR` asset that cannot be read or built SHALL be skipped and logged once, and the view SHALL fall back to
the clear color and the scene's own ambient color, with the rest of the scene still drawn. This SHALL hold for a
missing folder or `meta.json`, a missing, unreadable or truncated `.hdr`, a file that is not a supported Radiance
image, an image of the wrong shape or over the size limit, and a GPU without OpenGL 3 or without renderable 16-bit
floating point textures.

#### Scenario: Truncated file

- **WHEN** the `.hdr` of the asset a scene names is cut in half
- **THEN** no sky is drawn, the models and terrain are still drawn and lit by the ambient color, and the problem is
  written to the IDE log once

#### Scenario: Not an image

- **WHEN** the `.hdr` file holds text that is not a Radiance image
- **THEN** the scene still renders with its ambient color, and the problem is written to the IDE log

#### Scenario: Declared size too large

- **WHEN** the resolution line declares `-Y 100000 +X 200000`
- **THEN** the file is rejected from its header, without allocating the image, and the reason is logged

#### Scenario: GPU cannot build the environment

- **WHEN** the view runs without OpenGL 3, or a 16-bit floating point framebuffer is incomplete
- **THEN** the sky is skipped with a logged reason and the rest of the scene renders with its ambient color

#### Scenario: Switching to a broken sky

- **WHEN** a scene is showing a working HDR sky and the user assigns an unreadable one
- **THEN** the old sky stops being drawn and stops lighting the scene, the background is the clear color, the
  scene is lit by its ambient color, and the rest of the scene keeps rendering
