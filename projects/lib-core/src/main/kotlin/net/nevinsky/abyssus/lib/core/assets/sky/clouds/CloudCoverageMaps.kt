/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.util.GL_HALF_FLOAT
import net.nevinsky.abyssus.lib.core.util.checkFramebufferComplete

private const val GL_R16F = 0x822D
private const val GL_RED = 0x1903
private const val GL_LINEAR_MIPMAP_LINEAR = 0x2703
private const val GL_REPEAT = 0x2901

/** The coverage noise repeats every this many first-octave cells (`CLOUD_NOISE_PERIOD` in `clouds_common.glsl`). */
const val COVERAGE_MAP_PERIOD_CELLS = 64

private const val MAX_MAP_SIZE = 2048

/** A baked coverage map: the GL [texture] and how many of its texels span one first-octave cell. */
class CloudCoverageMap(val texture: Int, val texelsPerCell: Float)

/**
 * The coverage noise of cloud types baked to textures, one per (seed, octaves), so the volumetric march looks the
 * field up (a mipmapped fetch, whose level also drops the detail finer than a pixel) instead of summing its octaves at
 * every step. Each map is baked on first use by `clouds_coverage_bake.frag` into an `R16F` texture sized for the
 * type's finest octave, and kept until [dispose]. Needs OpenGL 3; GL thread only.
 */
class CloudCoverageMaps(shaders: ShaderStorage) : Disposable {
    private var bake: ShaderProgram? =
        shaders.program("clouds.vert", "clouds_common.glsl", "clouds_coverage_bake.frag")
    private val maps = HashMap<Pair<Int, Int>, CloudCoverageMap>()

    /**
     * The map of [type], baked now if it is the first time, through [framebuffer] and [mesh]. Leaves the framebuffer
     * binding, viewport and blending for the caller to put back.
     */
    fun map(type: CloudType, framebuffer: Int, mesh: Mesh): CloudCoverageMap =
        maps.getOrPut(type.ordinal to type.octaves) { bake(type, framebuffer, mesh) }

    private fun bake(type: CloudType, framebuffer: Int, mesh: Mesh): CloudCoverageMap {
        val gl = Gdx.gl
        // the finest octave has 2^(octaves-1) noise cells per cell; give it two texels per noise cell where that fits
        val size = (COVERAGE_MAP_PERIOD_CELLS * 4 shl (type.octaves - 1).coerceAtLeast(0)).coerceAtMost(MAX_MAP_SIZE)
        val texture = gl.glGenTexture()
        try {
            gl.glBindTexture(GL20.GL_TEXTURE_2D, texture)
            gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL_R16F, size, size, 0, GL_RED, GL_HALF_FLOAT, null)
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL_REPEAT)
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL_REPEAT)
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer)
            gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, texture, 0)
            checkFramebufferComplete("cloud coverage map")
            gl.glViewport(0, 0, size, size)
            gl.glDisable(GL20.GL_BLEND)
            val program = checkNotNull(bake)
            program.bind()
            program.setUniformi("u_seed", type.ordinal)
            program.setUniformi("u_octaves", type.octaves)
            mesh.render(program, GL20.GL_TRIANGLES)
            gl.glBindTexture(GL20.GL_TEXTURE_2D, texture)
            gl.glGenerateMipmap(GL20.GL_TEXTURE_2D)
            gl.glBindTexture(GL20.GL_TEXTURE_2D, 0)
        } catch (e: Exception) {
            gl.glDeleteTexture(texture)
            throw e
        }
        return CloudCoverageMap(texture, size.toFloat() / COVERAGE_MAP_PERIOD_CELLS)
    }

    override fun dispose() {
        maps.values.forEach { Gdx.gl.glDeleteTexture(it.texture) }
        maps.clear()
        bake?.dispose()
        bake = null
    }
}
