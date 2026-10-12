/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What a deduction is for. */
enum class Criterion(val label: String, val unit: String) {
    BASE_HEIGHT("Base height", "m"),
    TRACK_DEVIATION("Track deviation", "m"),
    PARALLEL_45("45° parallel", "m"),
    LINE_ANGLE("Line angle", "°"),
    CORNER_RADIUS("Corner radius", "m"),
    SEGMENT_LENGTH("Segment length", "laps"),
    SUPERIMPOSITION("Superimposition", "m"),
    SYMMETRY("Symmetry", "m"),
    TAKEOFF_ROLL("Take-off roll", "m"),
    TOUCHDOWN("Touchdown speed", "m/s"),
}

/** One deduction from a mark: the [measured] error (in the criterion's unit) cost [amount] marks. */
data class Deduction(val criterion: Criterion, val measured: Float, val amount: Float)

/**
 * A graded manoeuvre: the mark in [tenths] (10 to 100, so 85 is 8.5), what was deducted, and the ideal and flown tracks as
 * directions on the hemisphere, kept for the debrief.
 */
class Graded(val tenths: Int, val deductions: List<Deduction>, val ideal: List<Vector3>, val flown: List<Vector3>) {
    val mark: Float get() = tenths / 10f
}

/** The shortest take-off roll (m). */
const val MIN_ROLL = 4.5f

/** The base height the judge expects (m): the hemisphere's centre. */
private const val BASE = BASE_HEIGHT

/**
 * Marks a recognised manoeuvre from its flown path (design decision 5). The flown points are matched to the ideal track by
 * progress, each to the nearest ideal point within ±15% of its own, so that a repeat cannot snap onto another repeat.
 * Every criterion is `min(cap, perUnit × max(0, measured − tolerance))`, non-decreasing in the error; the mark is
 * `clamp(10 − Σ deductions, 1, 10)` in tenths.
 */
class Grader(private val radius: Float, val weights: MarkWeights = MarkWeights()) {
    private val tracks = IdealTracks(radius)
    private val geometry = HemisphereGeometry(radius)
    private val sampler = IdealSampler(radius)

    /** The ideal track of a recognised manoeuvre, placed at its anchor and without its lead-in and lead-out. */
    fun idealFor(figure: Figure, anchor: Float): IdealTrack = when (figure) {
        Figure.TAKEOFF -> tracks.takeoff(anchor)
        Figure.REVERSE_WINGOVER -> tracks.reverseWingover(anchor)
        Figure.INSIDE_LOOPS -> tracks.loops(anchor, 3)
        Figure.INVERTED_LAPS -> tracks.invertedLaps(anchor, 2)
        Figure.OUTSIDE_LOOPS -> tracks.loops(anchor, 3, inverted = true)
        Figure.INSIDE_SQUARES -> tracks.squares(anchor, 2)
        Figure.OUTSIDE_SQUARES -> tracks.squares(anchor, 2, inverted = true)
        Figure.INSIDE_TRIANGLES -> tracks.triangles(anchor, 2)
        Figure.HORIZONTAL_EIGHTS -> tracks.horizontalEights(anchor, 2)
        Figure.SQUARE_EIGHTS -> tracks.squareEights(anchor, 2)
        Figure.VERTICAL_EIGHTS -> tracks.verticalEights(anchor, 2)
        Figure.HOURGLASS -> tracks.hourglass(anchor)
        Figure.OVERHEAD_EIGHTS -> tracks.overheadEights(anchor, 2)
        Figure.CLOVER -> tracks.clover(anchor)
        Figure.LANDING -> tracks.landing(anchor)
    }.judged()

    /**
     * Marks the manoeuvre [recognition] found in [points] (the whole flight's resampled points); [tokens] are the tokens
     * of the burst it was found in.
     */
    fun grade(recognition: Recognition, points: List<PathPoint>, tokens: List<Token>): Graded {
        val ideal = idealFor(recognition.figure, recognition.anchor)
        val segment = points.subList(recognition.from, recognition.to + 1)
        val inside = tokens.filter { it.from >= recognition.from - 1 && it.to <= recognition.to + 1 }
        return gradeSegment(recognition.figure, ideal, segment, inside, points)
    }

    private fun gradeSegment(
        figure: Figure, ideal: IdealTrack, segment: List<PathPoint>, tokens: List<Token>, all: List<PathPoint>, extra: List<Deduction> = emptyList(),
    ): Graded {
        val deductions = ArrayList<Deduction>(extra)
        if (segment.size < 3) return finish(deductions + Deduction(Criterion.TRACK_DEVIATION, 99f, 99f), ideal, segment)
        val match = align(segment, ideal)
        deductions += deviation(segment, ideal, match)
        deductions += baseHeight(segment, ideal, match)
        deductions += parallel(segment, ideal, match)
        deductions += lines(figure, tokens)
        deductions += corners(tokens)
        deductions += lengths(figure, tokens, all)
        if (ideal.repeats > 1 && ideal.superimposed) deductions += superimposition(segment, ideal, match)
        ideal.crossing?.let { deductions += crossing(segment, ideal, match, it) }
        return finish(deductions, ideal, segment)
    }

    private fun finish(deductions: List<Deduction>, ideal: IdealTrack, segment: List<PathPoint>): Graded {
        val total = deductions.sumOf { it.amount.toDouble() }.toFloat()
        val tenths = ((10f - total).coerceIn(1f, 10f) * 10f).roundToInt()
        return Graded(tenths, deductions.filter { it.amount > 0f }, ideal.points.map { it.u }, segment.map { it.u })
    }

    /**
     * For each flown point the index of the ideal point it is matched to: the nearest one ahead of the point before it, so
     * a figure that crosses itself or repeats does not match a point of another pass. The first point is matched within the
     * first [WINDOW] of the track.
     */
    internal fun align(segment: List<PathPoint>, ideal: IdealTrack): IntArray {
        val pts = ideal.points
        val match = IntArray(segment.size)
        var previous = 0
        for (k in segment.indices) {
            val from = if (k == 0) 0 else max(0, previous - BACK)
            val to = if (k == 0) max(1, (pts.size * WINDOW).toInt()) else min(pts.size - 1, previous + AHEAD)
            var best = from
            var bestDot = -2f
            for (i in from..to) {
                val d = segment[k].u.dot(pts[i].u)
                if (d > bestDot) {
                    bestDot = d
                    best = i
                }
            }
            match[k] = best
            previous = best
        }
        return match
    }

    private fun metres(a: Vector3, b: Vector3) = Vector3(a).sub(b).len() * radius

    private fun deviation(segment: List<PathPoint>, ideal: IdealTrack, match: IntArray): Deduction {
        var sum = 0.0
        for (k in segment.indices) {
            val d = metres(segment[k].u, ideal.points[match[k]].u)
            sum += d * d
        }
        val rms = sqrt(sum / segment.size).toFloat()
        return Deduction(Criterion.TRACK_DEVIATION, rms, weights.trackDeviation.deduct(rms))
    }

    private fun baseHeight(segment: List<PathPoint>, ideal: IdealTrack, match: IntArray): Deduction {
        var worst = 0f
        val lowest = FloatArray(ideal.repeats) { Float.MAX_VALUE }
        for (k in segment.indices) {
            if (!segment[k].airborne) continue
            val point = ideal.points[match[k]]
            if (point.tag == Tag.BASE) worst = max(worst, abs(segment[k].height - BASE))
            else if (geometry.latitude(point.u) <= BOTTOM_BAND) lowest[point.repeat] = min(lowest[point.repeat], segment[k].height)
        }
        for (h in lowest) if (h != Float.MAX_VALUE) worst = max(worst, abs(h - BASE))
        return Deduction(Criterion.BASE_HEIGHT, worst, weights.baseHeight.deduct(worst))
    }

    private fun parallel(segment: List<PathPoint>, ideal: IdealTrack, match: IntArray): Deduction {
        var worst = 0f
        for (r in 0 until ideal.repeats) {
            val top = ideal.repeat(r).maxOf { geometry.latitude(it.u) }
            if (top < 40f || top > 47f) continue
            var flownTop = -90f
            var seen = false
            for (k in segment.indices) if (ideal.points[match[k]].repeat == r) {
                flownTop = max(flownTop, geometry.latitude(segment[k].u))
                seen = true
            }
            if (seen) worst = max(worst, abs(flownTop - top) * MathUtils.degreesToRadians * radius)
        }
        return Deduction(Criterion.PARALLEL_45, worst, weights.parallel45.deduct(worst))
    }

    private fun lines(figure: Figure, tokens: List<Token>): Deduction {
        var worst = 0f
        for (t in tokens) if (t is Straight && t.length > 3f) {
            val expected = when {
                figure == Figure.INSIDE_TRIANGLES -> 30f
                t.offVertical < VERTICAL_TOLERANCE -> 0f
                else -> continue
            }
            worst = max(worst, abs(t.offVertical - expected))
        }
        return Deduction(Criterion.LINE_ANGLE, worst, weights.lineAngle.deduct(worst))
    }

    private fun corners(tokens: List<Token>): Deduction {
        val radii = tokens.filterIsInstance<Corner>().map { it.radius }
        val mean = if (radii.isEmpty()) 0f else radii.average().toFloat()
        return Deduction(Criterion.CORNER_RADIUS, mean, weights.cornerRadius.deduct(mean))
    }

    /** Widths that must be ⅛ lap: the squares' and the triangles' (in laps). */
    private fun lengths(figure: Figure, tokens: List<Token>, segment: List<PathPoint>): Deduction {
        var worst = 0f
        val all = tokens.filterIsInstance<Straight>().filter { it.length > 3f }
        when (figure) {
            Figure.INSIDE_SQUARES, Figure.OUTSIDE_SQUARES -> {
                val verticals = all.filter { it.offVertical < VERTICAL_TOLERANCE }
                for (pair in verticals.chunked(2)) if (pair.size == 2) {
                    val a = azimuthOf(pair[0], segment)
                    val b = azimuthOf(pair[1], segment)
                    worst = max(worst, abs(abs(wrapDeg(a - b)) - SQUARE_WIDTH) / 360f)
                }
            }
            Figure.INSIDE_TRIANGLES -> {
                for (pair in all.chunked(2)) if (pair.size == 2) {
                    val a = equatorAzimuth(pair[0], segment)
                    val b = equatorAzimuth(pair[1], segment)
                    if (a != null && b != null) worst = max(worst, abs(abs(wrapDeg(a - b)) - TRIANGLE_BASE) / 360f)
                }
            }
            else -> {}
        }
        return Deduction(Criterion.SEGMENT_LENGTH, worst, weights.segmentLength.deduct(worst))
    }

    private fun azimuthOf(straight: Straight, points: List<PathPoint>): Float {
        val mid = (straight.from + straight.to) / 2
        return geometry.azimuth(points[min(max(mid, 0), points.size - 1)].u)
    }

    /** Where the great circle of [straight] crosses the base, as an azimuth (degrees), or null. */
    fun equatorAzimuth(straight: Straight, points: List<PathPoint>): Float? = equatorCrossing(straight.from, straight.to, points)

    private fun equatorCrossing(from: Int, to: Int, points: List<PathPoint>): Float? {
        val a = points[from].u
        val b = points[to].u
        val normal = Vector3(a).crs(b)
        if (normal.len2() < 1e-8f) return null
        val d = normal.crs(Vector3(0f, 1f, 0f))
        if (d.len2() < 1e-8f) return null
        if (d.dot(a) < 0f) d.scl(-1f)
        return geometry.azimuth(d.nor())
    }

    private fun superimposition(segment: List<PathPoint>, ideal: IdealTrack, match: IntArray): Deduction {
        val first = ArrayList<Vector3>()
        val rest = ArrayList<Pair<Int, Vector3>>()
        for (k in segment.indices) {
            val r = ideal.points[match[k]].repeat
            if (r == 0) first += segment[k].u else rest += r to segment[k].u
        }
        // each later repeat's points against the first repeat's path: the nearest point, and a half step more for the gaps
        var worst = 0f
        if (first.size >= 2) for ((_, u) in rest) {
            var best = Float.MAX_VALUE
            for (i in 0 until first.size - 1) best = min(best, distanceToSegment(u, first[i], first[i + 1]))
            worst = max(worst, best * radius - STEP_LENGTH / 2f)
        }
        return Deduction(Criterion.SUPERIMPOSITION, worst, weights.superimposition.deduct(worst))
    }

    /** The distance (unit sphere) from [p] to the segment [a]..[b]. */
    private fun distanceToSegment(p: Vector3, a: Vector3, b: Vector3): Float {
        val ab = Vector3(b).sub(a)
        val len2 = ab.len2()
        val t = if (len2 > 0f) (Vector3(p).sub(a).dot(ab) / len2).coerceIn(0f, 1f) else 0f
        return Vector3(a).mulAdd(ab, t).dst(p)
    }

    private fun crossing(segment: List<PathPoint>, ideal: IdealTrack, match: IntArray, x: Vector3): Deduction {
        val passes = ArrayList<Vector3>()
        var current: Vector3? = null
        var cut = false
        for (k in segment.indices) {
            val near = geometry.angle(ideal.points[match[k]].u, x) < CROSSING_BAND
            if (near) {
                val c = current ?: Vector3().also {
                    current = it
                    cut = k == 0
                }
                // the flown point of the pass nearest the ideal crossing
                if (c.isZero || geometry.angle(segment[k].u, x) < geometry.angle(c, x)) c.set(segment[k].u)
            } else if (current != null) {
                // a pass cut off by the start of the judged stretch is not whole
                if (!cut) passes += requireNotNull(current)
                current = null
            }
        }
        // ... nor is one cut off by its end
        var worst = 0f
        for (i in passes.indices) for (j in i + 1 until passes.size) worst = max(worst, metres(passes[i], passes[j]))
        return Deduction(Criterion.SYMMETRY, worst, weights.symmetry.deduct(worst))
    }

    // the take-off and the landing

    /**
     * Marks the take-off flown in [points] from the release (the first point) to [end], 3 laps of azimuth after it: the
     * ground roll to the [liftoff] point, the climb to the base over the release point and the two laps at the base.
     */
    fun gradeTakeoff(points: List<PathPoint>, liftoff: Int, end: Int): Graded {
        val rolled = points[liftoff].distance - points[0].distance
        val ideal = tracks.takeoff(geometry.azimuth(points[0].u), rolled.coerceIn(MIN_ROLL, QUARTER_LAP * radius))
        val outside = max(0f, max(MIN_ROLL - rolled, rolled - QUARTER_LAP * radius))
        val segment = points.subList(0, end + 1)
        val match = align(segment, ideal)
        val deductions = ArrayList<Deduction>()
        deductions += Deduction(Criterion.TAKEOFF_ROLL, outside, weights.takeoffRoll.deduct(outside))
        deductions += deviation(segment, ideal, match)
        var worst = 0f
        for (k in segment.indices) if (ideal.points[match[k]].tag == Tag.BASE) worst = max(worst, abs(segment[k].height - BASE))
        deductions += Deduction(Criterion.BASE_HEIGHT, worst, weights.baseHeight.deduct(worst))
        return finish(deductions, ideal, segment)
    }

    /**
     * Marks the landing in [points], from the place the plane left the base (index [from]) to touchdown ([to]): the glide,
     * which should take 1 lap to bring the plane down from the base (measured from its height at [from]), and the vertical
     * speed at touchdown (m/s), [sink].
     */
    fun gradeLanding(points: List<PathPoint>, from: Int, to: Int, sink: Float): Graded {
        val segment = points.subList(from, to + 1)
        val ideal = tracks.landing(geometry.azimuth(segment.first().u))
        val deductions = ArrayList<Deduction>()
        val match = align(segment, ideal)
        deductions += deviation(segment, ideal, match)
        val laps = abs(segment.last().azimuth - segment.first().azimuth) / 360f * (BASE / max(segment.first().height, 0.5f))
        deductions += Deduction(Criterion.SEGMENT_LENGTH, abs(laps - 1f), weights.segmentLength.deduct(abs(laps - 1f)))
        deductions += Deduction(Criterion.TOUCHDOWN, sink, weights.touchdown.deduct(sink))
        return finish(deductions, ideal, segment)
    }

    private companion object {
        const val WINDOW = 0.15f
        const val BACK = 4
        const val AHEAD = 90
        const val BOTTOM_BAND = 6f
        const val SUPERIMPOSITION_BINS = 24
        const val CROSSING_BAND = 4f
        const val QUARTER_LAP = 0.5f * MathUtils.PI
    }
}
