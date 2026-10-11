/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudField
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.SUN_OCCLUSION_FLOOR
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.bandDistance
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Texels across the cloud shadow map. */
const val CLOUD_SHADOW_SIZE = 512

/** Metres across the cloud shadow map (the scene's units are taken as metres). */
const val CLOUD_SHADOW_EXTENT = 4000f

private const val GL_R16F = 0x822D
private const val GL_RED = 0x1903
private const val GL_HALF_FLOAT = 0x140B
private const val GL_FRAMEBUFFER_BINDING = 0x8CA6
private const val BANDS = 3

/**
 * A square map of how much sun the clouds let through to the ground around the orbit target: [size] texels across
 * [extent] metres, one `R16F` channel (1 clear, down to the sun-occlusion floor under the thickest clouds). It is
 * the GPU twin of `SunOcclusion.instant`: it uses the same coverage, wind, band spheres, column density and floor as
 * the sky shows, so the shadows are where the clouds are. Centered on the orbit target, snapped to whole texels so
 * patches do not shimmer as it moves. GL thread only, with the context current; never owns the clouds.
 *
 * A map that cannot be made (no OpenGL 3, an incomplete float framebuffer, a shader that does not compile) is
 * logged once to [log]; [texture] stays null and the caller keeps its uniform dimming.
 */
class CloudShadowMap(
    private val shaders: ShaderStorage,
    private val log: Logger = NOPLogger.NOP_LOGGER,
    private val field: CloudField = CloudField(),
    val size: Int = CLOUD_SHADOW_SIZE,
    val extent: Float = CLOUD_SHADOW_EXTENT,
) : Disposable {
    /** The map; null until the first successful [update] and after a failure. */
    var texture: GpuTexture? = null
        private set

    /** True once the map could not be made: nothing is retried until [dispose] and a new instance. */
    var failed = false
        private set

    private var framebuffer = 0
    private var program: ShaderProgram? = null
    private var mesh: Mesh? = null
    private var disposed = false
    private var originX = 0f
    private var originZ = 0f
    private val bandA = FloatArray(BANDS * 4)
    private val bandB = FloatArray(BANDS * 4)
    private val bandC = FloatArray(BANDS * 4)

    /**
     * Redraws the map for [clouds] and the sun toward [sun] at [timeSeconds], centered on ([centerX], [centerZ]).
     * [planetRadius] and [cameraHeight] place the bands as the sky draws them. Returns false when there is no map.
     */
    fun update(
        clouds: CloudMeta, sun: Vector3, centerX: Float, centerZ: Float, planetRadius: Float, cameraHeight: Float,
        timeSeconds: Double,
    ): Boolean {
        check(!disposed)
        if (failed) return false
        val gl = Gdx.gl
        val ints = BufferUtils.newIntBuffer(16)
        val caps = listOf(GL20.GL_BLEND, GL20.GL_DEPTH_TEST, GL20.GL_CULL_FACE, GL20.GL_SCISSOR_TEST)
        var previous = 0
        var viewport = IntArray(4)
        var enabled = emptyList<Boolean>()
        try {
            checkNotNull(Gdx.gl30) { "The cloud shadow map needs OpenGL 3" }
            if (texture == null) allocate()
            snapOrigin(centerX, centerZ)
            fillBands(clouds, sun, planetRadius, cameraHeight, timeSeconds)
            gl.glGetIntegerv(GL_FRAMEBUFFER_BINDING, ints)
            previous = ints.get(0)
            gl.glGetIntegerv(GL20.GL_VIEWPORT, ints)
            viewport = IntArray(4) { ints.get(it) }
            enabled = caps.map(gl::glIsEnabled)
            caps.forEach(gl::glDisable)
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer)
            gl.glViewport(0, 0, size, size)
            val shader = program!!
            shader.bind()
            shader.setUniformf("u_origin", originX, originZ)
            shader.setUniformf("u_extent", extent)
            shader.setUniformf("u_mapSize", size.toFloat())
            shader.setUniformf("u_floor", SUN_OCCLUSION_FLOOR)
            shader.setUniform4fv("u_bandA", bandA, 0, bandA.size)
            shader.setUniform4fv("u_bandB", bandB, 0, bandB.size)
            shader.setUniform4fv("u_bandC", bandC, 0, bandC.size)
            mesh!!.render(shader, GL20.GL_TRIANGLES)
            return true
        } catch (failure: Exception) {
            fail(failure)
            return false
        } finally {
            if (enabled.isNotEmpty()) {
                gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
                gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
                caps.zip(enabled).forEach { (cap, on) -> if (on) gl.glEnable(cap) else gl.glDisable(cap) }
            }
        }
    }

    /**
     * The map's current content for ray tracing: [target] x [target] values (red channel, 0 to 1), row-major from the
     * map's corner, each the mean of a block of texels. GL thread; null when there is no map or the read failed. This
     * stalls the GPU for a moment, so call it only when a new copy is needed.
     */
    fun readback(target: Int): FloatArray? {
        val map = texture ?: return null
        if (failed || target <= 0 || size % target != 0) return null
        val gl = Gdx.gl
        val ints = BufferUtils.newIntBuffer(1)
        gl.glGetIntegerv(GL_FRAMEBUFFER_BINDING, ints)
        val previous = ints.get(0)
        return try {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer)
            val pixels = BufferUtils.newFloatBuffer(map.width * map.height * 4)
            gl.glReadPixels(0, 0, map.width, map.height, GL20.GL_RGBA, GL20.GL_FLOAT, pixels)
            val block = size / target
            FloatArray(target * target) { i ->
                val x0 = (i % target) * block
                val y0 = (i / target) * block
                var sum = 0f
                for (y in y0 until y0 + block) for (x in x0 until x0 + block) sum += pixels.get((y * size + x) * 4)
                (sum / (block * block)).coerceIn(0f, 1f)
            }
        } catch (failure: Exception) {
            log.warn("The cloud shadow map could not be read back for ray tracing: ${failure.message ?: failure}")
            null
        } finally {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
        }
    }

    /**
     * Sets [out] to the transform taking a world position to map coordinates (x and y in 0 to 1 cover the map) for the
     * sun toward [sun]: the position is first slid along the sun ray to the ground, so a tall model is shaded where the
     * clouds above its ground footprint are, as the map is looked up with the receiver's height.
     */
    fun worldToMap(sun: Vector3, out: Matrix4): Matrix4 {
        val sy = if (abs(sun.y) < MIN_SUN_Y) (if (sun.y < 0f) -MIN_SUN_Y else MIN_SUN_Y) else sun.y
        out.idt()
        val v = out.`val`
        v.fill(0f)
        v[Matrix4.M00] = 1f / extent
        v[Matrix4.M01] = -(sun.x / sy) / extent
        v[Matrix4.M03] = -originX / extent
        v[Matrix4.M11] = -(sun.z / sy) / extent
        v[Matrix4.M12] = 1f / extent
        v[Matrix4.M13] = -originZ / extent
        v[Matrix4.M33] = 1f
        return out
    }

    private fun snapOrigin(centerX: Float, centerZ: Float) {
        val texel = extent / size
        originX = floor(centerX / texel + 0.5f) * texel - extent / 2f
        originZ = floor(centerZ / texel + 0.5f) * texel - extent / 2f
    }

    private fun fillBands(clouds: CloudMeta, sun: Vector3, planetRadius: Float, cameraHeight: Float, timeSeconds: Double) {
        bandC.fill(0f)
        val bands = listOf(clouds.low, clouds.mid, clouds.high)
        for ((i, band) in bands.withIndex()) {
            if (band == null) continue
            val type = band.type
            val offset = field.windOffset(band, timeSeconds)
            bandA[i * 4] = type.scale * type.stretch; bandA[i * 4 + 1] = type.scale
            bandA[i * 4 + 2] = offset[0]; bandA[i * 4 + 3] = offset[1]
            bandB[i * 4] = type.ordinal.toFloat(); bandB[i * 4 + 1] = type.octaves.toFloat()
            bandB[i * 4 + 2] = type.profile.ordinal.toFloat(); bandB[i * 4 + 3] = band.coverage
            val t = bandDistance(sun, (band.base + band.top) / 2f, planetRadius, cameraHeight) ?: continue
            bandC[i * 4] = band.density
            bandC[i * 4 + 1] = band.thickness / max(abs(sun.y), MIN_SUN_Y)
            bandC[i * 4 + 2] = sun.x * t; bandC[i * 4 + 3] = sun.z * t
        }
    }

    private fun allocate() {
        val gl = Gdx.gl
        val map = GpuTexture(GL20.GL_TEXTURE_2D, size, size)
        texture = map
        map.bind()
        gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL_R16F, size, size, 0, GL_RED, GL_HALF_FLOAT, null)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE)
        gl.glBindTexture(GL20.GL_TEXTURE_2D, 0)
        framebuffer = gl.glGenFramebuffer()
        val ints = BufferUtils.newIntBuffer(1)
        gl.glGetIntegerv(GL_FRAMEBUFFER_BINDING, ints)
        val previous = ints.get(0)
        try {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer)
            gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, map.textureObjectHandle, 0)
            val status = gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER)
            check(status == GL20.GL_FRAMEBUFFER_COMPLETE) { "The cloud shadow framebuffer is incomplete (status 0x${status.toString(16)})" }
        } finally {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
        }
        program = shaders.program("cloud_shadow.vert", "clouds_common.glsl", "cloud_shadow.frag")
        mesh = createFullscreenTriangle()
    }

    private fun fail(failure: Exception) {
        failed = true
        log.warn("Cloud shadows are unavailable; the sun is dimmed uniformly instead: ${failure.message ?: failure}")
        release()
    }

    /** Forgets everything without GL calls: the context the resources were made in is gone. */
    fun abandon() {
        texture = null
        program = null
        mesh = null
        framebuffer = 0
        failed = false
    }

    private fun release() {
        texture?.dispose()
        texture = null
        program?.dispose()
        program = null
        mesh?.dispose()
        mesh = null
        if (framebuffer != 0) Gdx.gl.glDeleteFramebuffer(framebuffer)
        framebuffer = 0
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        release()
    }

    private companion object {
        /** The sun's height above which the path through a band is not stretched further (as in SunOcclusion). */
        const val MIN_SUN_Y = 0.1f
    }
}
