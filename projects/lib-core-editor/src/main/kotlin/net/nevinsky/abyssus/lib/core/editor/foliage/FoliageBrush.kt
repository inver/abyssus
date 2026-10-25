/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.editor.brush.MaskRect
import net.nevinsky.abyssus.lib.core.editor.brush.emptyMaskRect
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.brush.BrushPath
import net.nevinsky.abyssus.lib.core.editor.brush.smoothFalloff
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a stroke does to the mask values: [Paint] raises them, [Erase] lowers them. */
enum class FoliageBrushMode { Paint, Erase }

/** The falloff units of one stamp: it adds `strength * 64 * smoothstep(1 - d / radius)` at the distance d. */
private const val BRUSH_UNITS: Float = 64f

/**
 * The brush of the Paint Foliage mode (design decision 8): it stamps along the world-space drag path at intervals of
 * a quarter of the radius, each stamp adding `±strength * 64 * smoothstep(1 - d / radius)` to the mask texels
 * within the radius and clamping the result to 0 through 255.
 *
 * The mask lives in terrain-local space: a stamp's centre goes through the inverse of the entity's [world] matrix,
 * and the distance `d` is measured through the entity's scale along the terrain's own axes. For the translation,
 * yaw rotation and scaling terrain entities are built from, that is exactly the world-space distance on the terrain
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
    private val path = BrushPath(terrain.size, maskResolution, world)

    init {
        require(maskResolution >= 2) { "mask resolution needs at least 2" }
    }

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
    ): MaskRect {
        require(mask.size == maskResolution * maskResolution) { "a $maskResolution by $maskResolution mask" }
        if (!(strength > 0f) || !path.canStamp(radius)) return emptyMaskRect()
        var rect = emptyMaskRect()
        path.walk(from, to, radius) { at -> rect = rect.union(stamp(mask, at, radius, strength, mode)) }
        return rect
    }

    /** One stamp at the world point [at]: the changed texels inside the radius, clamped and reported as a rectangle. */
    private fun stamp(mask: ByteArray, at: Vector3, radius: Float, strength: Float, mode: FoliageBrushMode): MaskRect {
        var minX = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = -1
        var maxZ = -1
        path.forEachInRadius(at, radius) { gx, gz, falloff ->
            val delta = (strength * BRUSH_UNITS * smoothFalloff(falloff)).roundToInt()
            if (delta == 0) return@forEachInRadius
            val index = gz * maskResolution + gx
            val old = mask[index].toInt() and 0xFF
            val value = when (mode) {
                FoliageBrushMode.Paint -> min(255, old + delta)
                FoliageBrushMode.Erase -> max(0, old - delta)
            }
            if (value == old) return@forEachInRadius
            mask[index] = value.toByte()
            if (gx < minX) minX = gx
            if (gz < minZ) minZ = gz
            if (gx > maxX) maxX = gx
            if (gz > maxZ) maxZ = gz
        }
        return if (minX > maxX) emptyMaskRect() else MaskRect(minX, minZ, maxX, maxZ)
    }
}
