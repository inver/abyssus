/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudField
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudScene
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechniques
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.LayeredClouds
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.ShellClouds
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.VolumetricClouds
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

/** How far above the ground, in metres, a procedural sky (and its clouds) is seen from. */
const val SKY_CAMERA_HEIGHT = 100f

/** The altitude, in metres, whose sunlight and sky light every cloud band is lit with. */
private const val CLOUD_LIGHT_ALTITUDE = 1500f

/**
 * A sky computed per pixel by the asset's own GLSL: one fullscreen triangle, no cube. Built on the GL thread; throws
 * when the shaders do not compile, which the asset cache logs once and remembers.
 *
 * Its clouds, the `CLOUDS` asset it names, are read from [assets] on every draw (a reloaded cloud asset is picked up
 * without rebuilding the sky, which never owns or disposes it) and drawn over the atmosphere by a plugin-owned pass (the
 * asset's shader never sees them) with the technique the frame asks for, or the cloud asset's own. Each technique's
 * renderer is built from [shaders] the first time it is needed ([cloudFactory] replaces that, for tests); one that
 * cannot be built is logged to [log] once and the next simpler one draws instead.
 */
class ProceduralSky(
    prepared: PreparedProceduralSky,
    private val assets: BuiltAssets = BuiltAssets { null },
    shaders: ShaderStorage = ShaderStorage(),
    log: Logger = NOPLogger.NOP_LOGGER,
    cloudFactory: ((CloudTechnique) -> CloudRenderer)? = null,
) : SkyRenderer {
    /** The atmosphere the asset's shader is given; clouds sit on its planet. */
    val params: AtmosphereParams = prepared.params

    private val cloudsName: String? = prepared.clouds

    /** The built cloud asset this sky draws; null without one, while it loads or when it failed. */
    val cloudAsset: Clouds? get() = cloudsName?.let { assets.get(it) as? Clouds }

    /** The weather this sky draws now, or null without a built cloud asset. */
    val clouds: CloudMeta? get() = cloudAsset?.settings

    private val program = ShaderProgram(prepared.vertex, prepared.fragment).also {
        if (!it.isCompiled) {
            val log = it.log
            it.dispose()
            throw IllegalStateException("Procedural sky shader failed to compile: $log")
        }
    }
    private val mesh = createFullscreenTriangle()
    private val invViewProj = Matrix4()
    private val field = CloudField()
    private val ambient = SkyAmbientEstimate(params)
    private val techniques = CloudTechniques(prepared.name, log, cloudFactory ?: { technique ->
        when (technique) {
            CloudTechnique.LAYERED -> LayeredClouds(shaders, field)
            CloudTechnique.SHELLS -> ShellClouds(shaders, field)
            CloudTechnique.VOLUMETRIC -> VolumetricClouds(shaders, field)
        }
    })

    private val lightsScene = prepared.lightsScene
    private val lightingName = prepared.name
    private val lightingShaders = shaders
    private val lightingLog = log
    private var leases = 0

    /**
     * The sky light of this sky: built and refreshed while the asset opts in with `lightsScene` or a consumer holds an
     * [EnvironmentLease]; null otherwise, so nothing is built for nobody. Owned by this sky and released with it.
     */
    var lighting: ProceduralSkyLighting? =
        if (lightsScene) ProceduralSkyLighting(params, shaders, prepared.name, log) else null
        private set

    /** The completed lighting environment to light the scene with; null without opt-in or until the first build is done. */
    val lightingEnvironment: HdrEnvironment? get() = if (lightsScene) lighting?.current else null

    /**
     * A consumer's claim on this sky's cloudy environment, the cube lighting the sky gives, independent of `lightsScene`
     * and without replacing the scene's ambient light. While any lease is open the sky builds it and keeps it up to date
     * with the sun, the clouds and their wind; when the last closes (and the asset does not opt in) it is released.
     * [environment] and [revision] are read on the GL thread: the environment is borrowed from the sky, valid until the
     * next frame's update, and a changed [revision] says a new one replaced it (so a consumer can drop what it derived
     * from the old one). Close the lease before the sky is disposed; the sky never hands out a disposed environment.
     */
    inner class EnvironmentLease internal constructor() : AutoCloseable {
        private var closed = false

        /** The completed environment, or null while the first build is under way, after close or after the sky is released. */
        val environment: HdrEnvironment? get() = if (closed) null else lighting?.current

        /** Counts the replacements of [environment]; 0 until the first one is complete. */
        val revision: Long get() = if (closed) 0L else lighting?.revision ?: 0L

        override fun close() {
            if (closed) return
            closed = true
            leases--
            if (leases == 0 && !lightsScene) {
                lighting?.dispose()
                lighting = null
            }
        }
    }

    /** Starts building the cloudy environment for a consumer (see [EnvironmentLease]); GL thread. */
    fun acquireEnvironment(): EnvironmentLease {
        leases++
        if (lighting == null) lighting = ProceduralSkyLighting(params, lightingShaders, lightingName, lightingLog)
        return EnvironmentLease()
    }

    /**
     * Advances the sky light one frame (a no-op while nobody needs it): rebuilds it when the sun or clouds changed, one
     * build step per call, while [visible]. GL thread only.
     */
    fun updateLighting(sun: Vector3, timeSeconds: Double, visible: Boolean, deltaSeconds: Double) {
        lighting?.update(sun, cloudAsset, timeSeconds, visible, deltaSeconds)
    }

    /** The cloud technique the last [draw] used; null when it drew no clouds. */
    var drawnTechnique: CloudTechnique? = null
        private set

    /** True when this sky has clouds to draw that some technique can still draw. */
    val hasClouds: Boolean get() = clouds?.visible == true && !techniques.exhausted

    /**
     * Draws the atmosphere seen from [camera]'s orientation with the sun toward [frame]'s sun, then its clouds (unless
     * the frame leaves them out). The caller sets depth and cull state.
     */
    override fun draw(camera: Camera, frame: SkyFrame) {
        val sun = frame.sun
        rotationOnlyViewProj(camera, invViewProj).inv()
        program.bind()
        program.setUniformMatrix("u_invViewProj", invViewProj)
        program.setUniformf("u_sunDir", sun.x, sun.y, sun.z)
        program.setUniformf("u_cameraHeight", SKY_CAMERA_HEIGHT)
        program.setUniformf("u_planetRadius", params.planetRadius)
        program.setUniformf("u_atmosphereRadius", params.atmosphereRadius)
        program.setUniformf("u_betaRayleigh", params.betaRayleigh[0], params.betaRayleigh[1], params.betaRayleigh[2])
        program.setUniformf("u_betaMie", params.betaMie)
        program.setUniformf("u_heightRayleigh", params.heightRayleigh)
        program.setUniformf("u_heightMie", params.heightMie)
        program.setUniformf("u_mieG", params.mieG)
        program.setUniformf("u_sunIntensity", params.sunIntensity)
        mesh.render(program, GL20.GL_TRIANGLES)
        drawClouds(camera, frame)
    }

    private fun drawClouds(camera: Camera, frame: SkyFrame) {
        drawnTechnique = null
        val clouds = cloudAsset?.takeIf { frame.clouds && it.settings.visible } ?: return
        val (technique, renderer) = techniques.renderer(frame.technique ?: clouds.settings.technique) ?: return
        val scene = CloudScene(
            camera, invViewProj, frame.sun, frame.timeSeconds, clouds, params,
            ambient.ambient(frame.sun, CLOUD_LIGHT_ALTITUDE),
        )
        try {
            renderer.draw(scene)
            drawnTechnique = technique
        } catch (e: Exception) {
            techniques.fail(technique, e)
        }
    }

    override fun dispose() {
        lighting?.dispose()
        lighting = null
        techniques.dispose()
        mesh.dispose()
        program.dispose()
    }
}
