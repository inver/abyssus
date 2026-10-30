# Spec Delta

## ADDED Requirements

### Requirement: Scene editing works without the IDE

Validating a native scene, project or asset `meta.json`, and applying a component edit, a transform edit and an
asset-property edit to its text, SHALL work from a library on a project folder with no IDE running. For the same
input text the result SHALL be byte-identical to what the plugin writes: same key order, same number text, same
omitted defaults, and the same refusal with the same reason.

#### Scenario: Move an entity headlessly

- **WHEN** a library caller applies the transform edit that the gizmo drag writes to `Model 0` of the Untitled
  fixture's scene text, moving it to x 1
- **THEN** the returned text differs from the input only in `localPosition.x`, and equals the text the plugin
  writes for the same drag

#### Scenario: Refuse a legacy document

- **WHEN** a library caller validates a scene text that has `ecs.componentIdentifiers`
- **THEN** it is refused with the same reason the plugin shows, and no text is produced

#### Scenario: Asset property edit

- **WHEN** a library caller sets a terrain asset's `additional.size` to a positive whole number
- **THEN** only that key changes and `version`, `uuid`, `type`, `lastModified` and unknown keys are untouched

### Requirement: The editing library never depends on the IDE

The editing library SHALL load and run with only libGDX, the model and asset libraries, the runtime and Jackson on its
classpath; building it with an IntelliJ class on the classpath is not required.

#### Scenario: Build without the platform

- **WHEN** the library module's tests run
- **THEN** they pass with no IntelliJ Platform artifact on the test classpath
