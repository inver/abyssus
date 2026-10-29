# Spec Delta

## Purpose

Defines what a Control Line release build signs and publishes, when a build must fail instead of publishing, and how
signing keys are kept and rotated, so players and the game's own updater can tell a real release from a forged one.

## ADDED Requirements

### Requirement: A tagged release publishes a signed update manifest

Pushing a Control Line release tag SHALL build the game and publish a release that holds the update parts, a manifest
that names every part with its size and checksum, and a signature of that manifest. The manifest SHALL name the version,
the signing key and the installer content the update cannot replace.

#### Scenario: Release tag pushed

- **WHEN** the tag `control-line-v1.2.0` is pushed and every platform builds
- **THEN** a release of that name is published with the update parts, the manifest `manifest.json`, its signature
  `manifest.json.sig` and the installers

#### Scenario: Manifest content

- **WHEN** a published manifest is read
- **THEN** it states version 1.2.0, the id of the key that signed it, each part's name, size and checksum, and a digest
  of the installer-only content

### Requirement: Release builds fail rather than publish unsigned

A release build SHALL fail, publishing nothing, when any signing step cannot run: the update key, the Windows signature,
the macOS signature or notarization, or the checksum signature. A failed platform SHALL leave the previous release as the
latest.

#### Scenario: Signing credentials missing

- **WHEN** a tag build runs without the update signing key
- **THEN** the build fails naming the missing signing step and no release is created

#### Scenario: Notarization rejected

- **WHEN** Apple rejects the notarization of the macOS image
- **THEN** the build fails, naming the rejection, and the previous release remains the latest

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

### Requirement: Linux packages are covered by a signed checksum list

A release SHALL publish a `SHA256SUMS` file naming every Linux package and installer in it, with a signature that
verifies against the project's published update key, and the documentation SHALL show how to check it.

#### Scenario: Checking a download

- **WHEN** a player verifies the `.AppImage` against `SHA256SUMS` and the signature as documented
- **THEN** the check passes for the published file and fails for a file changed by one byte

### Requirement: Builds that are not releases stay unsigned and need no secrets

Pull-request builds and the rolling canary SHALL be built, checked and published exactly as before, unsigned, without
access to any signing key, certificate or credential, and without the ability to sign anything.

#### Scenario: Pull request from a fork

- **WHEN** a pull request from a fork is built
- **THEN** every package is built and checked, no secret is read, and no signature step runs

#### Scenario: Canary is not an update source

- **WHEN** the canary is published
- **THEN** it holds no manifest signature and the installed game never offers it as an update

### Requirement: Signing keys are protected and can be rotated

The update signing key and the platform signing credentials SHALL be available only to the release build that runs in
the protected release environment, SHALL NOT appear in logs or artifacts, and a published installer SHALL accept a second
public key so a key can be rotated without stranding installed games.

#### Scenario: Key not exposed

- **WHEN** a release build log and its artifacts are searched for the private key
- **THEN** the key is not found

#### Scenario: Rotation

- **WHEN** a release is signed with the new key and the installed game carries both the old and the new public key
- **THEN** the game verifies it and updates, and a game that carries only the old key reports that a new installer is
  needed

### Requirement: A Control Line release never publishes the plugin

Publishing a Control Line release SHALL NOT start the Abyssus plugin's Marketplace publication, and publishing a plugin
release SHALL NOT produce Control Line packages or an update.

#### Scenario: Control Line release published

- **WHEN** a release tagged `control-line-v1.2.0` is published
- **THEN** the plugin publication workflow does not run
