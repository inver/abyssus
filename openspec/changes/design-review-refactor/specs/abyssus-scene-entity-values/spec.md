# Spec Delta

## Purpose

Defines how the values of a scene entity's components, including the ones a file leaves out, are interpreted, so that
the scene view, the properties panel and every edit agree on what an entity is, and edits change only what they are
about.

## ADDED Requirements

### Requirement: One interpretation of component values
The scene view and the Abyssus Properties panel SHALL show the same value for every component field of an entity,
whether the file states the value or leaves it out.

#### Scenario: Stated light values agree
- **WHEN** the Untitled fixture's `Main Scene` is open in the scene view and entity `7` (a `LIGHT_DIRECTIONAL` whose
  `LightComponent.light` has color r 1, g 0.96, b 0.84 and intensity 1.2) is selected in the Abyssus view
- **THEN** the Properties panel shows color 1 / 0.96 / 0.84 and intensity 1.2
- **AND** the scene view lights the scene with that color and intensity

#### Scenario: Position values agree
- **WHEN** entity `2` (`Model 2`) of `Main Scene` is selected
- **THEN** the panel's `localPosition` is -5.250234 / 1.3647921 / 6.35708 and the view draws the model there with
  the file's `localRotation`, and scale 1 because the file omits `localScale`

### Requirement: Light defaults
A `LightComponent` value that the file omits SHALL be read as: intensity 1, color white when the `color` object is
missing, 0 for a channel missing inside a present `color` object, range 100, cone angle 45 and edge softness 0.2.
The values SHALL be the same whether the light's values are nested under `light` or sit directly in the component.

#### Scenario: Missing intensity
- **WHEN** a scene has a `LIGHT_POINT` entity whose `LightComponent` is `{"light":{"color":{"r":1,"g":1,"b":1,"a":1}}}`
- **THEN** the Properties panel shows intensity 1
- **AND** the scene view lights with intensity 1

#### Scenario: Missing color channel
- **WHEN** a light's `LightComponent` is `{"color":{"r":0.2},"intensity":2}`
- **THEN** both the panel and the view use color r 0.2, g 0, b 0 and intensity 2

#### Scenario: Missing color object
- **WHEN** a light's `LightComponent` is `{"light":{"intensity":0.5}}`
- **THEN** both the panel and the view use a white light of intensity 0.5

#### Scenario: Missing range, cone and softness
- **WHEN** entity `8` (`Spot Light 8`, `LIGHT_SPOT`) of `Main Scene` omits `range`, `coneAngle` and `edgeSoftness`
- **THEN** the panel shows range 100, cone angle 45 and edge softness 20 (percent)
- **AND** the view draws the spot with reach 100, a 45 degree cone and softness 0.2

### Requirement: Reading never writes
Opening, viewing or selecting a scene or one of its entities SHALL NOT change the scene file, including values it
omits.

#### Scenario: Defaults stay omitted
- **WHEN** `Main Scene` is opened in the scene view, and entities `7` and `8` are selected in the Abyssus view
- **THEN** `Main Scene.scene` is byte-for-byte unchanged, and neither light gains `range`, `coneAngle`,
  `edgeSoftness` or `intensity` keys it did not have

### Requirement: Transform edits keep the file's style
Moving, rotating or dropping an entity in the scene view SHALL write only the `PositionComponent` (and, for a camera,
`CameraComponent.camera`) values that changed. Every other key, its order and its number text SHALL stay as they were,
and the edit SHALL be one undoable command.

#### Scenario: Move writes only the position
- **WHEN** entity `0` (`Model 0`) of `Main Scene` is dragged along the X handle and released
- **THEN** only `ecs.entities.0.components.PositionComponent.localPosition` changes in the file
- **AND** Undo in the scene view tab restores the file to its previous text

#### Scenario: Transform number text is unchanged by this change
- **WHEN** an entity is moved so that a coordinate becomes a whole number
- **THEN** the value is written in the same form the scene view writes today (for example `2.0`)

#### Scenario: No change, no write
- **WHEN** a drag or Drop ends with the entity where it started
- **THEN** the file is not written and no command is added to the undo stack
