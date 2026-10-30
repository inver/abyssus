/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.lib.core.util.wrapDegrees

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import net.nevinsky.abyssus.lib.core.util.azimuthRadiansOf
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private const val D2R = MathUtils.degreesToRadians

/** The angular radius of a loop (degrees): circles between the base and the 45° parallel (design decision 4). */
const val LOOP_RADIUS = 22.5f

/** The azimuth (degrees) between the centre of a loop and its first vertical, the circle's east-most point. */
val LOOP_VERTICAL_OFFSET: Float = asin(sin(LOOP_RADIUS * D2R) / cos(LOOP_RADIUS * D2R)) / D2R

/** A square loop is this many degrees of azimuth wide: ⅛ lap. */
const val SQUARE_WIDTH = 45f

/** Where a triangle's legs lean 30° off vertical, its base is this wide (degrees of azimuth): `2 asin(tan 30°)`. */
val TRIANGLE_BASE: Float = 2f * asin(tan(30f * D2R)) / D2R

/** The stubs before the first corner and after the last are this long (degrees), so those corners round like the others. */
private const val LEAD = 20f

/** The leaves of the four-leaf clover are circles of this angular radius (degrees). */
const val CLOVER_RADIUS = 15f

/**
 * The ideal tracks of the F2B schedule (design decision 4): the polyline of directions each manoeuvre's F2B description
 * defines, placed at an anchor azimuth the matcher takes from the flown track. They are built here for a hemisphere of
 * [radius] metres, which sets the 1 m corners and the height of the ground. All angles are degrees. Every figure turns
 * left (seen from outside); the outside figures are the same shapes flown inverted, so their left turns are outside turns.
 *
 * Each function says what its anchor is; the shapes are this game's reading of the F2B descriptions and are documented
 * in the README.
 */
class IdealTracks(val radius: Float, cornerMetres: Float = 1f) {
    /** The corners' radius (degrees): 1 m unless a test flies rounder ones. */
    private val corner = (cornerMetres / radius) / D2R
    private val sphere = HemisphereGeometry(radius)

    /** The latitude of the ground under the plane (degrees): the plane on the ground is [BASE_HEIGHT] below the base. */
    val groundLatitude: Float = -asin((BASE_HEIGHT / radius).coerceIn(0f, 1f)) / D2R

    /** Take-off from the release point at azimuth [release]: a ground roll of [roll] metres, a climb to the base over the release point one lap on, then 2 laps at the base. */
    fun takeoff(release: Float, roll: Float = 8f): IdealTrack {
        val b = TrackBuilder()
        val rolled = (roll / radius) / D2R
        b.edge(parallel(groundLatitude, release, release + rolled, Tag.ROLL))
        val climb = ArrayList<Pair<Vector3, Tag>>()
        val n = 360
        for (i in 1..n) {
            val t = i / n.toFloat()
            climb += place(groundLatitude * (1f - t), release + rolled + (360f - rolled) * t) to Tag.CLIMB
        }
        b.add(climb)
        b.edge(parallel(0f, release + 360f, release + 1080f, Tag.BASE))
        return b.build()
    }

    /** The reverse wingover whose first climb is at azimuth [climb]: up the meridian over the zenith and down the other side, half a lap inverted at the base, and back over the zenith. */
    fun reverseWingover(climb: Float): IdealTrack {
        val edges = listOf(
            parallel(0f, climb - LEAD, climb, Tag.BASE),
            meridian(climb, 0f, 90f),
            meridian(climb + 180f, 90f, 0f, Tag.OVER_TOP),
            parallel(0f, climb + 180f, climb + 360f, Tag.BASE),
            meridian(climb, 0f, 90f),
            meridian(climb + 180f, 90f, 0f, Tag.OVER_TOP),
            parallel(0f, climb + 180f, climb + 180f + LEAD, Tag.BASE),
        )
        val path = rounded(edges, corner).trimmedToCorners()
        val b = TrackBuilder()
        // inverted along the base between the two passes
        val from = path.junctionStart[3]
        val to = path.junctionStart[4]
        b.add(path.points.subList(0, from))
        b.inverted = true
        b.add(path.points.subList(from, to))
        b.inverted = false
        b.add(path.points.subList(to, path.points.size))
        return b.build()
    }

    private fun loopCentre(vertical: Float) = place(LOOP_RADIUS, vertical - LOOP_VERTICAL_OFFSET)

    /** [count] loops (inside, or outside when [inverted]) whose first vertical, the circle's east-most point, is at azimuth [vertical]. They start and end at the circle's bottom, on the base. */
    fun loops(vertical: Float, count: Int, inverted: Boolean = false): IdealTrack {
        val b = TrackBuilder()
        b.inverted = inverted
        val centre = loopCentre(vertical)
        val bottom = place(0f, vertical - LOOP_VERTICAL_OFFSET)
        repeat(count) {
            b.repeat()
            b.edge(circleArc(centre, LOOP_RADIUS, bottom, 360f))
        }
        return b.build()
    }

    /** [laps] laps of level inverted flight at the base, from azimuth [from]. */
    fun invertedLaps(from: Float, laps: Int = 2): IdealTrack {
        val b = TrackBuilder()
        b.inverted = true
        for (lap in 0 until laps) {
            b.repeat()
            b.edge(parallel(0f, from + 360f * lap, from + 360f * (lap + 1), Tag.BASE))
        }
        return b.build()
    }

    /** [count] square loops with their first vertical climb at azimuth [climb] (inside, or outside when [inverted]): vertical sides, ⅛ lap wide, from the base to the 45° parallel. From the first corner to the last. */
    fun squares(climb: Float, count: Int, inverted: Boolean = false): IdealTrack {
        val b = TrackBuilder()
        b.inverted = inverted
        val edges = ArrayList<Edge>()
        edges += parallel(0f, climb - LEAD, climb, Tag.BASE)
        val firsts = ArrayList<Int>()
        repeat(count) {
            firsts += edges.size
            edges += meridian(climb, 0f, 45f)
            edges += parallel(45f, climb, climb - SQUARE_WIDTH)
            edges += meridian(climb - SQUARE_WIDTH, 45f, 0f)
            edges += parallel(0f, climb - SQUARE_WIDTH, if (it == count - 1) climb - SQUARE_WIDTH + LEAD else climb, Tag.BASE)
        }
        return finish(b, edges, firsts)
    }

    /** [count] triangular loops, the first starting at azimuth [start] at the base's east end: legs 30° off vertical, the apex on the 45° parallel. From the first corner to the last. */
    fun triangles(start: Float, count: Int, inverted: Boolean = false, apexLatitude: Float = 45f): IdealTrack {
        val b = TrackBuilder()
        b.inverted = inverted
        val half = TRIANGLE_BASE / 2f
        val right = place(0f, start)
        val apex = place(apexLatitude, start - half)
        val left = place(0f, start - TRIANGLE_BASE)
        val edges = ArrayList<Edge>()
        edges += parallel(0f, start - LEAD, start, Tag.BASE)
        val firsts = ArrayList<Int>()
        repeat(count) {
            firsts += edges.size
            edges += greatCircle(right, apex, Tag.LEG)
            edges += greatCircle(apex, left, Tag.LEG)
            edges += parallel(0f, start - TRIANGLE_BASE, if (it == count - 1) start - TRIANGLE_BASE + LEAD else start, Tag.BASE)
        }
        return finish(b, edges, firsts)
    }

    /** Rounds [edges], keeps from the first corner to the last and marks a repeat at each of [firsts] (indexes of edges). */
    private fun finish(b: TrackBuilder, edges: List<Edge>, firsts: List<Int>): IdealTrack {
        val path = rounded(edges, corner).trimmedToCorners()
        b.add(path.points)
        for (e in firsts) b.markRepeat(path.junctionStart[e])
        return b.build()
    }

    /** [count] horizontal eights whose lobes meet at the crossing point at azimuth [crossing]: an inside loop then an outside one, side by side. From the first passage of the crossing point. */
    fun horizontalEights(crossing: Float, count: Int, connected: Boolean = false): IdealTrack {
        val half = acos(((cos(LOOP_RADIUS * 2 * D2R) - sin(LOOP_RADIUS * D2R) * sin(LOOP_RADIUS * D2R)) / (cos(LOOP_RADIUS * D2R) * cos(LOOP_RADIUS * D2R))).coerceIn(-1f, 1f)) / D2R / 2f
        val west = place(LOOP_RADIUS, crossing - half)
        val east = place(LOOP_RADIUS, crossing + half)
        val x = Vector3(west).add(east).nor()
        val b = TrackBuilder()
        b.crossing = x
        if (connected) {
            val climbTo = sphere.latitude(x)
            b.add(rounded(listOf(parallel(0f, crossing - LEAD, crossing, Tag.BASE), meridian(crossing, 0f, climbTo, Tag.CONNECT)), corner).points.map { it.first to Tag.CONNECT })
            b.judgedFrom = b.size - 1
        }
        repeat(count) {
            b.repeat()
            b.edge(circleArc(west, LOOP_RADIUS, x, 360f))
            b.edge(circleArc(east, LOOP_RADIUS, x, -360f))
        }
        if (connected) {
            b.judgedTo = b.size
            // out by a further three quarters of the west lobe, which ends level at the base going on
            b.add(circleArc(west, LOOP_RADIUS, x, 270f).points.map { it to Tag.CONNECT })
        }
        return b.build()
    }

    /**
     * [count] square horizontal eights with the shared vertical side at azimuth [crossing]: each lobe is a square ⅛ lap wide,
     * the west one with inside corners and the east one with outside corners, both from the base to the 45° parallel.
     * From the first corner on the 45° parallel (after the way up) to the last corner of the east lobe.
     */
    fun squareEights(crossing: Float, count: Int, connected: Boolean = false): IdealTrack {
        val edges = ArrayList<Edge>()
        val w = SQUARE_WIDTH
        if (connected) edges += parallel(0f, crossing - LEAD, crossing, Tag.BASE)
        edges += meridian(crossing, if (connected) 0f else 30f, 45f, Tag.CONNECT)
        val firsts = ArrayList<Int>()
        repeat(count) {
            firsts += edges.size
            // the west lobe, with left turns
            edges += parallel(45f, crossing, crossing - w)
            edges += meridian(crossing - w, 45f, 0f)
            edges += parallel(0f, crossing - w, crossing, Tag.BASE)
            edges += meridian(crossing, 0f, 45f)
            // the east lobe, with right turns
            edges += parallel(45f, crossing, crossing + w)
            edges += meridian(crossing + w, 45f, 0f)
            edges += parallel(0f, crossing + w, crossing, Tag.BASE)
            edges += meridian(crossing, 0f, 45f)
        }
        val lastUp = edges.size - 1
        if (connected) {
            // out by a further west lobe: along the parallel, down, onto the base going east
            edges += parallel(45f, crossing, crossing - w, Tag.CONNECT)
            edges += meridian(crossing - w, 45f, 0f, Tag.CONNECT)
            edges += parallel(0f, crossing - w, crossing - w + LEAD, Tag.CONNECT)
        } else {
            edges += parallel(45f, crossing, crossing - LEAD, Tag.CONNECT)
        }
        val path = rounded(edges, corner)
        val b = TrackBuilder()
        b.add(path.points)
        for (e in firsts) b.markRepeat(path.junctionStart[e])
        b.judgedFrom = path.junctionStart[firsts[0]]
        b.judgedTo = path.edgeStart[lastUp]
        b.crossing = place(22.5f, crossing)
        return b.build()
    }

    /** [count] vertical eights on the meridian [crossing]: an inside loop between 0° and 45° and an outside one between 45° and 90°, meeting at 45°. From the base. */
    fun verticalEights(crossing: Float, count: Int): IdealTrack {
        val lower = place(LOOP_RADIUS, crossing)
        val upper = place(3f * LOOP_RADIUS, crossing)
        val bottom = place(0f, crossing)
        val x = place(45f, crossing)
        val b = TrackBuilder()
        b.crossing = x
        repeat(count) {
            b.repeat()
            b.edge(circleArc(lower, LOOP_RADIUS, bottom, 180f))
            b.edge(circleArc(upper, LOOP_RADIUS, x, -360f))
            b.edge(circleArc(lower, LOOP_RADIUS, x, 180f))
        }
        return b.build()
    }

    /** The hourglass about the meridian [axis]: from the base corner a leg up to the top edge near the zenith, the top edge, the crossing leg back and the base edge; the legs cross at the 45° parallel. */
    fun hourglass(axis: Float): IdealTrack {
        val w = SQUARE_WIDTH / 2f
        // the top corners' latitude that puts the crossing of the legs on the 45° parallel
        var low = 46f
        var high = 89f
        repeat(40) {
            val top = (low + high) / 2f
            val leg = greatCircle(place(0f, axis + w), place(top, axis - w), Tag.LEG)
            val crossing = leg.points.minBy { kotlin.math.abs(wrapDegrees(azimuthRadiansOf(it.x, it.z) / D2R - axis)) }
            if (asin(crossing.y.coerceIn(-1f, 1f)) / D2R > 45f) high = top else low = top
        }
        val top = (low + high) / 2f
        val bl = place(0f, axis - w)
        val br = place(0f, axis + w)
        val tl = place(top, axis - w)
        val tr = place(top, axis + w)
        val edges = listOf(
            parallel(0f, axis + w - LEAD, axis + w, Tag.BASE),
            greatCircle(br, tl, Tag.LEG),
            greatCircle(tl, tr, Tag.OVER_TOP),
            greatCircle(tr, bl, Tag.LEG),
            greatCircle(bl, br, Tag.BASE),
        )
        val path = rounded(edges, corner)
        val b = TrackBuilder()
        b.add(path.points)
        b.judgedFrom = path.junctionStart[1]
        b.markRepeat(b.judgedFrom)
        return b.build()
    }

    /** [count] overhead eights whose loops cross at the zenith, on the line of azimuth [axis] and its opposite: two loops with their bottoms on the 45° parallel. From the first pass over the zenith. */
    fun overheadEights(axis: Float, count: Int, connected: Boolean = false): IdealTrack {
        val zenith = Vector3(0f, 1f, 0f)
        val t = place(0f, axis + 90f)
        val leftNormal = Vector3(zenith).crs(t)
        val leftCentre = Vector3(zenith).scl(cos(LOOP_RADIUS * D2R)).mulAdd(leftNormal, sin(LOOP_RADIUS * D2R)).nor()
        val rightCentre = Vector3(zenith).scl(cos(LOOP_RADIUS * D2R)).mulAdd(leftNormal, -sin(LOOP_RADIUS * D2R)).nor()
        val b = TrackBuilder()
        b.crossing = zenith
        if (connected) {
            val approach = axis - 90f
            b.add(rounded(listOf(parallel(0f, approach - LEAD, approach, Tag.BASE), meridian(approach, 0f, 90f, Tag.CONNECT)), corner).points.map { it.first to Tag.CONNECT })
            b.judgedFrom = b.size - 1
        }
        repeat(count) {
            b.repeat()
            b.edge(circleArc(leftCentre, LOOP_RADIUS, zenith, 360f))
            b.edge(circleArc(rightCentre, LOOP_RADIUS, zenith, -360f))
        }
        if (connected) {
            b.judgedTo = b.size
            // on over the zenith and down the meridian to the base, going on east
            val down = axis + 90f
            b.add(rounded(listOf(meridian(down, 90f, 0f, Tag.CONNECT), parallel(0f, down, down + LEAD, Tag.CONNECT)), corner).points.map { it.first to Tag.CONNECT })
        }
        return b.build()
    }

    /** The four-leaf clover about the zenith, entered by the vertical climb at azimuth [climb] (the 9 o'clock vertical): four ¾ loops of equal size, each joined to the next through the zenith, ending in the climb through it. */
    fun clover(climb: Float, connected: Boolean = false): IdealTrack {
        val rho = CLOVER_RADIUS
        val gamma = asin(sqrt(2f) * sin(rho * D2R)) / D2R
        val d = atan(tan(gamma * D2R) * cos(45f * D2R)) / D2R
        val b = TrackBuilder()
        b.crossing = Vector3(0f, 1f, 0f)
        b.superimposed = false
        val entry = rounded(listOf(parallel(0f, climb - LEAD, climb, Tag.BASE), meridian(climb, 0f, 90f)), corner)
        b.add(entry.points)
        b.judgedFrom = entry.points.indexOfFirst { it.second == Tag.CORNER }
        b.edge(meridian(climb + 180f, 90f, 90f - d, Tag.CONNECT))
        var arm = climb + 180f
        repeat(4) { leaf ->
            b.repeat()
            val centre = place(90f - gamma, arm + 45f)
            val p = place(90f - d, arm)
            val q = place(90f - d, arm + 90f)
            val sweep = sweepAbout(centre, p, q)
            b.edge(circleArc(centre, rho, p, sweep))
            if (leaf < 3) {
                arm -= 90f
                b.edge(greatCircle(q, place(90f - d, arm), Tag.CONNECT))
            } else {
                b.edge(greatCircle(q, Vector3(0f, 1f, 0f), Tag.VERTICAL))
            }
        }
        if (connected) {
            b.judgedTo = b.size
            // on over the zenith and down to the base, going on east
            val down = arm + 90f + 180f
            b.add(rounded(listOf(meridian(down, 90f, 0f, Tag.CONNECT), parallel(0f, down, down + LEAD, Tag.CONNECT)), corner).points.map { it.first to Tag.CONNECT })
        }
        return b.build()
    }

    /** The landing glide from the base at azimuth [start]: one lap down to the ground, the engine stopped. */
    fun landing(start: Float): IdealTrack {
        val b = TrackBuilder()
        val n = 360
        b.add(List(n + 1) { place(groundLatitude * it / n, start + 360f * it / n) to Tag.GLIDE })
        return b.build()
    }

    /** The angle (degrees, 0..360) to turn from [a] to [b] counterclockwise about [centre] (seen from outside). */
    private fun sweepAbout(centre: Vector3, a: Vector3, b: Vector3): Float {
        val pa = Vector3(a).mulAdd(centre, -a.dot(centre)).nor()
        val pb = Vector3(b).mulAdd(centre, -b.dot(centre)).nor()
        var angle = atan2(Vector3(pa).crs(pb).dot(centre), pa.dot(pb)) / D2R
        if (angle < 0f) angle += 360f
        return angle
    }
}

/** [degrees] wrapped into (-180, 180]. */
