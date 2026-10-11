/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.hdr

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable

private const val GL_RGB16F = 0x881B
private const val GL_RGBA16F = 0x881A
private const val GL_HALF_FLOAT = 0x140B
private const val GL_FRAMEBUFFER_BINDING = 0x8CA6

/**
 * Float cubes and the one framebuffer that renders into their faces, shared by the passes that build lighting cubes. Every
 * pass runs through [withFramebuffer], which restores the framebuffer, viewport and state it found. GL thread only,
 * inside `GdxRuntime.withContext`; [dispose] deletes the framebuffer (not the cubes, which the caller owns).
 */
internal class CubeTargets : Disposable {
    private var fbo = 0

    /** A cube of 16-bit float faces [size] wide with [levels] mip levels allocated, clamped and linearly filtered. */
    fun cube(size: Int, levels: Int, alpha: Boolean = false): GpuTexture {
        val gl = Gdx.gl
        val texture = GpuTexture(GL20.GL_TEXTURE_CUBE_MAP, size, size)
        texture.bind()
        for (level in 0 until levels) for (face in 0 until 6) {
            val s = maxOf(size shr level, 1)
            gl.glTexImage2D(
                GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face,
                level,
                if (alpha) GL_RGBA16F else GL_RGB16F,
                s,
                s,
                0,
                if (alpha) GL20.GL_RGBA else GL20.GL_RGB,
                GL_HALF_FLOAT,
                null
            )
        }
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL30.GL_TEXTURE_MAX_LEVEL, levels - 1)
        val min = if (levels > 1) GL20.GL_LINEAR_MIPMAP_LINEAR else GL20.GL_LINEAR
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MIN_FILTER, min)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE)
        gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        return texture
    }

    /** Fills the mip levels below 0 of [cube] from level 0. */
    fun generateMipmaps(cube: GpuTexture) {
        cube.bind()
        Gdx.gl.glGenerateMipmap(GL20.GL_TEXTURE_CUBE_MAP)
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
    }

    /** Makes [face] of [level] of [target] the colour target; throws when the GPU cannot render to it. */
    fun attach(target: GpuTexture, face: Int, level: Int) {
        val gl = Gdx.gl
        gl.glFramebufferTexture2D(
            GL20.GL_FRAMEBUFFER,
            GL20.GL_COLOR_ATTACHMENT0,
            GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face,
            target.textureObjectHandle,
            level
        )
        val status = gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER)
        check(status == GL20.GL_FRAMEBUFFER_COMPLETE) {
            "16-bit float framebuffer is incomplete (status 0x${status.toString(16)})"
        }
    }

    /** Runs [block] with the framebuffer bound and blending, depth, culling and scissor off; restores what it found. */
    fun withFramebuffer(block: () -> Unit) {
        val gl = Gdx.gl
        if (fbo == 0) fbo = gl.glGenFramebuffer()
        val ints = BufferUtils.newIntBuffer(16)
        gl.glGetIntegerv(GL_FRAMEBUFFER_BINDING, ints)
        val previous = ints.get(0)
        ints.clear()
        gl.glGetIntegerv(GL20.GL_VIEWPORT, ints)
        val viewport = IntArray(4) { ints.get(it) }
        val caps = listOf(GL20.GL_BLEND, GL20.GL_DEPTH_TEST, GL20.GL_CULL_FACE, GL20.GL_SCISSOR_TEST)
        val enabled = caps.map(gl::glIsEnabled)
        caps.forEach(gl::glDisable)
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
        try {
            block()
        } finally {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
            gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            caps.zip(enabled).forEach { (cap, on) -> if (on) gl.glEnable(cap) }
            gl.glActiveTexture(GL20.GL_TEXTURE0)
            gl.glBindTexture(GL20.GL_TEXTURE_2D, 0)
            gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        }
    }

    override fun dispose() {
        if (fbo != 0) Gdx.gl.glDeleteFramebuffer(fbo)
        fbo = 0
    }
}
