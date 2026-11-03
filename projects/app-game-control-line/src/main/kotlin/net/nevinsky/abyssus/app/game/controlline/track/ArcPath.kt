/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.app.game.controlline.track

import com.badlogic.gdx.math.Vector3
import kotlin.math.acos

/** The arc in radians between the unit vectors [a] and [b]. */
internal fun arcAngle(a: Vector3, b: Vector3): Float = acos(a.dot(b).coerceIn(-1f, 1f))

/** The arc length in radians of the path through [points] (unit vectors). */
internal fun arcLength(points: List<Vector3>): Float {
    var sum = 0f
    for (i in 1 until points.size) sum += arcAngle(points[i - 1], points[i])
    return sum
}

/** [points] without their first [length] radians of arc, the cut put in as the first point. */
internal fun dropStart(points: List<Vector3>, length: Float): List<Vector3> {
    var travelled = 0f
    for (i in 1 until points.size) {
        val step = arcAngle(points[i - 1], points[i])
        if (travelled + step >= length) {
            val t = if (step > 0f) (length - travelled) / step else 0f
            return listOf(Vector3(points[i - 1]).lerp(points[i], t).nor()) + points.subList(i, points.size)
        }
        travelled += step
    }
    return listOf(points.last())
}

/** [points] without their last [length] radians of arc, the cut put in as the last point. */
internal fun dropEnd(points: List<Vector3>, length: Float): List<Vector3> = dropStart(points.asReversed(), length).asReversed()
