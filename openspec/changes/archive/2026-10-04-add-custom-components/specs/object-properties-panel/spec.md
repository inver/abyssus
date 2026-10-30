# Spec Delta

## MODIFIED Requirements

### Requirement: Entity and component properties

When an entity of a scene is selected the panel SHALL show a header with its name and id and one section per component it has; when a component is selected it SHALL show only that component's section. A modeled component SHALL list its fields with editors suited to the value (number, text, choice, color, entity reference, asset reference); a schema-declared component SHALL list its fields the same way, also with editors for true/false and 3D vectors, under the field labels and in the groups its schema declares; an unmodeled component, including one whose schema is unknown, SHALL be shown as read-only JSON text with a note saying the plugin does not edit it.

#### Scenario: Entity selected

- **WHEN** entity `0` (`Model 0`) of `Main Scene` is selected
- **THEN** the header shows `Model 0` and `0`, and sections show its name, type, position and render components with their values

#### Scenario: Component selected

- **WHEN** the `Light` row of an entity is selected
- **THEN** the panel shows the light's color and intensity, any supported range field, cone angle and edge softness when the entity is a spotlight

#### Scenario: Unmodeled component

- **WHEN** a `PickableComponent` is selected
- **THEN** its JSON is shown without editors and with a note that it is not edited by the plugin

#### Scenario: Schema-declared component selected

- **WHEN** the `PlaneComponent` row of entity `0` of the `Custom` scene is selected
- **THEN** the panel shows its fields under their declared labels, grouped as declared (`lineLength` under `Lines`),
  with a choice editor for `kind`, a checkbox for `hasTipWeight` and x / y / z editors for `leadout`

### Requirement: Edit components in the panel

Changing a field in the panel SHALL update that component in the scene file as the `scene-component-editing` capability defines; a rejected value SHALL leave the previous value shown with the reason beside the field. The panel SHALL offer an "Add component" choice listing the modeled and schema-declared kinds the entity lacks and a "Remove" action on each modeled or schema-declared component's section.

#### Scenario: Change a value

- **WHEN** the user sets `intensity` of a selected light to `2` and confirms
- **THEN** the scene file holds `2` for it and the panel shows `2`

#### Scenario: Invalid value

- **WHEN** the user enters `abc` for a number field
- **THEN** the field returns to its previous value, a message appears beside it and the file is unchanged

#### Scenario: Add from the panel

- **WHEN** the user chooses Add component > Light on an entity without one
- **THEN** a light section with the default values appears and the file holds the new component

#### Scenario: Remove from the panel

- **WHEN** the user chooses Remove on the Light section
- **THEN** the section disappears and the component is gone from the file

#### Scenario: Add a schema-declared component from the panel

- **WHEN** the user chooses Add component > Plane on entity `1` of the `Custom` scene
- **THEN** a Plane section with the declared defaults appears and the file holds `"PlaneComponent": {}` for entity `1`
