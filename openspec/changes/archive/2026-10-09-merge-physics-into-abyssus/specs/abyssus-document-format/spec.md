## ADDED Requirements

### Requirement: Project physics switch

A project `.abss` MAY hold a top-level boolean `physicsEnabled`. When `physicsEnabled` is `true`, Abyssus edits, draws
and plays the project's physics. When it is missing or `false`, Abyssus does none of these. A value that is not a
boolean SHALL count as off and be reported once, and the file SHALL stay unchanged. Abyssus SHALL write `physicsEnabled`
only when the user changes the switch, leaving every other member, key order and number text as they were.

#### Scenario: Missing key means off
- **WHEN** the `Untitled` project, whose `Untitled.abss` has no `physicsEnabled`, is opened
- **THEN** physics is off for it and `Untitled.abss` is not modified

#### Scenario: Turning physics on writes one key
- **WHEN** the user turns physics on for a copy of `Untitled`
- **THEN** `Untitled.abss` gains `"physicsEnabled": true`, and `mainCamera`, `settings`, `activeSceneName`,
  `selectedCamera` and `name` keep their order and number text

#### Scenario: Not a boolean
- **WHEN** a project's `.abss` holds `"physicsEnabled": "yes"`
- **THEN** physics is off for it, one message names `physicsEnabled`, and the file is unchanged

#### Scenario: Unsupported project never enables physics
- **WHEN** a project has unsupported or missing native markers and `physicsEnabled: true`
- **THEN** physics remains off, a format reason is shown and the document stays unchanged

#### Scenario: Unsaved edit and Undo
- **WHEN** the user changes the boolean in the open `.abss` text and then undoes the edit without saving
- **THEN** the project's physics state follows both changes in open views without reopening them

#### Scenario: Turning physics off preserves the choice
- **WHEN** the user disables physics through the project's checkbox
- **THEN** only `physicsEnabled` becomes `false`, and unrelated text stays unchanged

#### Scenario: Standalone runtime ignores the editor switch
- **WHEN** a game or external play host loads a native project with `physicsEnabled: false`
- **THEN** its physics configuration remains controlled by the game or host rather than the editor switch
