/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import net.nevinsky.abyssus.lib.core.editor.ray.RayModePhase
import net.nevinsky.abyssus.lib.core.editor.ray.RayViewRuntime
import net.nevinsky.abyssus.lib.raytracing.*

/**
 * The game's side of one ray view (no GL): offers built frames, hands back the newest completed one, and turns a
 * failed or unavailable view into the reason the game states when it falls back to raster.
 */
class RayFieldRuntime(private val view: RayViewRuntime<Unit>) {
    // ray settings are modest: a development aid on a game loop (design decision 11)
    private val settings = RayRenderSettings(targetSamples = 64, maxRaysPerFrame = 8_388_608)
    private var requested = false

    /** Why ray mode stopped, or null while it works; sticky once set. */
    var failure: String? = null
        private set

    /** Offers [frame] at the window's size; the first call also asks for ray mode. */
    fun offer(frame: RayFieldFrame, width: Int, height: Int) {
        if (failure != null) return
        if (!requested) {
            requested = true
            view.setRequested(true)
        }
        if (check()) return
        val scene = frame.scene
        scene.unsupportedReason()?.let { failure = "ray scene not supported ($it)"; return }
        val request = RaySceneRequest(
            frame.key, width, height, frame.camera, scene,
            maxReflectionBounces = 1, maxRefractionBounces = 0, maxRaysPerFrame = settings.maxRaysPerFrame,
        )
        val rays = RayWorkBudget().perCameraSample(scene, 1, 0).toInt().coerceAtLeast(1)
        view.offer(
            RayRenderInput(
                request, RayDisplayKey(width, height, "field"), frame.contentRevision, rays, null, settings, 0,
                frame.environmentRevision,
            ),
            Unit,
        )
    }

    /** The newest completed ray frame, or null while there is none (or after a failure). */
    fun latest(): RayFrame? = if (check()) null else view.latest()?.frame

    /** Reads the view's mode; true when ray mode is over. */
    fun check(): Boolean {
        if (failure != null) return true
        val mode = view.mode
        when (mode.phase) {
            RayModePhase.Failed -> failure = "ray tracing stopped: ${mode.failure ?: "unknown reason"}"
            RayModePhase.Unavailable -> failure = "ray tracing unavailable"
            else -> Unit
        }
        return failure != null
    }

    fun close() = view.close()
}
