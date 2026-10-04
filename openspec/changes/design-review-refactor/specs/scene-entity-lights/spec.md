# Spec Delta

## MODIFIED Requirements

### Requirement: Light limits and failures

The view SHALL ignore light entities it cannot read and SHALL cap the number of lights of each
kind it applies at the renderer's supported maximum, preferring the lights nearest the camera
target, without failing.

#### Scenario: Too many lights

- **WHEN** a scene defines more lights of one kind than the renderer supports
- **THEN** the supported number is applied and the view still renders

#### Scenario: Unreadable light

- **WHEN** a light entity's component data cannot be read at all
- **THEN** that light is skipped and the others still apply

#### Scenario: Missing or malformed light values

- **WHEN** a light entity's `color` or `PositionComponent` is missing, or is not an object of numbers
- **THEN** those values take the documented defaults (see "Light values match the Properties panel"), and the light
  and the others still apply

## ADDED Requirements

### Requirement: Light values match the Properties panel

The view SHALL light the scene with the same color, intensity, range, cone angle and edge softness that the Abyssus
Properties panel shows for each light, including values the file omits. Omitted values SHALL be: intensity 1, color
white when the `color` object is missing, 0 for a channel missing inside a present `color` object, range 100, cone angle
45 and edge softness 0.2. Values nested under `light` and flat in the component SHALL be read alike.

#### Scenario: Stated values agree

- **WHEN** a test copy of Untitled's `Main Scene` has a `LightComponent` added to existing entity `7`
  (`Directional Light 7`, `LIGHT_DIRECTIONAL`) with `light.color` r 1, g 0.96, b 0.84, a 1 and `light.intensity` 1.2,
  and that entity is selected while the scene view is open
- **THEN** the Properties panel shows color 1 / 0.96 / 0.84 and intensity 1.2
- **AND** the view lights the scene with that color and intensity

#### Scenario: Missing intensity

- **WHEN** a scene has a `LIGHT_POINT` entity whose `LightComponent` is `{"light":{"color":{"r":1,"g":1,"b":1,"a":1}}}`
- **THEN** the Properties panel shows intensity 1
- **AND** the view lights with intensity 1

#### Scenario: Missing color channel

- **WHEN** a light's `LightComponent` is `{"color":{"r":0.2},"intensity":2}`
- **THEN** both the panel and the view use color r 0.2, g 0, b 0 and intensity 2

#### Scenario: Missing color object

- **WHEN** a light's `LightComponent` is `{"light":{"intensity":0.5}}`
- **THEN** both the panel and the view use a white light of intensity 0.5

#### Scenario: Omitted spotlight values

- **WHEN** a test copy of Untitled's `Main Scene` has a new entity `8` named `Spot Light 8`, with type `LIGHT_SPOT`,
  a position and `LightComponent.light` holding white color and intensity 1 but omitting `range`, `coneAngle` and
  `edgeSoftness`, and the user opens the view and selects that entity
- **THEN** the panel shows range 100, cone angle 45 and edge softness 20 (percent)
- **AND** the view draws the spot with reach 100, a 45 degree cone and softness 0.2
- **AND** the prepared copy of `Main Scene.scene` is unchanged by opening the view and selecting the entity
