/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import java.util.IdentityHashMap

/**
 * Uniform packing shared by model consumers and dedicated surface shaders. The caller binds the atlas to [unit].
 *
 * One instance belongs to one shader (its state is not global). [bind] runs for every renderable, so it allocates
 * nothing once warm: the arrays are packed again only when the atlas (a [ShadowAtlasAttribute] is one revision of the
 * map: its records are copied when it is made) or the record of a light changes, uniform locations are looked up once
 * per program, and a program that already holds this revision with the same texture unit gets no upload at all.
 */
class ShadowAtlasBindings {
    private val matrices = FloatArray(MAX_VIEWS * 16)
    private val tiles = FloatArray(MAX_VIEWS * 4)
    private val positions = FloatArray(MAX_VIEWS * 3)
    private val fars = FloatArray(MAX_VIEWS)
    private val bias = FloatArray(MAX_VIEWS)
    private val dirs = FloatArray(MAX_DIRECTIONAL)
    private val local = FloatArray(MAX_POINT * 6)
    private val cones = FloatArray(MAX_SPOT)

    // what the arrays were packed from: the atlas and, per light slot, the record (identity: records belong to the atlas)
    private var packed = false
    private var packedAtlas: ShadowAtlasAttribute? = null
    private val packedDirs = arrayOfNulls<ShadowLightRecord>(MAX_DIRECTIONAL)
    private val packedPoints = arrayOfNulls<ShadowLightRecord>(MAX_POINT)
    private val packedSpots = arrayOfNulls<ShadowLightRecord>(MAX_SPOT)
    private var dirCount = -1
    private var pointCount = -1
    private var spotCount = -1

    /** Bumped every time the arrays are packed again: a program holding an older one is uploaded to. */
    private var revision = 0L

    private class Program(program: ShaderProgram) {
        val sampler = program.fetchUniformLocation("u_shadowAtlas", false)
        val enabled = program.fetchUniformLocation("u_shadowEnabled", false)
        val texel = program.fetchUniformLocation("u_shadowTexel", false)
        val matrices = program.fetchUniformLocation("u_shadowMatrices[0]", false)
        val tiles = program.fetchUniformLocation("u_shadowTiles[0]", false)
        val positions = program.fetchUniformLocation("u_shadowPositions[0]", false)
        val fars = program.fetchUniformLocation("u_shadowFars[0]", false)
        val bias = program.fetchUniformLocation("u_shadowBias[0]", false)
        val dirs = program.fetchUniformLocation("u_dirShadowTile[0]", false)
        val local = program.fetchUniformLocation("u_pointShadowTiles[0]", false)
        val cones = program.fetchUniformLocation("u_spotShadowTile[0]", false)
        var revision = -1L
        var unit = -1
    }

    private val programs = IdentityHashMap<ShaderProgram, Program>()

    /** How many times uniforms were uploaded to a program; a bind that found the program up to date adds nothing. */
    var uploads = 0L
        private set

    fun bind(program: ShaderProgram, atlas: ShadowAtlasAttribute?, directional: List<ShadowLightRecord?>,
             points: List<ShadowLightRecord?>, spots: List<ShadowLightRecord?>, unit: Int) {
        if (!samePack(atlas, directional, points, spots)) {
            pack(atlas, directional, points, spots)
            revision++
        }
        val state = programs.getOrPut(program) { Program(program) }
        if (state.revision == revision && state.unit == unit) return
        upload(program, state, atlas, unit)
        state.revision = revision
        state.unit = unit
        uploads++
    }

    /** Forgets [program] (it was disposed): its locations and what it held. */
    fun forget(program: ShaderProgram) {
        programs.remove(program)
    }

    private fun samePack(atlas: ShadowAtlasAttribute?, directional: List<ShadowLightRecord?>, points: List<ShadowLightRecord?>,
                         spots: List<ShadowLightRecord?>): Boolean {
        if (!packed || atlas !== packedAtlas) return false
        return same(packedDirs, dirCount, directional, MAX_DIRECTIONAL) && same(packedPoints, pointCount, points, MAX_POINT) &&
            same(packedSpots, spotCount, spots, MAX_SPOT)
    }

    private fun same(packed: Array<ShadowLightRecord?>, count: Int, now: List<ShadowLightRecord?>, max: Int): Boolean {
        val n = minOf(now.size, max)
        if (n != count) return false
        for (i in 0 until n) if (packed[i] !== now[i]) return false
        return true
    }

    private fun pack(atlas: ShadowAtlasAttribute?, directional: List<ShadowLightRecord?>, points: List<ShadowLightRecord?>,
                     spots: List<ShadowLightRecord?>) {
        for (i in 0 until MAX_VIEWS) IDENTITY.copyInto(matrices, i * 16)
        tiles.fill(0f)
        positions.fill(0f)
        fars.fill(1f)
        bias.fill(0f)
        var index = 0
        val records = atlas?.records.orEmpty()
        for (r in records.indices) {
            val record = records[r]
            require(index + record.views.size <= MAX_VIEWS) { "A shadow atlas supports at most $MAX_VIEWS views" }
            for (v in record.views.indices) {
                val view = record.views[v]
                view.matrix.`val`.copyInto(matrices, index * 16)
                val uv = view.uvTransform
                tiles[index * 4] = uv.x; tiles[index * 4 + 1] = uv.y; tiles[index * 4 + 2] = uv.z; tiles[index * 4 + 3] = uv.w
                positions[index * 3] = record.position.x; positions[index * 3 + 1] = record.position.y; positions[index * 3 + 2] = record.position.z
                fars[index] = record.far; bias[index] = record.depthBias
                index++
            }
        }
        dirs.fill(-1f); local.fill(-1f); cones.fill(-1f)
        dirCount = minOf(directional.size, MAX_DIRECTIONAL)
        for (i in 0 until dirCount) {
            val record = directional[i]
            packedDirs[i] = record
            if (record != null) startOf(records, record).let { if (it >= 0) dirs[i] = it.toFloat() }
        }
        pointCount = minOf(points.size, MAX_POINT)
        for (i in 0 until pointCount) {
            val record = points[i]
            packedPoints[i] = record
            if (record != null && record.views.size == 6) startOf(records, record).let { start ->
                if (start >= 0) for (face in 0..5) local[i * 6 + face] = (start + face).toFloat()
            }
        }
        spotCount = minOf(spots.size, MAX_SPOT)
        for (i in 0 until spotCount) {
            val record = spots[i]
            packedSpots[i] = record
            if (record != null) startOf(records, record).let { if (it >= 0) cones[i] = it.toFloat() }
        }
        packedAtlas = atlas
        packed = true
    }

    /** The first view index of the atlas record of [record]'s light (the last such record), or -1 when the atlas has none. */
    private fun startOf(records: List<ShadowLightRecord>, record: ShadowLightRecord): Int {
        var index = 0
        var start = -1
        for (r in records.indices) {
            val candidate = records[r]
            if (candidate.lightId == record.lightId) start = index
            index += candidate.views.size
        }
        return start
    }

    private fun upload(program: ShaderProgram, state: Program, atlas: ShadowAtlasAttribute?, unit: Int) {
        if (state.sampler >= 0) program.setUniformi(state.sampler, unit)
        if (state.enabled >= 0) program.setUniformf(state.enabled, if (atlas != null) 1f else 0f)
        if (state.texel >= 0) program.setUniformf(state.texel, 1f / (atlas?.atlas?.width ?: 4096), 1f / (atlas?.atlas?.height ?: 4096))
        if (state.matrices >= 0) program.setUniformMatrix4fv(state.matrices, matrices, 0, matrices.size)
        if (state.tiles >= 0) program.setUniform4fv(state.tiles, tiles, 0, tiles.size)
        if (state.positions >= 0) program.setUniform3fv(state.positions, positions, 0, positions.size)
        if (state.fars >= 0) program.setUniform1fv(state.fars, fars, 0, fars.size)
        if (state.bias >= 0) program.setUniform1fv(state.bias, bias, 0, bias.size)
        if (state.dirs >= 0) program.setUniform1fv(state.dirs, dirs, 0, dirs.size)
        if (state.local >= 0) program.setUniform1fv(state.local, local, 0, local.size)
        if (state.cones >= 0) program.setUniform1fv(state.cones, cones, 0, cones.size)
    }

    private companion object {
        const val MAX_VIEWS = 16
        const val MAX_DIRECTIONAL = 2
        const val MAX_POINT = 5
        const val MAX_SPOT = 5
        val IDENTITY: FloatArray = Matrix4().`val`
    }
}
