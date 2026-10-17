# Spec Delta

## ADDED Requirements

### Requirement: Every host loads the same asset kinds

The scene view and any game built on the Abyssus libraries SHALL load each asset kind the same way, from one list of
supported kinds. When an asset kind is added, the scene view and the game SHALL both load it once it is added to that
list. A host that needs only some kinds (colliders read only models and terrains) SHALL pick them from the same list,
not keep its own.

#### Scenario: Same sky in the editor and the game

- **WHEN** the scene view and a game loader each load `skybox_hdr`, `skybox_physical` and `skybox_default` of the
  `Untitled` project
- **THEN** both load an HDR sky, a procedural sky and a six-face cube respectively, and neither reports a missing loader

#### Scenario: Same terrain in the editor and the game

- **WHEN** the scene view and a game loader each load `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b` of the `Untitled`
  project
- **THEN** both load a terrain of size `1600` with texture repetition `60.0`, as its `meta.json` holds

#### Scenario: A new asset kind needs one registration

- **WHEN** a developer adds an asset kind with its own loader to the list of supported kinds
- **THEN** the scene view and a game built on the libraries both load assets of that kind, with no other change to
  either
