/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private const val MIN_RADIUS = 1f
private const val MIN_ASPECT = 0.1f

/** The share of the view left around the framed sphere. */
private const val MARGIN = 1.15f

/** Where an orbit camera looks and from how far to show a box. */
data class Framing(val target: Vector3, val distance: Float)

/**
 * Works out how an orbit camera frames a world box: it looks at the box's center from far enough that the box's bounding
 * sphere fits the narrower side of the view with some margin. The view direction is not part of it. No GL needed.
 */
class FrameSelection {
    /**
     * [min] and [max] are the box corners; [fovDegrees] is the camera's vertical field of view and [aspect] its width over
     * height. A point-sized box is framed as a one-unit-radius sphere.
     */
    fun frame(min: Vector3, max: Vector3, fovDegrees: Float, aspect: Float): Framing {
        val center = Vector3((min.x + max.x) / 2f, (min.y + max.y) / 2f, (min.z + max.z) / 2f)
        val dx = max.x - min.x
        val dy = max.y - min.y
        val dz = max.z - min.z
        val radius = max(sqrt(dx * dx + dy * dy + dz * dz) / 2f, MIN_RADIUS)
        val vertical = Math.toRadians(fovDegrees.toDouble() / 2.0)
        val horizontal = atan(tan(vertical) * aspect.coerceAtLeast(MIN_ASPECT))
        val half = min(vertical, horizontal)
        val distance = (radius / sin(half)).toFloat() * MARGIN
        return Framing(center, distance)
    }
}
