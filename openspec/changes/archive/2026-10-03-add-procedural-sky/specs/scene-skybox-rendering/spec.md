# Spec Delta

## MODIFIED Requirements

### Requirement: Skybox is displayed as the background

When a scene has `skyboxEnabled` true and `skyboxName` names a skybox asset folder in the project's
`assets` folder, the view SHALL draw that skybox behind everything else, following the camera's
orientation but not its position. A `SKYBOX` asset is built from the six face images listed in the
asset's `meta.json`; a `SKYBOX_PROCEDURAL` asset is drawn as described by the `scene-procedural-sky` capability.

#### Scenario: Skybox shown

- **WHEN** a scene enables its skybox and names `skybox_default`
- **THEN** the background is the skybox, and orbiting the camera changes which part is visible

#### Scenario: Procedural sky shown

- **WHEN** a scene enables its skybox and names `skybox_physical`
- **THEN** the background is the procedural atmosphere, and orbiting the camera changes which part is visible

#### Scenario: Skybox disabled or unnamed

- **WHEN** `skyboxEnabled` is false, or `skyboxName` is null (as in `Main Scene`)
- **THEN** no skybox is drawn and the background is the scene's clear color

#### Scenario: Skybox does not occlude content

- **WHEN** a skybox and models are both shown
- **THEN** models and terrain are always drawn in front of the skybox, regardless of camera distance

### Requirement: Skybox failures are isolated

A skybox whose folder, metadata or face images are missing or unreadable SHALL be skipped and
logged; the view falls back to the clear color and everything else still renders. The same holds for a
procedural sky whose shader files are missing or do not compile.

#### Scenario: Missing face image

- **WHEN** one of the six faces named in `meta.json` does not exist
- **THEN** no skybox is drawn and the rest of the scene renders

#### Scenario: Procedural sky cannot be built

- **WHEN** the named `SKYBOX_PROCEDURAL` asset's fragment shader file does not exist
- **THEN** no skybox is drawn and the rest of the scene renders
