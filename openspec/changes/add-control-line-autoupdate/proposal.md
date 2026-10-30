# Proposal

## Why

A player who installs Control Line from a native package stays on that build until they find and download a new
installer by hand, and every package is unsigned, so Windows SmartScreen and macOS Gatekeeper warn on each first launch.
Updates have to reach players without a reinstall, and anything the game downloads and runs has to be proven to come from
this project before it runs.

## What Changes

- The installed game starts through a small launcher. It checks the project's tagged GitHub releases, downloads a newer
  game version in the background into the player's folder, verifies it, and runs it from the next start. The check never
  delays or blocks the start; offline, or on any failure, the game starts on the version it has.
- What an update replaces is the game's own code and its bundled project. The bundled Java runtime, the native
  libraries and the launcher stay with the installer; when a release changes any of them, the game keeps running on
  the version it has and says a new installer is needed.
- Releases are immutable GitHub releases, and GitHub signs them: an immutable release carries a release attestation
  (signed by GitHub, with an RFC 3161 time-stamp) that lists the tag, the commit and the SHA-256 of every asset. The project
  holds no update signing key. The launcher carries GitHub's trusted root and accepts a manifest only when that attestation
  verifies and lists the manifest; it refuses any file that does not match the manifest, and a version older than the one it
  runs.
- Release builds sign the installers: the Windows `.msi` (Authenticode), the macOS `.dmg` (Developer ID, notarized and
  stapled), and the Linux packages through `SHA256SUMS` covered by GitHub's release attestation. Pull-request and canary builds stay unsigned and need
  no secrets.
- A tag `control-line-vX.Y.Z` runs a release workflow that builds the packages, signs everything in a protected
  environment, and publishes the release with the update parts and the manifest.
- The player can turn updates off.
- **BREAKING for the packaging change:** `add-control-line-native-packages` declares the packages unsigned and lists
  auto-update as out of scope; its delta spec is amended by a task here (see Impact).

Out of scope: an update notice or progress inside the game's screens (the status goes to the log and a status file),
delta patches inside a part, updating the Java runtime or native libraries in place, an update channel for the canary
or other pre-releases, Linux package repositories, Linux or Windows arm64, a TUF client for GitHub's trusted root
(the root is bundled and refreshed by hand with an installer). No native Abyssus file (`.scene`, `.abss`, asset `meta.json`) is read, written or changed; the update manifest
is a separate document (`schemaVersion` 1) that is not an Abyssus document and does not go through the Abyssus document
validation. The game's saved scores under `~/.abyssus-control-line/` are not touched.

## Capabilities

### New Capabilities
- `game-auto-update`: how an installed game finds, verifies, downloads and starts a newer version, and what it does when
  it cannot.
- `release-signing`: what a release build must sign (update manifest, installers, checksums), what is published, and
  when a build must fail instead of publishing.

### Modified Capabilities

(none in `openspec/specs/`: the requirements this change touches, "Native packages hold no editor code" and "Packages
carry the game's icon and are unsigned", are still only in the delta of `add-control-line-native-packages`, which a task
amends. Archive this change after that one.)

## Impact

- New module `projects/lib-updater` (plain JVM library: manifest model, signature check, release source, sync) and
  `projects/app-game-control-line-launcher` (the packaged application's entry point). Both are registered in
  `settings.gradle.kts`; the launcher is the `jpackage` main jar, the game's jars become the updatable payload.
- `build-logic` packaging plugin: parts and manifest tasks, signing hooks, the launcher as `mainJar`, `modules.txt`
  gains the HTTPS and signature modules; `CheckPackagePayload` accepts the launcher and updater jars.
- `.github/workflows/`: a new `release-control-line.yml` (tag `control-line-v*`, protected `release` environment);
  `release.yml` (plugin Marketplace publish) is guarded so a Control Line release never triggers it;
  `build-control-line.yml` stays unsigned.
- `openspec/changes/add-control-line-native-packages/specs/game-distribution/spec.md` is amended (not unsigned any more
  on release builds; the package library list is the distribution's plus the launcher and updater).
- Secrets and certificates: a Windows signing route, an Apple Developer ID and notarization
  credentials, all in the protected environment only. No update signing key exists. The repository setting "Immutable
  releases" must be on.
- `projects/app-game-control-line/README.md`, `AGENTS.md` (layout, commands, hard rules), `docs/ai/architecture.md` and
  a new `projects/lib-updater/README.md`.
- The repository is public, so players need no token to read its releases (checked against the anonymous GitHub API).
