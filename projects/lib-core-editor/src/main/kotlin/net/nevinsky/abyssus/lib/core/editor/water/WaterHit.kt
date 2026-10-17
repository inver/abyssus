/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.water

import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.water.WaterSettings

/** The surface a ray meets first and how far along the ray that is. */
data class WaterPick(val id: String, val distance: Float)

/**
 * Where a ray meets a water surface, on the CPU. A surface is its bounded rectangle at the mean level (waves are not
 * followed), seen from above only: a ray from below or level with it, a surface that is hidden, and a ray parallel to
 * the surface or beyond [maxDistance] meet nothing. No GL needed.
 */
class WaterHit {
    /** The distance along [ray] to [surface]'s rectangle, or null when it is not met. */
    fun distance(ray: Ray, surface: WaterSettings, maxDistance: Float = Float.MAX_VALUE): Float? {
        if (!surface.enabled) {
            return null
        }
        val dy = ray.direction.y
        if (dy > -PARALLEL) {
            return null
        }
        val dropToSurface = ray.origin.y - surface.level
        if (dropToSurface <= 0.0) {
            return null
        }
        val t = (dropToSurface / -dy).toFloat()
        if (!t.isFinite() || t > maxDistance) {
            return null
        }
        val x = ray.origin.x + ray.direction.x * t
        val z = ray.origin.z + ray.direction.z * t
        if (kotlin.math.abs(x - surface.x) > surface.width / 2 || kotlin.math.abs(z - surface.z) > surface.length / 2) {
            return null
        }
        return t
    }

    /** The nearest of [surfaces] met by [ray]; the first listed wins a tie. */
    fun nearest(ray: Ray, surfaces: List<WaterSettings>, maxDistance: Float = Float.MAX_VALUE): WaterPick? {
        var best: WaterPick? = null
        for (surface in surfaces) {
            val d = distance(ray, surface, maxDistance) ?: continue
            if (best == null || d < best.distance) best = WaterPick(surface.id, d)
        }
        return best
    }
}

/** Rays flatter than this are treated as parallel to the surface. */
private const val PARALLEL = 1e-5f

/** A water move shown before it is written: the new centre of each surface being moved. */
class WaterPreview {
    /** [water] with the previewed surfaces at their new centre; invalid rows are left as they are. */
    fun apply(water: List<WaterSurface>, moved: Map<String, Vec3>): List<WaterSurface> =
        if (moved.isEmpty()) water else water.map { surface ->
            val at = moved[surface.id]
            if (surface is ValidWater && at != null) ValidWater(
                surface.settings.copy(
                    x = at.x.toDouble(),
                    level = at.y.toDouble(),
                    z = at.z.toDouble()
                )
            )
            else surface
        }
}
