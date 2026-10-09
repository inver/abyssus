/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.sky.clouds

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.gdx.util.GeometryUtils.Companion.createFullscreenTriangle
import kotlin.math.abs

private const val GL_RGBA16F = 0x881A
private const val GL_HALF_FLOAT = 0x140B
private const val GL_FRAMEBUFFER_BINDING = 0x8CA6

/** How much of the resolved image comes from this frame; the rest is the reprojected history. */
private const val HISTORY_BLEND = 0.15f

/** A camera turning more than this many degrees between two frames starts the history again. */
private const val HISTORY_RESET_DEGREES = 10f

/**
 * Ray-marched clouds: each band marched in 3D at half the view's resolution into an `RGBA16F` target, accumulated over
 * frames with reprojection, then upsampled over the atmosphere. The 3D noise textures belong to the cloud asset
 * ([Clouds]); building throws when OpenGL 3 is not available, and drawing throws when the asset has no noise textures
 * or a float render target cannot be made, so the sky falls back to shells. Holds per-view GL objects: one instance per
 * sky, and skies are per view.
 */
class VolumetricClouds(shaders: ShaderStorage, private val field: CloudField) : CloudRenderer() {
    init {
        checkNotNull(Gdx.gl30) { "volumetric clouds need OpenGL 3" }
    }

    private var march: ShaderProgram? = null
    private var resolve: ShaderProgram? = null
    private var composite: ShaderProgram? = null
    private var mesh: Mesh? = null
    private var framebuffer = 0

    /** The half-resolution targets: this frame's march, and the history written by the previous and this frame. */
    private var current = 0
    private val history = IntArray(2)
    private var latest = 0
    private var width = 0
    private var height = 0
    private var hasHistory = false
    private var frame = 0

    private val viewProj = Matrix4()
    private val prevViewProj = Matrix4()
    private val prevDirection = Vector3()
    private val prevProjection = Matrix4()

    /** How many times the history started again (a camera jump, a resize, a new target), for tests. */
    var historyResets = 0
        private set

    init {
        try {
            march = shaders.program("clouds.vert", "clouds_common.glsl", "clouds_light.glsl", "clouds_volumetric.frag")
            resolve = shaders.program("clouds.vert", "clouds_resolve.frag")
            composite = shaders.program("clouds.vert", "clouds_composite.frag")
            mesh = createFullscreenTriangle()
            framebuffer = Gdx.gl.glGenFramebuffer()
        } catch (e: Exception) {
            dispose()
            throw e
        }
    }

    override fun draw(scene: CloudScene) {
        check(scene.clouds.baseNoise != 0) { scene.clouds.noiseFailure ?: "the cloud asset has no noise textures" }
        val gl = Gdx.gl
        val ints = BufferUtils.newIntBuffer(16)
        gl.glGetIntegerv(GL20.GL_VIEWPORT, ints)
        val viewport = IntArray(4) { ints.get(it) }
        ints.clear()
        gl.glGetIntegerv(GL_FRAMEBUFFER_BINDING, ints)
        val previousFramebuffer = ints.get(0)
        ensureTargets(maxOf(1, viewport[2] / 2), maxOf(1, viewport[3] / 2))
        rotationOnlyViewProj(scene.camera, viewProj)
        if (hasHistory && jumped(scene.camera)) resetHistory()

        val blendWasOn = gl.glIsEnabled(GL20.GL_BLEND)
        try {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer)
            gl.glViewport(0, 0, width, height)
            marchBands(scene)
            gl.glDisable(GL20.GL_BLEND)
            resolveHistory(scene)
        } finally {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previousFramebuffer)
            gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            if (blendWasOn) gl.glEnable(GL20.GL_BLEND) else gl.glDisable(GL20.GL_BLEND)
        }
        val composite = checkNotNull(composite)
        withPremultipliedBlend {
            composite.bind()
            composite.setUniformMatrix("u_invViewProj", scene.invViewProj)
            bindTexture(0, GL20.GL_TEXTURE_2D, history[latest])
            composite.setUniformi("u_clouds", 0)
            checkNotNull(mesh).render(composite, GL20.GL_TRIANGLES)
        }
        unbindTextures()
        prevViewProj.set(viewProj)
        prevDirection.set(scene.camera.direction)
        prevProjection.set(scene.camera.projection)
        frame++
    }

    /** Starts the temporal history again from the next frame. */
    fun resetHistory() {
        hasHistory = false
        historyResets++
    }

    private fun marchBands(scene: CloudScene) {
        val gl = Gdx.gl
        attach(current)
        gl.glClearColor(0f, 0f, 0f, 0f)
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val march = checkNotNull(march)
        val mesh = checkNotNull(mesh)
        withPremultipliedBlend {
            march.bind()
            setCloudFrame(march, scene, height.toFloat())
            bindTexture(0, GL_TEXTURE_3D, scene.clouds.baseNoise)
            bindTexture(1, GL_TEXTURE_3D, scene.clouds.detailNoise)
            march.setUniformi("u_baseNoise", 0)
            march.setUniformi("u_detailNoise", 1)
            march.setUniformf("u_frame", (frame % 64).toFloat())
            for (band in scene.clouds.settings.bandsFarToNear()) {
                setCloudBand(march, band, field, scene.timeSeconds)
                mesh.render(march, GL20.GL_TRIANGLES)
            }
        }
    }

    private fun resolveHistory(scene: CloudScene) {
        val next = 1 - latest
        attach(history[next])
        val resolve = checkNotNull(resolve)
        resolve.bind()
        resolve.setUniformMatrix("u_invViewProj", scene.invViewProj)
        resolve.setUniformMatrix("u_prevViewProj", prevViewProj)
        resolve.setUniformi("u_hasHistory", if (hasHistory) 1 else 0)
        resolve.setUniformf("u_blend", HISTORY_BLEND)
        resolve.setUniformf("u_texel", 1f / width, 1f / height)
        bindTexture(0, GL20.GL_TEXTURE_2D, current)
        bindTexture(1, GL20.GL_TEXTURE_2D, history[latest])
        resolve.setUniformi("u_current", 0)
        resolve.setUniformi("u_history", 1)
        checkNotNull(mesh).render(resolve, GL20.GL_TRIANGLES)
        latest = next
        hasHistory = true
    }

    /** True when the camera turned or zoomed too far since the last frame for the history to line up. */
    private fun jumped(camera: Camera): Boolean {
        val cos = MathUtils.cosDeg(HISTORY_RESET_DEGREES)
        if (prevDirection.dot(camera.direction) < cos * prevDirection.len() * camera.direction.len()) return true
        val a = prevProjection.`val`
        val b = camera.projection.`val`
        return a.indices.any { abs(a[it] - b[it]) > 1e-3f }
    }

    private fun ensureTargets(w: Int, h: Int) {
        if (w == width && h == height && current != 0) return
        deleteTargets()
        width = w
        height = h
        current = target(w, h)
        history[0] = target(w, h)
        history[1] = target(w, h)
        latest = 0
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer)
        attach(current)
        val status = Gdx.gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER)
        check(status == GL20.GL_FRAMEBUFFER_COMPLETE) {
            "16-bit float cloud target is incomplete (status 0x${
                status.toString(
                    16
                )
            })"
        }
        resetHistory()
    }

    private fun target(w: Int, h: Int): Int {
        val gl = Gdx.gl
        val texture = gl.glGenTexture()
        gl.glBindTexture(GL20.GL_TEXTURE_2D, texture)
        gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL20.GL_RGBA, GL_HALF_FLOAT, null)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE)
        gl.glBindTexture(GL20.GL_TEXTURE_2D, 0)
        return texture
    }

    private fun attach(texture: Int) {
        Gdx.gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, texture, 0)
    }

    private fun bindTexture(unit: Int, target: Int, texture: Int) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit)
        Gdx.gl.glBindTexture(target, texture)
    }

    private fun unbindTextures() {
        val gl = Gdx.gl
        gl.glActiveTexture(GL20.GL_TEXTURE1)
        gl.glBindTexture(GL20.GL_TEXTURE_2D, 0)
        gl.glBindTexture(GL_TEXTURE_3D, 0)
        gl.glActiveTexture(GL20.GL_TEXTURE0)
        gl.glBindTexture(GL20.GL_TEXTURE_2D, 0)
        gl.glBindTexture(GL_TEXTURE_3D, 0)
    }

    private fun rotationOnlyViewProj(camera: Camera, out: Matrix4): Matrix4 {
        out.set(camera.view)
        out.setTranslation(0f, 0f, 0f)
        return out.mulLeft(camera.projection)
    }

    private fun deleteTargets() {
        val gl = Gdx.gl
        for (texture in intArrayOf(current, history[0], history[1])) if (texture != 0) gl.glDeleteTexture(texture)
        current = 0
        history.fill(0)
        width = 0
        height = 0
    }

    override fun dispose() {
        deleteTargets()
        val gl = Gdx.gl
        if (framebuffer != 0) gl.glDeleteFramebuffer(framebuffer)
        framebuffer = 0
        listOfNotNull(march, resolve, composite, mesh).forEach { it.dispose() }
        march = null
        resolve = null
        composite = null
        mesh = null
    }
}
