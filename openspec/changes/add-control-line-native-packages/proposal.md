# Proposal

## Why

Control Line ships only as a universal zip that needs a Java runtime on the player's machine. Players expect an
installer per OS that starts the game without installing Java. The JDK's own `jpackage` builds those packages, so no
third-party packager is needed.

## What Changes

- Control Line is packaged with `jpackage` and a bundled, trimmed Java runtime:
  - Linux x64: `.deb`, `.rpm` and an `.AppImage` (an `app-image` folder wrapped by `appimagetool`).
  - Windows x64: `.msi`.
  - macOS arm64 and macOS Intel x64: one `.dmg` each.
- The game finds its bundled `project/ControlLine` through `-DcontrolLine.project=$APPDIR/project/ControlLine`, whatever
  the install layout; no game code changes.
- macOS packages start the JVM with `-XstartOnFirstThread`.
- A script renders the committed `png`, `ico` and `icns` icons from `control-line-icon.svg`.
- A check lists the jars inside each built package and fails on editor-only code, like `checkShippedClasspath`
  does for the Gradle classpath.
- CI builds the packages on a runner per OS and architecture, attaches them to the canary release and builds them
  (unsigned) on pull requests as a smoke test.
- The universal zip stays unchanged.

Out of scope here (signing of releases and auto-update are the change `add-control-line-autoupdate`): code signing and notarization (the packages are unsigned, so Gatekeeper and SmartScreen warn on first
launch; the README says how to open them), Linux arm64, Windows arm64, auto-update, store listings, a ray-traced
distribution. No native Abyssus file (`.scene`, `.abss`, asset `meta.json`) is read, written or changed, and the
document format and its validation are untouched.

## Capabilities

### New Capabilities

### Modified Capabilities
- `game-distribution`: adds requirements for native installer packages (contents, runtime, bundled project lookup, no
  editor code in a package, unsigned) next to the existing editor-free and Play requirements.

## Impact

- `projects/app-game-control-line/app-game-control-line.gradle.kts`: `jpackage` tasks, icon task, package check.
- `.github/workflows/build-control-line.yml`: matrix of four jobs and the canary upload.
- New committed icon files under the game's project resources; `control-line-icon.svg` becomes their source.
- `projects/app-game-control-line/README.md` and `docs/ai/` distribution notes.
- Build tools on the runners: `dpkg-deb`/`fakeroot`, `rpmbuild`, WiX, `appimagetool` (pinned, checksum verified).
