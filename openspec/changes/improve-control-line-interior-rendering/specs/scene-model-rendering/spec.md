## ADDED Requirements

### Requirement: Emissive strength is honoured

A model material's emissive colour SHALL be multiplied by its emissive strength (1 when the model gives none) wherever the model is drawn,
in the scene view and in a game. A strength above 1 SHALL make the surface brighter than its colour alone. A model without emissive strength
SHALL be drawn exactly as before.

#### Scenario: Strong emissive

- **WHEN** a model whose material has emissive colour `(0.2, 0.2, 0.2)` and emissive strength 5 is drawn in an unlit scene
- **THEN** its surface is drawn as bright as one with emissive colour `(1, 1, 1)` and strength 1

#### Scenario: Model without strength

- **WHEN** a model of the `Untitled` project, none of whose materials gives an emissive strength, is drawn in `Main Scene`
- **THEN** each pixel equals the pixel drawn before this requirement existed, within 1/255
