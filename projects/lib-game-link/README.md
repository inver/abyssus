# lib-game-link

Everything a game links only so that Abyssus can talk to it. A plain JVM library (package
`net.nevinsky.abyssus.lib.gamelink`, no IntelliJ imports) wired by constructors, on `lib-physics`, `lib-core` and `lib-core-editor` (which writes the play frames' JSON).

A shipped game never depends on it from its main code: `./gradlew :app-game-control-line:checkShippedClasspath` fails
the build if it does. A game that supports Play puts its `PlayModule` in a source set of its own (Control Line:
`src/play`) with `playImplementation(project(":lib-game-link"))`.

## `play`

| Class | Role |
|---|---|
| `PlayModule` | What a game runs in Play: its components, the systems to run before each advance, input, telemetry and debug lines |
| `PhysicsOnlyPlayModule` | The module Abyssus's bundled host runs when a project has no `play.json`: physics alone, with each rope's tension |
| `PlayHost`, `PlayHostMain` | The play process: loads the scene text it is sent, builds a `PhysicsWorld`, runs at 120 Hz and sends poses at most 60 times a second over a loopback socket |
| `PlayProtocol` | The socket protocol (`PLAY_PROTOCOL` = 1), with `PlayFrame`, `PlayInput` and `DebugLine` |
| `PlayFile` | `<project>/abyssus/play.json`: `{protocol, module, classpath, jvmArgs}` |
| `PlayExportMain` | Writes `play.json` with the classpath it runs on made absolute |

`PlayHostMain` is `net.nevinsky.abyssus.lib.gamelink.play.PlayHostMain`; `play.json` does not name it, so the file format
is unchanged.

## Exporting `play.json`

Run the game's export task on a disposable copy of its project (the file holds machine-local paths and stays out of
version control). For Control Line:

```
./gradlew :app-game-control-line:exportPlay -PplayProject=/path/to/copy/of/ControlLine
```

A game of your own adds a `JavaExec` task with `PlayExportMain` as its main class on the runtime classpath of the
source set that holds its `PlayModule`; the arguments are the module's class name and the project folder.

**Run `exportPlay` again after updating to this module:** a `play.json` written before the Play bridge moved here names a
classpath without `lib-game-link` and the game's `play` output, so the host cannot start (Abyssus reports that Play
ended unexpectedly). Export again whenever the classpath changes.

## Jolt

Only a game or the play host loads Jolt, never the IDE. The `playHost` configuration resolves the four platforms'
`ReleaseSp` natives; Abyssus bundles them with this module and `lib-physics` in its `play-host/` folder, outside the
IDE-loaded `lib/` (`:plugin-abyssus:checkNoJoltInZip`).

Tests: `./gradlew :lib-game-link:test`.
