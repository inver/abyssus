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
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

/**
 * One version of a procedural sky's environment: [cube] holds the sky and its clouds as linear radiance (RGBA16F, 64² a
 * face, mips filled), for features that need to see the sky from a surface: water reflections beyond their budget, ray
 * tracing misses. It belongs to the [ProceduralSkyEnvironment] that made it and stays valid until that object next
 * refreshes or is disposed; [revision] grows with every new version, so a consumer knows when to refresh what it took from it.
 */
class SkyEnvironment(val cube: GpuTexture, val revision: Long)

/**
 * The cloudy-sky environment of a procedural sky, built only while a consumer asks for it (whether or not the sky has
 * `lightsScene`): [update] draws the cube for the sun toward `sun` and the clouds at the sky's time, and again when the
 * [LightingRefreshPolicy] says it is stale (a sun move over a degree, two seconds of drifting clouds, a different or
 * removed cloud asset). A rebuild is one synchronous pass over the six faces, so there is no fade and no half-built version;
 * a failure keeps the last cube, or none, and is logged once. GL thread only, inside `GdxRuntime.withContext`; a lost
 * context ends it without GL calls (the view abandons the whole sky).
 */
class ProceduralSkyEnvironment(
    private val params: AtmosphereParams,
    private val shaders: ShaderStorage,
    private val clouds: () -> Clouds?,
    private val log: Logger = NOPLogger.NOP_LOGGER,
    private val field: CloudField = CloudField(),
    private val policy: LightingRefreshPolicy = LightingRefreshPolicy(fadeSeconds = 0.0),
) : Disposable {
    private var cube: ProceduralLightingCube? = null
    private val cloudRevision = CloudRevision()
    private var revision = 0L
    private var failureLogged = false

    /** The latest version; null until [update] first succeeds. */
    var current: SkyEnvironment? = null
        private set

    /** Brings the environment up to date for the sun toward [sun] at sky time [timeSeconds] and returns it. Never throws. */
    fun update(sun: Vector3, timeSeconds: Double, visible: Boolean = true): SkyEnvironment? {
        val weather = clouds()
        val shown = weather?.settings?.takeIf { it.visible }
        val step = policy.update(
            LightingFrame(sun, timeSeconds, cloudRevision.of(weather), shown != null, shown?.drifts == true, visible)
        )
        val ticket = step.build ?: return current
        try {
            val target = cube ?: ProceduralLightingCube(params, shaders, field).also { cube = it }
            target.render(sun, timeSeconds, weather)
            if (policy.completed(ticket) != Completion.DISCARD) {
                revision++
                current = SkyEnvironment(target.texture, revision)
            }
        } catch (e: Exception) {
            policy.failed(ticket)
            if (!failureLogged) {
                failureLogged = true
                log.warn("Procedural sky environment failed; keeping ${if (current == null) "none" else "the last one"}", e)
            }
        }
        return current
    }

    override fun dispose() {
        cube?.dispose()
        cube = null
        current = null
        policy.reset()
    }
}
