# Spec Delta

## Purpose

Defines what a Control Line release build signs and publishes, who does the signing, when a build must fail instead of
publishing, and what the installed game trusts, so players and the game's own updater can tell a real release from a forged
one without this project holding an update signing key. Release authenticity comes from GitHub: an immutable release carries a
release attestation signed by GitHub that lists the tag, the commit and the SHA-256 of every asset.

## ADDED Requirements

### Requirement: A tagged release publishes an immutable release signed by GitHub

Pushing a Control Line release tag SHALL build the game and publish an immutable GitHub release that holds the update parts,
a manifest that names every part with its size and checksum, a `SHA256SUMS` file and the installers. The project SHALL hold no
update signing key: the manifest is authenticated by GitHub's release attestation of that release, which lists `manifest.json`
with its SHA-256. The manifest SHALL name the version and the installer content the update cannot replace.

#### Scenario: Release tag pushed

- **WHEN** the tag `control-line-v1.2.0` is pushed and every platform builds
- **THEN** an immutable release of that name is published with the update parts, `manifest.json`, `SHA256SUMS` and the
  installers, and GitHub's release attestation of the tag lists each of them with its SHA-256

#### Scenario: Manifest content

- **WHEN** a published manifest is read
- **THEN** it states version 1.2.0, each part's name, size and checksum, and a digest of the installer-only content, and
  names no key

### Requirement: Release builds fail rather than publish unsigned

A release build SHALL fail, publishing nothing, when a signing step cannot run before publishing: the Windows signature, the
macOS signature or notarization, or the repository's immutable-release setting being off. After publishing, the build SHALL
wait for GitHub's release attestation and fail, reporting the difference, when it is missing or does not list every uploaded
file with the SHA-256 that was uploaded. A failed platform SHALL leave the previous release as the latest.

#### Scenario: Immutable releases switched off

- **WHEN** a tag build runs while immutable releases are not enabled for the repository
- **THEN** the build fails naming the setting and no release is created

#### Scenario: Notarization rejected

- **WHEN** Apple rejects the notarization of the macOS image
- **THEN** the build fails, naming the rejection, and the previous release remains the latest

#### Scenario: Attestation does not match

- **WHEN** GitHub's release attestation of the published tag lists a file with another SHA-256 than the one uploaded
- **THEN** the build fails naming the file

### Requirement: Windows and macOS installers are signed by the project

On a release build the Windows installer SHALL carry a valid code signature naming the project's publisher, and the
macOS disk image SHALL be signed with a Developer ID certificate, notarized and stapled, so that neither operating
system shows an unidentified-publisher warning for the signed file.

#### Scenario: Windows installer signature

- **WHEN** the released `.msi` is checked with the operating system's signature viewer
- **THEN** it shows a valid signature with the project's publisher name and a trusted timestamp

#### Scenario: macOS disk image

- **WHEN** the released `.dmg` is opened and the app started on a Mac with Gatekeeper on
- **THEN** the app starts without a warning that the developer cannot be verified, and the system reports the app as
  notarized

### Requirement: Linux packages are covered by the release attestation

A release SHALL publish a `SHA256SUMS` file naming every Linux package and installer in it, covered by GitHub's release
attestation like every other asset, and the documentation SHALL show how to check a download against the attestation and
against `SHA256SUMS`.

#### Scenario: Checking a download

- **WHEN** a player verifies the `.AppImage` as documented (`gh release verify-asset`, and `sha256sum` against `SHA256SUMS`)
- **THEN** the check passes for the published file and fails for a file changed by one byte

### Requirement: Builds that are not releases stay unsigned and need no secrets

Pull-request builds and the rolling canary SHALL be built, checked and published exactly as before, unsigned, without
access to any signing credential, and without the ability to sign anything.

#### Scenario: Pull request from a fork

- **WHEN** a pull request from a fork is built
- **THEN** every package is built and checked, no secret is read, and no signature step runs

#### Scenario: Canary is not an update source

- **WHEN** the canary is published
- **THEN** it is a pre-release without a manifest and the installed game never offers it as an update

### Requirement: Platform credentials are protected and the trust root is pinned

The Windows and macOS signing credentials SHALL be available only to the release build that runs in the protected release
environment and SHALL NOT appear in logs or artifacts. The installed game SHALL carry GitHub's trusted root for release
attestations, and SHALL refuse an attestation that does not chain to it.

#### Scenario: Credentials not exposed

- **WHEN** a release build log and its artifacts are searched for the signing credentials
- **THEN** none is found

#### Scenario: Unknown certificate authority

- **WHEN** a release's attestation chains to a certificate authority that the installed game's trusted root does not contain
- **THEN** the release is not applied, the game keeps running its version and the status file names the verification failure

### Requirement: A Control Line release never publishes the plugin

Publishing a Control Line release SHALL NOT start the Abyssus plugin's Marketplace publication, and publishing a plugin
release SHALL NOT produce Control Line packages or an update.

#### Scenario: Control Line release published

- **WHEN** a release tagged `control-line-v1.2.0` is published
- **THEN** the plugin publication workflow does not run
