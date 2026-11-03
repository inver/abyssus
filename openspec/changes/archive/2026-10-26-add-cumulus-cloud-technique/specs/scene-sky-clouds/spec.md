# Spec Delta

## MODIFIED Requirements

### Requirement: Selectable cloud technique

The same cloud description SHALL be drawable by four techniques: `layered` (a flat cloud layer per band), `cumulus`
(a flat layer per band shaded as puffy heaped clouds, with bright sunlit tops, grey bases and ragged edges, at a cost
close to `layered`), `shells` (each band with thickness, parallax and darker bases) and `volumetric` (clouds
ray-marched through each band in 3D). All four SHALL show clouds in the same places with the same coverage and wind,
differing only in detail and depth.

#### Scenario: Same sky, three techniques

- **WHEN** one sky is shown with each technique in turn, without moving the camera
- **THEN** the clouds cover the same regions of the sky in all of them, and only their shape detail and thickness differ

#### Scenario: Technique from the asset

- **WHEN** a sky's cloud asset has `"technique": "volumetric"` and a scene view opens on it
- **THEN** the view draws volumetric clouds

#### Scenario: Cumulus look

- **WHEN** a sky with a heaped low band is drawn with `cumulus` in daylight
- **THEN** each cloud's sun side is bright white, its underside is a soft grey, its edge is ragged rather than a
  smooth gradient, and the sky around it stays a deep blue

### Requirement: Per-view technique override

The scene view toolbar SHALL offer a Clouds choice of *Asset*, *Layered*, *Cumulus*, *Shells* and *Volumetric*.
*Asset* (the default for every newly opened view) SHALL use the cloud asset's `technique`; any other choice SHALL
override it in that view only. The choice SHALL NOT change any file, SHALL NOT affect other open views, and SHALL be
disabled when the scene's sky has no clouds to draw.

#### Scenario: Override in one view

- **WHEN** the user picks *Layered* in one scene view while another view of the same scene is open
- **THEN** only the first view draws layered clouds, and `meta.json` is unchanged

#### Scenario: Cumulus override

- **WHEN** the user picks *Cumulus* in a scene view whose cloud asset says `shells`
- **THEN** that view draws cumulus clouds and `meta.json` is unchanged

#### Scenario: Reopen resets

- **WHEN** the view with the override is closed and opened again
- **THEN** its Clouds choice is *Asset*

#### Scenario: No clouds

- **WHEN** the scene names a procedural skybox without a cloud asset to draw, or a non-procedural skybox
- **THEN** the Clouds choice is disabled

### Requirement: Cloud failures are isolated

A cloud technique that cannot be built (a shader that does not compile, a resource that cannot be created) SHALL be
logged once and the view SHALL fall back to the next simpler technique (volumetric to shells to cumulus to layered),
then to the sky without clouds. The atmosphere and the rest of the scene SHALL still render, without a modal error.

#### Scenario: Volumetric resources unavailable

- **WHEN** the cloud asset's volumetric noise textures cannot be created
- **THEN** the view draws shells, logs the reason once, and the rest of the scene renders

#### Scenario: Cumulus shader does not compile

- **WHEN** the cumulus shader cannot be built
- **THEN** the view draws layered clouds, logs the reason once, and the rest of the scene renders
