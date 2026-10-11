/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudField
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudScene
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.LinearShellClouds
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle

const val PROCEDURAL_LIGHTING_SIZE = 64
const val PROCEDURAL_LIGHTING_LEVELS = 7

/**
 * Owned linear RGBA16F atmosphere-and-shell cube. Built and rendered on the current GL thread only; borrowed clouds
 * are never disposed. The caller disposes this source after the environment build has finished sampling it.
 */
class ProceduralLightingCube(
    private val params: AtmosphereParams,
    private val shaders: ShaderStorage = ShaderStorage(),
) : Disposable {
    val texture = GpuTexture(GL20.GL_TEXTURE_CUBE_MAP, PROCEDURAL_LIGHTING_SIZE, PROCEDURAL_LIGHTING_SIZE)
    private var fbo = 0
    private var program: ShaderProgram? = null
    private var mesh: Mesh? = null
    private var shells: LinearShellClouds? = null
    private var allocated = false
    private var disposed = false
    private val ambient = SkyAmbientEstimate(params)
    private val camera = PerspectiveCamera(90f, PROCEDURAL_LIGHTING_SIZE.toFloat(), PROCEDURAL_LIGHTING_SIZE.toFloat())
    private val inverse = Matrix4()

    fun render(sun: Vector3, clouds: Clouds?, timeSeconds: Double) {
        check(!disposed)
        checkNotNull(Gdx.gl30) { "Procedural sky lighting needs OpenGL 3" }
        if (!allocated) allocate()
        val gl = Gdx.gl
        val ints = BufferUtils.newIntBuffer(16)
        gl.glGetIntegerv(0x8CA6, ints)
        val previous = ints.get(0)
        gl.glGetIntegerv(GL20.GL_VIEWPORT, ints)
        val viewport = IntArray(4) { ints.get(it) }
        val caps = listOf(GL20.GL_BLEND, GL20.GL_DEPTH_TEST, GL20.GL_CULL_FACE, GL20.GL_SCISSOR_TEST)
        val enabled = caps.map(gl::glIsEnabled)
        try {
            caps.forEach(gl::glDisable)
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
            gl.glViewport(0, 0, PROCEDURAL_LIGHTING_SIZE, PROCEDURAL_LIGHTING_SIZE)
            val atmosphere = program ?: shaders.program("clouds.vert", "atmosphere_linear.frag").also { program = it }
            val triangle = mesh ?: createFullscreenTriangle().also { mesh = it }
            for (face in 0..5) {
                gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0,
                    GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, texture.textureObjectHandle, 0)
                check(gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER) == GL20.GL_FRAMEBUFFER_COMPLETE) {
                    "Procedural lighting RGBA16F framebuffer is incomplete"
                }
                orient(face)
                inverse.set(camera.combined).inv()
                atmosphere.bind()
                atmosphere.setUniformMatrix("u_invViewProj", inverse)
                atmosphere.setUniformf("u_sunDir", sun)
                atmosphere.setUniformf("u_cameraHeight", SKY_CAMERA_HEIGHT)
                atmosphere.setUniformf("u_planetRadius", params.planetRadius)
                atmosphere.setUniformf("u_atmosphereRadius", params.atmosphereRadius)
                atmosphere.setUniformf("u_betaRayleigh", params.betaRayleigh[0], params.betaRayleigh[1], params.betaRayleigh[2])
                atmosphere.setUniformf("u_betaMie", params.betaMie)
                atmosphere.setUniformf("u_heightRayleigh", params.heightRayleigh)
                atmosphere.setUniformf("u_heightMie", params.heightMie)
                atmosphere.setUniformf("u_mieG", params.mieG)
                atmosphere.setUniformf("u_sunIntensity", params.sunIntensity)
                triangle.render(atmosphere, GL20.GL_TRIANGLES)
                if (clouds?.settings?.visible == true) {
                    val renderer = shells ?: LinearShellClouds(shaders, CloudField()).also { shells = it }
                    renderer.draw(CloudScene(camera, inverse, sun, timeSeconds, clouds, params, ambient.ambient(sun, 1500f)))
                }
            }
            texture.bind(0)
            gl.glGenerateMipmap(GL20.GL_TEXTURE_CUBE_MAP)
        } finally {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
            gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            caps.zip(enabled).forEach { (cap, on) -> if (on) gl.glEnable(cap) else gl.glDisable(cap) }
            gl.glActiveTexture(GL20.GL_TEXTURE0)
            gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        }
    }

    private fun allocate() {
        val gl = Gdx.gl
        texture.bind(0)
        for (level in 0 until PROCEDURAL_LIGHTING_LEVELS) for (face in 0..5) {
            val size = PROCEDURAL_LIGHTING_SIZE shr level
            gl.glTexImage2D(GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, level, 0x881A,
                size, size, 0, GL20.GL_RGBA, 0x140B, null)
        }
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL30.GL_TEXTURE_MAX_LEVEL, PROCEDURAL_LIGHTING_LEVELS - 1)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR_MIPMAP_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, 0x8072, GL20.GL_CLAMP_TO_EDGE)
        gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        fbo = gl.glGenFramebuffer()
        allocated = true
    }

    private fun orient(face: Int) {
        camera.position.setZero()
        when (face) {
            0 -> { camera.direction.set(1f, 0f, 0f); camera.up.set(0f, -1f, 0f) }
            1 -> { camera.direction.set(-1f, 0f, 0f); camera.up.set(0f, -1f, 0f) }
            2 -> { camera.direction.set(0f, 1f, 0f); camera.up.set(0f, 0f, 1f) }
            3 -> { camera.direction.set(0f, -1f, 0f); camera.up.set(0f, 0f, -1f) }
            4 -> { camera.direction.set(0f, 0f, 1f); camera.up.set(0f, -1f, 0f) }
            5 -> { camera.direction.set(0f, 0f, -1f); camera.up.set(0f, -1f, 0f) }
        }
        camera.update()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        shells?.dispose()
        program?.dispose()
        mesh?.dispose()
        texture.dispose()
        if (fbo != 0) Gdx.gl.glDeleteFramebuffer(fbo)
        fbo = 0
    }
}
