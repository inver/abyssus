/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.CubeSource
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironmentBuild
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

/**
 * The light a procedural sky with `lightsScene` gives the scene, one per view. A [LightingRefreshPolicy] decides when to
 * rebuild (the sun moved, drifting clouds, a cloud reload or visibility change); each rebuild renders a
 * [ProceduralLightingCube] and turns it into an [HdrEnvironment], one step per [update]. It holds the [current]
 * environment, the [next] one while it fades in ([fade] 0 to 1) and at most one build. A failed build keeps [current]
 * (none yet: the scene stays on its ambient color) and is retried by the policy. GL thread only, with the context
 * current; it never owns the clouds it samples.
 */
class ProceduralSkyLighting(
    private val params: AtmosphereParams,
    private val shaders: ShaderStorage,
    private val skyName: String,
    private val log: Logger = NOPLogger.NOP_LOGGER,
    private val policy: LightingRefreshPolicy = LightingRefreshPolicy(),
) : Disposable {
    private class Build(val request: LightingBuildRequest, val cube: ProceduralLightingCube) : Disposable {
        var environment: HdrEnvironmentBuild? = null

        override fun dispose() {
            environment?.dispose()
            cube.dispose()
        }
    }

    private val results = HashMap<Long, HdrEnvironment>()
    private var active: Build? = null
    private var lastClouds: Clouds? = null
    private var cloudRevision = 0L
    private val sun = Vector3()

    /** The completed environment lighting the scene; null until the first build is done. */
    var current: HdrEnvironment? = null
        private set

    /** The environment fading in over [current]; null when none is. */
    var next: HdrEnvironment? = null
        private set

    /** How far [next] has replaced [current], 0 to 1. */
    var fade = 0f
        private set

    /** Counts the changes of [current]: 0 until the first environment is complete, then one more for every replacement. */
    var revision = 0L
        private set

    /**
     * Moves the light one frame: asks the policy what is needed for [sunDirection], [clouds] at [timeSeconds], does one
     * build step if a build is active and the view is [visible], and fades [next] in by [deltaSeconds].
     */
    fun update(sunDirection: Vector3, clouds: Clouds?, timeSeconds: Double, visible: Boolean, deltaSeconds: Double) {
        if (clouds !== lastClouds) {
            lastClouds = clouds
            cloudRevision++
        }
        val settings = clouds?.settings
        val bands = settings?.bandsFarToNear().orEmpty()
        val request = LightingRefreshRequest(
            LightingSunDirection(sunDirection.x.toDouble(), sunDirection.y.toDouble(), sunDirection.z.toDouble()),
            LightingRevision(skyName, 0, clouds?.name, cloudRevision, settings?.visible == true),
            timeSeconds,
            bands.any { it.windX != 0f || it.windZ != 0f },
        )
        val state = policy.update(request, visible, deltaSeconds)
        val build = state.build
        if (active != null && active?.request?.id != build?.id) release()
        if (build != null && state.canStepBuild) step(build, clouds, request)
        publish()
    }

    private fun step(request: LightingBuildRequest, clouds: Clouds?, frame: LightingRefreshRequest) {
        var build = active
        try {
            if (build == null) {
                build = Build(request, ProceduralLightingCube(params, shaders)).also { active = it }
                val direction = request.request.sun
                sun.set(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
                build.cube.render(sun, clouds, frame.cloudTimeSeconds)
                build.environment = HdrEnvironmentBuild(
                    CubeSource(build.cube.texture, PROCEDURAL_LIGHTING_LEVELS), shaders
                )
                return
            }
            if (!checkNotNull(build.environment).step()) return
            val environment = build.environment!!.finish()
            release()
            if (policy.completed(request)) results[request.id] = environment else environment.dispose()
        } catch (failure: Exception) {
            release()
            policy.failed(request)
            log.warn("Sky '$skyName': lighting build failed; ${if (current == null) "keeping the ambient light" else "keeping the last lighting"}", failure)
        }
    }

    private fun release() {
        active?.dispose()
        active = null
    }

    /** Mirrors the policy into [current] / [next] and disposes every result the policy no longer holds. */
    private fun publish() {
        val state = policy.state
        val keep = setOfNotNull(state.current?.id, state.next?.id)
        results.keys.filter { it !in keep }.forEach { results.remove(it)?.dispose() }
        val published = state.current?.let { results[it.id] }
        if (published !== current && published != null) revision++
        current = published
        next = state.next?.let { results[it.id] }
        fade = if (next != null) state.fadeWeight.toFloat() else 0f
    }

    /** Forgets everything without GL calls: the context the resources were made in is gone. */
    fun abandon() {
        policy.resetContext()
        results.clear()
        active = null
        if (current != null) revision++
        current = null
        next = null
        fade = 0f
        lastClouds = null
    }

    override fun dispose() {
        release()
        results.values.forEach(Disposable::dispose)
        results.clear()
        current = null
        next = null
    }
}
