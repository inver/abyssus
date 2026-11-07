/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.glutils.ShaderProgram
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
    /** The packed view and light tables, laid out as `ShadowDataTexture` describes. */
    internal val data = FloatArray(SHADOW_DATA_WIDTH * SHADOW_DATA_HEIGHT * 4)

    private var texture: FloatDataTexture? = null
    private var uploadedRevision = -1L

    /**
     * The texture holding the packed data, made on first use. The caller binds it to a unit and passes that unit to
     * [bind].
     */
    val dataTexture: FloatDataTexture
        get() = texture ?: FloatDataTexture(SHADOW_DATA_WIDTH, SHADOW_DATA_HEIGHT).also { texture = it }

    // what the arrays were packed from: the atlas and, per light slot, the record (identity: records belong to the
    // atlas)
    private var packed = false
    private var packedAtlas: ShadowAtlasAttribute? = null
    private val packedDirs = arrayOfNulls<ShadowLightRecord>(SHADOW_MAX_DIRECTIONAL)
    private val packedPoints = arrayOfNulls<ShadowLightRecord>(SHADOW_MAX_LOCAL)
    private val packedSpots = arrayOfNulls<ShadowLightRecord>(SHADOW_MAX_LOCAL)
    private var dirCount = -1
    private var pointCount = -1
    private var spotCount = -1

    /** Bumped every time the arrays are packed again: a program holding an older one is uploaded to. */
    private var revision = 0L

    private class Program(program: ShaderProgram) {
        val sampler = program.fetchUniformLocation("u_shadowAtlas", false)
        val data = program.fetchUniformLocation("u_shadowData", false)
        val enabled = program.fetchUniformLocation("u_shadowEnabled", false)
        val texel = program.fetchUniformLocation("u_shadowTexel", false)
        var revision = -1L
        var unit = -1
        var dataUnit = -1
    }

    private val programs = IdentityHashMap<ShaderProgram, Program>()

    /** How many times uniforms were uploaded to a program; a bind that found the program up to date adds nothing. */
    var uploads = 0L
        private set

    fun bind(
        program: ShaderProgram,
        atlas: ShadowAtlasAttribute?,
        directional: List<ShadowLightRecord?>,
        points: List<ShadowLightRecord?>,
        spots: List<ShadowLightRecord?>,
        unit: Int,
        dataUnit: Int,
    ) {
        if (!samePack(atlas, directional, points, spots)) {
            pack(atlas, directional, points, spots)
            revision++
        }
        val data = dataTexture
        if (uploadedRevision != revision || !data.allocated) {
            data.upload(dataUnit, this.data)
            uploadedRevision = revision
        }
        val state = programs.getOrPut(program) { Program(program) }
        if (state.revision == revision && state.unit == unit && state.dataUnit == dataUnit) {
            return
        }
        upload(program, state, atlas, unit, dataUnit)
        state.revision = revision
        state.unit = unit
        state.dataUnit = dataUnit
        uploads++
    }

    /** Forgets [program] (it was disposed): its locations and what it held. */
    fun forget(program: ShaderProgram) {
        programs.remove(program)
    }

    /** Releases the data texture; the bindings can be used again and make a new one. */
    fun dispose() {
        texture?.dispose()
        texture = null
        uploadedRevision = -1L
        programs.clear()
        packed = false
    }

    private fun samePack(
        atlas: ShadowAtlasAttribute?,
        directional: List<ShadowLightRecord?>,
        points: List<ShadowLightRecord?>,
        spots: List<ShadowLightRecord?>,
    ): Boolean {
        if (!packed || atlas !== packedAtlas) {
            return false
        }
        return same(packedDirs, dirCount, directional, SHADOW_MAX_DIRECTIONAL) &&
            same(packedPoints, pointCount, points, SHADOW_MAX_LOCAL) &&
            same(packedSpots, spotCount, spots, SHADOW_MAX_LOCAL)
    }

    private fun same(packed: Array<ShadowLightRecord?>, count: Int, now: List<ShadowLightRecord?>, max: Int): Boolean {
        val n = minOf(now.size, max)
        if (n != count) {
            return false
        }
        for (i in 0 until n) if (packed[i] !== now[i]) {
            return false
        }
        return true
    }

    private fun pack(
        atlas: ShadowAtlasAttribute?,
        directional: List<ShadowLightRecord?>,
        points: List<ShadowLightRecord?>,
        spots: List<ShadowLightRecord?>,
    ) {
        data.fill(0f)
        for (table in intArrayOf(SHADOW_DIRECTIONAL_TABLE_ROW, SHADOW_POINT_TABLE_ROW, SHADOW_SPOT_TABLE_ROW)) {
            val entries = if (table == SHADOW_DIRECTIONAL_TABLE_ROW) SHADOW_MAX_DIRECTIONAL else SHADOW_MAX_LOCAL
            for (i in 0 until entries) data[entryOffset(table, i)] = -1f
        }
        var index = 0
        val records = atlas?.records.orEmpty()
        for (r in records.indices) {
            val record = records[r]
            require(index + record.views.size <= SHADOW_MAX_VIEWS) {
                "A shadow atlas supports at most $SHADOW_MAX_VIEWS views"
            }
            for (v in record.views.indices) {
                val view = record.views[v]
                view.matrix.`val`.copyInto(data, texel(index, 0))
                val uv = view.uvTransform
                val tile = texel(index, 4)
                data[tile] = uv.x
                data[tile + 1] = uv.y
                data[tile + 2] = uv.z
                data[tile + 3] = uv.w
                val position = texel(index, 5)
                data[position] = record.position.x
                data[position + 1] = record.position.y
                data[position + 2] = record.position.z
                data[position + 3] = record.far
                data[texel(index, 6)] = record.depthBias
                index++
            }
        }
        dirCount = minOf(directional.size, SHADOW_MAX_DIRECTIONAL)
        for (i in 0 until dirCount) {
            val record = directional[i]
            packedDirs[i] = record
            if (record != null) {
                startOf(records, record).let {
                    if (it >= 0) {
                        data[entryOffset(SHADOW_DIRECTIONAL_TABLE_ROW, i)] = it.toFloat()
                    }
                }
            }
        }
        pointCount = minOf(points.size, SHADOW_MAX_LOCAL)
        for (i in 0 until pointCount) {
            val record = points[i]
            packedPoints[i] = record
            if (record != null && record.views.size == SHADOW_POINT_FACES) {
                startOf(records, record).let { start ->
                    if (start >= 0) {
                        data[entryOffset(SHADOW_POINT_TABLE_ROW, i)] = start.toFloat()
                    }
                }
            }
        }
        spotCount = minOf(spots.size, SHADOW_MAX_LOCAL)
        for (i in 0 until spotCount) {
            val record = spots[i]
            packedSpots[i] = record
            if (record != null) {
                startOf(records, record).let {
                    if (it >= 0) {
                        data[entryOffset(SHADOW_SPOT_TABLE_ROW, i)] = it.toFloat()
                    }
                }
            }
        }
        packedAtlas = atlas
        packed = true
    }

    /**
     * The first view index of the atlas record of [record]'s light (the last such record), or -1 when the atlas has
     * none.
     */
    private fun startOf(records: List<ShadowLightRecord>, record: ShadowLightRecord): Int {
        var index = 0
        var start = -1
        for (r in records.indices) {
            val candidate = records[r]
            if (candidate.lightId == record.lightId) {
                start = index
            }
            index += candidate.views.size
        }
        return start
    }

    private fun upload(program: ShaderProgram, state: Program, atlas: ShadowAtlasAttribute?, unit: Int, dataUnit: Int) {
        if (state.sampler >= 0) {
            program.setUniformi(state.sampler, unit)
        }
        if (state.data >= 0) {
            program.setUniformi(state.data, dataUnit)
        }
        if (state.enabled >= 0) {
            program.setUniformf(
                state.enabled,
                if (atlas != null) {
                    1f
                } else {
                    0f
                },
            )
        }
        if (state.texel >= 0) {
            program.setUniformf(state.texel, 1f / (atlas?.atlas?.width ?: 4096), 1f / (atlas?.atlas?.height ?: 4096))
        }
    }

    private companion object {
        fun texel(row: Int, column: Int) = (row * SHADOW_DATA_WIDTH + column) * 4

        /** The float index of table entry [i], whose x holds the light's first view (or -1). */
        fun entryOffset(tableRow: Int, i: Int) = texel(tableRow + i / SHADOW_DATA_WIDTH, i % SHADOW_DATA_WIDTH)
    }
}
