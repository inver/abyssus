/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight

/**
 * The ground height under a world X/Z: the highest terrain surface a downward ray finds, tested the way picking tests
 * a terrain, so a translated, rotated or scaled terrain works. CPU only, no GL.
 */
class GroundHeights(private val terrains: List<TerrainTarget>) : GroundHeight {
    private val picker = ScenePicker()
    private val corner = Vector3()

    /** Highest world Y any terrain reaches, and lowest; null without a terrain. */
    private val bounds: Pair<Float, Float>? = run {
        var top = Float.NEGATIVE_INFINITY
        var bottom = Float.POSITIVE_INFINITY
        for (t in terrains) {
            val low = t.data.heights.min()
            val high = t.data.heights.max()
            val size = t.data.size.toFloat()
            for (x in floatArrayOf(0f, size)) for (y in floatArrayOf(low, high)) for (z in floatArrayOf(0f, size)) {
                corner.set(x, y, z).mul(t.world)
                if (corner.y > top) top = corner.y
                if (corner.y < bottom) bottom = corner.y
            }
        }
        if (top.isFinite() && bottom.isFinite()) top to bottom else null
    }

    private val coarsest: Float = terrains.maxOfOrNull { it.data.size.toFloat() / (it.data.resolution - 1) * 2f } ?: 0f

    /** The terrain height at [x], [z], or [fallbackY] over no terrain. */
    override fun heightAt(x: Float, z: Float, fallbackY: Float): Float = terrainHeight(x, z) ?: fallbackY

    /** The height of the highest terrain surface at [x], [z]; null over no terrain. */
    fun terrainHeight(x: Float, z: Float): Float? {
        val (top, bottom) = bounds ?: return null
        val originY = top + 1f
        val ray = Ray(Vector3(x, originY, z), Vector3(0f, -1f, 0f))
        // the march takes steps of half a cell, so a coarse terrain needs a ray at least that long to see its surface
        val reach = maxOf(originY - bottom + 1f, coarsest)
        var best: Float? = null
        for (t in terrains) {
            val distance = picker.terrainDistance(ray, t, reach) ?: continue
            val y = originY - distance
            best = best?.let { maxOf(it, y) } ?: y
        }
        return best
    }
}
