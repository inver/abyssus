/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.brush.BrushPath
import net.nevinsky.abyssus.lib.core.editor.pick.LineSink
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import kotlin.math.cos
import kotlin.math.sin

private const val SEGMENTS = 64

/** The circle a terrain brush covers on [terrain] around the world point [at]: the footprint of every brush, from [BrushPath]'s own terrain-local axes. */
internal class BrushCursor(val terrain: TerrainTarget, val at: Vector3, val radius: Float) {
    fun draw(lines: LineSink) {
        if (terrain.world.det() == 0f) return
        val path = BrushPath(terrain.data.size, terrain.data.resolution, terrain.world)
        if (path.scaleX <= 0f || path.scaleZ <= 0f) return
        val centre = Vector3(path.toLocal(at))
        fun point(i: Int): Vector3? {
            val angle = i * Math.PI * 2.0 / SEGMENTS
            val x = centre.x + radius * cos(angle).toFloat() / path.scaleX
            val z = centre.z + radius * sin(angle).toFloat() / path.scaleZ
            val y = terrain.data.heightAt(x, z) ?: return null
            val p = Vector3(x, y + 0.03f, z).mul(terrain.world)
            return Vector3(p.x, p.y, p.z)
        }

        var previous = point(0)
        for (i in 1..SEGMENTS) {
            val next = point(i)
            if (previous != null && next != null) lines.line(previous, next, Color(1f, 0.8f, 0.2f, 1f))
            previous = next
        }
    }
}
