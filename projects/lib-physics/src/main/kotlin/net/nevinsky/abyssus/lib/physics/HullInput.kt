/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
  * The most points a convex hull collider is built from (design decision D11 of
  * `improve-control-line-performance-stability`):
 * a hull's build time then does not grow with the model's vertex count. Jolt keeps at most 256 hull vertices anyway.
 */
const val MAX_HULL_INPUT = 1024

private const val GOLDEN_ANGLE = 2.3999632f

/**
 * At most [max] of [points] that span their convex hull: [points] themselves when there are no more than [max];
 * otherwise, for each of [max] directions spread evenly over the sphere (a Fibonacci lattice), the point farthest along
 * it. Every point kept is a vertex of the hull of [points], so the hull of the result lies inside it and touches it in
 * every sampled direction. Kept in the order of [points], and deterministic: the same points give the same result.
 */
fun hullInput(points: List<Vector3>, max: Int = MAX_HULL_INPUT): List<Vector3> {
    require(max >= 4) { "a hull needs at least 4 points" }
    if (points.size <= max) {
        return points
    }
    val n = points.size
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    val zs = FloatArray(n)
    for (i in 0 until n) {
        val p = points[i]
        xs[i] = p.x; ys[i] = p.y; zs[i] = p.z
    }
    val kept = BooleanArray(n)
    for (d in 0 until max) {
        val y = 1f - 2f * (d + 0.5f) / max
        val r = sqrt(1f - y * y)
        val phi = d * GOLDEN_ANGLE
        val x = cos(phi) * r
        val z = sin(phi) * r
        var best = 0
        var bestDot = Float.NEGATIVE_INFINITY
        for (i in 0 until n) {
            val dot = xs[i] * x + ys[i] * y + zs[i] * z
            if (dot > bestDot) {
                bestDot = dot
                best = i
            }
        }
        kept[best] = true
    }
    return points.filterIndexed { i, _ -> kept[i] }
}
