# lib-updater

Auto-update for an installed game: the release manifest and its authentication by GitHub's release attestation, the release
lookup, the sync and the version store. A plain JVM library, root package
`net.nevinsky.abyssus.lib.updater`, wired by constructors, with no `object` or `companion object` (`checkNoSingletons`).
It holds no editor, IntelliJ, AWT or libGDX import (`checkNoEditorCode`), because a game ships it through its launcher.
Depends on Jackson and BouncyCastle (RFC 3161 time-stamp tokens). Behavior is specified in `openspec/changes/add-control-line-autoupdate` (`game-auto-update`).

## Manifest (`manifest/`)

`manifest.json` describes one release. It is not an Abyssus document and has its own `schemaVersion`, currently `1`:

| Field             | Meaning                                                                                                                                                                                                              |
|-------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `schemaVersion`   | `1`. Any other value means "a new installer is needed" (`ManifestException.newInstallerNeeded`).                                                                                                                     |
| `product`         | `control-line`.                                                                                                                                                                                                      |
| `version`         | Numeric dotted (`1.2.0`). `VersionOrder` compares numbers, so `1.10.0` is above `1.9.0`; anything else is rejected.                                                                                                  |
| `keyId`           | Id of the key that signed it. Must be the key that verified the signature.                                                                                                                                           |
| `mainClass`       | The game's main class, loaded from the synced jars.                                                                                                                                                                  |
| `installerDigest` | SHA-256 of the installer-only content. A difference from the installed digest means a new installer is needed.                                                                                                       |
| `parts[]`         | `name`, `size`, `sha256` of each downloadable zip.                                                                                                                                                                   |
| `files[]`         | `path`, `size`, `sha256` and `part` of each file. `part` is the zip it comes in, or `installer` for a jar the installer owns (native code): it is not downloaded, only copied from the installed folder and checked. |

Unknown fields are ignored, so a later release can add data an older game skips.

## Trust rules

There is no update signing key. A release is an **immutable GitHub release**, and GitHub signs it: it issues a *release
attestation*, a Sigstore bundle whose DSSE envelope holds an in-toto statement
(`https://in-toto.io/attestation/release/v0.2`) naming the repository, the tag, the commit (`pkg:github/<repo>@<tag>`, SHA-1)
and every asset with its SHA-256. The game applies a manifest only when that attestation verifies **and** lists `manifest.json`
with the SHA-256 of the exact bytes it downloaded (`ReleaseManifestAuthenticator`).

`ReleaseAttestationVerifier` (`attest/`) accepts a bundle only when all of these hold:

1. An RFC 3161 time-stamp token verifies with a time-stamping authority of the trusted root and covers the SHA-256 of the
   envelope signature.
2. The signing certificate chains to a certificate authority of the trusted root that was valid at that time, is not a CA,
   has the code-signing usage and names `https://dotcom.releases.github.com` (GitHub's release signer).
3. The envelope's ECDSA P-256 signature over its PAE verifies with the certificate's key.
4. The statement is a release statement for this repository and the expected tag.

The bundle comes from `GET /repos/<repo>/attestations/sha256:<digest of manifest.json>` (anonymous; inline or a Snappy-compressed
`bundle_url`) and is untrusted until it verifies. GitHub issues the attestation a short while after publishing, so a very
new release is not applied yet; the next start asks again. A version below the running one is never applied, even when attested.
Part and file hashes live inside the manifest, so replacing a release asset cannot pass verification.

**Trusted root.** `src/main/resources/github-trusted-root.json` is the `trusted_root.json` target of GitHub's TUF repository
(`https://tuf-repo.github.com`; SHA-256 `484cdfe1a7c65479c5ba2a22193d1be90f0020db1997de696ab207434c62fbb7` when taken, the value
GitHub's signed targets metadata lists). It is bundled, so it changes only with a new installer; a release attested under a
CA it lacks is refused and `status.json` names the failure. To refresh it, read `targets.json` at the newest version listed in
`snapshot.json` (`https://tuf-repo.github.com/<n>.snapshot.json`, `<m>.targets.json`), download
`targets/<sha256>.trusted_root.json`, compare the SHA-256 and replace the file. A TUF client would automate this.

**Tests.** `ReleaseAttestationTest` runs the verifier on a real attestation GitHub published for `cli/cli` v2.103.3
(`src/test/resources/cli-release-attestation.json`, fetched from the attestations API and Snappy-decoded) and on tampered copies.

## Release lookup (`release/`)

`ReleaseSource` returns the newest stable release (`null` when none). `GitHubReleaseSource` reads
`GET /repos/<owner>/<repo>/releases` anonymously, drops drafts, pre-releases (the canary) and tags without the
`control-line-v` prefix plus a numeric version, and keeps the highest. It sends the cached ETag (`ResponseCache`;
`store.FileResponseCache` keeps it in `releases.etag` / `releases.json`), so a `304` reuses the cached list at no
rate-limit
cost. Only asset **names** and URLs are taken from that JSON; what the game runs comes from the signed manifest.

`HttpFetcher` is the only network code: a `HostPolicy` checks every request and every redirect hop (production policy:
HTTPS to `github.com`, `api.github.com` or `*.githubusercontent.com`; tests inject a loopback policy), at most 5
redirects,
connect and answer timeouts, a fixed `User-Agent`, no automatic redirects. `FakeReleaseSource` (test fixtures) stands in
for a source.

## Sync (`sync/`)

`UpdateSync.run()` is one check and never throws for network, verification or disk problems: it returns a `SyncOutcome`
and writes `status.json`.

1. Updates off (`UpdateSettings`) -> `Off`, no request.
2. Newest stable release not above the running version -> `UpToDate`; the manifest is not downloaded.
3. Download `manifest.json`, authenticate it (see "Trust rules"), require `product`, and require the manifest version to
   equal
   the release's and to be above the running version.
4. `installerDigest` differs from the installed `installer.digest` (or an unknown `schemaVersion`) ->
   `NewInstallerNeeded`.
5. `UpdatePlanner` compares per-file hashes with the folder in use and downloads only the parts with a changed file; the
   other files are copied. Each part is checked for size and SHA-256 while streaming, `SafeUnzip` extracts it (no `..`,
   absolute or backslash paths, no entry missing from the manifest, size and hash checked), then every manifest file is
   checked again in `staging/<version>/`.
6. `VersionStore.commit` writes `COMPLETE`, renames the folder into `versions/` and replaces `current` by an atomic
   move.

Any failure removes the staging folder and leaves `current` alone. Nothing outside the update folder is written.

## Update folder (`store/`)

```
settings.properties   enabled=true|false      (also CONTROL_LINE_UPDATES=off, -DcontrolLine.updates=off)
status.json           result of the last check (running, newest, waiting version, reason, failed file)
state.json            launcher state: launching version, unclean starts, versions marked bad
current               the version to run
versions/<v>/         complete versions (marker COMPLETE); the current and the previous one are kept
staging/<v>/          a download in progress; removed on failure and at the next start
```

A `current` that is not a well-formed version runs the seed; one that names a missing or incomplete version runs the
newest
complete, not-bad version, else the seed.

## Release checks

- **Before publishing:** `./gradlew :lib-updater:verifyReleaseManifest -PreleaseDir=<folder> [-PrequireChecksums=true]` checks that
  the manifest binds, every part has the size and SHA-256 the manifest states and every file `SHA256SUMS` lists matches. It
  prints each problem and fails.
- **After publishing:** `./gradlew :lib-updater:verifyPublishedRelease -Prepository=<owner/repo> -Ptag=<tag> -PreleaseDir=<folder>`
  waits (default 10 minutes) for GitHub's attestation of the tag, verifies it with the same code the game runs and checks it
  lists every file of the folder with the SHA-256 that was uploaded.
- **A player checks a download** with the GitHub CLI: `gh release verify <tag> -R <owner/repo>` (the release is immutable and
  attested) and `gh release verify-asset <tag> <file> -R <owner/repo>` (the file is one of its assets); or by hand with
  `sha256sum --ignore-missing -c SHA256SUMS` against the downloaded `SHA256SUMS`.
