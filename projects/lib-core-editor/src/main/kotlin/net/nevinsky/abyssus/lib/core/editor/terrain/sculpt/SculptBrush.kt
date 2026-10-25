/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.brush.BrushPath
import net.nevinsky.abyssus.lib.core.editor.brush.smoothFalloff
import net.nevinsky.abyssus.lib.core.editor.brush.MaskRect
import net.nevinsky.abyssus.lib.core.editor.brush.emptyMaskRect

/**
 * The brush of the Sculpt Terrain mode: it stamps a [SculptTool] along the world-space drag path (the stamps of
 * [BrushPath]) and adds each stamp's changes to the [draft]. A vertex within the radius weighs
 * `strength * smoothstep(1 - d / radius)`.
 *
 * One stroke is one instance, like `FoliageBrush`. Pure math over the draft: it runs on the AWT thread and touches no
 * `Gdx.*`.
 */
class SculptBrush(private val draft: SculptDraft, size: Int, world: Matrix4) {
    private val path = BrushPath(size, draft.resolution, world)
    private val grid = SculptGrid(draft.resolution, size, draft.composed)
    private val footprint = SculptFootprint()
    private var changes = FloatArray(0)
    private var strokeStartHeight: Float? = null
    private var stamped = false

    /**
     * Stamps [tool] along the segment from [from] to [to] and returns the rectangle of vertices whose height changed;
     * empty when nothing did. [inverted] is the Shift state, [radius] is in world units.
     */
    fun stroke(
        from: Vector3,
        to: Vector3,
        radius: Float,
        strength: Float,
        tool: SculptTool,
        inverted: Boolean
    ): MaskRect {
        val reach = tool.reach(radius)
        if (!(strength > 0f) || !path.canStamp(reach)) {
            return emptyMaskRect()
        }
        var rect = emptyMaskRect()
        path.walk(from, to, reach) { at ->
            if (tool.singleStamp && stamped) return@walk
            stamped = true
            rect = rect.union(stamp(at, reach, strength, tool, inverted))
        }
        return rect
    }

    private fun stamp(at: Vector3, radius: Float, strength: Float, tool: SculptTool, inverted: Boolean): MaskRect {
        footprint.clear()
        path.forEachInRadius(at, radius) { gx, gz, falloff ->
            val weight = if (tool.smoothFalloff) strength * smoothFalloff(falloff) else strength
            if (weight > 0f) footprint.add(gx, gz, weight)
        }
        if (footprint.count == 0) return emptyMaskRect()
        val centre = path.toLocal(at)
        val start = strokeStartHeight ?: grid.heightAt(centre.x, centre.z).also { strokeStartHeight = it }
        val stamp = SculptStamp(
            grid, footprint, centre.x, centre.z, path.cell * path.scaleX, path.cell * path.scaleZ, start, inverted,
        )
        if (changes.size < footprint.count) changes = FloatArray(footprint.count)
        changes.fill(0f, 0, footprint.count)
        tool.changes(stamp, changes)
        var rect = emptyMaskRect()
        for (i in 0 until footprint.count) {
            val change = changes[i]
            if (change == 0f) continue
            draft.add(footprint.gx[i], footprint.gz[i], change)
            rect = rect.union(MaskRect(footprint.gx[i], footprint.gz[i], footprint.gx[i], footprint.gz[i]))
        }
        return rect
    }
}
