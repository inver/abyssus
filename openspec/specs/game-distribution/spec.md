# game-distribution Specification

## Purpose

Defines what a shipped game build contains: the game and the runtime it plays on, and none of the editor's code, while
Play keeps working from a separate build of the same game.

## Requirements

### Requirement: Shipped game holds no editor code

A game's distribution SHALL contain only the game and the runtime it plays on. It SHALL NOT contain asset importers,
the Play bridge (play host, play protocol, and the play export entry point), ray-tracing scene data, the scene writer,
or any editor library or plugin.

#### Scenario: Control Line distribution

- **WHEN** Control Line's distribution is built
- **THEN** its libraries hold no editor module, and no class of the FlightGear importer, the play host or protocol,
  the play export entry point, the ray-tracing snapshots or the scene writer

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

### Requirement: Play comes from a separate build of the game

The launch file a game exports for Abyssus SHALL come from a build of the game that includes its Play code. Exporting
`abyssus/play.json` SHALL name a classpath on which Play runs the game's module. This separation SHALL NOT change
`abyssus/components.schema.json`.

#### Scenario: Control Line export and Play

- **WHEN** Control Line exports its launch file for a copy of its project and that project is played in Abyssus
- **THEN** `play.json` names the game's play module and protocol 1, its classpath holds the game's play code, and the
  plane flies on its lines in Play as before
- **AND** the committed `components.schema.json` is unchanged

#### Scenario: Physics-only Play unchanged

- **WHEN** the `Physics` project, which has no `play.json`, is played in Abyssus
- **THEN** the bodies simulate with physics alone, as before

### Requirement: The build refuses editor code in shipped code

The repository's checks SHALL fail when a game's shipped classpath reaches an editor-only library, or when a library
that games ship contains editor imports or ray-tracing scene-data code. The failure SHALL name the offending library or
source file.

#### Scenario: Editor library added to the game

- **WHEN** Control Line's main code is made to depend on the editor's core library and the checks run
- **THEN** they fail and name that library

#### Scenario: Ray data added to a shipped library

- **WHEN** a ray-tracing snapshot type is added to the main sources of a library the game ships and the checks run
- **THEN** they fail and name the file

### Requirement: Development-only ray view stays out of distributions

The opt-in ray-traced field view and the ray-tracing modules it loads SHALL remain a development-only path: the shipped game's source set and runtime classpath SHALL NOT reference them, running the ray view in a checkout SHALL NOT change what the packaging or export tasks produce, and the shipped-classpath verification SHALL keep covering the shipped classpath and SHALL pass with the ray source set present.

#### Scenario: Shipped classpath check with ray code present

- **WHEN** the shipped-classpath check runs in a checkout that contains the ray field source set
- **THEN** it passes, because the shipped classpath includes neither the ray source set nor the ray-tracing modules

#### Scenario: Distribution excludes ray code

- **WHEN** Control Line's distribution is built with the ray field source set present
- **THEN** it contains no ray-tracing module and no ray source-set class, and the packaged game starts and plays as before

#### Scenario: Play export unchanged by the ray view

- **WHEN** the Play launch file is exported after the ray view has been available in development
- **THEN** its classpath holds the same modules as before, with no ray-tracing module and no ray source-set output