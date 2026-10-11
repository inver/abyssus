/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CLOUD_EXTINCTION
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudField
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.SUN_OCCLUSION_FLOOR
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.bandDistance
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Texels across the cloud shadow map. */
const val CLOUD_SHADOW_SIZE = 512

/** Metres across the map: the scene's units are taken as metres. */
const val CLOUD_SHADOW_EXTENT = 4000f

/** The share of the map's width, at each edge, over which it fades into the uniform sun dimming. */
const val CLOUD_SHADOW_EDGE_FADE = 0.1f

/** The lowest sun height the map's projection follows, so a sun at the horizon does not shear it without end. */
private const val MIN_SUN_HEIGHT = 0.1f

private const val GL_R16F = 0x822D
private const val GL_RED = 0x1903
private const val GL_HALF_FLOAT = 0x140B
private const val MAX_BANDS = 3

/**
 * The shadow of the clouds on the ground: a [CLOUD_SHADOW_SIZE]² `R16F` map of a [CLOUD_SHADOW_EXTENT]-metre square
 * around the orbit target, each texel the share of the sun's light the clouds let through the ray that reaches it. It
 * evaluates exactly the model of `SunOcclusion.instant` (the same coverage field, wind, band intersection with the
 * planet's sphere, column density, path length and floor, from `clouds_common.glsl`), so it agrees with the sky and with
 * the uniform dimming it replaces; unlike it, nothing smooths it over time, because the map itself moves slowly.
 *
 * Fragments are looked up through [worldToMap]: a world position slides along the sun direction to the ground, and
 * that point's place in the square is its texture coordinate. The square follows the target in whole texels, so the
 * pattern does not shimmer as the camera orbits. GL thread only, inside `GdxRuntime.withContext` and only while the canvas
 * is safely on screen; [update] restores the framebuffer and state it finds. If the target or shader cannot be built,
 * [update] returns false and logs the reason once, and the caller keeps the uniform dimming.
 */
class CloudShadowMap(
    private val shaders: ShaderStorage,
    private val log: Logger = NOPLogger.NOP_LOGGER,
    private val field: CloudField = CloudField(),
    private val createTarget: () -> FrameBuffer = ::defaultTarget,
) : Disposable {
    private var target: FrameBuffer? = null
    private var program: ShaderProgram? = null
    private var mesh: Mesh? = null
    private var failed = false

    /** The map, once [update] has drawn it; null before that and after a failure. */
    val texture: GLTexture? get() = target?.colorBufferTexture

    /** From world positions to the map's texture coordinates (x and y) for the last [update]. */
    val worldToMap: Matrix4 = Matrix4()

    /** The world x and z of the map's middle in the last [update]. */
    val center: FloatArray = FloatArray(2)

    /** True once the map could not be built; it is not tried again. */
    val unavailable: Boolean get() = failed

    /**
     * Draws the shadow of [clouds] for the sun toward [sun] (a unit vector) around ([targetX], [targetZ]) at [timeSeconds];
     * [planetRadius] and [cameraHeight] place the bands as the sky draws them. Returns false (and leaves [texture] null)
     * when the map cannot be made.
     */
    fun update(
        clouds: CloudMeta, sun: Vector3, targetX: Float, targetZ: Float, planetRadius: Float, cameraHeight: Float,
        timeSeconds: Double,
    ): Boolean {
        if (failed) return false
        val state = ShadowGlState()
        try {
            val program = program ?: shaders.program("cloud_shadow.vert", "clouds_common.glsl", "cloud_shadow.frag")
                .also { program = it }
            val buffer = target ?: createTarget().also { target = it }
            val quad = mesh ?: createFullscreenTriangle().also { mesh = it }
            val texel = CLOUD_SHADOW_EXTENT / CLOUD_SHADOW_SIZE
            center[0] = floor(targetX / texel) * texel
            center[1] = floor(targetZ / texel) * texel
            setWorldToMap(sun)

            buffer.begin()
            val gl = Gdx.gl
            gl.glViewport(0, 0, CLOUD_SHADOW_SIZE, CLOUD_SHADOW_SIZE)
            listOf(GL20.GL_BLEND, GL20.GL_DEPTH_TEST, GL20.GL_CULL_FACE, GL20.GL_SCISSOR_TEST).forEach(gl::glDisable)
            program.bind()
            program.setUniformf("u_center", center[0], center[1])
            program.setUniformf("u_extent", CLOUD_SHADOW_EXTENT)
            program.setUniformf("u_floor", SUN_OCCLUSION_FLOOR)
            bindBands(program, clouds, sun, planetRadius, cameraHeight, timeSeconds)
            quad.render(program, GL20.GL_TRIANGLES)
            buffer.end()
            return true
        } catch (e: Exception) {
            fail(e)
            return false
        } finally {
            state.restore()
        }
    }

    /**
     * The map as [CLOUD_SHADOW_SIZE]² visibility bytes (0 dark to 255 lit, row 0 first as drawn), read back from the last
     * [update]; null when there is no map. A full-size read stalls the pipeline, so ask only when the map is captured for another
     * consumer (ray tracing), not every frame.
     */
    fun readVisibility(): ByteArray? {
        val buffer = target ?: return null
        val state = ShadowGlState()
        return try {
            buffer.begin()
            val pixels = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer(CLOUD_SHADOW_SIZE * CLOUD_SHADOW_SIZE)
            Gdx.gl.glReadPixels(0, 0, CLOUD_SHADOW_SIZE, CLOUD_SHADOW_SIZE, GL_RED, GL20.GL_FLOAT, pixels)
            buffer.end()
            ByteArray(CLOUD_SHADOW_SIZE * CLOUD_SHADOW_SIZE) { (pixels.get(it).coerceIn(0f, 1f) * 255f + .5f).toInt().toByte() }
        } catch (e: Exception) {
            log.warn("Cloud shadow map could not be read back; ray tracing keeps the uniform cloud dimming", e)
            null
        } finally {
            state.restore()
        }
    }

    private fun bindBands(
        program: ShaderProgram, clouds: CloudMeta, sun: Vector3, planetRadius: Float, cameraHeight: Float, timeSeconds: Double,
    ) {
        val bands = listOfNotNull(clouds.low, clouds.mid, clouds.high).take(MAX_BANDS)
        program.setUniformi("u_bandCount", bands.size)
        bands.forEachIndexed { i, band ->
            val type = band.type
            val t = bandDistance(sun, (band.base + band.top) / 2f, planetRadius, cameraHeight)
            val offset = field.windOffset(band, timeSeconds)
            // a band the sun ray never reaches casts nothing: a zero coefficient
            val path = band.thickness / max(abs(sun.y), 0.1f)
            program.setUniformf("u_bandScale[$i]", type.scale * type.stretch, type.scale)
            program.setUniformf("u_bandOffset[$i]", offset[0], offset[1])
            program.setUniformf("u_bandShift[$i]", if (t == null) 0f else sun.x * t, if (t == null) 0f else sun.z * t)
            program.setUniformi("u_bandSeed[$i]", type.ordinal)
            program.setUniformi("u_bandOctaves[$i]", type.octaves)
            program.setUniformi("u_bandProfile[$i]", type.profile.ordinal)
            program.setUniformf("u_bandCoverage[$i]", band.coverage)
            program.setUniformf("u_bandCoeff[$i]", if (t == null) 0f else CLOUD_EXTINCTION * band.density * path)
        }
    }

    private fun setWorldToMap(sun: Vector3) {
        val height = max(sun.y, MIN_SUN_HEIGHT)
        val e = CLOUD_SHADOW_EXTENT
        val m = worldToMap.`val`
        m.fill(0f)
        m[Matrix4.M00] = 1f / e
        m[Matrix4.M01] = -(sun.x / height) / e
        m[Matrix4.M03] = 0.5f - center[0] / e
        m[Matrix4.M11] = -(sun.z / height) / e
        m[Matrix4.M12] = 1f / e
        m[Matrix4.M13] = 0.5f - center[1] / e
        m[Matrix4.M22] = 1f
        m[Matrix4.M33] = 1f
    }

    private fun fail(e: Exception) {
        if (!failed) log.warn("Cloud shadow map unavailable; the sun keeps its uniform cloud dimming", e)
        failed = true
        release()
    }

    private fun release() {
        runCatchingKeepingCancellation {
            target?.dispose()
            mesh?.dispose()
            program?.dispose()
        }
        target = null
        mesh = null
        program = null
    }

    /** Forgets the GL objects without calling GL: the context they were made in is gone. */
    fun abandon() {
        target = null
        mesh = null
        program = null
    }

    override fun dispose() = release()
}

private fun defaultTarget(): FrameBuffer {
    checkNotNull(Gdx.gl30) { "Cloud shadows need OpenGL 3" }
    val builder = com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferBuilder(CLOUD_SHADOW_SIZE, CLOUD_SHADOW_SIZE)
    builder.addColorTextureAttachment(GL_R16F, GL_RED, GL_HALF_FLOAT)
    val buffer = builder.build()
    buffer.colorBufferTexture.apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
    }
    return buffer
}
