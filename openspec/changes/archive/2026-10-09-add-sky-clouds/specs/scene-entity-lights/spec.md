# Spec Delta

## ADDED Requirements

### Requirement: Clouds dim the sun

While the scene's sky draws clouds, the scene's sun SHALL be dimmed by the clouds between the orbit target and
the sun. The sun is the brightest directional light, the one that positions the procedural sun. Its intensity SHALL be
multiplied by the clouds' transmittance along the sun direction, never below 10%, and SHALL change smoothly as clouds
drift. Other lights, the ambient light and the HDR environment SHALL be unaffected, and no file SHALL change.

#### Scenario: A cloud covers the sun

- **WHEN** a dense low cloud drifts between the orbit target and the sun in a copy of `Main Scene` (whose entity `7` is
  the sun)
- **THEN** surfaces lit by entity `7` darken while the cloud passes, and brighten again after it

#### Scenario: Other lights unaffected

- **WHEN** the sun is covered and `Spot Light 8` lights part of the terrain
- **THEN** the spot light's contribution is unchanged

#### Scenario: Clear sky

- **WHEN** no cloud lies along the sun direction, or the sky has no clouds to draw
- **THEN** entity `7` lights the scene at its full intensity, as before this change

#### Scenario: Storm floor

- **WHEN** the sky's cloud asset holds dense, thick storm clouds (the `storm` template) and the sun is fully covered
- **THEN** the sun light is dimmed to no less than 10% of its intensity
