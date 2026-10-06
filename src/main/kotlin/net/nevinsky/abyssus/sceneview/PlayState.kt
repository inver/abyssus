/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation

/**
 * Play in one Scene view, without Swing or GL: `IDLE -> STARTING -> PLAYING <-> PAUSED -> IDLE`, and `FAILED` when the
 * simulation ends on its own. Every method runs on the EDT; [ui] brings the simulation's callbacks there (tests pass a
 * direct executor). A provider that throws is switched off for the view with one error to [logError].
 */
class PlayState(
    private var provider: SceneSimulationProvider?,
    private val source: String = provider?.javaClass?.name.orEmpty(),
    private val ui: (Runnable) -> Unit = Runnable::run,
    private val logError: (String, Throwable) -> Unit = { _, _ -> },
) {
    enum class Phase { IDLE, STARTING, PLAYING, PAUSED, FAILED }

    var phase = Phase.IDLE
        private set

    /** Why the last simulation failed, while [phase] is `FAILED`. */
    var failure: String? = null
        private set

    /** Called after every change of [phase] or [available]. */
    var onChanged: (() -> Unit)? = null

    /** Whether the view offers Play: a provider is installed and has not failed by throwing. */
    val available: Boolean get() = provider != null

    /** Whether a simulation runs or is starting: poses come from it, gizmos are off and input goes to it. */
    val active: Boolean get() = phase == Phase.STARTING || phase == Phase.PLAYING || phase == Phase.PAUSED

    private var simulation: SceneSimulation? = null
    private var generation = 0

    /** Starts a simulation from [request] when idle or failed; resumes a paused one. */
    fun play(request: () -> SimulationRequest) {
        when (phase) {
            Phase.IDLE, Phase.FAILED -> start(request)
            Phase.PAUSED -> call { it.resume() }?.let { change(Phase.PLAYING) }
            Phase.STARTING, Phase.PLAYING -> Unit
        }
    }

    fun pause() {
        if (phase == Phase.PLAYING) call { it.pause() }?.let { change(Phase.PAUSED) }
    }

    /** Advances a paused simulation by one step; it stays paused. */
    fun step() {
        if (phase == Phase.PAUSED) call { it.step() }
    }

    /** Ends the simulation; the view shows the scene as the document holds it. */
    fun stop() {
        if (!active) {
            if (phase == Phase.FAILED) change(Phase.IDLE)
            return
        }
        end()
        change(Phase.IDLE)
    }

    /** Escape while playing stops; it returns whether it did, so Escape does nothing else then. */
    fun escape(): Boolean = active.also { if (it) stop() }

    /** The scene's document is about to change: play stops first, so the edit applies to the authored scene. */
    fun documentChanging() = stop()

    fun input(event: SimulationInput) {
        if (phase == Phase.PLAYING || phase == Phase.PAUSED) call { it.input(event) }
    }

    /** The simulated poses to show, or null for the authored ones. */
    fun poses(): Map<String, Pose>? {
        if (!active) return null
        val sim = simulation ?: return null
        return runCatchingKeepingCancellation { sim.poses() }.getOrNull()
    }

    private fun start(request: () -> SimulationRequest) {
        val p = provider ?: return
        failure = null
        val g = ++generation
        change(Phase.STARTING)
        val listener = object : SimulationListener {
            override fun started() = ui(Runnable { if (g == generation && phase == Phase.STARTING) change(Phase.PLAYING) })
            override fun failed(message: String) = ui(Runnable { if (g == generation && active) fail(message) })
        }
        runCatchingKeepingCancellation { p.start(request(), listener) }
            .onSuccess { if (g == generation && active) simulation = it else it.stop() }
            .onFailure { disable(it) }
    }

    private fun fail(message: String) {
        end()
        failure = message
        change(Phase.FAILED)
    }

    private fun end() {
        generation++
        val sim = simulation ?: return
        simulation = null
        runCatchingKeepingCancellation { sim.stop() }.onFailure { logError("Simulation of $source failed to stop", it) }
    }

    /** Runs [command] on the simulation; a throwing provider is switched off. Null when it threw or there is none. */
    private fun call(command: (SceneSimulation) -> Unit): Unit? {
        val sim = simulation ?: return null
        return runCatchingKeepingCancellation { command(sim) }.onFailure { disable(it) }.getOrNull()
    }

    private fun disable(error: Throwable) {
        logError("Scene simulation of $source failed and is switched off for this view", error)
        provider = null
        val sim = simulation
        simulation = null
        generation++
        sim?.let { s -> runCatchingKeepingCancellation { s.stop() } }
        failure = error.message
        change(Phase.IDLE)
    }

    private fun change(next: Phase) {
        phase = next
        onChanged?.invoke()
    }
}
