/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.AtmosphereParams
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.SKY_CAMERA_HEIGHT
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.SkyAmbient


/**
 * What a cloud technique draws in one frame: the cloud asset [clouds] seen from [camera] (whose rotation-only inverse view-projection
 * is [invViewProj]) at [timeSeconds], lit by the sun toward [sun] through the atmosphere [params], with [ambient] the
 * sky's light around them.
 */
class CloudScene(
    val camera: Camera,
    val invViewProj: Matrix4,
    val sun: Vector3,
    val timeSeconds: Double,
    val clouds: Clouds,
    val params: AtmosphereParams,
    val ambient: SkyAmbient,
)

/**
 * One way of drawing clouds over a procedural sky's atmosphere: blended premultiplied over what is already drawn, far
 * band first. Built on the GL thread (throws when it cannot be: a shader that does not compile, a resource that cannot be
 * created) and drawn there with the context current; the caller has depth testing and writing off.
 */
abstract class CloudRenderer : Disposable {

    private val GL_BLEND_DST_RGB = 0x80C8
    private val GL_BLEND_SRC_RGB = 0x80C9
    private val GL_BLEND_DST_ALPHA = 0x80CA
    private val GL_BLEND_SRC_ALPHA = 0x80CB

    abstract fun draw(scene: CloudScene)

    /**
     * Sets the uniforms of `clouds_light.glsl` that the whole frame shares, for a target [targetHeight] pixels high (the
     * camera's viewport when null).
     */
    protected fun setCloudFrame(program: ShaderProgram, scene: CloudScene, targetHeight: Float? = null) {
        program.setUniformMatrix("u_invViewProj", scene.invViewProj)
        // projection[1][1] is 1 / tan(fov / 2): one pixel spans about fov / height radians
        val height = (targetHeight ?: scene.camera.viewportHeight).coerceAtLeast(1f)
        program.setUniformf("u_pixelAngle", 2f / (scene.camera.projection.`val`[Matrix4.M11] * height))
        program.setUniformf("u_sunDir", scene.sun.x, scene.sun.y, scene.sun.z)
        program.setUniformf("u_planetRadius", scene.params.planetRadius)
        program.setUniformf("u_cameraHeight", SKY_CAMERA_HEIGHT)
        val a = scene.ambient
        program.setUniformf("u_sunlight", a.sunlight[0], a.sunlight[1], a.sunlight[2])
        program.setUniformf("u_zenith", a.zenith[0], a.zenith[1], a.zenith[2])
        program.setUniformf("u_horizon", a.horizon[0], a.horizon[1], a.horizon[2])
    }

    /** Sets the band uniforms of `clouds_light.glsl` for [band] drifted to [timeSeconds]. */
    protected fun setCloudBand(
        program: ShaderProgram,
        band: CloudMeta.CloudBand,
        field: CloudField,
        timeSeconds: Double
    ) {
        val type = band.type
        val offset = field.windOffset(band, timeSeconds)
        program.setUniformf("u_bandScale", type.scale * type.stretch, type.scale)
        program.setUniformf("u_bandOffset", offset[0], offset[1])
        program.setUniformi("u_bandSeed", type.ordinal)
        program.setUniformi("u_bandOctaves", type.octaves)
        program.setUniformi("u_bandProfile", type.profile.ordinal)
        program.setUniformf("u_bandCoverage", band.coverage)
        program.setUniformf("u_bandDensity", band.density)
        program.setUniformf("u_bandBase", band.base)
        program.setUniformf("u_bandTop", band.top)
    }

    /** Runs [block] with premultiplied-alpha blending on, then puts back the blending state it found. */
    fun withPremultipliedBlend(block: () -> Unit) {
        val gl = Gdx.gl
        val wasOn = gl.glIsEnabled(GL20.GL_BLEND)
        val ints = BufferUtils.newIntBuffer(16)
        gl.glGetIntegerv(GL_BLEND_SRC_RGB, ints)
        val srcRgb = ints.get(0)
        gl.glGetIntegerv(GL_BLEND_DST_RGB, ints)
        val dstRgb = ints.get(0)
        gl.glGetIntegerv(GL_BLEND_SRC_ALPHA, ints)
        val srcAlpha = ints.get(0)
        gl.glGetIntegerv(GL_BLEND_DST_ALPHA, ints)
        val dstAlpha = ints.get(0)
        gl.glEnable(GL20.GL_BLEND)
        gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
        try {
            block()
        } finally {
            gl.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
            if (!wasOn) gl.glDisable(GL20.GL_BLEND)
        }
    }
}
