# Spec Delta

## Purpose

Defines how an installed Control Line finds, verifies, downloads and starts a newer version of itself from the project's
tagged releases, and what it does when it cannot, so players stay current without reinstalling and never run a file that
is not from this project.

## ADDED Requirements

### Requirement: A newer release is found at start without delaying it

An installed game SHALL check the project's tagged releases for a newer version each time it starts, within a bounded
wait, and SHALL start the game whatever the outcome. The check SHALL NOT delay the main menu by more than that wait.

#### Scenario: Offline start

- **WHEN** the game is started on a machine with no network
- **THEN** the main menu opens on the version already installed, within the normal start time plus no more than the
  check's wait, and the game's log names the skipped check

#### Scenario: Release server slow or failing

- **WHEN** the release server does not answer within the wait, or answers with an error
- **THEN** the game starts on its current version and the next start checks again

#### Scenario: Already current

- **WHEN** the newest published version equals the running version
- **THEN** nothing is downloaded and the game starts as before

### Requirement: Only stable tagged releases are an update source

The game SHALL consider only releases of the project that are published, are not pre-releases and carry the Control Line
release tag prefix. The rolling canary and any other release SHALL NOT be offered as an update.

#### Scenario: Canary newer than the stable release

- **WHEN** the canary pre-release is newer than the latest stable Control Line release
- **THEN** the game does not download the canary and compares only against the stable release

#### Scenario: Plugin release published

- **WHEN** the project publishes a release of the Abyssus plugin that has no Control Line tag
- **THEN** the game ignores it

### Requirement: An update is verified before it is ever run

A downloaded update SHALL be run only after its manifest is authenticated, and after every downloaded file matches the size
and checksum that manifest states. The manifest is authenticated when GitHub's signed release attestation for the release's
tag, which verifies against the trusted root the installed game carries, lists `manifest.json` with the SHA-256 of the
downloaded bytes. Anything that fails SHALL
be discarded, SHALL NOT be run, and SHALL leave the current version unchanged.

#### Scenario: Tampered file

- **WHEN** a downloaded part differs by one byte from the checksum in the verified manifest
- **THEN** the part is deleted, the update is not applied, the game starts on its current version and the log names the
  failed file

#### Scenario: Manifest not covered by GitHub's attestation

- **WHEN** GitHub's release attestation for the tag does not verify, or does not list this `manifest.json` with this SHA-256
- **THEN** the manifest is ignored, nothing is downloaded, the game starts on its current version and the status file names
  the failure

#### Scenario: Attestation not published yet

- **WHEN** a release was published moments ago and GitHub has not issued its attestation
- **THEN** the release is not applied, the game starts on its current version and the next start checks again

### Requirement: A game never moves to an older version

The game SHALL NOT apply an update whose version is lower than the version it runs, even when the release is correctly
attested.

#### Scenario: Old release re-published

- **WHEN** a correctly attested manifest for version 1.0.4 is offered to a game running 1.0.9
- **THEN** it is ignored and the game stays on 1.0.9

### Requirement: An update applies at the next start and never half-applies

A downloaded update SHALL take effect only on a start after the download finished and verified, never inside a running
game. An interrupted or failed download SHALL leave the previous version intact and startable.

#### Scenario: Update downloaded during play

- **WHEN** an update finishes downloading while the player is flying a round
- **THEN** the running game is unchanged and the next start runs the new version

#### Scenario: Power cut while downloading

- **WHEN** the machine loses power halfway through a download and the game is started again
- **THEN** it starts on the previous version, and the download is resumed or restarted without a repair step

#### Scenario: New version cannot start

- **WHEN** a freshly applied version fails before its main menu opens
- **THEN** the next start runs the previous good version and does not retry the failed one until a newer release exists

### Requirement: An update replaces the game and its project, not the runtime

An update SHALL replace the game's own code and its bundled project. It SHALL NOT replace the Java runtime, the native
libraries or the launcher. When a release needs any of those changed, the game SHALL keep running on its current version
and SHALL state that a new installer is needed, with the release page address.

#### Scenario: Game code and project changed

- **WHEN** a release changes the game's code and one of its bundled scenes only
- **THEN** after the next start the game runs the new code and the new scene

#### Scenario: Native library changed

- **WHEN** a release changes a native library or the bundled Java runtime
- **THEN** the game is not updated in place, keeps running, and the status file states that a new installer is needed

### Requirement: The player's data is never touched by an update

An update SHALL NOT read, write, move or delete the player's saved scores or any file outside the game's update folder.

#### Scenario: Scores survive an update

- **WHEN** a game with saved scores is updated and started
- **THEN** the score tables show the same entries as before the update

### Requirement: The player can turn updates off

The player SHALL be able to turn update checks off, and when off the game SHALL make no network request for updates and
SHALL run only the version it has. The check SHALL send nothing that identifies the player beyond what any HTTPS request
carries.

#### Scenario: Updates off

- **WHEN** updates are turned off and the game is started with the network available
- **THEN** no request for updates is made and the game starts on its current version

### Requirement: The result of the last check is readable

After each start the game SHALL leave a status file in its update folder that states the running version, the newest
version seen, whether an update is waiting for the next start, and the reason when a check, a download or a verification
failed or a new installer is needed.

#### Scenario: Update waiting

- **WHEN** an update was downloaded and verified during the last session
- **THEN** the status file names the running version, the waiting version and that it applies at the next start

#### Scenario: Verification failure

- **WHEN** a download failed verification
- **THEN** the status file names the version and the failed file
