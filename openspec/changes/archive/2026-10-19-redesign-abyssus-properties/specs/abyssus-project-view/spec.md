# Spec Delta

## ADDED Requirements

### Requirement: Enabled eye on entities and components

An entity row that is not a `GROUP` and a component row of a togglable component SHALL show the eye used for scene settings, on when the `enabled` flag is absent or true and off when it is false. Clicking it SHALL write the flag as one undoable edit through the document writer. A disabled entity, its component rows and their children SHALL be drawn dimmed with gray icons.

#### Scenario: Disable an entity
- **WHEN** the user clicks the eye on the entity `Racer`
- **THEN** `Racer` gets `enabled: false`, its row and component rows are dimmed and the Properties panel shows it disabled

#### Scenario: Disable a component
- **WHEN** the user clicks the eye on `Collider`
- **THEN** only that component entry gets `enabled: false`

#### Scenario: Group and Name rows
- **WHEN** a `GROUP` entity and a `Name` component are listed
- **THEN** neither shows an eye

#### Scenario: Re-enable removes the flag
- **WHEN** the user turns an off eye on
- **THEN** the `enabled` key is removed and the rest of the entry is unchanged
