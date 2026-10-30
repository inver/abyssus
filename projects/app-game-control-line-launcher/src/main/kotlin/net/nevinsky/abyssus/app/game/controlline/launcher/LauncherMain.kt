/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.launcher

import net.nevinsky.abyssus.lib.updater.attest.GitHubAttestations
import net.nevinsky.abyssus.lib.updater.attest.ReleaseAttestationVerifier
import net.nevinsky.abyssus.lib.updater.attest.ReleaseManifestAuthenticator
import net.nevinsky.abyssus.lib.updater.attest.bundledTrustedRoot
import net.nevinsky.abyssus.lib.updater.release.GitHubReleaseSource
import net.nevinsky.abyssus.lib.updater.release.HttpFetcher
import net.nevinsky.abyssus.lib.updater.release.githubHostPolicy
import net.nevinsky.abyssus.lib.updater.store.*
import net.nevinsky.abyssus.lib.updater.sync.UpdateSync
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.*

/**
 * The packaged application's entry point. `controlLine.installDir` names the install folder (it holds `game/` and
 * `installer.digest`); `controlLine.updateDir` overrides the player's update folder (`~/.abyssus-control-line/updates`).
 */
fun main() {
    val resources = Properties().apply {
        Launcher::class.java.getResourceAsStream("/launcher.properties")!!.use { load(it) }
    }
    val install = Path.of(System.getProperty("controlLine.installDir") ?: error("controlLine.installDir is not set"))
    val updates = System.getProperty("controlLine.updateDir")?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".abyssus-control-line", "updates")
    val seed = install.resolve("game")
    val config = LauncherConfig(
        seed = seed,
        updates = updates,
        seedVersion = resources.getProperty("version"),
        mainClass = resources.getProperty("mainClass"),
        environment = System.getenv(),
        systemProperty = System::getProperty,
        updatesDefault = resources.getProperty("updatesDefault").toBoolean(),
    )
    val digest = Files.readString(install.resolve("installer.digest")).trim()
    val check = { running: String ->
        val fetcher = HttpFetcher(githubHostPolicy(), Duration.ofSeconds(10), "control-line/$running")
        val repository = resources.getProperty("repository")
        // a release is trusted only when GitHub's signed release attestation (bundled trusted root) lists its manifest
        val authenticator = ReleaseManifestAuthenticator(GitHubAttestations(fetcher, repository), ReleaseAttestationVerifier(bundledTrustedRoot(), repository))
        UpdateSync(
            GitHubReleaseSource(
                fetcher,
                resources.getProperty("repository"),
                resources.getProperty("tagPrefix"),
                FileResponseCache(updates)
            ),
            fetcher, authenticator, VersionStore(updates, seed),
            UpdateSettings(
                updates.resolve("settings.properties"),
                System.getenv(),
                System::getProperty,
                config.updatesDefault
            ),
            UpdateState(updates.resolve("state.json")), UpdateStatus(updates.resolve("status.json")),
            resources.getProperty("product"), running, digest,
        ).run().let { System.err.println("[control-line] Update check: $it") }
    }
    Launcher(config, ClassLoaderRunner(), check).start()
}
