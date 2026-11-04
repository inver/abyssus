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
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import net.nevinsky.abyssus.lib.core.util.GlStateScope
import net.nevinsky.abyssus.lib.core.util.OFFSCREEN_CAPS
import net.nevinsky.abyssus.lib.core.util.checkFramebufferComplete

/** The size of each face of a cached atmosphere, in texels: about 5.7 texels a degree. */
const val ATMOSPHERE_CACHE_SIZE = 512

/** The define a procedural sky's fragment shader declares (`#define SKY_CACHEABLE` on a line) to have its atmosphere cached. */
const val SKY_CACHEABLE = "SKY_CACHEABLE"

/** Defined when a cacheable sky's shader renders the cube: everything but what [SKY_DETAIL_ONLY] draws exactly (the sun's disc). */
const val SKY_BAKE = "SKY_BAKE"

/**
 * Defined when a cacheable sky's shader draws over the cached cube: it discards every pixel a cube map would blur
 * nothing of, and draws the others (the sun's disc, the horizon line) as it would without the cache.
 */
const val SKY_DETAIL_ONLY = "SKY_DETAIL_ONLY"

private const val GL_TEXTURE_CUBE_MAP_SEAMLESS = 0x884F
private const val CACHE_LEVELS = 10

private val CACHEABLE = Regex("""(?m)^\s*#define\s+$SKY_CACHEABLE\b""")

/** Whether the procedural sky fragment shader [fragment] declares itself cacheable ([SKY_CACHEABLE]). */
fun declaresCacheable(fragment: String): Boolean = CACHEABLE.containsMatchIn(fragment)

/**
 * The atmosphere of a cacheable procedural sky (design decision D15 of `improve-control-line-performance-stability`),
 * kept in a cube map rendered only when the sun moves: the bundled sky integrated 16 x 8 scattering steps for every
 * pixel of the window every frame, under a sun that does not move. Each frame draws the cube with a lookup, then the
 * sky's own shader with [SKY_DETAIL_ONLY] over it, which keeps only the pixels a cube would blur, so those are exact.
 *
 * The sky's shader is project content, so this is used only for one that declares [SKY_CACHEABLE]; its vertex shader
 * must give the world direction as `v_dir` from `u_invViewProj`, as a procedural sky's does. The cube holds the shader's
 * output (already tone mapped and encoded), 8 bits a channel. GL thread only; owns its programs, cube and framebuffer.
 */
internal class AtmosphereCache(
    vertex: String,
    fragment: String,
    lookupFragment: String,
    private val params: AtmosphereParams,
) : Disposable {
    private val bake = compile(vertex, "#define $SKY_BAKE\n$fragment", "bake")
    private val detail = compile(vertex, "#define $SKY_DETAIL_ONLY\n$fragment", "detail")
    private val lookup = compile(vertex, lookupFragment, "lookup")
    private val texture = GpuTexture(GL20.GL_TEXTURE_CUBE_MAP, ATMOSPHERE_CACHE_SIZE, ATMOSPHERE_CACHE_SIZE)
    private var fbo = 0
    private val camera = PerspectiveCamera(90f, ATMOSPHERE_CACHE_SIZE.toFloat(), ATMOSPHERE_CACHE_SIZE.toFloat())
    private val inverse = Matrix4()
    private val bakedFor = Vector3()
    private var baked = false

    /** How many times the cube was rendered: once, and again each time the sun moved. */
    var bakes = 0
        private set

    private fun compile(vertex: String, fragment: String, what: String): ShaderProgram =
        ShaderProgram(vertex, fragment).also {
            if (!it.isCompiled) {
                val log = it.log
                it.dispose()
                throw IllegalStateException("Procedural sky $what shader failed to compile: $log")
            }
        }

    /** Draws the atmosphere seen through [invViewProj] (camera translation removed) with the sun toward [sun]. */
    fun draw(mesh: Mesh, invViewProj: Matrix4, sun: Vector3) {
        if (!baked || bakedFor != sun) render(mesh, sun)
        val gl = Gdx.gl
        val seamless = gl.glIsEnabled(GL_TEXTURE_CUBE_MAP_SEAMLESS)
        gl.glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
        try {
            texture.bind(0)
            lookup.bind()
            lookup.setUniformMatrix("u_invViewProj", invViewProj)
            lookup.setUniformi("u_cubemap", 0)
            mesh.render(lookup, GL20.GL_TRIANGLES)
        } finally {
            if (!seamless) gl.glDisable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
            gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        }
        detail.bind()
        detail.setUniformMatrix("u_invViewProj", invViewProj)
        params.bindTo(detail, sun)
        mesh.render(detail, GL20.GL_TRIANGLES)
    }

    private fun render(mesh: Mesh, sun: Vector3) {
        if (fbo == 0) allocate()
        val gl = Gdx.gl
        val state = GlStateScope(OFFSCREEN_CAPS)
        try {
            state.disableCaps()
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
            gl.glViewport(0, 0, ATMOSPHERE_CACHE_SIZE, ATMOSPHERE_CACHE_SIZE)
            bake.bind()
            params.bindTo(bake, sun)
            for (face in 0..5) {
                gl.glFramebufferTexture2D(
                    GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face,
                    texture.textureObjectHandle, 0,
                )
                checkFramebufferComplete("Procedural sky atmosphere cache")
                orientCubeFace(camera, face)
                bake.setUniformMatrix("u_invViewProj", inverse.set(camera.combined).inv())
                mesh.render(bake, GL20.GL_TRIANGLES)
            }
            texture.bind(0)
            gl.glGenerateMipmap(GL20.GL_TEXTURE_CUBE_MAP)
        } finally {
            state.restore()
            gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        }
        bakedFor.set(sun)
        baked = true
        bakes++
    }

    private fun allocate() {
        val gl = Gdx.gl
        texture.bind(0)
        for (face in 0..5) {
            gl.glTexImage2D(
                GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, 0, GL20.GL_RGBA, ATMOSPHERE_CACHE_SIZE, ATMOSPHERE_CACHE_SIZE, 0,
                GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, null,
            )
        }
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL30.GL_TEXTURE_MAX_LEVEL, CACHE_LEVELS - 1)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR_MIPMAP_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, 0x8072, GL20.GL_CLAMP_TO_EDGE)
        gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        fbo = gl.glGenFramebuffer()
    }

    override fun dispose() {
        bake.dispose()
        detail.dispose()
        lookup.dispose()
        texture.dispose()
        if (fbo != 0) Gdx.gl.glDeleteFramebuffer(fbo)
        fbo = 0
    }
}
