# Design

## Context

See `proposal.md` for the motivation and `specs/game-auto-update` and `specs/release-signing` for the required behavior.

Facts that shape the approach (read from the repository):

- `add-control-line-native-packages` (open, 14 of 25 tasks done) builds the packages: `stagePackageInput` copies
  `installDist`'s `lib/*.jar` and `project/ControlLine` into `build/packaging/input/`, `jpackage --type app-image` runs
  on it with `--runtime-image` from a `jlink` runtime of nine modules (`packaging/modules.txt`), and every installer is
  built `--app-image` from that folder. `CheckPackagePayload` requires the app-image's jars to equal the distribution's
  `lib/` and refuses editor-only code. The launcher gets `-DcontrolLine.project=$APPDIR/project/ControlLine`.
- `Main.kt` already honours the `controlLine.project` property first, so the game needs no change to run from another
  folder. Scores live in `~/.abyssus-control-line/` (`ScoreTable.kt`, `PatternScoreTable.kt`).
- The installed locations are not writable by the player (Program Files, `/Applications/*.app`, `/opt`, the read-only
  AppImage mount), and a macOS bundle must not be modified after signing. Updated files therefore live in the player's
  folder, and the installed copy is the seed and the fallback.
- The distribution's `lib/` is 80 MB because it carries LWJGL and Jolt natives of every OS; the bundled project is 33 MB in
  276 files.
- `.github/workflows/release.yml` runs the plugin's Marketplace publication on every GitHub `release` event
  (`prereleased`, `released`). The canary is created with `GITHUB_TOKEN`, which does not trigger other workflows; a
  release created by hand would.
- The repository `inver/abyssus` is public (the anonymous GitHub API answers 200 for it), so the game reads its releases
  without a token. A private repository would need a read token inside the game, which this design refuses to ship.
- `update4j`, first proposed, was archived read-only by its owner in March 2024 and its signature behavior could not be
  confirmed from its documentation, so this design owns the small sync client.
- `lib-*` modules and `lib-core-editor` carry guard tasks (`checkNoSingletons`, `checkNoEditorCode`,
  `checkPackageCycles`, `checkNoRunCatching`); the new modules join them.

## Goals / Non-Goals

**Goals:**
- A verified, atomic, never-blocking update path whose trust anchor is one Ed25519 key pair the project controls.
- Signed installers on all three desktop systems from the same release run, and a release that cannot publish unsigned.
- All decision logic (version order, signature check, diff planning, safe extraction, version store, release lookup)
  testable headless, with no GL, no AWT and no network except a loopback test server.

**Non-Goals:**
- Game-side UI for updates, delta patches inside a file, in-place runtime or native-library updates, resuming a partial
  download with HTTP ranges, protection against an attacker who only withholds new releases (freeze attacks), an
  update channel for pre-releases, signing for Linux repositories.

## Decisions

### 1. What starts, and what the player's folder holds

```
 installed (read-only, signed by the OS signature)        player's folder ~/.abyssus-control-line/updates/
 +--------------------------------------------+           +---------------------------------------------+
 | launcher/    launcher + lib-updater jars   |           | settings.properties   enabled=true          |
 | game/        seed game jars (all)          |           | status.json           last check result     |
 | game/project/ControlLine                   |           | state.json            etag, bad versions,   |
 | installer.digest                           |           |                       unclean-start count   |
 | runtime/  (jlink image)                    |           | current               "1.2.0"               |
 +--------------------------------------------+           | versions/1.2.0/{game/*.jar, project/...}    |
                                                          | staging/1.3.0/...     (deleted on failure)  |
 jpackage launcher -> Launcher.main                       +---------------------------------------------+
   1. pick game dir: versions/<current> if COMPLETE and not marked bad, else the seed
   2. start the update check on a daemon thread (does not delay step 3)
   3. URLClassLoader(game jars, parent = platform loader) -> set controlLine.project -> call the game's main
```

- The launcher is the `jpackage` main jar. The game's jars are no longer on the launcher's class path; they sit in
  `game/` and are loaded by a `URLClassLoader` whose parent is the platform loader, so the game sees exactly its own
  jars (its own Jackson and Kotlin stdlib) from either the seed or a synced version, and the launcher's libraries stay
  private. The launcher sets the context class loader and `controlLine.project` to the chosen folder's project, so the
  `-DcontrolLine.project=$APPDIR/...` launcher option of the packaging change is replaced by this.
- The launcher's thread is the process's first thread, so macOS `-XstartOnFirstThread` still applies to the game's
  window. The launcher makes no GL and no AWT call and never touches `Gdx.*`; the update thread is a plain daemon thread
  doing HTTP and file IO. Nothing of `lib-updater` runs in the IntelliJ process.
- **Alternative: spawn the game as a child JVM.** Rejected as the default because a second JVM shows a second Dock icon on
  macOS and needs the launcher to stay resident; kept as the fallback behind a small `GameRunner` interface if the
  class-loader route fails the spike (tasks 1.1 and 1.2) on any OS.
- The game's main class and entry are named by the signed manifest (`mainClass`), defaulting to the seed's.

### 2. The unit of an update: two parts, per-file checks

A release publishes two zip **parts** and a manifest:

- `game-code-<version>.zip`: the game's jars **except** installer-only ones.
- `game-project-<version>.zip`: `project/ControlLine`.

Installer-only files are the jars that carry native code (name patterns `*natives*` and `jolt-jni-*` in
`packaging/updater.properties`). The manifest lists every file of both parts with its path, size and SHA-256, plus each
part's name, size and SHA-256. Alternatives: one asset per file (276 project files and over 30 jars exceed what a release
should hold), and a binary diff (new tooling for a 33 MB project that rarely changes). Plain jars are not reproducible
byte for byte, so the code part is nearly always downloaded; the client compares per-file hashes and skips a whole part
whose files are all present, which keeps an update of the project alone or of the code alone small.

`installerDigest` in the manifest is the SHA-256 over: the sorted names and hashes of the installer-only jars, the content of
`modules.txt` with the JDK's version string, and the launcher protocol number. It is deliberately not a hash of the
launcher jar, which would change on every build. The installed `installer.digest` is compared with it: a difference
means a new installer is needed. An unknown manifest `schemaVersion` means the same.

### 3. Finding and trusting a release

- `ReleaseSource` calls `GET /repos/<owner>/<repo>/releases` (public, anonymous, with an `If-None-Match` ETag kept in
  `state.json`; a 304 costs no rate limit), drops drafts and pre-releases and tags without the `control-line-v` prefix,
  and takes the highest numeric version. The repository and prefix are generated into a launcher resource at build time.
- Only asset **names** are taken from that JSON. The manifest and its signature are downloaded first; the manifest is
  verified over its exact bytes before it is parsed; part URLs are the release's own asset URLs for the names the signed
  manifest lists. Downloads must be HTTPS to `github.com` or `*.githubusercontent.com`.
- **Signature:** Ed25519 through the JDK's `Signature`. The launcher resource `update-keys.json` holds one or two
  `{id, publicKey}` entries; the manifest names `keyId`. A manifest naming an unknown key id is rejected, which is also the
  rotation story: ship the next public key in a release signed by the old one, then sign with the new key, and an
  installed game that has only the old key reports that a new installer is needed.
- **Bind, do not hand-parse:** the manifest and `state.json` are bound to DTOs with Jackson (the library the game already
  ships), not read token by token.
- **No downgrade:** a manifest whose version is below the running one is ignored. Numeric dotted order only (`1.10.0` >
  `1.9.0`); a non-numeric version is rejected.

### 4. Download, verify, switch

`UpdateSync` builds `staging/<version>/`: it copies unchanged files from the current version folder (or the seed), downloads
the parts that contain a changed file, checks each part's size and SHA-256 while streaming, extracts with a guard that
rejects `..`, absolute and unlisted entries (zip-slip), checks every extracted file against the manifest, writes a `COMPLETE`
marker last, then renames `staging/<version>` to `versions/<version>` and replaces `current` through a temporary file and an
atomic move (the way the score tables already write). Anything that throws deletes the staging folder and records the
reason in `status.json`. Free space is checked before the download. Keep the current and the previous version; older
folders are deleted after a successful switch. A player's quit during a download abandons it (the daemon thread ends with
the JVM); the next start restarts it. That is the "resumed or restarted" the spec allows.

### 5. A new version that cannot start

The game has no health call, so the launcher infers it. It records `launching=<version>` in `state.json` before calling the
game's main and clears it once the game has run for 30 seconds or its main returned normally. A throwable out of the
game's main, or a `launching` mark still present at the next start (a crash within the first seconds), counts as an
unclean start of that version; two in a row mark the version bad. A bad version is skipped, the previous good version (or
the seed) runs, and the bad one is not retried until a newer release exists. This needs no game change. A crash after 30
seconds of play never counts.

### 6. Settings, status, privacy

`settings.properties` (`enabled=true|false`), the environment variable `CONTROL_LINE_UPDATES=off` and the system property
`controlLine.updates=off` turn updates off; off means no HTTP request at all. The only request data is what HTTPS and the
GitHub API carry; the User-Agent is `control-line/<version>`. `status.json` follows the spec (running version, newest seen,
waiting version, reason). A new-installer notice gives the release page address.

### 7. Modules and guards

- `projects/lib-updater`: plain JVM library, root package `net.nevinsky.abyssus.lib.updater`, wired by constructors, no
  `object` or `companion object` with behavior, no editor, IntelliJ, AWT or libGDX import. Packages: `manifest` (DTOs,
  `ManifestVerifier`, `VersionOrder`), `release` (`ReleaseSource`, the GitHub implementation, a fake for tests), `sync`
  (`UpdatePlanner`, `SafeUnzip`, `UpdateSync`), `store` (`VersionStore`, `UpdateState`, `UpdateStatus`, `UpdateSettings`).
  Time, HTTP and file roots are constructor arguments, so tests use a temporary folder and a loopback server.
- `projects/app-game-control-line-launcher`: the `jpackage` main jar: `Launcher` (picks the folder, starts the check,
  runs the game), `GameRunner` and its class-loader implementation. Depends on `lib-updater` and the Kotlin stdlib only.
- Both join `checkNoSingletons`, `checkPackageCycles`, `checkNoRunCatching` and `checkNoEditorCode`; the launcher module gets
  the same shipped-classpath check as the game. `CheckPackagePayload` now treats `launcher/` and `game/` as two sets: `game/`
  equals the distribution's `lib/` as before, `launcher/` holds only the launcher, `lib-updater` and their dependencies, and
  both reject editor-only code.
- `modules.txt` gains `java.net.http` and `jdk.crypto.ec` (HTTPS needs the EC key exchange and Ed25519 lives there in JDK 21;
  `jdeps` cannot see either, because they load through providers), each with its reason in the file.

### 8. Release pipeline

```
 tag control-line-v1.2.0
   |
   +--> build matrix (ubuntu-22.04, windows, macos arm64, macos intel)   environment: release
   |      sign app-image content + installer (Windows / macOS), notarize + staple (macOS)
   |      upload packages
   +--> publish (ubuntu)                                                 environment: release
          download all -> game parts + installer.digest from the Linux job -> manifest.json
          -> sign manifest.json and SHA256SUMS with the update key -> verify with lib-updater
          -> gh release create control-line-v1.2.0 (not a pre-release)
```

- The packaging steps move into a reusable workflow `_package-control-line.yml` with a `release` input. `build-control-line.yml`
  (pull requests and canary) calls it with `release: false`: no environment, no secrets, no signing, as today. The
  tag workflow calls it with `release: true`. If GitHub rejects an empty `environment:` expression, the release jobs are
  separate jobs guarded by `if:` instead.
- The version comes from the tag (`control-line-v1.2.0` gives `-PcontrolLineVersion=1.2.0`); the workflow rejects a tag that
  is not `X.Y.Z`, and `.dmg` needs a major version above 0.
- The `release` environment requires reviewer approval and is reachable only from tags matching `control-line-v*`
  (a tag protection rule, set in the repository settings by the maintainer). Fork pull requests never see it.
- **Update key:** an Ed25519 key pair made once by `scripts/generate-update-key.sh` (`openssl genpkey`); the public half is
  committed in the launcher's `update-keys.json`, the private half is the secret `UPDATE_SIGNING_KEY` in the `release`
  environment only. `SignManifest` (a task in `build-logic`, plain JDK `Signature`) reads it from the environment, signs in
  memory, and never writes it to disk or the log. `SHA256SUMS` is signed by the same key in the same step; the README
  documents `openssl pkeyutl -verify -rawin` for it.
- `release.yml` gets a job-level `if: ${{ !startsWith(github.event.release.tag_name, 'control-line-v') }}` so a Control Line
  release never starts the Marketplace publication; the new workflow does not trigger on `release` events at all.
- **macOS:** `jpackage --mac-sign --mac-signing-keychain <temp keychain> --mac-signing-key-user-name "<Developer ID
  Application: ...>" --mac-entitlements packaging/macos/entitlements.plist` on the app-image and the disk image, then
  `xcrun notarytool submit --wait` and `xcrun stapler staple`. The certificate and the notary credentials are imported into a
  throwaway keychain that the job deletes. The entitlements file allows JIT and unsigned executable memory for the JVM and
  disables library validation, which LWJGL and Jolt natives (extracted at run time) need. The exact flag names are checked
  against `jpackage --help` of the pinned JDK in the first signing task before anything depends on them.
- **Windows:** `jpackage` has no signing option. A `signWindows` hook runs a configured command template
  (`{file}` placeholder) over the app-image's launcher and DLLs after `packageApplication`, then over the `.msi` after
  `packageMsi`, with a timestamp server. The command is set by the workflow, so the route is a CI choice
  (open question below). A route that signs a finished artifact remotely signs the `.msi` in a CI step and leaves the
  launcher inside it unsigned.
- **Linux:** `SHA256SUMS` plus its signature; no per-package signing.
- A release job fails, publishing nothing, when any signing input is missing or a signature does not verify (spec
  "Release builds fail rather than publish unsigned"). The previous release stays the latest because `gh release create` runs
  last, after every platform and every verification passed.

### 9. Where tests live

Headless, in `lib-updater/src/test`: `ManifestVerifier` (good, unknown key, tampered bytes, missing signature, both keys),
`VersionOrder`, `UpdatePlanner` (which parts are needed), `SafeUnzip` (traversal, absolute path, unlisted entry, size
mismatch), `VersionStore` (atomic switch, interrupted staging, previous-version fallback, bad-version marking),
`ReleaseSource` against a loopback `HttpServer` serving GitHub-shaped JSON (pre-release and prefix filtering, 304,
timeouts, redirects to an allowed and to a disallowed host), `UpdateSync` end to end against that server including a
corrupted part. In `app-game-control-line-launcher/src/test`: the class-loader runner against a tiny fake game main (sets
the property, runs on the calling thread, reports an exception as an unclean start). In `build-logic`: `SignManifest` and
the manifest writer produce what `ManifestVerifier` accepts (a fixture signed by the task, verified in `lib-updater`'s
test through a committed sample). GL, signing providers and notarization can only be seen on real machines and in CI;
those are the numbered manual checks in `tasks.md`.

## Risks / Trade-offs

- [The game misbehaves under a child `URLClassLoader` (libGDX/LWJGL/Jolt native loading, `Class.forName`, resources)] →
  spike first on all three OS (tasks 1.1, 1.2); fallback is the child-process runner behind `GameRunner`.
- [`jpackage` puts the nested seed jars on the launcher's class path or lays `game/` out differently per OS] → the spike
  records the real layout; the game's loader never delegates to the system loader, so a duplicate is harmless, and
  `CheckPackagePayload` reads the real folders.
- [A hardened macOS runtime refuses natives or an updated jar] → natives stay in the signed bundle; the entitlements
  are tested on a notarized build in the first signing task; updated jars are plain Java, not Mach-O.
- [SmartScreen still warns for a new, correctly signed `.msi` until reputation builds] → documented in the README; the
  signature is still required (publisher shown, not "unknown").
- [Notarization is slow or flaky] → `--wait` with a timeout and a retry, failing the release (never publishing unsigned).
- [The private update key leaks or is lost] → it exists only as a protected secret; rotation is built in (two public
  keys); a leak needs a new release signed by the new key and, for games that only know the old key, a new installer.
- [An attacker with release-write access replaces assets] → part and file hashes are inside the signed manifest, so
  replaced assets fail verification; only the private key can forge an update.
- [An attacker withholds new releases (freeze)] → accepted and listed as a non-goal.
- [The API rate limit (60 an hour per address) or a blocked GitHub] → ETag requests, one check per start, and a silent
  skip on failure.
- [Two installers' worth of drift: the seed is older than a synced version] → a synced version is always a complete copy;
  the seed is only the fallback and the first-run base.
- [The `release.yml` guard is forgotten] → a task adds it and a check in `scripts/check-docs.sh` is not needed; the spec
  scenario "Control Line release published" is verified in the rehearsal.
- [Disk use: two versions of up to 80 MB of jars plus 33 MB of project in the player's folder] → only changed files are
  downloaded but each version folder is complete; older ones are deleted after a switch.

## Migration Plan

1. Land the modules and the launcher with updates **off by default in the build** until the rehearsal passes (the
   `settings.properties` default flips to on in the same change as the first real release).
2. Rehearse in a private clone or fork with a throwaway key pair and test certificates where possible: tag `v1.0.1`, install,
   tag `v1.0.2`, start, restart, check the version.
3. Set the repository secrets and the `release` environment, add the tag protection rule, commit the real public key, push
   the first real tag.
4. Players on a package built before this change have no launcher and need one new installer; the README says so. After
   that every update is in place.
5. Rollback: delete the release (the launcher then sees the previous stable one and, because it never downgrades, stays on
   what it has); revoke a bad key by shipping the next public key.

## Open Questions

- The Windows signing route (SignPath Foundation for open source, or Microsoft Artifact Signing) depends on the
  maintainer's country and publisher identity and on the repository's eligibility. It changes only the CI step that fills
  the `signWindows` command or signs the `.msi` remotely, not the spec, the hook or the other tasks; the Windows signing
  task starts with the choice.
