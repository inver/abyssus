# Spec Delta

## ADDED Requirements

### Requirement: Add a dummy without the IDE

`HeadlessEditing` SHALL offer `addDummy`, which adds a dummy child to an entity of a scene text with the same result as
the Add Dummy action: same fields, same unique sibling naming, same ids. It SHALL return the text unchanged when the
parent entity does not exist.

#### Scenario: Same as the action

- **WHEN** `addDummy` is called for entity `9` of a copy of `Untitled`
- **THEN** the result holds the new entity described by the `scene-dummies` capability's "What lands in the file"

#### Scenario: Unknown parent

- **WHEN** `addDummy` is called for an entity id the scene does not have
- **THEN** the text is returned unchanged
