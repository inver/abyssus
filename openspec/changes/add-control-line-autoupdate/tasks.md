# Tasks

Archive this change after `add-control-line-native-packages`: it amends that change's delta (task 8.4), and its payload
check and package tasks (tasks 3.4, 3.3 and 6.1 there) are the base of section 5.

## 1. Spikes (throwaway; findings go into `design.md`)

- [ ] 1.1 Run Control Line from a `URLClassLoader` (parent = platform loader) over the `installDist` jars with `controlLine.project` set, on this Windows machine, on a Mac and on a Linux desktop (or the CI Linux runner under `xvfb-run` with Mesa). Verify the main menu opens and a round can be flown, and that LWJGL, libGDX and Jolt load their natives. If any OS fails, record the failure and make the child-process runner (design decision 1) the default before going on.
- [ ] 1.2 Build the 1.1 launcher into a `jpackage` app-image with the seed jars in a `game/` folder. Verify which jars `jpackage` puts on the launcher's class path, where `game/` lands per OS (`app/` on Windows, `lib/app` on Linux, `Contents/app` on macOS) and that `$APPDIR` is not needed any more; record the layout.
- [ ] 1.3 Add `java.net.http` and `jdk.crypto.ec` to a throwaway `jlink` image and run a class that fetches `https://api.github.com/repos/inver/abyssus/releases` and verifies an Ed25519 signature. Verify both work, and that the image without those two modules fails the HTTPS call (so the entries are justified).
- [ ] 1.4 Run `jpackage --help` of the pinned JDK 21 and of JDK 25 and record the exact macOS signing flags (`--mac-sign`, keychain, key user name, entitlements) in `design.md` decision 8. Verify the flag names in the design text match the help output.

## 2. `lib-updater`: manifest, version order, signature

- [ ] 2.1 Add the module `projects/lib-updater` (plain JVM, Kotlin, Jackson, no editor/IntelliJ/AWT/libGDX import), register it in `settings.gradle.kts`, and add it to `checkNoSingletons`, `checkNoEditorCode`, `checkPackageCycles` and `checkNoRunCatching`. Verify `./gradlew :lib-updater:test checkNoSingletons checkNoEditorCode checkPackageCycles checkNoRunCatching` passes on the empty module.
- [ ] 2.2 Implement the manifest DTOs (`schemaVersion` 1, `product`, `version`, `keyId`, `mainClass`, `installerDigest`, `parts[]`, `files[]`) bound with Jackson, and `VersionOrder` (numeric dotted, rejects non-numeric). Verify `VersionOrderTest` (1.10.0 above 1.9.0, equal, non-numeric rejected) and `ManifestBindingTest` (round trip, unknown `schemaVersion` reported as "installer needed") pass.
- [ ] 2.3 Implement `ManifestVerifier` (Ed25519 over the exact manifest bytes, key set from `update-keys.json`, key id lookup). Verify `ManifestVerifierTest` covers: valid, tampered by one byte, unknown key id, missing signature, second key accepted, rotation to a key the game lacks.
- [ ] 2.4 Document the manifest format and the trust rules in `projects/lib-updater/README.md`. Verify `scripts/check-docs.sh` passes.

## 3. `lib-updater`: release lookup, sync, version store

- [ ] 3.1 Implement `ReleaseSource` (GitHub releases list, prefix and pre-release/draft filtering, ETag, timeouts, HTTPS and host allow-list, redirect handling) behind an interface with a fake. Verify `GitHubReleaseSourceTest` against a loopback `HttpServer`: canary newer than stable ignored, plugin release ignored, 304 reuses the cached answer, a slow server times out, a redirect to a disallowed host is refused.
- [ ] 3.2 Implement `UpdatePlanner` (which parts are needed from per-file hashes) and `SafeUnzip` (rejects `..`, absolute paths, entries not in the manifest, size and hash mismatch). Verify `UpdatePlannerTest` (project-only change downloads only the project part) and `SafeUnzipTest` (each rejection) pass.
- [ ] 3.3 Implement `VersionStore` (`versions/`, `staging/`, `COMPLETE` marker, `current` switched by temporary file and atomic move, keep current and previous, free-space check) and `UpdateState` (ETag, unclean-start count, bad versions). Verify `VersionStoreTest`: an interrupted staging folder is ignored and removed, a half-written `current` falls back to the seed, a bad version is skipped, older folders are deleted after a switch.
- [ ] 3.4 Implement `UpdateSync` on top of 3.1 to 3.3, `UpdateSettings` (file, environment variable, system property) and `UpdateStatus` (`status.json`). Verify `UpdateSyncTest` end to end against the loopback server: update applied at the next start only, a corrupted part discarded with the file named in `status.json`, a lower version ignored, updates off makes no request, scores in the temporary home folder untouched.
- [ ] 3.5 Update `projects/lib-updater/README.md` for 3.1 to 3.4 and add the module to the `AGENTS.md` layout and hard rules and to `docs/ai/architecture.md`. Verify `scripts/check-docs.sh` passes.

## 4. Launcher module

- [ ] 4.1 Add `projects/app-game-control-line-launcher` (depends on `lib-updater` and the Kotlin stdlib only) with `Launcher`, `GameRunner` and the class-loader runner from decision 1, the generated repository/prefix resource and `update-keys.json`. Verify `LauncherTest` with a fake game main: the property is set, the main runs on the calling thread, the update thread does not delay it, an offline check does not fail the start.
- [ ] 4.2 Implement the unclean-start rule (decision 5) in the launcher. Verify `UncleanStartTest`: two failures within 30 seconds mark a version bad and the previous version runs, a crash after 30 seconds does not count, a clean exit clears the mark.
- [ ] 4.3 Give the launcher module the shipped-classpath check and add it to `check`. Verify adding `:lib-core-editor` to its dependencies makes `./gradlew :app-game-control-line-launcher:checkShippedClasspath` fail naming the module; remove the dependency afterwards.
- [ ] 4.4 Document the launcher and the player's update folder (`settings.properties`, `status.json`, how to turn updates off) in `projects/app-game-control-line/README.md`. Verify `scripts/check-docs.sh` passes.

## 5. Packaging: layout, parts, manifest

- [ ] 5.1 Change `abyssus.control-line-packaging` to stage `launcher/`, `game/` (the seed), `game/project/ControlLine` and `installer.digest`, use the launcher jar as `mainJar`, and drop the `-DcontrolLine.project` launcher option. Verify `./gradlew :app-game-control-line:packageApplication` on this machine and start the result: the main menu opens from the seed with updates off.
- [ ] 5.2 Update `CheckPackagePayload` for the two folders (`game/` equals the distribution's `lib/`, `launcher/` holds only the launcher, `lib-updater` and their dependencies, editor-only code refused in both) and `checkExtractedPackage` with it. Verify `CheckPackagePayloadTest` in `build-logic`: clean payload passes, an editor jar in `launcher/` or `game/` fails naming it, a missing game jar fails.
- [ ] 5.3 Add `modules.txt` entries `java.net.http` and `jdk.crypto.ec` with their reasons. Verify `./gradlew :app-game-control-line:jlinkRuntime` passes and `build/packaging/runtime/bin/java --list-modules` lists them.
- [ ] 5.4 Add the tasks that build the two parts, `installer.digest` and `manifest.json` (`packaging/updater.properties` names the installer-only patterns; the digest follows decision 2). Verify `ManifestWriterTest` in `build-logic` (files listed with the right hashes, installer-only jars excluded, digest unchanged when only game code changes and changed when `modules.txt` changes) and that the manifest the task writes binds with `lib-updater`'s DTOs.
- [ ] 5.5 Document the parts, the manifest and the digest in `projects/app-game-control-line/README.md` and add the new commands to `AGENTS.md`. Verify `scripts/check-docs.sh` passes.

## 6. Update key and manifest signing

- [ ] 6.1 Add `scripts/generate-update-key.sh` (`openssl genpkey -algorithm ED25519`, prints the public key in the launcher's `update-keys.json` form, writes the private key only where told) and commit a throwaway test key pair under `lib-updater` test resources only. Verify running it twice yields different keys and the public key binds as a key entry.
- [ ] 6.2 Add `SignManifest` to `build-logic` (JDK Ed25519, private key read from the `UPDATE_SIGNING_KEY` environment variable, written nowhere, never logged) producing `manifest.json.sig` and `SHA256SUMS.sig`. Verify `SignManifestTest`: the signature verifies with `lib-updater`'s `ManifestVerifier`, a missing variable fails naming it, the key text appears in no output or file of the test.
- [ ] 6.3 Add `verifyReleaseManifest` (runs `ManifestVerifier` and the part hashes on a release folder before anything is published). Verify it passes on a good folder and fails on a changed part and on a changed manifest byte.
- [ ] 6.4 Document the key, its storage, rotation (two public keys) and how to verify `SHA256SUMS` with `openssl pkeyutl -verify -rawin` in `projects/lib-updater/README.md` and the game's README. Verify `scripts/check-docs.sh` passes, and verify the documented `openssl` command accepts a signed test file and rejects a changed one.

## 7. Platform signing

- [ ] 7.1 Add `packaging/macos/entitlements.plist` and macOS signing to the packaging plugin (flags as recorded in 1.4), enabled only when signing inputs are present, plus notarize-and-staple steps. Verify on a Mac with a Developer ID certificate in a throwaway keychain: `codesign --verify --deep --strict`, `spctl --assess --type execute` and `xcrun stapler validate` pass on the `.dmg` and the app, and the notarized app flies a round. With no certificate, `packageDmg` still builds unsigned. If no certificate exists yet, leave this open and list the steps for the maintainer.
- [ ] 7.2 Decide the Windows route (design open question), then add the `signWindows` hook (command template, `{file}`, timestamp server) over the app-image launcher and DLLs and the `.msi`, or the remote `.msi` signing step. Verify on Windows: `signtool verify /pa /v` passes on the `.msi` and the installed launcher, the publisher name is the project's, and without the command the build still produces an unsigned `.msi`. If the certificate route is not set up yet, leave this open and list the steps for the maintainer.
- [ ] 7.3 Generate `SHA256SUMS` for the release's packages and sign it (6.2). Verify the `.AppImage` passes the documented check and a one-byte change fails.
- [ ] 7.4 Document the signed packages in the game's README: what each OS shows now, the macOS and Windows first-launch notes for the unsigned canary kept, SmartScreen reputation, and the one-time reinstall for players who have a package from before the launcher. Verify `scripts/check-docs.sh` passes.

## 8. Workflows

- [ ] 8.1 Move the package steps of `build-control-line.yml` into a reusable workflow `_package-control-line.yml` with a `release` input, and make `build-control-line.yml` call it with `release: false`. Verify with `workflow_dispatch` on the branch that the canary jobs, artifacts and names are as before and no secret is read.
- [ ] 8.2 Add `release-control-line.yml` (trigger: tags `control-line-v*`; rejects a tag that is not `X.Y.Z`; build jobs and `publish` run in the `release` environment; `publish` builds the parts and manifest, signs, runs `verifyReleaseManifest`, then creates a non-prerelease release last). Verify `actionlint` accepts it and a dry run on a rehearsal tag in a clone reaches the publish step.
- [ ] 8.3 Guard `release.yml` with `if: ${{ !startsWith(github.event.release.tag_name, 'control-line-v') }}`. Verify by reading the evaluated expression in a rehearsal where a `control-line-v1.0.1` release is created by hand: the plugin publication job is skipped.
- [ ] 8.4 Amend the delta `openspec/changes/add-control-line-native-packages/specs/game-distribution/spec.md`: "Packages carry the game's icon and are unsigned" becomes "unsigned unless built by a release" and "Native packages hold no editor code" accepts the launcher and updater libraries. Verify `openspec validate add-control-line-native-packages --strict` and `openspec validate add-control-line-autoupdate --strict` pass.
- [ ] 8.5 Maintainer setup (cannot be done from the repository): create the `release` environment with required reviewers, add the tag protection rule for `control-line-v*`, store `UPDATE_SIGNING_KEY` and the platform credentials, commit the real public key. Verify the rehearsal in 9.1 reads each secret only in the `release` environment and that a fork pull request build has none.

## 9. Integration

- [ ] 9.1 Rehearse in a private clone or fork with a throwaway key: tag `control-line-v1.0.1`, install it on Windows, macOS and Linux; tag `v1.0.2`; start the installed game, restart it, and check `status.json` and the running version on each. Verify the game runs 1.0.1 on the first start, 1.0.2 after the restart, scores unchanged, and the log shows no network call with updates off.
- [ ] 9.2 Rehearse the failures once: a part changed after signing, a manifest signed by another key, an old release re-published, a release that changes `modules.txt`, and the network cut mid-download. Verify each matches its spec scenario (current version keeps running, reason in `status.json`, new-installer notice for the runtime change).
- [ ] 9.3 Run `./gradlew check`, `scripts/check-docs.sh`, `openspec validate add-control-line-autoupdate --strict`, and the shipped-classpath and payload checks. Verify all pass, then list any open manual checks (7.1, 7.2, 8.5, 9.1, 9.2) in the archive summary.

## Workflow follow-up

- Archive `add-control-line-native-packages` first, then this change, so its delta amendment and the new specs merge into `openspec/specs/`.
