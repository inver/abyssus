# Spec Delta

## MODIFIED Requirements

### Requirement: Add Light offers directional, sun and spot

The plugin SHALL offer an Add Light action with three choices, Directional, Sun and Spot, in the Scene view toolbar and
in the right-click menu of a scene row of the Abyssus tree. The action SHALL be unavailable when the scene file cannot be
read as a scene. Availability SHALL be judged from the scene's current text, and repeated checks of an unchanged scene
SHALL NOT re-read it.

#### Scenario: Toolbar choices

- **WHEN** the user opens Add Light in the toolbar of the `Main Scene` Scene view
- **THEN** the choices are Directional, Sun and Spot

#### Scenario: Tree choices

- **WHEN** the user right-clicks the `Main Scene` row in the Abyssus tree
- **THEN** the menu has Add Light with the same three choices

#### Scenario: Unreadable scene

- **WHEN** the scene file holds text that is not valid JSON
- **THEN** Add Light is disabled and nothing is written

#### Scenario: Readable again

- **WHEN** the invalid text of `Main Scene.scene` is fixed in the text tab, without saving
- **THEN** Add Light is enabled again in the toolbar and in the tree

#### Scenario: Unchanged scene is not re-read

- **WHEN** the IDE asks for Add Light's availability many times while `Main Scene.scene` does not change
- **THEN** the scene's text is parsed at most once for all of those checks
