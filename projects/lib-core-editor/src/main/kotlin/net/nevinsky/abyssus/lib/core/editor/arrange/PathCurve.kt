/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.badlogic.gdx.math.Vector3
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** How many straight pieces approximate one span between two control points. */
const val PATH_CURVE_STEPS = 16

private const val KNOT_ALPHA = 0.5
private const val KNOT_EPS = 1e-6

/**
 * The smooth line of a path arrangement: a centripetal Catmull-Rom spline that passes through every control point.
 * Fewer than three points (and an open line's ends) give straight pieces. Pure geometry, no GL.
 */
class PathCurve {
    /** Number of spans: one fewer than the points, or as many when the line closes. */
    fun spanCount(pointCount: Int, closed: Boolean): Int =
        if (pointCount < 2) {
            0
        } else if (closed && pointCount > 2) {
            pointCount
        } else pointCount - 1

    /** The control points with the smooth line between them as a polyline, ending on the last point (or the first when closed). */
    fun sample(points: List<Vector3>, closed: Boolean, steps: Int = PATH_CURVE_STEPS): List<Vector3> {
        val spans = spanCount(points.size, closed)
        if (spans == 0 || points.size < 3) return if (spans == 0) points else points.take(spans + 1)
        val out = ArrayList<Vector3>(spans * steps + 1)
        for (span in 0 until spans) for (s in 0 until steps) out += at(points, closed, span, s.toFloat() / steps)
        out += points[(spans) % points.size]
        return out
    }

    /** The point of [span] at [t] (0 at its first control point, 1 at the next). */
    fun at(points: List<Vector3>, closed: Boolean, span: Int, t: Float): Vector3 {
        val n = points.size
        val wrap = closed && n > 2
        fun control(i: Int): Vector3 = when {
            wrap -> points[((i % n) + n) % n]
            i < 0 -> reflect(points[0], points[1])
            i >= n -> reflect(points[n - 1], points[n - 2])
            else -> points[i]
        }

        val p1 = control(span)
        val p2 = control(span + 1)
        if (n < 3) {
            return p1.lerp(p2, t)
        }
        return centripetal(control(span - 1), p1, p2, control(span + 2), t)
    }

    private fun reflect(end: Vector3, next: Vector3) =
        Vector3(2 * end.x - next.x, 2 * end.y - next.y, 2 * end.z - next.z)

    private fun knot(prev: Double, a: Vector3, b: Vector3): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val dz = (b.z - a.z).toDouble()
        return prev + max(sqrt(dx * dx + dy * dy + dz * dz), KNOT_EPS).pow(KNOT_ALPHA)
    }

    /** Barry-Goldman evaluation between [p1] and [p2]. */
    private fun centripetal(p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, u: Float): Vector3 {
        val t0 = 0.0
        val t1 = knot(t0, p0, p1)
        val t2 = knot(t1, p1, p2)
        val t3 = knot(t2, p2, p3)
        val t = t1 + (t2 - t1) * u
        fun mix(a: Vector3, b: Vector3, ta: Double, tb: Double): Vector3 {
            val w = ((t - ta) / (tb - ta)).toFloat()
            return Vector3(a.x + (b.x - a.x) * w, a.y + (b.y - a.y) * w, a.z + (b.z - a.z) * w)
        }

        val a1 = mix(p0, p1, t0, t1)
        val a2 = mix(p1, p2, t1, t2)
        val a3 = mix(p2, p3, t2, t3)
        val b1 = mix(a1, a2, t0, t2)
        val b2 = mix(a2, a3, t1, t3)
        return mix(b1, b2, t1, t2)
    }
}
