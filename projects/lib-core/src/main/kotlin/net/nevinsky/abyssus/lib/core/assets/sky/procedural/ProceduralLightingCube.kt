/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudField
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudScene
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.LinearShellClouds
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.CubeSource
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.CubeTargets
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle

/** Face size of the lighting cube. */
const val LIGHTING_CUBE_SIZE = 64

/** Mip levels of the lighting cube (64 down to 1), which the environment build samples to prefilter. */
const val LIGHTING_CUBE_LEVELS = 7

/** Forward, up and (as a check) right of each cube face, matching `cubeDir` in `hdr_common.glsl`. */
private val FACE_AXES = arrayOf(
    floatArrayOf(1f, 0f, 0f, 0f, -1f, 0f),
    floatArrayOf(-1f, 0f, 0f, 0f, -1f, 0f),
    floatArrayOf(0f, 1f, 0f, 0f, 0f, 1f),
    floatArrayOf(0f, -1f, 0f, 0f, 0f, -1f),
    floatArrayOf(0f, 0f, 1f, 0f, -1f, 0f),
    floatArrayOf(0f, 0f, -1f, 0f, -1f, 0f),
)

/**
 * The linear lighting of a procedural sky: its clear atmosphere (`atmosphere_linear.frag`, the same single scattering as
 * the fixture shader and [SkyAmbientEstimate]) with the shell clouds of a `CLOUDS` asset over it, as RGBA16F linear
 * radiance in a [LIGHTING_CUBE_SIZE]² cube, with no tone mapping and no sun disc. Shells draw it whatever technique the
 * view uses: they agree with the others on coverage, and six faces of volumetric clouds would cost too much.
 *
 * The lighting does not come from the asset's own `sky.frag`, whose output is display-ready, so a shader that departs
 * from the atmosphere parameters looks different from the light it gives. GL thread only, inside
 * `GdxRuntime.withContext`. [texture] is owned by this object; an environment build only borrows it ([source]).
 */
class ProceduralLightingCube(
    private val params: AtmosphereParams,
    private val shaders: ShaderStorage,
    private val field: CloudField = CloudField(),
) : Disposable {
    private val targets = CubeTargets()
    private val estimate = SkyAmbientEstimate(params)
    private val camera = PerspectiveCamera(90f, LIGHTING_CUBE_SIZE.toFloat(), LIGHTING_CUBE_SIZE.toFloat())
    private val forward = Vector3()
    private val up = Vector3()
    private var shellClouds: LinearShellClouds? = null

    /** The cube; its mip levels are filled after every [render]. */
    val texture: GpuTexture = targets.cube(LIGHTING_CUBE_SIZE, LIGHTING_CUBE_LEVELS, alpha = true)

    private val atmosphere = try {
        shaders.program("hdr_cube.vert", "hdr_common.glsl", "atmosphere_linear.frag")
    } catch (e: Exception) {
        texture.dispose()
        targets.dispose()
        throw e
    }
    private val mesh = createFullscreenTriangle()

    /** [texture] as the source of an environment build; the cube stays owned by this object. */
    fun source() = CubeSource(texture, LIGHTING_CUBE_SIZE, LIGHTING_CUBE_LEVELS)

    /**
     * Redraws the cube for the sun toward [sun] (a unit vector) and the weather [clouds] has at [timeSeconds] (null or
     * not visible: the clear sky). Throws when the cloud shaders cannot be built or the GPU cannot render to the cube; the
     * cube then holds an unspecified image, and the caller does not publish it.
     */
    fun render(sun: Vector3, timeSeconds: Double, clouds: Clouds?) {
        val drawClouds = clouds?.takeIf { it.settings.visible }
        val shells = drawClouds?.let { shellClouds ?: LinearShellClouds(shaders, field).also { s -> shellClouds = s } }
        targets.withFramebuffer {
            Gdx.gl.glViewport(0, 0, LIGHTING_CUBE_SIZE, LIGHTING_CUBE_SIZE)
            atmosphere.bind()
            atmosphere.setUniformf("u_sunDir", sun.x, sun.y, sun.z)
            atmosphere.setUniformf("u_cameraHeight", SKY_CAMERA_HEIGHT)
            atmosphere.setUniformf("u_planetRadius", params.planetRadius)
            atmosphere.setUniformf("u_atmosphereRadius", params.atmosphereRadius)
            atmosphere.setUniformf("u_betaRayleigh", params.betaRayleigh[0], params.betaRayleigh[1], params.betaRayleigh[2])
            atmosphere.setUniformf("u_betaMie", params.betaMie)
            atmosphere.setUniformf("u_heightRayleigh", params.heightRayleigh)
            atmosphere.setUniformf("u_heightMie", params.heightMie)
            atmosphere.setUniformf("u_mieG", params.mieG)
            atmosphere.setUniformf("u_sunIntensity", params.sunIntensity)
            for (face in 0 until 6) {
                targets.attach(texture, face, 0)
                atmosphere.setUniformi("u_face", face)
                mesh.render(atmosphere, GL20.GL_TRIANGLES)
            }
            if (shells != null && drawClouds != null) {
                val ambient = estimate.ambient(sun, CLOUD_LIGHT_ALTITUDE)
                for (face in 0 until 6) {
                    targets.attach(texture, face, 0)
                    faceCamera(face)
                    shells.draw(CloudScene(camera, camera.invProjectionView, sun, timeSeconds, drawClouds, params, ambient))
                }
            }
        }
        targets.generateMipmaps(texture)
    }

    private fun faceCamera(face: Int) {
        val axes = FACE_AXES[face]
        forward.set(axes[0], axes[1], axes[2])
        up.set(axes[3], axes[4], axes[5])
        camera.position.setZero()
        camera.direction.set(forward)
        camera.up.set(up)
        camera.update()
    }

    override fun dispose() {
        shellClouds?.dispose()
        shellClouds = null
        mesh.dispose()
        atmosphere.dispose()
        texture.dispose()
        targets.dispose()
    }
}
