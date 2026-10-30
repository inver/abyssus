/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.sync

import net.nevinsky.abyssus.lib.updater.hashing.sha256Hex
import net.nevinsky.abyssus.lib.updater.hashing.hex
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock
import net.nevinsky.abyssus.lib.updater.attest.ManifestAuthenticator
import net.nevinsky.abyssus.lib.updater.manifest.Manifest
import net.nevinsky.abyssus.lib.updater.manifest.ManifestException
import net.nevinsky.abyssus.lib.updater.manifest.ManifestPart
import net.nevinsky.abyssus.lib.updater.manifest.VerificationException
import net.nevinsky.abyssus.lib.updater.manifest.parseVersion
import net.nevinsky.abyssus.lib.updater.manifest.parseVersionOrNull
import net.nevinsky.abyssus.lib.updater.release.FetchException
import net.nevinsky.abyssus.lib.updater.release.HttpFetcher
import net.nevinsky.abyssus.lib.updater.release.Release
import net.nevinsky.abyssus.lib.updater.release.ReleaseSource
import net.nevinsky.abyssus.lib.updater.release.ReleaseSourceException
import net.nevinsky.abyssus.lib.updater.store.StatusData
import net.nevinsky.abyssus.lib.updater.store.UpdateSettings
import net.nevinsky.abyssus.lib.updater.store.UpdateState
import net.nevinsky.abyssus.lib.updater.store.UpdateStatus
import net.nevinsky.abyssus.lib.updater.store.VersionStore

/** What a check ended in. */
sealed interface SyncOutcome {
    /** Updates are turned off: no request was made. */
    data object Off : SyncOutcome

    data object NoRelease : SyncOutcome

    data object UpToDate : SyncOutcome

    data class Applied(val version: String) : SyncOutcome

    data class NewInstallerNeeded(val version: String?, val reason: String) : SyncOutcome

    /** A release was seen and refused, or the check failed; the current version keeps running. */
    data class Rejected(val reason: String, val file: String? = null) : SyncOutcome
}

private const val MANIFEST = "manifest.json"
private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024

/**
 * One update check: find the newest stable release, authenticate its manifest, stage the changed parts, check every
 * file, and make the new version current (it runs from the next start). Anything that fails removes the staging folder,
 * records the reason in `status.json` and leaves the current version unchanged. Writes only under the update folder.
 *
 * @param runningVersion the version this process runs; @param installedDigest the `installer.digest` of the install.
 */
class UpdateSync(
    private val source: ReleaseSource,
    private val fetcher: HttpFetcher,
    private val authenticator: ManifestAuthenticator,
    private val store: VersionStore,
    private val settings: UpdateSettings,
    private val state: UpdateState,
    private val status: UpdateStatus,
    private val product: String,
    private val runningVersion: String,
    private val installedDigest: String,
    private val clock: Clock = Clock.systemUTC(),
    private val planner: UpdatePlanner = UpdatePlanner(),
    private val unzip: SafeUnzip = SafeUnzip(),
) {
    /** Runs one check. Never throws for network, verification or storage problems: they come back as [SyncOutcome.Rejected]. */
    fun run(): SyncOutcome {
        if (!settings.enabled()) {
            write(null, "Updates are turned off")
            return SyncOutcome.Off
        }
        return try {
            check()
        } catch (e: ReleaseSourceException) {
            write(null, "Could not read the release list: ${e.message}")
            SyncOutcome.Rejected("Could not read the release list: ${e.message}")
        }
    }

    private fun check(): SyncOutcome {
        val running = parseVersion(runningVersion)
        val release = source.latestStable()
        if (release == null) {
            write(null, "No stable release was found")
            return SyncOutcome.NoRelease
        }
        if (release.version <= running) {
            write(release.version.toString(), null)
            return SyncOutcome.UpToDate
        }
        val newest = release.version.toString()
        val manifest = try {
            verifiedManifest(release)
        } catch (e: VerificationException) {
            return reject(newest, "The manifest of $newest could not be verified: ${e.message}", MANIFEST)
        } catch (e: ManifestException) {
            if (e.newInstallerNeeded) return needInstaller(release, e.message!!)
            return reject(newest, "The manifest of $newest is unusable: ${e.message}", MANIFEST)
        } catch (e: FetchException) {
            return reject(newest, "Could not fetch the manifest of $newest: ${e.message}")
        }
        val version = parseVersionOrNull(manifest.version)
        return when {
            manifest.product != product -> reject(newest, "The manifest is for '${manifest.product}', not '$product'", MANIFEST)
            version == null || version != release.version ->
                reject(newest, "The manifest version '${manifest.version}' does not match the release $newest", MANIFEST)
            version <= running -> {
                write(version.toString(), "Ignored a manifest for ${manifest.version}, not above $runningVersion")
                SyncOutcome.UpToDate
            }
            manifest.installerDigest != installedDigest ->
                needInstaller(release, "Version ${manifest.version} changes the Java runtime or native libraries")
            else -> stage(release, manifest)
        }
    }

    private fun verifiedManifest(release: Release): Manifest {
        val manifestAsset = release.asset(MANIFEST) ?: throw VerificationException("The release has no $MANIFEST")
        val bytes = fetcher.getBytes(manifestAsset.url, MAX_MANIFEST_BYTES)
        return authenticator.authenticate(release, bytes)
    }

    private fun stage(release: Release, manifest: Manifest): SyncOutcome {
        val version = manifest.version
        val base = store.resolve(state.load().bad).dir
        val plan = planner.plan(manifest, BaseFiles { relative -> base.resolve(relative).takeIf { Files.isRegularFile(it) }?.let { sha256Hex(it) } })
        try {
            store.cleanUp()
            val staging = store.beginStaging(version, manifest.files.sumOf { it.size })
            plan.filesToCopy.forEach { file ->
                val from = base.resolve(file.path)
                val to = staging.resolve(file.path)
                Files.createDirectories(to.parent)
                Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
            }
            for (part in plan.partsToDownload) {
                val asset = release.asset(part.name) ?: throw PartException("The release has no asset ${part.name}", part.name)
                val zip = staging.resolve(part.name + ".download")
                download(asset.url, part, zip)
                Files.newInputStream(zip).use { unzip.extract(it, manifest.files.filter { f -> f.part == part.name }, staging) }
                Files.delete(zip)
            }
            for (file in manifest.files) {
                val staged = staging.resolve(file.path)
                if (!Files.isRegularFile(staged) || Files.size(staged) != file.size || sha256Hex(staged) != file.sha256) {
                    throw PartException("'${file.path}' does not match the manifest", file.path)
                }
            }
            store.commit(version)
        } catch (e: PartException) {
            return failed(release, version, "The update $version failed verification: ${e.message}", e.file)
        } catch (e: UnzipException) {
            return failed(release, version, "The update $version failed verification: ${e.message}", e.file)
        } catch (e: FetchException) {
            return failed(release, version, "The download of $version failed: ${e.message}", null)
        } catch (e: IOException) {
            return failed(release, version, "The update $version could not be stored: ${e.message}", null)
        }
        status.write(
            StatusData(
                now(), runningVersion, newestVersion = version, waitingVersion = version, appliesAtNextStart = true,
                releaseUrl = release.pageUrl.ifEmpty { null },
            )
        )
        return SyncOutcome.Applied(version)
    }

    private fun download(url: java.net.URI, part: ManifestPart, target: Path) {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        fetcher.get(url).use { fetched ->
            if (fetched.status != 200) throw FetchException("${part.name} answered HTTP ${fetched.status}")
            Files.newOutputStream(target).use { sink ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = fetched.body.read(buffer)
                    if (n < 0) break
                    size += n
                    if (size > part.size) throw PartException("${part.name} is larger than the manifest says", part.name)
                    digest.update(buffer, 0, n)
                    sink.write(buffer, 0, n)
                }
            }
        }
        if (size != part.size) throw PartException("${part.name} is smaller than the manifest says", part.name)
        if (hex(digest.digest()) != part.sha256) throw PartException("${part.name} does not match its checksum", part.name)
    }

    private fun failed(release: Release, version: String, reason: String, file: String?): SyncOutcome {
        store.abandonStaging(version)
        status.write(
            StatusData(
                now(), runningVersion, newestVersion = version, releaseUrl = release.pageUrl.ifEmpty { null }, reason = reason,
                failedVersion = version, failedFile = file,
            )
        )
        return SyncOutcome.Rejected(reason, file)
    }

    private fun reject(newest: String, reason: String, file: String? = null): SyncOutcome {
        status.write(StatusData(now(), runningVersion, newestVersion = newest, reason = reason, failedVersion = newest, failedFile = file))
        return SyncOutcome.Rejected(reason, file)
    }

    private fun needInstaller(release: Release, reason: String): SyncOutcome {
        status.write(
            StatusData(
                now(), runningVersion, newestVersion = release.version.toString(), newInstallerNeeded = true,
                releaseUrl = release.pageUrl.ifEmpty { null }, reason = "$reason: a new installer is needed",
            )
        )
        return SyncOutcome.NewInstallerNeeded(release.version.toString(), reason)
    }

    private fun write(newest: String?, reason: String?) =
        status.write(StatusData(now(), runningVersion, newestVersion = newest, reason = reason))

    private fun now() = clock.instant().toString()
}

/** A part or file that does not match the signed manifest. */
private class PartException(message: String, val file: String) : Exception(message)
