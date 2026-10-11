/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudField
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironmentBuild
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.SkyLighting
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.SkyLightingState
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

/**
 * The lighting a procedural sky with `lightsScene` gives its scene: a linear cube of the sky and its clouds
 * ([ProceduralLightingCube]) turned into specular and irradiance cubes by an [HdrEnvironmentBuild], one step per frame,
 * and rebuilt as the sun and the clouds change ([LightingRefreshPolicy]). A new version fades in over the old.
 *
 * Owns at most the current and the next lighting, the cube, and one build in progress. [clouds] is read on every
 * [update]; a different object than the last time is a reloaded (or replaced, or removed) cloud asset, which makes the
 * lighting stale. [state] is null until the first build completes (the scene keeps its ambient color meanwhile), and a
 * later failed build leaves the last lighting in place. GL thread only, inside `GdxRuntime.withContext`; a lost
 * context ends it without GL calls (the view abandons the whole sky).
 */
class ProceduralSkyLighting(
    private val params: AtmosphereParams,
    private val shaders: ShaderStorage,
    private val clouds: () -> Clouds?,
    private val log: Logger = NOPLogger.NOP_LOGGER,
    private val field: CloudField = CloudField(),
    private val policy: LightingRefreshPolicy = LightingRefreshPolicy(),
) : Disposable {
    private var cube: ProceduralLightingCube? = null
    private var build: HdrEnvironmentBuild? = null
    private var ticket = 0
    private var current: SkyLighting? = null
    private var next: SkyLighting? = null
    private var weight = 0f
    private val cloudRevision = CloudRevision()
    private var failureLogged = false

    /** What to light with now; null while the first lighting builds or when it failed. */
    var state: SkyLightingState? = null
        private set

    /** True while a build is in progress (for tests and diagnostics). */
    val isBuilding: Boolean get() = build != null

    /** Moves the lighting on one frame for the sun toward [sun] at sky time [timeSeconds]. Never throws. */
    fun update(sun: Vector3, timeSeconds: Double, visible: Boolean = true) {
        val weather = clouds()
        val revision = cloudRevision.of(weather)
        val shown = weather?.settings?.takeIf { it.visible }
        val step = policy.update(
            LightingFrame(sun, timeSeconds, revision, shown != null, shown?.drifts == true, visible)
        )
        if (step.abandon) releaseBuild()
        if (step.promoted) promote()
        step.build?.let { start(it, sun, timeSeconds, weather) } ?: advance()
        weight = step.weight
        publish()
    }

    private fun start(newTicket: Int, sun: Vector3, timeSeconds: Double, weather: Clouds?) {
        ticket = newTicket
        try {
            val target = cube ?: ProceduralLightingCube(params, shaders, field).also { cube = it }
            target.render(sun, timeSeconds, weather)
            build = HdrEnvironmentBuild(target.source(), shaders)
        } catch (e: Exception) {
            fail(newTicket, e)
        }
    }

    private fun advance() {
        val running = build ?: return
        try {
            if (!running.step()) return
            val lighting = running.finishLighting()
            build = null
            when (policy.completed(ticket)) {
                Completion.DISCARD -> lighting.dispose()
                Completion.PUBLISH -> {
                    current?.dispose()
                    current = lighting
                }

                Completion.FADE -> {
                    next?.dispose()
                    next = lighting
                }
            }
        } catch (e: Exception) {
            fail(ticket, e)
        }
    }

    private fun fail(failedTicket: Int, e: Exception) {
        releaseBuild()
        policy.failed(failedTicket)
        if (!failureLogged) {
            failureLogged = true
            log.warn("Procedural sky lighting failed; keeping ${if (current == null) "the ambient color" else "the last lighting"}", e)
        }
    }

    private fun promote() {
        val faded = next ?: return
        current?.dispose()
        current = faded
        next = null
    }

    private fun releaseBuild() {
        build?.dispose()
        build = null
    }

    private fun publish() {
        val now = current
        state = when {
            now == null -> null
            state?.current === now && state?.next === next && state?.weight == weight -> state
            else -> SkyLightingState(now, next, if (next == null) 0f else weight)
        }
    }

    override fun dispose() {
        releaseBuild()
        next?.dispose()
        current?.dispose()
        cube?.dispose()
        next = null
        current = null
        cube = null
        state = null
        policy.reset()
    }
}
