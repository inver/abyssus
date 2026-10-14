# Spec Delta

## Purpose

Defines what a shipped game build contains: the game and the runtime it plays on, and none of the editor's code, while
Play and the files Abyssus reads from the game keep working from a separate build of the same game.

## ADDED Requirements

### Requirement: Shipped game holds no editor code

A game's distribution SHALL contain only the game and the runtime it plays on. It SHALL NOT contain asset importers,
the Play bridge (play host, play protocol, and the play and schema export entry points), ray-tracing scene data, the scene writer, or any
editor library or plugin.

#### Scenario: Control Line distribution

- **WHEN** Control Line's distribution is built
- **THEN** its libraries hold no editor module, and no class of the FlightGear importer, the play host or protocol,
  the play or schema export entry points, the ray-tracing snapshots or the scene writer

#### Scenario: Game still plays

- **WHEN** the built distribution is started and the player flies a round from the main menu
- **THEN** the game behaves as before, with the same screens, flight and scoring

### Requirement: Editor-only scene data is ignored by the game

A game SHALL load a scene that holds editor-only settings, such as `rayTracingEnabled` and `rayTracing`, without error
and without acting on them. Loading SHALL NOT change the file.

#### Scenario: Scene with ray-tracing settings

- **WHEN** the game loads a copy of Control Line's field scene with `"rayTracingEnabled": true` and a `rayTracing`
  block holding `"targetSamplesPerPixel": 64`
- **THEN** the field loads and renders as without those keys, and the file's bytes are unchanged

### Requirement: Play and exports come from a separate build of the game

The files a game exports for Abyssus SHALL come from a build of the game that includes its Play code. Exporting
`abyssus/play.json` SHALL name a classpath on which Play runs the game's module. Exporting
`abyssus/components.schema.json` SHALL give the same bytes as before this separation.

#### Scenario: Control Line export and Play

- **WHEN** Control Line exports for Abyssus and its project is played in Abyssus
- **THEN** `play.json` names the game's play module and protocol 1, the plane flies on its lines in Play as before,
  and `components.schema.json` is byte-identical to the committed file

#### Scenario: Physics-only Play unchanged

- **WHEN** the `Physics` project, which has no `play.json`, is played in Abyssus
- **THEN** the bodies simulate with physics alone, as before

### Requirement: The build refuses editor code in shipped code

The repository's checks SHALL fail when a game's shipped classpath reaches an editor-only library, or when a library
that games ship contains importer or ray-tracing scene-data code. The failure SHALL name the offending library or
source file.

#### Scenario: Editor library added to the game

- **WHEN** Control Line's main code is made to depend on the editor's core library and the checks run
- **THEN** they fail and name that library

#### Scenario: Ray data added to a shipped library

- **WHEN** a ray-tracing snapshot type is added to the main sources of a library the game ships and the checks run
- **THEN** they fail and name the file
