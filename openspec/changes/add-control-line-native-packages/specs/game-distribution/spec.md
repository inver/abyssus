# Spec Delta

## ADDED Requirements

### Requirement: Control Line ships native packages with its own runtime

Control Line SHALL be built as one installable package per supported platform, each carrying the Java runtime it plays
on, so that no Java installation is needed to install or start it. The packages are: `.deb`, `.rpm` and `.AppImage` for
Linux x64, `.msi` for Windows x64, and one `.dmg` each for macOS arm64 and macOS Intel x64.

#### Scenario: Linux packages

- **WHEN** the Linux build runs
- **THEN** it produces a `.deb`, an `.rpm` and an `.AppImage`, each holding the game, its bundled project and a Java
  runtime

#### Scenario: Windows package

- **WHEN** the Windows build runs
- **THEN** it produces an `.msi` that installs the game with a Java runtime and a Start menu entry

#### Scenario: macOS packages

- **WHEN** the macOS builds run on an arm64 machine and on an Intel machine
- **THEN** each produces a `.dmg` for its own architecture, whose app holds the game, its bundled project and a Java
  runtime

#### Scenario: Start without Java installed

- **WHEN** a package is installed (or, for the `.AppImage`, run) on a machine with no Java installation and the game is
  started
- **THEN** the main menu opens and a round can be flown

### Requirement: A packaged game finds its bundled project wherever it is installed

A packaged game SHALL load the project it ships with from its own installation, on every package type and operating
system, without a working-folder or `controlLine.project` setting from the player. Its writable data, such as scores,
SHALL stay in the player's home folder, so a read-only installation works.

#### Scenario: Started from another folder

- **WHEN** an installed game is started from a working folder that holds no project
- **THEN** it loads its bundled project and shows the same screens, flight and scoring as the Gradle distribution

#### Scenario: Read-only installation

- **WHEN** the `.AppImage` (mounted read-only) is run and a flight finishes with a score
- **THEN** the score is saved under the player's home folder and shown on the next start

### Requirement: Packages start the game with the platform's window thread rules

A package SHALL start the game with the platform's launch requirements already applied, so that the player never passes
a launch option. On macOS the game's window SHALL be created on the process's first thread.

#### Scenario: macOS launch

- **WHEN** a macOS package is opened from the Applications folder or the disk image
- **THEN** the game window opens and renders, without a crash on startup

#### Scenario: Other platforms

- **WHEN** a Linux or Windows package is started
- **THEN** the game window opens and renders with no macOS-only option applied

### Requirement: Native packages hold no editor code

A native package SHALL contain the same shipped code as the Gradle distribution and none of the editor-only libraries,
asset importers, the Play bridge, ray-tracing modules or the scene writer. The build SHALL fail when a built package
holds one, naming the library, and this check SHALL run on every package type.

#### Scenario: Clean package

- **WHEN** the package check runs on a package built from the shipped classpath
- **THEN** it passes, and the package's libraries list matches the Gradle distribution's

#### Scenario: Editor library in a package

- **WHEN** a package is built with an editor-only library in its libraries and the package check runs
- **THEN** the check fails and names that library

### Requirement: Packages carry the game's icon and are unsigned

Every package SHALL show the Control Line icon as the application icon where the platform shows one. The packages are
not code-signed or notarized; the game's documentation SHALL state this and how to open an unsigned package on macOS and
Windows.

#### Scenario: Icon shown

- **WHEN** a package is installed on its platform
- **THEN** the launcher entry or application shows the Control Line icon

#### Scenario: Unsigned notice

- **WHEN** a player reads the game's README before installing
- **THEN** it says the packages are unsigned and how to start them past the macOS and Windows first-launch warnings

### Requirement: Packages are built per platform and published with the canary

The release pipeline SHALL build every package on a machine of its own operating system and architecture, and SHALL
attach all of them, next to the universal archive, to the canary release of the main branch. Pull-request builds SHALL
build the same packages without publishing them.

#### Scenario: Main branch build

- **WHEN** the main branch builds successfully on all platforms
- **THEN** the canary release holds the `.deb`, `.rpm`, `.AppImage`, `.msi`, both `.dmg` files and the universal archive

#### Scenario: A platform build fails

- **WHEN** one platform's package build fails on the main branch
- **THEN** the canary release is not replaced, and the failing platform is named in the build result

#### Scenario: Pull request

- **WHEN** a pull request is built
- **THEN** every package is built and checked, and no release is created or changed
