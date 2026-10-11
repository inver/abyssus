# abyssus-document-format Specification

## Purpose

Defines the documents Abyssus accepts and writes as an independent libGDX editor, without requiring compatibility with another editor or serialized implementation class names.

## Requirements

### Requirement: Native document identity

Project `.abss`, scene `.scene` and asset `meta.json` documents SHALL require root `format` equal to `abyssus` and integer `formatVersion` equal to `1`. Extensions and the project layout SHALL remain `.abss` beside `scenes/` and `assets/`. Asset `version` SHALL retain its existing metadata meaning independently of `formatVersion`.

#### Scenario: Native project
- **WHEN** a native `Untitled.abss`, its native `scenes/Main Scene.scene` and native asset metadata are opened
- **THEN** the project, scene and assets are available through the existing tree, properties and scene view

#### Scenario: Independent metadata version
- **WHEN** an asset has `format: "abyssus"`, `formatVersion: 1` and `version: 1`
- **THEN** its document format and asset metadata version are read independently

### Requirement: Unsupported documents remain untouched

Abyssus SHALL reject missing or foreign format markers, noninteger or unsupported format versions, and scene payloads with `ecs.componentIdentifiers` or renderable `class` fields. It SHALL explain the unsupported format, perform no import or conversion, and leave rejected file and document text unchanged through opening, automatic formatting and plugin edit attempts. Supported sibling documents SHALL remain usable.

#### Scenario: Unmarked legacy scene
- **WHEN** a `.scene` without native markers is opened or targeted by a plugin edit
- **THEN** it is reported as unsupported and its disk bytes and document text remain unchanged

#### Scenario: Future version
- **WHEN** a project or scene has `formatVersion: 2`, or an asset metadata file has `formatVersion: "1"`
- **THEN** that document is refused with the version reason and no automatic rewrite

#### Scenario: Markers do not convert a legacy payload
- **WHEN** native markers are added to a scene still holding `ecs.componentIdentifiers` or a renderable `class`
- **THEN** Abyssus refuses that scene rather than accepting class-name aliases

#### Scenario: One unsupported asset
- **WHEN** a supported scene references one unsupported asset and other supported assets
- **THEN** that asset is shown as unavailable with its format reason and the remaining assets still load

### Requirement: Native identifiers and extensions

Component map keys such as `PositionComponent` SHALL be stable schema identifiers independent of source packages. Model and terrain renderables SHALL use `kind: "asset"`, `asset.type`, `asset.assetName` and optional `shaderKey`, without `class`. Unknown short component identifiers and unknown renderable kinds SHALL remain raw and round-trip without being instantiated. Optional archetype lists SHALL refer to these component identifiers.

#### Scenario: Model reference
- **WHEN** a native renderable holds `kind: "asset"` and a `MODEL` asset reference
- **THEN** it resolves the asset without a serialized Java class name

#### Scenario: Unknown native content
- **WHEN** a native scene holds a custom component `WindComponent` and an unknown renderable kind `debug-marker`
- **THEN** editing another component preserves both payloads and loading does not instantiate them

### Requirement: New documents use the native format

Every Abyssus operation that creates a project, scene or asset metadata document SHALL write the native markers and native identifiers. The current terrain creation operation SHALL produce native metadata. Normal edits SHALL preserve markers and unrelated fields, key order and number text. Merely reading native data SHALL NOT insert omitted defaults.

#### Scenario: New terrain
- **WHEN** the user creates a terrain asset
- **THEN** its `meta.json` includes `format: "abyssus"` and `formatVersion: 1` alongside its terrain metadata

#### Scenario: Native edit
- **WHEN** a native scene's selected model is moved and the move is undone
- **THEN** the move changes only its intended transform fields and Undo restores the original document text

### Requirement: Independent product and format documentation

The plugin description and user documentation SHALL describe Abyssus as an independent libGDX editor, document native version 1 and the compatibility break, and make no promise of another editor's support. Active development guidance SHALL use the Abyssus format contract. Inherited-code attribution and license notices SHALL remain accurate in third-party documentation; archived history SHALL remain intact.

#### Scenario: Product description
- **WHEN** a user reads the plugin description and current format documentation
- **THEN** they see Abyssus-owned project and asset formats, supported versions and the absence of legacy import

#### Scenario: Source provenance
- **WHEN** inherited-code provenance is consulted
- **THEN** its original project attribution and license information remain available separately from product positioning

### Requirement: Admission is consistent across loading entry points

Native project, scene and asset documents SHALL use the same format admission rules whether read from saved files, unsaved editor text or outside the editor. Rejected input SHALL remain unchanged with a format reason, and supported siblings SHALL stay usable. Raw ECS input and output SHALL refuse reserved identifier-table and renderable class fields without requiring an enclosing document header.

#### Scenario: Native fixture outside the editor
- **WHEN** Untitled's project, Main Scene and model metadata are read outside the editor
- **THEN** they are accepted without changing their bytes or inserting defaults

#### Scenario: Invalid metadata from either source
- **WHEN** saved or unsaved asset metadata has a missing marker, foreign format, fractional version or future version
- **THEN** it is refused before its settings or source assets are loaded, with the same format reason

#### Scenario: Raw ECS legacy payload
- **WHEN** an ECS block contains componentIdentifiers or a built-in Render component's renderable contains class
- **THEN** loading or writing refuses it, including when the built-in component is named by its fully qualified name

#### Scenario: Extension fields stay opaque
- **WHEN** an unknown component or a marker's nested payload contains class or componentIdentifiers
- **THEN** those extension fields remain accepted and unchanged

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
