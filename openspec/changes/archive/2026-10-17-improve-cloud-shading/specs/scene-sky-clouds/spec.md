# Spec Delta

## MODIFIED Requirements

### Requirement: Clouds look like the sky they are in

Clouds SHALL be lit by the same sun as the atmosphere: bright on the sun side, darker on the underside of thick clouds,
and warm-colored when the sun is near the horizon. Thick clouds SHALL stay luminous instead of clipping to a flat shade,
and cloud surfaces SHALL show depth: thin edges and bases darker than the lit tops and sun-facing sides. Clouds toward a
low sun SHALL take its warm haze. All three techniques SHALL light clouds by the same terms, so a sky's clouds look lit
by the same sun whichever technique draws them. Each band SHALL drift continuously along its wind, higher bands moving
at their own speed. Clouds SHALL follow the camera's orientation like the atmosphere, and SHALL NOT be drawn in front of
models, terrain or editor overlays.

#### Scenario: Sunset colors

- **WHEN** the scene's sun light is rotated so the sun sits near the horizon
- **THEN** the clouds near the sun turn orange to red, like the atmosphere behind them

#### Scenario: Drift

- **WHEN** the view stays open for ten seconds without input on a sky with wind
- **THEN** the clouds have moved along their bands' wind directions, without jumps

#### Scenario: Content stays in front

- **WHEN** the camera orbits a model with clouds behind it
- **THEN** the model, terrain, grid, markers and gizmos are drawn in front of the clouds

#### Scenario: Thick clouds stay luminous

- **WHEN** the view shows an overcast low band (coverage 1) at noon
- **THEN** the covered sky is a bright grey-white, not a near-black ceiling, and its shade varies smoothly instead of
  falling to one flat value

#### Scenario: Cloud surfaces show depth

- **WHEN** the view shows scattered cumulus with the sun low to the side
- **THEN** cloud bases and thin edges are darker than the sunlit tops and cores of the same clouds

#### Scenario: Silver lining

- **WHEN** the sun sits near the horizon behind a cloud field
- **THEN** the clouds around the sun are brighter and warmer than the clouds on the opposite side of the sky

#### Scenario: One lighting, three techniques

- **WHEN** one cloud sky is drawn with layered, shells and volumetric in turn
- **THEN** all three shade the same sun side bright, the same bases dark and the same low-sun glow warm
