/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.lib.core.util.wrapDegrees

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A turn is inside when the plane's up points to the inside of its bend (to the handle side for an upright loop). */
enum class Side { INSIDE, OUTSIDE }

/** One resampled point of a flown path: the plane's place on the hemisphere and what it was doing there. */
class PathPoint(
    val time: Float,
    /** The unit direction from the hemisphere's centre. */
    val u: Vector3,
    val up: Vector3,
    val height: Float,
    val airborne: Boolean,
    val engineRunning: Boolean,
    /** The metres flown along the path up to this point. */
    val distance: Float,
    /** Whether the plane's up points away from the hemisphere's local up. */
    val inverted: Boolean,
    /** The azimuth flown so far (degrees, unwrapped, from 0 at the first point; not counting the stretches near the zenith). */
    val azimuth: Float,
    /** The heading relative to the circle's direction (degrees: 0 along it, 90 up), or NaN near the zenith, where it means nothing. */
    val heading: Float,
)

/** A stretch of a flown path, as indexes [from]..[to] (inclusive) into [PathTokens.points]. */
sealed class Token {
    abstract val from: Int
    abstract val to: Int
}

/** A straight stretch along a great circle that is not level: [offVertical] degrees between its plane and the vertical. */
data class Straight(
    override val from: Int, override val to: Int, val length: Float, val offVertical: Float, val startLatitude: Float,
    val endLatitude: Float,
) : Token()

/** Level flight along a parallel: [latitude] degrees, [azimuthSpan] degrees flown (signed, growing in the flying direction). */
data class Level(
    override val from: Int, override val to: Int, val length: Float, val upright: Boolean, val latitude: Float, val azimuthSpan: Float,
) : Token()

/** A sharp turn of [angle] degrees (less than a loop), its tightest [radius] metres. */
data class Corner(override val from: Int, override val to: Int, val side: Side, val angle: Float, val radius: Float) : Token()

/** A steady turn of [angle] degrees (the geodesic turn; a loop of 22.5° radius is 332°), [pitchTurned] of which in pitch (a loop is 360°; NaN-free, near the zenith not counted). */
data class Arc(override val from: Int, override val to: Int, val side: Side, val angle: Float, val pitchTurned: Float = angle) : Token()

/** Rolling on the ground. */
data class GroundRoll(override val from: Int, override val to: Int, val length: Float) : Token()

/** The plane touching down from the air. */
data class Touchdown(override val from: Int, override val to: Int) : Token()

/** The distance between points of a flown path (m). */
const val STEP_LENGTH = 0.5f

/** A turn tighter than this (m) is a turn; wider ones are straight-like (design decision 2). */
const val TURN_RADIUS = 13f

/** ...and a turn goes on until it widens past this (m). */
const val TURN_END_RADIUS = 18f

/** A turn of at least this many degrees is an [Arc]; a lesser one is an [Arc] too when it is nowhere tighter than [CORNER_RADIUS], and a [Corner] otherwise. */
const val ARC_ANGLE = 200f

/** A turn of less than this many degrees is a wobble, not a token: the level flight it interrupts goes on through it. */
const val MIN_TURN = 30f

/** A turn that gets tighter than this (m) and is short is a corner. */
const val CORNER_RADIUS = 5.5f

/** Straight-like flight within this many degrees of latitude is [Level]... */
const val LEVEL_LATITUDE_SPAN = 12f

/** ...when it goes along the circle at least this many times as far (on the sphere) as it goes up or down. */
const val LEVEL_FLATNESS = 2f

/**
 * Reads a flown path as tokens (design decision 2): straights, level stretches, corners, arcs, ground roll and
 * touchdown. Samples are resampled every [STEP_LENGTH]; the turning of a step is the angle between its heading and the
 * last one, about the place's direction `u` from the hemisphere's centre, so great circles are straight whatever their
 * direction and nothing is singular at the zenith. A turn is inside or outside from the plane's up. A token is
 * reported when the next one starts or on [flush].
 */
class PathTokens(private val radius: Float) {
    private val geometry = HemisphereGeometry(radius)
    private val route = ArrayList<PathPoint>()

    /** The resampled points so far. */
    val points: List<PathPoint> get() = route

    /** The tokens reported so far, in order. */
    val tokens: MutableList<Token> = ArrayList()

    private val turns = ArrayList<Float>() // signed heading change at each point, positive turning left seen from outside
    private val pitchTurns = ArrayList<Float>() // the change of the heading relative to the circle at each point (NaN near the zenith)
    private val sides = ArrayList<Side>()
    private var travelled = 0f
    private var flownAzimuth = 0f
    private var lastAzimuth: Float? = null
    private var lastSample: Vector3? = null

    // the open token
    private var kind = Kind.NONE
    private var openFrom = 0
    private var openAngle = 0f
    private var openMinRadius = Float.MAX_VALUE
    private var insideAngle = 0f
    private var outsideAngle = 0f
    private var turning = false
    private var airborneBefore: Boolean? = null

    // a closed line stays here until the next token is known, so that a wobble between two stretches joins them
    private var pending: Token? = null
    private var bridged = false

    private enum class Kind { NONE, TURN, LINE, GROUND }

    /** Adds [sample]; returns the tokens it closed. */
    fun feed(sample: TrackSample): List<Token> {
        val u = geometry.direction(sample)
        val last = lastSample
        if (last != null) {
            val d = last.dst(u) * radius
            if (route.isNotEmpty() && d < STEP_LENGTH) return emptyList()
            travelled += d
        }
        lastSample = u
        val inverted = run {
            val ahead = Vector3(0f, 1f, 0f).mulAdd(u, -u.y)
            ahead.len2() > 1e-6f && sample.planeUp.dot(ahead) < 0f
        }
        val lat = geometry.latitude(u)
        val az = geometry.azimuth(u)
        val previousAzimuth = lastAzimuth
        lastAzimuth = az
        if (lat < 85f && previousAzimuth != null) flownAzimuth += wrapDegrees(az - previousAzimuth)
        val east = Vector3(0f, 1f, 0f).crs(u)
        val heading = if (lat < 80f && route.isNotEmpty() && east.len2() > 1e-6f) {
            val t = tangent(route.last().u, u)
            val e = east.nor()
            atan2(t.dot(Vector3(u).crs(e)), t.dot(e)) * MathUtils.radiansToDegrees
        } else Float.NaN
        route += PathPoint(sample.time, u, Vector3(sample.planeUp), sample.height, sample.airborne, sample.engineRunning, travelled, inverted, flownAzimuth, heading)
        return classify()
    }

    /** Closes the open token and returns every token closed by it. */
    fun flush(): List<Token> {
        val closed = ArrayList<Token>()
        close(route.size - 1, closed)
        emitPending(closed)
        return closed
    }

    /** The heading change (rad) at point [i], positive turning left seen from outside, and the side of the bend. */
    private fun heading(i: Int): Float {
        pitchTurns += if (i >= 1 && !route[i].heading.isNaN() && !route[i - 1].heading.isNaN()) wrapDegrees(route[i].heading - route[i - 1].heading) * MathUtils.degreesToRadians else Float.NaN
        if (i < 2) {
            turns += 0f
            sides += Side.INSIDE
            return 0f
        }
        val a = route[i - 2].u
        val b = route[i - 1].u
        val c = route[i].u
        val t1 = tangent(a, b)
        val t2 = tangent(b, c)
        val mid = Vector3(b)
        val delta = atan2(Vector3(t1).crs(t2).dot(mid), t1.dot(t2))
        turns += delta
        val bend = Vector3(mid).crs(t2).scl(if (delta >= 0f) 1f else -1f)
        sides += if (bend.dot(route[i - 1].up) >= 0f) Side.INSIDE else Side.OUTSIDE
        return delta
    }

    /**
     * How much the plane really turns at point [k] (rad): the smaller of its heading change about the place and its
     * change relative to the circle's direction. Flying level along a parallel bends as the parallel does (a parallel at
     * 45° has a 18 m radius of curvature) but does not turn, and a great circle does not bend but changes its heading
     * relative to the circle; neither is a turn.
     */
    private fun effective(k: Int): Float {
        val geodesic = abs(turns[k])
        val pitch = pitchTurns[k]
        return if (pitch.isNaN()) geodesic else min(geodesic, abs(pitch))
    }

    private fun tangent(a: Vector3, b: Vector3): Vector3 {
        val chord = Vector3(b).sub(a)
        val mid = Vector3(a).add(b).nor()
        return chord.mulAdd(mid, -chord.dot(mid)).nor()
    }

    private fun classify(): List<Token> {
        val i = route.size - 1
        heading(i)
        val closed = ArrayList<Token>()
        val j = i - 1 // the point classified: the window around it holds points j-1..j+1
        if (j < 2) return closed
        val p = route[j]
        val step = route[j].distance - route[j - 1].distance
        val smooth = (effective(j - 1) + effective(j) + effective(j + 1)) / 3f
        val signed = (turns[j - 1] + turns[j] + turns[j + 1]) / 3f
        val bendRadius = if (smooth > 1e-5f) step / smooth else Float.MAX_VALUE
        turning = if (turning) bendRadius < TURN_END_RADIUS else bendRadius < TURN_RADIUS
        val airborne = p.airborne
        val wanted = when {
            !airborne -> Kind.GROUND
            turning -> Kind.TURN
            else -> Kind.LINE
        }
        val side = sides[j]
        val splitsOnSide = kind == Kind.TURN && wanted == Kind.TURN && sideOfOpen() != side && abs(signed) > 0.02f && sideRun(side)
        // level flight changes token when the plane turns over
        val flip = kind == Kind.LINE && wanted == Kind.LINE && flipped(p)
        if (airborneBefore == true && !airborne) {
            close(j - 1, closed)
            emitPending(closed)
            emit(Touchdown(j, j), closed)
        }
        airborneBefore = airborne
        if (wanted != kind || splitsOnSide || flip) {
            close(j - 1, closed)
            kind = wanted
            openFrom = max(j - 0, 0)
            // a line that follows a wobble goes on from the line before it
            val before = pending
            if (wanted == Kind.LINE && before != null) {
                if (bridged) {
                    openFrom = before.from
                    pending = null
                    bridged = false
                } else emitPending(closed)
            }
            openAngle = 0f
            openMinRadius = Float.MAX_VALUE
            insideAngle = 0f
            outsideAngle = 0f
            openInverted = p.inverted
            flipStreak = 0
        }
        if (kind == Kind.TURN) {
            val angle = abs(turns[j]) * MathUtils.radiansToDegrees
            openAngle += angle
            if (side == Side.INSIDE) insideAngle += angle else outsideAngle += angle
            if (turns[j] != 0f) openMinRadius = min(openMinRadius, bendRadius)
        }
        return closed
    }

    private var openInverted = false
    private var flipStreak = 0

    /** Whether the plane has been turned over, against the open line, for a few points in a row. */
    private fun flipped(point: PathPoint): Boolean {
        // going up or down the plane's up is not told from the sphere's local up, so only level flight turns over
        val level = !point.heading.isNaN() && abs(sin(point.heading * MathUtils.degreesToRadians)) < 0.5f
        flipStreak = if (level && point.inverted != openInverted) flipStreak + 1 else 0
        return flipStreak >= 4
    }

    private var sideStreak = 0
    private var sideStreakSide: Side? = null

    private fun sideOfOpen() = if (insideAngle >= outsideAngle) Side.INSIDE else Side.OUTSIDE

    /** Whether the bend has been on [side] for a few points in a row (so a flicker of noise does not split a turn). */
    private fun sideRun(side: Side): Boolean {
        if (side == sideStreakSide) sideStreak++ else {
            sideStreakSide = side
            sideStreak = 1
        }
        return sideStreak >= 4
    }

    private fun close(end: Int, closed: MutableList<Token>) {
        val was = kind
        val token = make(openFrom, min(end, route.size - 1))
        kind = Kind.NONE
        when {
            token == null -> if (was == Kind.TURN && pending != null) bridged = true
            token is Level || token is Straight -> {
                emitPending(closed)
                pending = token
            }
            else -> {
                emitPending(closed)
                emit(token, closed)
            }
        }
    }

    private fun emit(token: Token, closed: MutableList<Token>) {
        tokens += token
        closed += token
    }

    private fun emitPending(closed: MutableList<Token>) {
        pending?.let { emit(it, closed) }
        pending = null
        bridged = false
    }

    /** The token that would be reported if the open one closed now, or null when none is open. */
    fun peekOpen(): Token? {
        // a wobble not yet over, after a line: the line goes on through it
        val before = pending
        if (kind == Kind.TURN && before != null && openAngle < MIN_TURN) return line(before.from, route.size - 2)
        return make(openFrom, route.size - 2)
    }

    private fun make(from: Int, to: Int): Token? {
        if (kind == Kind.NONE || to < from || to >= route.size) return null
        return when (kind) {
            Kind.GROUND -> GroundRoll(from, to, route[to].distance - route[from].distance)
            Kind.TURN -> {
                val side = sideOfOpen()
                if (openAngle < MIN_TURN) null
                else if (openAngle >= ARC_ANGLE || openMinRadius >= CORNER_RADIUS) Arc(from, to, side, openAngle, pitchTurned(from, to)) else Corner(from, to, side, openAngle, openMinRadius)
            }
            Kind.LINE -> line(from, to)
            Kind.NONE -> null
        }
    }

    private fun pitchTurned(from: Int, to: Int): Float {
        var sum = 0f
        for (k in from + 1..to) {
            val a = route[k - 1].heading
            val b = route[k].heading
            if (!a.isNaN() && !b.isNaN()) sum += wrapDegrees(b - a)
        }
        return abs(sum)
    }

    private fun line(from: Int, to: Int): Token? {
        if (to <= from) return null
        val a = route[from]
        val b = route[to]
        val length = b.distance - a.distance
        val lat0 = geometry.latitude(a.u)
        val lat1 = geometry.latitude(b.u)
        var lowest = 90f
        var highest = -90f
        var sumAzimuth = 0f
        var uprightCount = 0
        for (k in from..to) {
            val lat = geometry.latitude(route[k].u)
            lowest = min(lowest, lat)
            highest = max(highest, lat)
            if (!route[k].inverted) uprightCount++
            if (k > from && lat < 85f) sumAzimuth += wrapDegrees(geometry.azimuth(route[k].u) - geometry.azimuth(route[k - 1].u))
        }
        val along = abs(sumAzimuth) * cos(MathUtils.degreesToRadians * (lowest + highest) / 2f)
        if (highest - lowest <= LEVEL_LATITUDE_SPAN && highest < 85f && along >= LEVEL_FLATNESS * (highest - lowest)) {
            return Level(from, to, length, uprightCount * 2 >= to - from + 1, (lowest + highest) / 2, sumAzimuth)
        }
        val normal = Vector3(a.u).crs(b.u)
        // use a middle point for a long straight whose end points are nearly opposite (through the zenith)
        val mid = route[(from + to) / 2]
        val n = if (normal.len2() < 1e-4f) Vector3(mid.u).crs(tangent(route[max(from, to - 2)].u, b.u)) else Vector3(mid.u).crs(tangent(route[max(from, (from + to) / 2 - 1)].u, route[min(to, (from + to) / 2 + 1)].u))
        n.nor()
        val offVertical = asin(abs(n.y).coerceIn(0f, 1f)) * MathUtils.radiansToDegrees
        return Straight(from, to, length, offVertical, lat0, lat1)
    }
}
