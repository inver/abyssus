# Spec Delta

## ADDED Requirements

### Requirement: Volumetric clouds in the field sky

The field's sky SHALL draw its clouds with the technique its cloud asset names, and the game's cloud asset SHALL name
`volumetric`. While the game runs, if its frames take longer than 33 ms over a continuous two seconds with volumetric
clouds drawn, it SHALL draw the sky with the next simpler technique (shells, then cumulus, then layered) for the rest
of the session and write the reason once to the application log. It SHALL NOT show a modal message and SHALL NOT change
any file.

#### Scenario: Fast machine

- **WHEN** the game runs at its usual frame rate with the field loaded
- **THEN** the sky shows volumetric clouds and no step-down is logged

#### Scenario: Slow machine

- **WHEN** volumetric clouds hold the game under 30 frames a second for two seconds
- **THEN** the sky is drawn with shells from then on, one line in the log says why, and flying, scoring and the HUD
  are unaffected

#### Scenario: Volumetric unavailable

- **WHEN** the graphics context cannot build volumetric clouds
- **THEN** the sky is drawn with the next simpler technique, the reason is logged once, and the game starts
