/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.pattern.BASE_HEIGHT
import net.nevinsky.abyssus.app.game.controlline.pattern.Edge
import net.nevinsky.abyssus.app.game.controlline.pattern.IdealTrack
import net.nevinsky.abyssus.app.game.controlline.pattern.IdealTracks
import net.nevinsky.abyssus.app.game.controlline.pattern.LOOP_RADIUS
import net.nevinsky.abyssus.app.game.controlline.pattern.SQUARE_WIDTH
import net.nevinsky.abyssus.app.game.controlline.pattern.TRIANGLE_BASE
import net.nevinsky.abyssus.app.game.controlline.pattern.Tag
import net.nevinsky.abyssus.app.game.controlline.pattern.circleArc
import net.nevinsky.abyssus.app.game.controlline.pattern.greatCircle
import net.nevinsky.abyssus.app.game.controlline.pattern.meridian
import net.nevinsky.abyssus.app.game.controlline.pattern.parallel
import net.nevinsky.abyssus.app.game.controlline.pattern.place
import net.nevinsky.abyssus.app.game.controlline.pattern.rounded
import net.nevinsky.abyssus.lib.core.util.azimuthOf

/** The corners the bot flies (m): a little wider than the `Stunter`'s tightest turn (about 2.7 m at the stall). */
const val BOT_CORNER = 3.5f

/** The bot's hourglass: its top edge's latitude, half its width and half its base's width (degrees). */
const val HOURGLASS_TOP = 50f
const val HOURGLASS_WIDTH = 65f
const val HOURGLASS_BASE = 10f

/** The hourglass's top corners (m): tighter than the others, as the plane pushes them with gravity's help. */
private const val TOP_CORNER = 2.8f

/** The landing levels off this high above the ground (m) and lets the plane settle from there. */
const val FLARE_HEIGHT = 0.4f

/** How far along the base (degrees) the bot flies before a figure's first turn, and after its last one. */
const val LEAD_IN = 25f
const val LEAD_OUT = 60f

/**
 * The courses the bot flies, on a hemisphere of [radius] metres, each starting at the plane's azimuth `at` on the base and
 * ending level on the base with [LEAD_OUT] degrees to spare.
 *
 * The plane's up always points across its lines, so a pull turns it left (seen from outside) and a push right, and it flies
 * level inverted only clockwise. The inside figures are therefore [IdealTracks]' own shapes; the wingover's inverted half
 * lap, the inverted laps and the outside figures are flown clockwise along the same places. The bot turns over from one
 * direction to the other with a [turnOver]: up a meridian, over the zenith and down the other side, which the judge reads
 * as half a wingover and, the wingover being behind it, does not score.
 */
class F2bFigures(private val radius: Float, cornerMetres: Float = BOT_CORNER) {
    private val tracks = IdealTracks(radius, cornerMetres)
    private val corner = (cornerMetres / radius) * MathUtils.radiansToDegrees

    private fun path(edges: List<Edge>): List<Vector3> = rounded(edges, corner).points.map { it.first }

    /** [track]'s points with the base from [at] before them and the base on after them, in the direction the track ends. */
    private fun around(at: Float, track: IdealTrack): List<Vector3> {
        val points = track.points.map { it.u }
        val first = azimuthOf(points.first().x, points.first().z)
        val last = points.last()
        val end = azimuthOf(last.x, last.z)
        val clockwise = isClockwise(points[points.size - 2], last)
        return parallel(0f, at, unwrapAfter(at, first)).points + points +
            parallel(0f, end, end + if (clockwise) -LEAD_OUT else LEAD_OUT).points
    }

    /** Take-off from the plane at rest at azimuth [at]: the ground roll, a lap's climb to the base and two laps on it. */
    fun takeoff(at: Float): List<Vector3> = tracks.takeoff(at).points.map { it.u } + parallel(0f, at + 1080f, at + 1080f + LEAD_OUT).points

    /**
     * Half a lap from the base at [at] onto the base going the other way: up the meridian over the zenith, and down to the
     * base going [clockwise] (inverted) or anticlockwise (upright).
     */
    fun turnOver(at: Float, clockwise: Boolean): List<Vector3> {
        val climb = if (clockwise) at + LEAD_IN else at - LEAD_IN
        val down = climb + 180f
        return path(listOf(
            parallel(0f, at, climb, Tag.BASE),
            meridian(climb, 0f, 90f),
            meridian(down, 90f, 0f),
            parallel(0f, down, if (clockwise) down - LEAD_OUT else down + LEAD_OUT, Tag.BASE),
        ))
    }

    /** The reverse wingover: up and over, half a lap inverted (clockwise) along the base, and up and over again. */
    fun reverseWingover(at: Float): List<Vector3> {
        val climb = at + LEAD_IN
        return path(listOf(
            parallel(0f, at, climb, Tag.BASE),
            meridian(climb, 0f, 90f),
            meridian(climb + 180f, 90f, 0f),
            parallel(0f, climb + 180f, climb, Tag.BASE),
            meridian(climb, 0f, 90f),
            meridian(climb + 180f, 90f, 0f),
            parallel(0f, climb + 180f, climb + 180f + LEAD_OUT, Tag.BASE),
        ))
    }

    /** Three inside loops from the base, upright. */
    fun insideLoops(at: Float): List<Vector3> {
        val bottom = at + LEAD_IN
        val centre = place(LOOP_RADIUS, bottom)
        val start = place(0f, bottom)
        return parallel(0f, at, bottom).points + List(3) { circleArc(centre, LOOP_RADIUS, start, 360f).points }.flatten() +
            parallel(0f, bottom, bottom + LEAD_OUT).points
    }

    /** Three outside loops from the base flown inverted (clockwise): the same circles, pushed. */
    fun outsideLoops(at: Float): List<Vector3> {
        val bottom = at - LEAD_IN
        val centre = place(LOOP_RADIUS, bottom)
        val start = place(0f, bottom)
        return parallel(0f, at, bottom).points + List(3) { circleArc(centre, LOOP_RADIUS, start, -360f).points }.flatten() +
            parallel(0f, bottom, bottom - LEAD_OUT).points
    }

    /** Two inside square loops, upright: up, back along the 45° parallel, down and on along the base. */
    fun insideSquares(at: Float): List<Vector3> {
        val climb = at + LEAD_IN + SQUARE_WIDTH
        val edges = ArrayList<Edge>()
        edges += parallel(0f, at, climb, Tag.BASE)
        repeat(2) {
            edges += meridian(climb, 0f, 45f)
            edges += parallel(45f, climb, climb - SQUARE_WIDTH)
            edges += meridian(climb - SQUARE_WIDTH, 45f, 0f)
            edges += parallel(0f, climb - SQUARE_WIDTH, if (it == 1) climb - SQUARE_WIDTH + LEAD_OUT else climb, Tag.BASE)
        }
        return path(edges)
    }

    /** Two outside square loops from the base flown inverted (clockwise): up, on along the 45° parallel upright, down and back along the base, pushed. */
    fun outsideSquares(at: Float): List<Vector3> {
        val climb = at - LEAD_IN
        val edges = ArrayList<Edge>()
        edges += parallel(0f, at, climb, Tag.BASE)
        repeat(2) {
            edges += meridian(climb, 0f, 45f)
            edges += parallel(45f, climb, climb + SQUARE_WIDTH)
            edges += meridian(climb + SQUARE_WIDTH, 45f, 0f)
            edges += parallel(0f, climb + SQUARE_WIDTH, if (it == 1) climb + SQUARE_WIDTH - LEAD_OUT else climb, Tag.BASE)
        }
        return path(edges)
    }

    /** Two inside triangular loops, upright, legs 30° off vertical and the apex on the 45° parallel. */
    fun insideTriangles(at: Float): List<Vector3> {
        val start = at + LEAD_IN + TRIANGLE_BASE
        val right = place(0f, start)
        val apex = place(45f, start - TRIANGLE_BASE / 2f)
        val left = place(0f, start - TRIANGLE_BASE)
        val edges = ArrayList<Edge>()
        edges += parallel(0f, at, start, Tag.BASE)
        repeat(2) {
            edges += greatCircle(right, apex, Tag.LEG)
            edges += greatCircle(apex, left, Tag.LEG)
            edges += parallel(0f, start - TRIANGLE_BASE, if (it == 1) start - TRIANGLE_BASE + LEAD_OUT else start, Tag.BASE)
        }
        return path(edges)
    }

    fun horizontalEights(at: Float): List<Vector3> = around(at, tracks.horizontalEights(at + 2f * LEAD_IN, 2, connected = true))

    fun squareEights(at: Float): List<Vector3> = around(at, tracks.squareEights(at + 2f * LEAD_IN, 2, connected = true))

    fun verticalEights(at: Float): List<Vector3> = around(at, tracks.verticalEights(at + LEAD_IN, 2))

    /**
     * The hourglass about the meridian `at + 2 LEAD_IN`: from the base up a leg to the top edge, along it, down the crossing
     * leg and on along the base. The top edge is on the [HOURGLASS_TOP] parallel and wide enough for two corners the plane
     * can fly with straight flight between them; the shape the judge reads is the same: two inside corners at the base,
     * two outside ones at the top, and the inclined legs crossing.
     */
    fun hourglass(at: Float): List<Vector3> {
        val axis = at + 2f * LEAD_IN + HOURGLASS_BASE
        val br = place(0f, axis + HOURGLASS_BASE)
        val tl = place(HOURGLASS_TOP, axis - HOURGLASS_WIDTH)
        val tr = place(HOURGLASS_TOP, axis + HOURGLASS_WIDTH)
        val bl = place(0f, axis - HOURGLASS_BASE)
        val edges = listOf(
            parallel(0f, at, axis + HOURGLASS_BASE, Tag.BASE),
            greatCircle(br, tl, Tag.LEG),
            greatCircle(tl, tr, Tag.OVER_TOP),
            greatCircle(tr, bl, Tag.LEG),
            parallel(0f, axis - HOURGLASS_BASE, axis - HOURGLASS_BASE + LEAD_OUT + 2f * HOURGLASS_BASE, Tag.BASE),
        )
        return filleted(edges.map { it.points }, listOf(BOT_CORNER, TOP_CORNER, TOP_CORNER, BOT_CORNER), radius)
    }

    fun overheadEights(at: Float): List<Vector3> = around(at, tracks.overheadEights(at + 90f + 2f * LEAD_IN, 2, connected = true))

    fun clover(at: Float): List<Vector3> = around(at, tracks.clover(at + 2f * LEAD_IN, connected = true))

    /**
     * The landing from the base at [at], the engine stopped: down over a lap to [FLARE_HEIGHT] above the ground, then level
     * there while the plane slows and settles onto its wheels.
     */
    fun landing(at: Float): List<Vector3> {
        val flare = latitudeAt(FLARE_HEIGHT)
        val n = 360
        return List(n + 1) { place(flare * it / n, at + 360f * it / n) } + parallel(flare, at + 360f, at + 360f * 4).points
    }

    /** The latitude (degrees) of a place [height] metres above the ground, on the base's level ground. */
    private fun latitudeAt(height: Float): Float =
        Math.toDegrees(kotlin.math.asin(((height - BASE_HEIGHT) / radius).toDouble())).toFloat()

    /** The azimuth [to] unwrapped to lie at most a lap after [from]. */
    private fun unwrapAfter(from: Float, to: Float): Float {
        var t = to
        while (t < from) t += 360f
        while (t >= from + 360f) t -= 360f
        return t
    }

    /** Whether the step from [a] to [b] goes clockwise seen from above. */
    private fun isClockwise(a: Vector3, b: Vector3): Boolean {
        val east = Vector3(0f, 1f, 0f).crs(b)
        return Vector3(b).sub(a).dot(east) < 0f
    }
}
