/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    private val terrain: TerrainData,
    private val maskResolution: Int,
    world: Matrix4,
) {
    private val toLocal = Matrix4(world).inv()

    /** The world length of one terrain-local x (and z) unit: the entity's scale along the terrain's own axes. */
    private val scaleX = Vector3(1f, 0f, 0f).rot(world).len()
    private val scaleZ = Vector3(0f, 0f, 1f).rot(world).len()

    private val point = Vector3()
    private val local = Vector3()

    /** Distance since the last stamp, in world units; the press itself is always a stamp. */
    private var sinceLastStamp = 0f
    private var pressed = false

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
        if (!(radius > 0f) || !(strength > 0f) || !(scaleX > 0f) || !(scaleZ > 0f)) return emptyMaskRect()
        var rect = emptyMaskRect()
        if (!pressed) {
            pressed = true
            sinceLastStamp = 0f
            rect = rect.union(stamp(mask, point.set(from), radius, strength, mode))
        }
        val spacing = radius / 4f
        val distance = from.dst(to)
        var along = spacing - sinceLastStamp
        var lastStamp = Float.NaN
        while (along <= distance) {
            lastStamp = along
            rect = rect.union(stamp(mask, point.set(from).lerp(to, along / distance), radius, strength, mode))
            along += spacing
        }
        sinceLastStamp = if (lastStamp.isNaN()) sinceLastStamp + distance else distance - lastStamp
        return rect
    }

    /** One stamp at the world point [at]: the changed texels inside the radius, clamped and reported as a rectangle. */
    private fun stamp(mask: ByteArray, at: Vector3, radius: Float, strength: Float, mode: FoliageBrushMode): MaskRect {
        val centre = local.set(at).mul(toLocal)
        val cell = terrain.size.toFloat() / (maskResolution - 1)
        val fromX = max(0, floor((centre.x - radius / scaleX) / cell).toInt())
        val toX = min(maskResolution - 1, ceil((centre.x + radius / scaleX) / cell).toInt())
        val fromZ = max(0, floor((centre.z - radius / scaleZ) / cell).toInt())
        val toZ = min(maskResolution - 1, ceil((centre.z + radius / scaleZ) / cell).toInt())
        var minX = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = -1
        var maxZ = -1
        for (gz in fromZ..toZ) {
            for (gx in fromX..toX) {
                val dx = scaleX * (gx * cell - centre.x)
                val dz = scaleZ * (gz * cell - centre.z)
                val distance = sqrt(dx * dx + dz * dz)
                if (distance >= radius) continue
                val falloff = 1f - distance / radius
                val delta = (strength * BRUSH_UNITS * falloff * falloff * (3f - 2f * falloff)).roundToInt()
                if (delta == 0) continue
                val index = gz * maskResolution + gx
                val old = mask[index].toInt() and 0xFF
                val value = when (mode) {
                    FoliageBrushMode.Paint -> min(255, old + delta)
                    FoliageBrushMode.Erase -> max(0, old - delta)
                }
                if (value == old) continue
                mask[index] = value.toByte()
                if (gx < minX) minX = gx
                if (gz < minZ) minZ = gz
                if (gx > maxX) maxX = gx
                if (gz > maxZ) maxZ = gz
            }
        }
        return if (minX > maxX) emptyMaskRect() else MaskRect(minX, minZ, maxX, maxZ)
    }
}
