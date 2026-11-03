/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.brush.BrushStamp
import net.nevinsky.abyssus.lib.core.editor.brush.BrushTarget
import net.nevinsky.abyssus.lib.core.editor.brush.BrushTool
import net.nevinsky.abyssus.lib.core.editor.brush.ByteGrid
import net.nevinsky.abyssus.lib.core.editor.brush.GridRect
import net.nevinsky.abyssus.lib.core.editor.brush.StampBrush
import net.nevinsky.abyssus.lib.core.editor.brush.smoothFalloff
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a stroke does to the mask values: [Paint] raises them, [Erase] lowers them. */
enum class FoliageBrushMode { Paint, Erase }

/** The falloff units of one stamp: it adds `strength * 64 * smoothstep(1 - d / radius)` at the distance d. */
private const val BRUSH_UNITS: Float = 64f

/**
 * The tool of the Paint Foliage mode: each texel within the radius gets `strength * 64 * smoothstep(1 - d / radius)`
 * (design decision 8), negated while the stamp is reversed (Erase). The product is formed in this order, because the
 * target rounds it and a different order can move a half-way value to the other side.
 */
internal class PaintMaskTool : BrushTool<ByteGrid> {
    override fun changes(stamp: BrushStamp<ByteGrid>, out: FloatArray) {
        val f = stamp.footprint
        for (i in 0 until f.count) {
            val delta = stamp.strength * BRUSH_UNITS * smoothFalloff(f.falloff[i])
            out[i] = if (stamp.reversed) -delta else delta
        }
    }
}

/**
 * The mask of one density layer as what a stroke changes: the change of a texel is rounded in magnitude (half up) and
 * then applied with its sign, added or subtracted, and the result clamped to 0 through 255. A texel whose byte stays
 * the same reports no change.
 */
internal class MaskTarget(override val grid: ByteGrid) : BrushTarget<ByteGrid> {
    override fun apply(gx: Int, gz: Int, changes: FloatArray, offset: Int): Boolean {
        val change = changes[offset]
        val delta = abs(change).roundToInt()
        if (delta == 0) return false
        val index = gz * grid.resolution + gx
        val old = grid.values[index].toInt() and 0xFF
        val value = if (change > 0f) min(255, old + delta) else max(0, old - delta)
        if (value == old) return false
        grid.values[index] = value.toByte()
        return true
    }
}

/**
 * The brush of the Paint Foliage mode (design decision 8): a [StampBrush] over a density mask that stamps along the
 * world-space drag path at intervals of a quarter of the radius, each stamp adding `±strength * 64 * smoothstep(1 - d /
 * radius)` to the mask texels within the radius and clamping the result to 0 through 255.
 *
 * The mask lives in terrain-local space: a stamp's centre goes through the inverse of the entity's [world] matrix,
 * and the distance `d` is measured through the entity's scale along the terrain's own axes. For the translation, yaw
 * rotation and scaling terrain entities are built from, that is exactly the world-space distance on the terrain
 * plane, so a rotated or scaled entity paints under the world-space circle the view draws.
 *
 * Pure math over [TerrainData] and the mask: it runs on the AWT thread and touches no `Gdx.*`.
 *
 * One stroke is one instance: [stroke] is called once per drag segment and keeps the stamp spacing across the calls,
 * so a spot is stamped once however finely the drag is sampled. The mask is edited in place.
 */
class FoliageBrush(
    terrain: TerrainData,
    private val maskResolution: Int,
    world: Matrix4,
) {
    private val grid = ByteGrid(maskResolution, terrain.size, 1, ByteArray(0))
    private val tool = PaintMaskTool()
    private val brush = StampBrush(MaskTarget(grid), world)

    /**
     * Stamps along the segment from [from] to [to] (world-space stroke points) and returns the rectangle of mask
     * texels whose values changed; a stroke that changes nothing (an [FoliageBrushMode.Erase] over a zero mask)
     * reports an empty rectangle. [radius] is in world units, [strength] scales every stamp's falloff.
     */
    fun stroke(
        mask: ByteArray,
        from: Vector3,
        to: Vector3,
        radius: Float,
        strength: Float,
        mode: FoliageBrushMode,
    ): GridRect {
        require(mask.size == maskResolution * maskResolution) { "a $maskResolution by $maskResolution mask" }
        grid.values = mask
        return brush.stroke(from, to, radius, strength, tool, mode == FoliageBrushMode.Erase)
    }
}
