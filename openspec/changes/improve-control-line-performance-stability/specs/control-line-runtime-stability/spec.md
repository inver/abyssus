# Spec Delta

## Purpose

Defines what the running Control Line game does when it fails, what it records for a later diagnosis, and how it releases
what it holds when a flight, a screen or the game ends.

## ADDED Requirements

### Requirement: A failure leaves a crash record

When an exception escapes a frame, or any game thread, the game SHALL write a plain-text crash record to
`<user-home>/.abyssus-control-line/logs/`. The record names the time, the game version, the OS and architecture, the JVM
vendor and version, the GL renderer and version (when a context exists) and the stack trace. The game SHALL then exit with
a non-zero status.

#### Scenario: Exception during a frame

- **WHEN** an exception is thrown while the game draws a frame
- **THEN** a file `crash-<timestamp>.txt` appears in `<user-home>/.abyssus-control-line/logs/` holding the version, OS, JVM,
  GL renderer and the stack trace, and the process exits with a non-zero status

#### Scenario: Exception on another game thread

- **WHEN** an asset-preparation thread throws an exception that nothing handles
- **THEN** a crash record is written as for a frame failure

#### Scenario: Unwritable log folder

- **WHEN** the logs folder cannot be created or written
- **THEN** the record is printed to standard error instead and the game still exits with a non-zero status

### Requirement: Crash records are kept bounded

The game SHALL keep at most the ten newest crash records, deleting older ones when it writes a new record. It SHALL NOT send
a record anywhere.

#### Scenario: Eleventh crash

- **WHEN** the logs folder already holds ten crash records and the game writes another
- **THEN** the oldest record is deleted and ten remain

### Requirement: Saved data survives a crash

A crash SHALL NOT corrupt the score, controller, display or recording files. Each is either the last fully written version
or the new one.

#### Scenario: Crash while flying

- **WHEN** the game crashes during a flight after earlier scores were saved
- **THEN** on the next start every score table reads back as it was before that flight, and no file was renamed to `.bad`

### Requirement: The environment is logged at start

Once its window is open, the game SHALL log one line naming the game version, OS and architecture, JVM vendor and version,
maximum heap, GL renderer, GL version and the field view in use (raster or ray).

#### Scenario: Start

- **WHEN** the game starts with the raster field
- **THEN** its log holds one environment line naming the GL renderer and `raster`

### Requirement: The game releases what it holds on exit

On a normal exit the game SHALL release its physics worlds, GL resources and asset-preparation threads, and SHALL NOT
create a drawing resource only to release it.

#### Scenario: Quit from the main menu

- **WHEN** the player quits from the main menu
- **THEN** the process exits with status 0, no asset-preparation thread is left running, and no exception is logged

#### Scenario: Ray view failed before the first raster frame

- **WHEN** the ray field view fails and the player quits before a raster frame was drawn
- **THEN** the game exits with status 0 without building the raster renderer

### Requirement: A flight's drawn pose does not change its simulation

The plane's shown pose (between its last two simulated steps) SHALL NOT be what flight control, judging, recording or
physics read. They SHALL read the simulated pose.

#### Scenario: Same flight at two frame rates

- **WHEN** the same Watch AI flight is run once at 60 and once at 144 frames a second with the same fixed physics step
- **THEN** both flights produce the same recording and the same score

### Requirement: The game starts without a reported monitor

The game SHALL open its window when the windowing system reports no primary monitor (a locked screen, a virtual display):
windowed at a fixed position, or with a crash record naming the reason if no window can be made. It SHALL NOT fail with an
unexplained exception.

#### Scenario: Locked screen at start

- **WHEN** the game is started while the windowing system reports no primary monitor
- **THEN** the window opens at a fixed position in windowed mode, ignoring a saved fullscreen choice for that start, and the
  log says so
