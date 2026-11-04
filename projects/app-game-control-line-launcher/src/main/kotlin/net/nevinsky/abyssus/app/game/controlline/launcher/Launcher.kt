/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.launcher

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.nevinsky.abyssus.lib.updater.store.UpdateSettings
import net.nevinsky.abyssus.lib.updater.store.UpdateState
import net.nevinsky.abyssus.lib.updater.store.VersionStore

/** Where things are: the installed seed (`game/`), the player's update folder and the game to start. */
data class LauncherConfig(
    val seed: Path,
    val updates: Path,
    val seedVersion: String,
    val mainClass: String,
    val environment: Map<String, String> = emptyMap(),
    val systemProperty: (String) -> String? = { null },
    val updatesDefault: Boolean = true,
    /** A start counts as clean once the game has run this long, or its main returned. */
    val cleanAfter: Duration = Duration.ofSeconds(30),
)

/** Two unclean starts of one version in a row mark it bad. */
private const val UNCLEAN_LIMIT = 2

/**
 * Picks the game folder to run, starts [updateCheck] on a daemon thread (it never delays the start) and runs the game.
 * A start that ends in a throwable, or that is still marked `launching` at the next start (a crash within the first
 * [LauncherConfig.cleanAfter]), counts against that version; a synced version is skipped after two in a row. The seed is
 * never marked.
 */
class Launcher(
    private val config: LauncherConfig,
    private val runner: GameRunner,
    private val updateCheck: (runningVersion: String) -> Unit,
    private val log: (String) -> Unit = { System.err.println("[control-line] $it") },
) {
    private val state = UpdateState(config.updates.resolve("state.json"))
    private val store = VersionStore(config.updates, config.seed)

    fun start() {
        val settings = UpdateSettings(config.updates.resolve("settings.properties"), config.environment, config.systemProperty, config.updatesDefault)
        accountForLastStart()
        val folder = store.resolve(state.load().bad)
        val synced: String? = folder.version
        val running = synced ?: config.seedVersion
        if (settings.enabled()) startCheck(running) else log("Update check skipped: updates are off")
        if (synced != null) state.update { it.copy(launching = synced) }
        val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "control-line-clean-start").apply { isDaemon = true } }
        try {
            if (synced != null) {
                timer.schedule({ markClean(synced) }, config.cleanAfter.toMillis(), TimeUnit.MILLISECONDS)
            }
            runner.run(GameLaunch(gameJars(folder.dir), config.mainClass, folder.dir.resolve("project/ControlLine")))
            if (synced != null) markClean(synced)
        } catch (e: Throwable) {
            // after the clean period `launching` is already cleared: a late crash never counts
            if (synced != null && state.load().launching == synced) countUnclean(synced)
            throw e
        } finally {
            timer.shutdownNow()
        }
    }

    private fun startCheck(running: String) {
        Thread({
            try {
                updateCheck(running)
            } catch (e: Exception) {
                log("Update check failed: ${e.message}")
            }
        }, "control-line-update").apply { isDaemon = true }.start()
    }

    /** A `launching` mark left by the previous start means it crashed early. */
    private fun accountForLastStart() {
        val left = state.load().launching ?: return
        log("The previous start of $left did not finish cleanly")
        countUnclean(left)
    }

    private fun countUnclean(version: String) {
        state.update {
            val count = (it.unclean[version] ?: 0) + 1
            val bad = if (count >= UNCLEAN_LIMIT) it.bad + version else it.bad
            if (count >= UNCLEAN_LIMIT) log("Version $version marked bad after $count unclean starts")
            it.copy(launching = null, unclean = it.unclean + (version to count), bad = bad)
        }
    }

    private fun markClean(version: String) {
        state.update { if (it.launching == version) it.copy(launching = null, unclean = it.unclean - version) else it }
    }
}
