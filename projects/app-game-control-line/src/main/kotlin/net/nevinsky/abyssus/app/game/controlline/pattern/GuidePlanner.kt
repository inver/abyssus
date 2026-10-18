/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.lib.core.util.wrapDegrees

import com.badlogic.gdx.math.Vector3

/** How far ahead of the plane a guide starts (degrees of azimuth): a quarter lap. */
const val GUIDE_LEAD = 90f

/** The start gate is a bar from the ground up to this far above the base (m). */
const val GATE_HEIGHT = 1f

/** A search for the plane's place on a guide looks this far ahead of the last one, as a share of the track. */
private const val PROGRESS_WINDOW = 0.10f

/** The rings have this many segments. */
private const val RING_SEGMENTS = 128

/** What a guide segment is, for its colour and width. */
enum class GuideTag { GUIDE_AHEAD, GUIDE_DONE, GATE, RING_BASE, RING_45 }

/** A segment of the guide, [from] and [to] as offsets from the pilot's ground point. */
class GuideSegment(val from: Vector3, val to: Vector3, val tag: GuideTag)

/**
 * What the planner reads of a flight: the manoeuvre [expected] next (null once all are decided), the laps flown since the
 * last one ended, whether it has [started], the plane's unit [direction] from the hemisphere's centre, and the engine.
 */
class GuideInput(val expected: Figure?, val pauseLaps: Float?, val started: Boolean, val direction: Vector3?, val engineRunning: Boolean)

private class Guide(
    val figure: Figure,
    /** The track's places, as offsets from the pilot's ground point. */
    val places: List<Vector3>,
    /** The places as unit directions, for finding the plane on it. */
    val directions: List<Vector3>,
    val progress: FloatArray,
    /** The gate's azimuth, or null for the take-off, which has none. */
    val gate: Float?,
) {
    /** The index of the last place the plane has passed. */
    var done = 0
}

/**
 * The Noob guide (design decisions 1 to 4), without GL: the ideal track of the next manoeuvre, placed so that its first
 * point is at a start gate a quarter lap ahead of the plane when the pause after the last manoeuvre is complete. It stays
 * until the judge scores the manoeuvre, and from the moment the judge sees it start, the part flown is [GuideTag.GUIDE_DONE].
 * The base and 45° rings are always there. Offsets are from the pilot's ground point, as [HemisphereGeometry.position]'s.
 */
class GuidePlanner(radius: Float) {
    private val tracks = IdealTracks(radius)
    private val geometry = HemisphereGeometry(radius)
    private val rings: List<GuideSegment> = ring(0f, GuideTag.RING_BASE) + ring(PARALLEL_45, GuideTag.RING_45)
    private var guide: Guide? = null
    private var cache: List<GuideSegment>? = null

    /** The manoeuvre whose track is drawn, or null. */
    val figure: Figure? get() = guide?.figure

    /** The azimuth (degrees) of the start gate, or null without one. */
    val gateAzimuth: Float? get() = guide?.gate

    /** How many places of the track the plane has passed. */
    val passed: Int get() = guide?.done ?: 0

    /** Reads [judge]'s state after it has taken a sample. */
    fun update(judge: PatternJudge) = update(judge.guideInput())

    fun update(input: GuideInput) {
        if (guide != null && guide?.figure != input.expected) set(null)
        val expected = input.expected ?: return
        if (guide == null) place(expected, input)
        guide?.let { advance(it, input) }
    }

    /** The segments to draw: the rings, the guide and its gate. */
    fun segments(): List<GuideSegment> = cache ?: build().also { cache = it }

    private fun set(next: Guide?) {
        guide = next
        cache = null
    }

    private fun place(expected: Figure, input: GuideInput) {
        val direction = input.direction ?: return
        val here = geometry.azimuth(direction)
        when (expected) {
            Figure.TAKEOFF -> set(guideOf(expected, tracks.takeoff(here), null, 0f))
            // after the clover, none until the engine has stopped
            Figure.LANDING -> if (!input.engineRunning) set(gated(expected, tracks.landing(0f), here))
            else -> {
                val pause = input.pauseLaps ?: return
                if (pause >= PAUSE_LAPS && !input.started) set(gated(expected, trackOf(expected), here))
            }
        }
    }

    /** The whole ideal track of [figure] at azimuth 0, with the way in and out the eights and the clover have. */
    private fun trackOf(figure: Figure): IdealTrack = when (figure) {
        Figure.HORIZONTAL_EIGHTS -> tracks.horizontalEights(0f, 2, connected = true)
        Figure.SQUARE_EIGHTS -> tracks.squareEights(0f, 2, connected = true)
        Figure.OVERHEAD_EIGHTS -> tracks.overheadEights(0f, 2, connected = true)
        Figure.CLOVER -> tracks.clover(0f, connected = true)
        Figure.REVERSE_WINGOVER -> tracks.reverseWingover(0f)
        Figure.INSIDE_LOOPS -> tracks.loops(0f, 3)
        Figure.INVERTED_LAPS -> tracks.invertedLaps(0f, 2)
        Figure.OUTSIDE_LOOPS -> tracks.loops(0f, 3, inverted = true)
        Figure.INSIDE_SQUARES -> tracks.squares(0f, 2)
        Figure.OUTSIDE_SQUARES -> tracks.squares(0f, 2, inverted = true)
        Figure.INSIDE_TRIANGLES -> tracks.triangles(0f, 2)
        Figure.VERTICAL_EIGHTS -> tracks.verticalEights(0f, 2)
        Figure.HOURGLASS -> tracks.hourglass(0f)
        Figure.TAKEOFF, Figure.LANDING -> error("${figure.label} has its own guide")
    }

    /** [track], turned about the vertical so that its first point is at the gate, a quarter lap ahead of azimuth [here]. */
    private fun gated(figure: Figure, track: IdealTrack, here: Float): Guide {
        val gate = wrapDegrees(here + GUIDE_LEAD)
        return guideOf(figure, track, gate, wrapDegrees(gate - geometry.azimuth(track.points.first().u)))
    }

    private fun guideOf(figure: Figure, track: IdealTrack, gate: Float?, turn: Float): Guide {
        val directions = track.points.map { Vector3(it.u).rotate(Vector3.Y, turn) }
        return Guide(figure, directions.map(geometry::position), directions, FloatArray(directions.size) { track.points[it].progress }, gate)
    }

    private fun advance(g: Guide, input: GuideInput) {
        val direction = input.direction ?: return
        // the landing is flown from the moment it is drawn
        if (!input.started && g.figure != Figure.LANDING) return
        val limit = g.progress[g.done] + PROGRESS_WINDOW
        var best = g.done
        var closest = Float.NEGATIVE_INFINITY
        var i = g.done
        while (i < g.directions.size && g.progress[i] <= limit) {
            val dot = g.directions[i].dot(direction)
            if (dot > closest) {
                closest = dot
                best = i
            }
            i++
        }
        if (best != g.done) {
            g.done = best
            cache = null
        }
    }

    private fun build(): List<GuideSegment> {
        val out = ArrayList<GuideSegment>(rings)
        val g = guide ?: return out
        for (i in 0 until g.places.size - 1) {
            out += GuideSegment(g.places[i], g.places[i + 1], if (i + 1 <= g.done) GuideTag.GUIDE_DONE else GuideTag.GUIDE_AHEAD)
        }
        g.gate?.let {
            val base = geometry.position(place(0f, it))
            out += GuideSegment(Vector3(base.x, 0f, base.z), Vector3(base.x, BASE_HEIGHT + GATE_HEIGHT, base.z), GuideTag.GATE)
        }
        return out
    }

    private fun ring(latitude: Float, tag: GuideTag): List<GuideSegment> {
        val places = List(RING_SEGMENTS + 1) { geometry.position(place(latitude, 360f * it / RING_SEGMENTS)) }
        return places.zipWithNext { a, b -> GuideSegment(a, b, tag) }
    }
}
