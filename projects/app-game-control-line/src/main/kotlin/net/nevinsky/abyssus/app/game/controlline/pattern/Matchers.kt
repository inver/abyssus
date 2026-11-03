/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.lib.core.util.wrapDegrees

import com.badlogic.gdx.math.Vector3
import kotlin.math.max
import kotlin.math.roundToInt

/** How a recognised figure compares with the schedule's. */
enum class Verdict { COMPLETE, WRONG_REPEATS, NOT_COMPLETED }

/**
 * A manoeuvre recognised in a burst of flying: which [figure], whether it is whole ([verdict]), the [repeats] counted,
 * its judged stretch as point indexes [from]..[to] and the [anchor] azimuth (degrees) the ideal track is placed at.
 */
class Recognition(val figure: Figure, val verdict: Verdict, val from: Int, val to: Int, val repeats: Int, val anchor: Float)

/** Flying a manoeuvre has the plane level at this height or lower for at least this many degrees of azimuth: a pause, not a figure. */
const val PAUSE_AZIMUTH = 150f

/** Level flight is the base when its latitude is below this (degrees). */
const val BASE_LATITUDE = 20f

/** A pass over the zenith gets to this latitude (degrees). */
const val ZENITH_LATITUDE = 85f

/** Straight legs within this many degrees of vertical are verticals; more is an inclined leg. */
const val VERTICAL_TOLERANCE = 12f

/**
 * Recognises which F2B manoeuvre a burst of flying is (design decision 3), from the shape of its path alone: inside and
 * outside turns, sharp corners and how many, passes over the zenith. It counts repeats and does not look at how well the
 * figure was flown, so a badly flown square stays a square.
 */
class Matchers(radius: Float) {
    private val geometry = HemisphereGeometry(radius)

    private fun latitude(point: PathPoint) = geometry.latitude(point.u)

    private fun azimuth(point: PathPoint) = geometry.azimuth(point.u)

    private fun maxLatitude(points: List<PathPoint>, from: Int, to: Int) = (from..to).maxOf { latitude(points[it]) }

    private fun minLatitude(points: List<PathPoint>, from: Int, to: Int) = (from..to).minOf { latitude(points[it]) }

    /** The azimuth anchor (degrees) of the point at [index]. */
    private fun azimuthAt(points: List<PathPoint>, index: Int) = azimuth(points[index])

    /** Recognises the figure in [tokens] (a burst between pauses) over [points]; null when it is not one of the schedule's. */
    fun recognise(tokens: List<Token>, points: List<PathPoint>): Recognition? {
        val arcs = tokens.filterIsInstance<Arc>()
        val corners = tokens.filterIsInstance<Corner>()
        val straights = tokens.filterIsInstance<Straight>().filter { it.length > 3f }
        if (tokens.isEmpty()) return null
        val first = tokens.first().from
        val last = tokens.last().to
        val zenith = maxLatitude(points, first, last) >= ZENITH_LATITUDE
        val over = straights.filter { maxLatitude(points, it.from, it.to) >= ZENITH_LATITUDE }
        return when {
            arcs.isNotEmpty() -> arcFigure(arcs, corners, straights, points, zenith)
            over.isNotEmpty() && corners.size <= 6 -> wingover(tokens, over, corners, straights, points)
            corners.isNotEmpty() -> cornerFigure(corners, straights, points)
            else -> null
        }
    }

    // loops, eights and the clover

    private fun arcFigure(arcs: List<Arc>, corners: List<Corner>, straights: List<Straight>, points: List<PathPoint>, zenith: Boolean): Recognition? {
        val big = arcs.filter { it.angle >= 90f }
        if (big.isEmpty()) return null
        val from = big.first().from
        val to = big.last().to
        val inside = big.count { it.side == Side.INSIDE }
        val outside = big.size - inside
        val lowest = minLatitude(points, from, to)
        if (inside > 0 && outside > 0) {
            // both sides in turn: an eight
            val n = outside
            return when {
                zenith && lowest > 30f -> overhead(big, points, n)
                zenith -> Recognition(Figure.VERTICAL_EIGHTS, verdictOf(n, 2), from, to, n, geometry.azimuth(centre(big.first { it.side == Side.OUTSIDE }, points)))
                else -> {
                    val end = big.last { it.side == Side.OUTSIDE }.to
                    val crossing = if (big.size >= 2) geometry.azimuth(Vector3(centre(big[0], points)).add(centre(big[1], points))) else azimuthAt(points, from)
                    Recognition(Figure.HORIZONTAL_EIGHTS, verdictOf(n, 2), from, end, n, crossing)
                }
            }
        }
        val side = big.first().side
        if (zenith) {
            val leaves = big.size
            val v = if (leaves == 4) Verdict.COMPLETE else if (leaves < 4) Verdict.NOT_COMPLETED else Verdict.WRONG_REPEATS
            val entry = straights.firstOrNull { maxLatitude(points, it.from, it.to) >= ZENITH_LATITUDE }
            val start = corners.firstOrNull()?.from ?: entry?.from ?: from
            val end = (to until points.size).firstOrNull { latitude(points[it]) >= 88f } ?: to
            return Recognition(Figure.CLOVER, v, start, end, leaves, azimuthAt(points, entry?.from ?: from))
        }
        val turned = big.sumOf { it.pitchTurned.toDouble() }.toFloat()
        val n = (turned / 360f).roundToInt()
        val figure = if (side == Side.INSIDE) Figure.INSIDE_LOOPS else Figure.OUTSIDE_LOOPS
        val verdict = if (turned < 300f) Verdict.NOT_COMPLETED else verdictOf(n, 3)
        return Recognition(figure, verdict, from, to, n, wrapDegrees(geometry.azimuth(centre(big.first(), points)) + LOOP_VERTICAL_OFFSET))
    }

    private fun overhead(arcs: List<Arc>, points: List<PathPoint>, n: Int): Recognition {
        val from = arcs.first().from
        val to = arcs.last().to
        // the first loop is the left one, whose centre lies at the azimuth opposite the axis
        val axis = geometry.azimuth(centre(arcs.first(), points)) - 180f
        return Recognition(Figure.OVERHEAD_EIGHTS, verdictOf(n, 2), from, to, n, wrapDegrees(axis))
    }

    // squares, triangles, the hourglass, the square eights

    private fun cornerFigure(corners: List<Corner>, straights: List<Straight>, points: List<PathPoint>): Recognition? {
        val inside = corners.count { it.side == Side.INSIDE }
        val outside = corners.size - inside
        val legs = straights.filter { maxLatitude(points, it.from, it.to) < ZENITH_LATITUDE }
        val verticals = legs.filter { it.offVertical < VERTICAL_TOLERANCE }
        val inclined = legs.filter { it.offVertical >= VERTICAL_TOLERANCE }
        if (inside > 0 && outside > 0) {
            if (corners.size <= 5 && inclined.isNotEmpty()) {
                // four corners, two each way, along inclined legs
                val v = if (corners.size == 4) Verdict.COMPLETE else Verdict.NOT_COMPLETED
                return hourglass(corners, inclined, points, v)
            }
            if (verticals.isEmpty() || corners.size < 6) return null
            // square eights: the first corner is the way up from the base; each eight is four corners each way
            val n = outside / 4
            val judged = corners.drop(1)
            val end = judged.last { it.side == Side.OUTSIDE }.to
            val v = if (outside % 4 != 0) Verdict.NOT_COMPLETED else verdictOf(n, 2)
            return Recognition(Figure.SQUARE_EIGHTS, v, judged.first().from, end, n, azimuthAt(points, verticals.first().from))
        }
        val side = corners.first().side
        val triangles = inclined.size > verticals.size
        val per = if (triangles) 3 else 4
        if (corners.size < 2) return null
        if (!triangles && verticals.isEmpty()) return null
        val n = corners.size / per
        val v = if (corners.size % per != 0 || n == 0) Verdict.NOT_COMPLETED else verdictOf(n, 2)
        val from = corners.first().from
        val to = corners.last().to
        return if (triangles) {
            if (side != Side.INSIDE) null else Recognition(Figure.INSIDE_TRIANGLES, v, from, to, n, equatorCrossing(inclined.firstOrNull(), points) ?: azimuthAt(points, from))
        } else {
            val figure = if (side == Side.INSIDE) Figure.INSIDE_SQUARES else Figure.OUTSIDE_SQUARES
            Recognition(figure, v, from, to, n, wrapMean(points, verticals.first()))
        }
    }

    private fun hourglass(corners: List<Corner>, legs: List<Straight>, points: List<PathPoint>, verdict: Verdict): Recognition {
        val from = corners.first().from
        val lastCorner = corners.last().to
        // the bottom edge along the base: the quarter of a lap after the last corner
        var to = lastCorner
        val start = points[lastCorner].azimuth
        while (to + 1 < points.size && points[to + 1].azimuth - start < SQUARE_WIDTH) to++
        val leg = legs.firstOrNull { it.from >= from }
        val axis = (equatorCrossing(leg, points) ?: azimuthAt(points, from)) - SQUARE_WIDTH / 2f
        return Recognition(Figure.HOURGLASS, verdict, from, to, 1, wrapDegrees(axis))
    }

    // the reverse wingover

    private fun wingover(tokens: List<Token>, over: List<Straight>, corners: List<Corner>, straights: List<Straight>, points: List<PathPoint>): Recognition? {
        val passes = over.size
        val v = when {
            passes == 2 -> Verdict.COMPLETE
            passes < 2 -> Verdict.NOT_COMPLETED
            else -> Verdict.WRONG_REPEATS
        }
        val from = corners.firstOrNull { it.from <= over.first().from }?.from ?: over.first().from
        val to = corners.lastOrNull { it.from > over.last().to }?.to ?: tokens.last().to
        return Recognition(Figure.REVERSE_WINGOVER, v, from, to, passes, azimuthAt(points, over.first().from))
    }

    /** Where the great circle of [straight] crosses the base, as an azimuth (degrees), or null. */
    private fun equatorCrossing(straight: Straight?, points: List<PathPoint>): Float? =
        straight?.let { geometry.equatorCrossing(points[it.from].u, points[it.to].u) }

    /** The unit direction of the centre of the circle that [arc] goes round: the mean of its points. */
    private fun centre(arc: Arc, points: List<PathPoint>): Vector3 {
        val sum = Vector3()
        for (k in arc.from..arc.to) sum.add(points[k].u)
        return sum.nor()
    }

    private fun verdictOf(count: Int, required: Int) = when {
        count == required -> Verdict.COMPLETE
        count < required -> Verdict.WRONG_REPEATS
        else -> Verdict.WRONG_REPEATS
    }


    /** The mean azimuth of a straight's points. */
    private fun wrapMean(points: List<PathPoint>, straight: Straight): Float {
        val first = azimuth(points[straight.from])
        var sum = 0f
        for (k in straight.from..straight.to) sum += wrapDegrees(azimuth(points[k]) - first)
        return wrapDegrees(first + sum / (straight.to - straight.from + 1))
    }
}
