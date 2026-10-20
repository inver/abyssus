/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.pattern.place
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import net.nevinsky.abyssus.lib.core.util.azimuthOf

/**
 * Where the course wants the plane, at the place nearest to it: the [point] (unit), the course's direction there
 * ([tangent], unit, in the tangent plane at [point]) and its geodesic [curvature] [lead] metres on (1/m, positive turning
 * left seen from outside, which is a pull).
 */
class Aim(val point: Vector3, val tangent: Vector3, val curvature: Float)

/** A course on the flight hemisphere of radius [Course.radius] the pilot steers along. */
sealed interface Course {
    val radius: Float

    /** The aim for a plane at [u] (unit, from the hemisphere's centre) flying at [speed] m/s, curvature looked up [lead] metres on. */
    fun aim(u: Vector3, lead: Float): Aim
}

/**
 * Level flight along the parallel at [latitude] degrees, anticlockwise seen from above (the flying direction, upright)
 * or [clockwise] (inverted), without end.
 */
class Parallel(override val radius: Float, val latitude: Float, val clockwise: Boolean = false) : Course {
    override fun aim(u: Vector3, lead: Float): Aim {
        val azimuth = azimuthOf(u.x, u.z)
        val point = place(latitude, azimuth)
        val a = Math.toRadians(azimuth.toDouble()).toFloat()
        val east = Vector3(-kotlin.math.sin(a), 0f, -kotlin.math.cos(a))
        if (clockwise) east.scl(-1f)
        // a parallel north of the base bends towards the pole: left going anticlockwise, right going clockwise
        val curvature = tan(Math.toRadians(latitude.toDouble())).toFloat() / radius
        return Aim(point, east, if (clockwise) -curvature else curvature)
    }
}

/**
 * A course along [points] (unit vectors, dense, in flying order) that is flown once. The place the plane is matched to
 * only moves on, and only by a bounded look-ahead, so a crossing or a repeat of the same circle never jumps to a later
 * pass ([progress]).
 */
class PathCourse(override val radius: Float, points: List<Vector3>) : Course {
    val points: List<Vector3> = points.map { Vector3(it).nor() }

    /** Metres along the path to each point. */
    val distance: FloatArray = FloatArray(this.points.size)

    private val tangents: List<Vector3>
    private val curvatures: FloatArray

    init {
        require(this.points.size >= 3) { "a course needs at least 3 points" }
        for (i in 1 until this.points.size) distance[i] = distance[i - 1] + angle(this.points[i - 1], this.points[i]) * radius
        tangents = this.points.indices.map { i ->
            val a = this.points[max(0, i - 1)]
            val b = this.points[min(this.points.size - 1, i + 1)]
            tangentAt(this.points[i], Vector3(b).sub(a))
        }
        // the turn about each point over a window of about a metre each way, so the corners' few points do not spike it
        curvatures = FloatArray(this.points.size) { i ->
            val back = indexAt(distance[i] - CURVATURE_WINDOW)
            val ahead = indexAt(distance[i] + CURVATURE_WINDOW)
            val span = distance[ahead] - distance[back]
            if (span < 1e-4f) 0f else turn(tangents[back], tangents[ahead], this.points[i]) / span
        }
    }

    /** The index of the last point the plane has been matched to. */
    var progress = 0
        private set

    val length: Float get() = distance.last()

    /** Metres flown along the course so far. */
    val travelled: Float get() = distance[progress]

    /** Metres of course left. */
    val remaining: Float get() = length - travelled

    override fun aim(u: Vector3, lead: Float): Aim {
        val limit = distance[progress] + SEARCH_AHEAD
        var best = progress
        var closest = -2f
        var i = progress
        while (i < points.size && distance[i] <= limit) {
            val dot = points[i].dot(u)
            if (dot > closest) {
                closest = dot
                best = i
            }
            i++
        }
        progress = best
        val ahead = indexAt(distance[best] + lead)
        return Aim(Vector3(points[best]), Vector3(tangents[best]), curvatures[ahead])
    }

    /** The index of the point at [metres] along the course, clamped to its ends. */
    fun indexAt(metres: Float): Int {
        if (metres <= 0f) return 0
        if (metres >= distance.last()) return points.size - 1
        var low = 0
        var high = points.size - 1
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (distance[mid] <= metres) low = mid else high = mid
        }
        return low
    }

    private companion object {
        /** How far ahead of the last match (m) the next may be found. */
        const val SEARCH_AHEAD = 4f

        const val CURVATURE_WINDOW = 0.6f
    }
}

/** The angle (rad) between unit vectors [a] and [b]. */
internal fun angle(a: Vector3, b: Vector3): Float = acos(a.dot(b).coerceIn(-1f, 1f))

/** [direction] projected into the tangent plane at [u] and normalised (a new vector). */
internal fun tangentAt(u: Vector3, direction: Vector3): Vector3 = Vector3(direction).mulAdd(u, -direction.dot(u)).nor()

/** The signed angle (rad) from [from] to [to] about [axis]: positive turning left seen from outside. */
internal fun turn(from: Vector3, to: Vector3, axis: Vector3): Float = atan2(Vector3(from).crs(to).dot(axis), from.dot(to))

/** The latitude (degrees) of unit vector [u]. */
internal fun latitudeOf(u: Vector3): Float = Math.toDegrees(asin(u.y.coerceIn(-1f, 1f)).toDouble()).toFloat()
