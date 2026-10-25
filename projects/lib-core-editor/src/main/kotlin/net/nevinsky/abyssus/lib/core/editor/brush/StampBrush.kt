/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.brush

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3

/**
 * The engine of every terrain brush: it stamps a [BrushTool] along the world-space drag path (the stamps of
 * [BrushPath]), asks the tool for the changes of the cells under each stamp from the grid as it was, and has the
 * [target] apply them. A cell within the radius weighs `strength * smoothstep(1 - d / radius)`.
 *
 * One stroke is one instance. Pure math over the target: it runs on the AWT thread and touches no `Gdx.*`.
 */
class StampBrush<G : BrushGrid>(private val target: BrushTarget<G>, world: Matrix4) {
    private val grid = target.grid
    private val path = BrushPath(grid.size, grid.resolution, world)
    private val footprint = Footprint()
    private var changes = FloatArray(0)
    private var strokeStart: Float? = null
    private var stamped = false

    /**
     * Stamps [tool] along the segment from [from] to [to] and returns the rectangle of cells that changed; empty when
     * nothing did. [reversed] is the Shift state, [radius] is in world units.
     */
    fun stroke(
        from: Vector3,
        to: Vector3,
        radius: Float,
        strength: Float,
        tool: BrushTool<G>,
        reversed: Boolean
    ): GridRect {
        val reach = tool.reach(radius)
        if (!(strength > 0f) || !path.canStamp(reach)) return emptyGridRect()
        var rect = emptyGridRect()
        path.walk(from, to, reach) { at ->
            if (tool.singleStamp && stamped) return@walk
            stamped = true
            rect = rect.union(stamp(at, reach, strength, tool, reversed))
        }
        return rect
    }

    private fun stamp(at: Vector3, radius: Float, strength: Float, tool: BrushTool<G>, reversed: Boolean): GridRect {
        footprint.clear()
        path.forEachInRadius(at, radius) { gx, gz, falloff ->
            val weight = if (tool.smoothFalloff) {
                strength * smoothFalloff(falloff)
            } else strength
            if (weight > 0f) {
                footprint.add(gx, gz, falloff, weight)
            }
        }
        if (footprint.count == 0) {
            return emptyGridRect()
        }
        val centre = path.toLocal(at)
        val start = strokeStart ?: grid.sampleAt(centre.x, centre.z).also { strokeStart = it }
        val stamp = BrushStamp(
            grid,
            footprint,
            centre.x,
            centre.z,
            path.cell * path.scaleX,
            path.cell * path.scaleZ,
            strength,
            start,
            reversed,
        )
        val channels = tool.channels
        val used = footprint.count * channels
        if (changes.size < used) {
            changes = FloatArray(used * 2)
        }
        changes.fill(0f, 0, used)
        tool.changes(stamp, changes)
        var rect = emptyGridRect()
        for (i in 0 until footprint.count) {
            if (!target.apply(footprint.gx[i], footprint.gz[i], changes, i * channels)) {
                continue
            }
            rect = rect.union(GridRect(footprint.gx[i], footprint.gz[i], footprint.gx[i], footprint.gz[i]))
        }
        return rect
    }
}
