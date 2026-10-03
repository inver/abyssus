# Spec Delta

## MODIFIED Requirements

### Requirement: Entity and component properties

When an entity of a scene is selected the panel SHALL show a header with its name and id and one section per component it has; when a component is selected it SHALL show only that component's section. A modeled component SHALL list its fields with editors suited to the value (number, text, choice, color, entity reference, asset reference); an unmodeled component SHALL be shown as read-only JSON text with a note saying the plugin does not edit it.

#### Scenario: Entity selected

- **WHEN** entity `0` (`Model 0`) of `Main Scene` is selected
- **THEN** the header shows `Model 0` and `0`, and sections show its name, type, position and render components with their values

#### Scenario: Component selected

- **WHEN** the `Light` row of an entity is selected
- **THEN** the panel shows the light's color and intensity, any supported range field, and cone angle and edge softness when the entity is a spotlight

#### Scenario: Unmodeled component

- **WHEN** a `PickableComponent` is selected
- **THEN** its JSON is shown without editors and with a note that it is not edited by the plugin


## ADDED Requirements

### Requirement: Spotlight beam controls

For a selected spotlight entity or its light component, the panel SHALL expose editable Cone angle in degrees and Edge softness in percent. Cone angle SHALL mean the full cone width. These controls SHALL NOT appear for point or directional lights, and SHALL refresh after scene edits or Undo.

#### Scenario: Spotlight selected
- **WHEN** a spotlight or its Light component is selected
- **THEN** Cone angle and Edge softness controls show its effective values with their units

#### Scenario: Point light selected
- **WHEN** a point or directional light is selected
- **THEN** the panel does not offer spotlight cone or softness controls

#### Scenario: External scene edit
- **WHEN** the saved beam settings change in the scene text while the spotlight is selected
- **THEN** the controls and displayed beam refresh without reselecting it
