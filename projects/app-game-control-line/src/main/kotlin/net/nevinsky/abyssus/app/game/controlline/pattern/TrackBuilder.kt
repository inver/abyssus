/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What a part of an ideal track is, for grading. */
enum class Tag { BASE, PARALLEL, VERTICAL, LEG, CORNER, ARC, OVER_TOP, CLIMB, GLIDE, ROLL, CONNECT }

/**
 * A point of an ideal track: the unit direction [u] from the hemisphere's centre, what it is ([tag]), how far along the
 * track it is ([progress], 0..1 by length), whether the plane flies inverted there, the repeat it belongs to, and, for
 * the glide and the take-off, the height above the ground (m), or NaN.
 */
class IdealPoint(val u: Vector3, val tag: Tag, val progress: Float, val inverted: Boolean, val repeat: Int)

/**
 * An ideal track: [points] in order, with the index at which each of the [repeats] starts. The points before
 * [judgedFrom] are the way in from the base, and those from [judgedTo] the way out, which are flown but not judged.
 */
class IdealTrack(
    val points: List<IdealPoint>, val repeatStarts: List<Int>, val judgedFrom: Int = 0, val judgedTo: Int = points.size,
    /** The place the figure passes more than once, where it must stay in one place, or null. */
    val crossing: Vector3? = null,
    /** Whether the repeats are on top of each other (the clover's leaves are not). */
    val superimposed: Boolean = true,
) {
    val repeats: Int get() = repeatStarts.size

    /** The track without its lead-in and lead-out. */
    fun judged(): IdealTrack =
        if (judgedFrom == 0 && judgedTo == points.size) this else IdealTrack(points.subList(judgedFrom, judgedTo), repeatStarts.map { max(0, it - judgedFrom) }, 0, judgedTo - judgedFrom, crossing, superimposed)

    /** The points of repeat [r]. */
    fun repeat(r: Int): List<IdealPoint> = points.subList(repeatStarts[r], if (r + 1 < repeatStarts.size) repeatStarts[r + 1] else points.size)
}

/** A piece of path between two corners: dense unit vectors, and the [tag] they get. */
class Edge(val points: List<Vector3>, val tag: Tag, val inverted: Boolean = false)

/** The angular step (degrees) of the ideal tracks' points. */
const val IDEAL_STEP = 0.4f

private const val D2R = MathUtils.degreesToRadians

/** The unit vector at [latitude] and [azimuth] degrees. */
fun place(latitude: Float, azimuth: Float): Vector3 {
    val lat = latitude * D2R
    val az = azimuth * D2R
    return Vector3(cos(lat) * cos(az), sin(lat), -cos(lat) * sin(az))
}

/** The edge along the meridian [azimuth] from latitude [from] to [to]. */
fun meridian(azimuth: Float, from: Float, to: Float, tag: Tag = Tag.VERTICAL): Edge {
    val n = max(2, ceil(abs(to - from) / IDEAL_STEP).toInt() + 1)
    return Edge(List(n) { place(from + (to - from) * it / (n - 1), azimuth) }, tag)
}

/** The edge along the parallel [latitude] from azimuth [from] to [to]. */
fun parallel(latitude: Float, from: Float, to: Float, tag: Tag = Tag.PARALLEL): Edge {
    val n = max(2, ceil(abs(to - from) * cos(latitude * D2R) / IDEAL_STEP).toInt() + 1)
    return Edge(List(n) { place(latitude, from + (to - from) * it / (n - 1)) }, tag)
}

/** The edge along the great circle from [a] to [b] (the shorter way). */
fun greatCircle(a: Vector3, b: Vector3, tag: Tag): Edge {
    val omega = acos(a.dot(b).coerceIn(-1f, 1f))
    val n = max(2, ceil(omega / D2R / IDEAL_STEP).toInt() + 1)
    val sinOmega = sin(omega)
    return Edge(List(n) {
        val t = it / (n - 1f)
        if (omega < 1e-5f) Vector3(a) else Vector3(a).scl(sin((1f - t) * omega) / sinOmega).mulAdd(b, sin(t * omega) / sinOmega).nor()
    }, tag)
}

/**
 * The edge round the small circle about [center] of angular radius [rho] degrees, from the point [start] on it, turning
 * left (counterclockwise seen from outside) by [sweep] degrees, or right when [sweep] is negative.
 */
fun circleArc(center: Vector3, rho: Float, start: Vector3, sweep: Float, tag: Tag = Tag.ARC): Edge {
    val n = max(2, ceil(abs(sweep) * sin(rho * D2R) / IDEAL_STEP).toInt() + 1)
    return Edge(List(n) { Vector3(start).rotateRad(center, sweep * D2R * it / (n - 1)).nor() }, tag)
}

/**
 * The tangent direction at the end of [points] (unit, in the tangent plane at the last point) from its last two points.
 */
private fun endTangent(points: List<Vector3>): Vector3 = tangent(points[points.size - 2], points.last())

private fun startTangent(points: List<Vector3>): Vector3 = tangent(points[0], points[1])

private fun tangent(a: Vector3, b: Vector3): Vector3 {
    val mid = Vector3(a).add(b).nor()
    val chord = Vector3(b).sub(a)
    return chord.mulAdd(mid, -chord.dot(mid)).nor()
}

/** The points of [points] after dropping the first [length] radians of arc, with the first one replaced by the cut. */
private fun dropStart(points: List<Vector3>, length: Float): List<Vector3> {
    var travelled = 0f
    for (i in 1 until points.size) {
        val step = acos(points[i - 1].dot(points[i]).coerceIn(-1f, 1f))
        if (travelled + step >= length) {
            val t = if (step > 0f) (length - travelled) / step else 0f
            val cut = Vector3(points[i - 1]).lerp(points[i], t).nor()
            return listOf(cut) + points.subList(i, points.size)
        }
        travelled += step
    }
    return listOf(points.last())
}

private fun length(points: List<Vector3>): Float {
    var sum = 0f
    for (i in 1 until points.size) sum += acos(points[i - 1].dot(points[i]).coerceIn(-1f, 1f))
    return sum
}

/** The points of a [rounded] path, with where each edge's corner begins: `junctionStart[j]` is the index of the first point of the corner before edge `j` (0 for the first edge). */
class RoundedPath(val points: List<Pair<Vector3, Tag>>, val junctionStart: IntArray, val edgeStart: IntArray)

/**
 * A path of [edges], one after the other, with the corners between them rounded to [cornerRadius] degrees (design
 * decision 4: corners are ideal at a 1 m radius). A corner is a quadratic curve through the old vertex, which cuts each
 * edge by `d = r sin(t/2) / cos²(t/2)` for a turn of `t`. Edges meet where the one before ends. Returns the points tagged.
 */
fun rounded(edges: List<Edge>, cornerRadius: Float, cornerTag: Tag = Tag.CORNER): RoundedPath {
    val cut = edges.map { it.points }.toMutableList()
    val arcs = HashMap<Int, List<Vector3>>()
    for (j in 0 until edges.size - 1) {
        val a = cut[j]
        val b = cut[j + 1]
        val vertex = a.last()
        val tIn = endTangent(a)
        val tOut = startTangent(b)
        val theta = abs(atan2(Vector3(tIn).crs(tOut).dot(vertex), tIn.dot(tOut)))
        if (theta < 3f * D2R || cornerRadius <= 0f) continue
        var d = cornerRadius * D2R * sin(theta / 2f) / (cos(theta / 2f) * cos(theta / 2f))
        d = min(d, 0.45f * min(length(a), length(b)))
        val trimmedA = dropEnd(a, d)
        val trimmedB = dropStart(b, d)
        val p0 = trimmedA.last()
        val p1 = trimmedB.first()
        val steps = max(6, ceil(theta / D2R / 8f).toInt())
        arcs[j] = List(steps - 1) {
            val s = (it + 1f) / steps
            Vector3(p0).scl((1 - s) * (1 - s)).mulAdd(vertex, 2 * s * (1 - s)).mulAdd(p1, s * s).nor()
        }
        cut[j] = trimmedA
        cut[j + 1] = trimmedB
    }
    val out = ArrayList<Pair<Vector3, Tag>>()
    val starts = IntArray(edges.size)
    val edgeStarts = IntArray(edges.size)
    for (j in edges.indices) {
        if (j > 0) starts[j] = out.size - (arcs[j - 1]?.size ?: 0)
        edgeStarts[j] = out.size
        for (p in cut[j]) out += p to edges[j].tag
        arcs[j]?.forEach { out += it to cornerTag }
    }
    return RoundedPath(out, starts, edgeStarts)
}

/** [path]'s points from its first corner to its last (the lead-in and lead-out edges dropped), with the junction starts moved to match. */
fun RoundedPath.trimmedToCorners(): RoundedPath {
    val first = points.indexOfFirst { it.second == Tag.CORNER }
    val last = points.indexOfLast { it.second == Tag.CORNER }
    if (first < 0) return this
    return RoundedPath(points.subList(first, last + 1), IntArray(junctionStart.size) { max(0, junctionStart[it] - first) }, IntArray(edgeStart.size) { max(0, edgeStart[it] - first) })
}

private fun dropEnd(points: List<Vector3>, length: Float): List<Vector3> = dropStart(points.asReversed(), length).asReversed()

/**
 * Collects an ideal track's points from edges and arcs, remembering the plane's orientation and the repeat each belongs
 * to. [build] numbers the progress by length.
 */
class TrackBuilder {
    private val points = ArrayList<Triple<Vector3, Tag, Boolean>>()
    private val starts = ArrayList<Int>()

    /** Whether the plane flies inverted from now on. */
    var inverted = false

    /** The index of the first judged point; the points before it are the lead-in. */
    var judgedFrom = 0

    /** The index after the last judged point; the points from it are the lead-out. -1 for the end of the track. */
    var judgedTo = -1

    /** The place the figure crosses itself, or null. */
    var crossing: Vector3? = null

    /** Whether the repeats are on top of each other. */
    var superimposed = true

    /** The last point. */
    val end: Vector3 get() = points.last().first

    /** The tangent at the end (the way on). */
    val heading: Vector3 get() = tangent(points[points.size - 2].first, points.last().first)

    /** The number of points so far. */
    val size: Int get() = points.size

    /** Starts a new repeat at the next point. */
    fun repeat() = markRepeat(points.size)

    /** Starts a new repeat at point [index]. */
    fun markRepeat(index: Int) {
        starts += index
    }

    /** Adds [items] (a point equal to the last is skipped). */
    fun add(items: List<Pair<Vector3, Tag>>) {
        for ((u, tag) in items) {
            if (points.isNotEmpty() && points.last().first.dst2(u) < 1e-10f) continue
            points += Triple(Vector3(u), tag, inverted)
        }
    }

    /** Adds the [edges] with rounded corners; returns the [RoundedPath] and its first index in the track. */
    fun path(edges: List<Edge>, cornerRadius: Float): Pair<RoundedPath, Int> {
        val path = rounded(edges, cornerRadius)
        val first = points.size
        add(path.points)
        return path to first
    }

    fun edge(edge: Edge) = add(edge.points.map { it to edge.tag })

    fun build(): IdealTrack {
        val cumulative = FloatArray(points.size)
        for (i in max(1, judgedFrom + 1) until points.size) cumulative[i] = cumulative[i - 1] + acos(points[i - 1].first.dot(points[i].first).coerceIn(-1f, 1f))
        val total = max(cumulative.last(), 1e-6f)
        val marks = starts.sorted().ifEmpty { listOf(0) }
        return IdealTrack(
            points.mapIndexed { i, (u, tag, inverted) -> IdealPoint(u, tag, cumulative[i] / total, inverted, max(0, marks.count { it <= i } - 1)) },
            marks,
            judgedFrom,
            if (judgedTo < 0) points.size else judgedTo,
            crossing,
            superimposed,
        )
    }
}
