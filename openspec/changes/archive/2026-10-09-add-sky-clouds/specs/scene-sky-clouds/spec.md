# Spec Delta

## Purpose

Lets a procedural sky show realistic clouds in low, mid and high bands that drift with the wind, described once in the
sky asset and drawn by a choice of techniques that trade realism for speed.

## ADDED Requirements

### Requirement: Sky names a cloud asset

A `SKYBOX_PROCEDURAL` asset's `meta.json` `additional` MAY hold `clouds`: the `uuid` of a `CLOUDS` asset (see
`cloud-assets`), whose bands the sky draws with that asset's `technique`. A missing `clouds`, a `uuid` no asset
declares, a value that is not text, or a cloud asset with no valid band SHALL draw the sky exactly as without clouds.
A `uuid` no asset declares SHALL be logged once.

#### Scenario: Fixture sky stays cloudless

- **WHEN** a copy of `Untitled`'s `Main Scene` uses `skybox_physical`, whose `meta.json` has no `clouds`
- **THEN** the sky is drawn exactly as before this change

#### Scenario: Clouds named

- **WHEN** the copy gets a `CLOUDS` asset `clouds_fair` with `"low": {"type": "cumulus", "coverage": 0.5}`, and
  `skybox_physical`'s `additional` gains `"clouds"` set to its `uuid`
- **THEN** the view shows cumulus clouds over the atmosphere after the sky is loaded again

#### Scenario: Unknown cloud asset

- **WHEN** `clouds` holds a `uuid` that no asset of the project declares
- **THEN** the sky is drawn without clouds and the problem is logged once

### Requirement: Cloud bands

A cloud asset's bands `low`, `mid` and `high` each SHALL hold a `type`, `base` and `top` altitudes in metres, `coverage` (0 to 1), `density` (0 or more) and
`wind` (two numbers, metres per second along x and z). Types SHALL be per band: low `cumulus`, `stratus`,
`stratocumulus`; mid `altocumulus`, `altostratus`; high `cirrus`, `cirrostratus`. Altitudes SHALL be within the band:
low 300–2500, mid 2000–7000, high 6000–13000, with `base` below `top`. An omitted field SHALL take its type's default.

#### Scenario: Type defaults

- **WHEN** a cloud asset's band is `"low": {"type": "cumulus"}`
- **THEN** it uses cumulus defaults: base 800, top 2000, coverage 0.4, density 0.8 and a light wind

#### Scenario: Three levels at once

- **WHEN** the sky's cloud asset has a low `cumulus`, a mid `altocumulus` and a high `cirrus` band
- **THEN** all three are drawn, the cirrus above and behind the others, and lower bands hide the higher ones where
  they overlap

#### Scenario: Invalid band

- **WHEN** a band has a type from another band (`"low": {"type": "cirrus"}`), `base` not below `top`, an altitude
  outside its band, or a non-numeric value
- **THEN** that band is skipped and logged once, the other bands and the sky still draw, and no file is changed

### Requirement: Clouds look like the sky they are in

Clouds SHALL be lit by the same sun as the atmosphere: bright on the sun side, darker on the underside of thick clouds,
and warm-colored when the sun is near the horizon. Each band SHALL drift continuously along its wind, higher bands
moving at their own speed. Clouds SHALL follow the camera's orientation like the atmosphere, and SHALL NOT be drawn in
front of models, terrain or editor overlays.

#### Scenario: Sunset colors

- **WHEN** the scene's sun light is rotated so the sun sits near the horizon
- **THEN** the clouds near the sun turn orange to red, like the atmosphere behind them

#### Scenario: Drift

- **WHEN** the view stays open for ten seconds without input on a sky with wind
- **THEN** the clouds have moved along their bands' wind directions, without jumps

#### Scenario: Content stays in front

- **WHEN** the camera orbits a model with clouds behind it
- **THEN** the model, terrain, grid, markers and gizmos are drawn in front of the clouds

### Requirement: Selectable cloud technique

The same cloud description SHALL be drawable by three techniques: `layered` (a flat cloud layer per band), `shells`
(each band with thickness, parallax and darker bases) and `volumetric` (clouds ray-marched through each band in 3D).
All three SHALL show clouds in the same places with the same coverage and wind, differing only in detail and depth.

#### Scenario: Same sky, three techniques

- **WHEN** one sky is shown with each technique in turn, without moving the camera
- **THEN** the clouds cover the same regions of the sky in all three, and only their shape detail and thickness differ

#### Scenario: Technique from the asset

- **WHEN** a sky's cloud asset has `"technique": "volumetric"` and a scene view opens on it
- **THEN** the view draws volumetric clouds

### Requirement: Per-view technique override

The scene view toolbar SHALL offer a Clouds choice of *Asset*, *Layered*, *Shells* and *Volumetric*. *Asset* (the
default for every newly opened view) SHALL use the cloud asset's `technique`; any other choice SHALL override it in that view
only. The choice SHALL NOT change any file, SHALL NOT affect other open views, and SHALL be disabled when the scene's
sky has no clouds to draw.

#### Scenario: Override in one view

- **WHEN** the user picks *Layered* in one scene view while another view of the same scene is open
- **THEN** only the first view draws layered clouds, and `meta.json` is unchanged

#### Scenario: Reopen resets

- **WHEN** the view with the override is closed and opened again
- **THEN** its Clouds choice is *Asset*

#### Scenario: No clouds

- **WHEN** the scene names a procedural skybox without a cloud asset to draw, or a non-procedural skybox
- **THEN** the Clouds choice is disabled

### Requirement: Volumetric falls back to shells

While volumetric clouds are drawn, a view whose frames take longer than 33 ms over a continuous two seconds SHALL
switch its own override to *Shells* and show a short non-modal note saying why. The user MAY choose *Volumetric* again.
After a second fallback in the same view, the view SHALL keep shells until it is reopened.

#### Scenario: Slow machine

- **WHEN** volumetric clouds hold a view at 20 frames per second for two seconds
- **THEN** the view switches to shells, its Clouds choice shows *Shells*, and the toolbar says volumetric clouds were
  too slow

#### Scenario: Second fallback sticks

- **WHEN** the user picks *Volumetric* again and the view is again too slow for two seconds
- **THEN** the view switches to shells and the choice stays on *Shells* for as long as the view is open

#### Scenario: Fast machine

- **WHEN** volumetric clouds render within the budget
- **THEN** no fallback happens

### Requirement: Cloud failures are isolated

A cloud technique that cannot be built (a shader that does not compile, a resource that cannot be created) SHALL be
logged once and the view SHALL fall back to the next simpler technique (volumetric to shells to layered), then to the
sky without clouds. The atmosphere and the rest of the scene SHALL still render, without a modal error.

#### Scenario: Volumetric resources unavailable

- **WHEN** the cloud asset's volumetric noise textures cannot be created
- **THEN** the view draws shells, logs the reason once, and the rest of the scene renders
