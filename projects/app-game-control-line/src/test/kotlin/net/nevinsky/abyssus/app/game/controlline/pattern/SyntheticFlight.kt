/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.lib.core.util.wrapDegrees

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** How a synthetic flight strays from its ideal track. */
data class Perturbation(
    /** Everything is turned round the pilot by this many degrees of azimuth. */
    val azimuthShift: Float = 0f,
    /** Each repeat after the first is turned this many degrees further round than the one before it. */
    val repeatShift: Float = 0f,
    /** Metres the bottoms of the figures are raised: the points near the base, fading out by 20 degrees of latitude. */
    val lowRaise: Float = 0f,
    /** Metres the whole figure is raised, except what is on the ground. */
    val raise: Float = 0f,
)

/**
 * Flies an ideal track as [TrackSample]s at a set speed, one every 1/120 s: the test stand-in for the plane (design
 * decision 9). The plane's up is the track's own: upright, or inverted where the ideal track says so. Samples carry the
 * place on a hemisphere of [radius] metres centred 1.5 m above a flat ground at y = 0.
 */
class SyntheticFlight(private val radius: Float = 18f, private val speed: Float = 22f) {
    private val dt = 1f / 120f
    private val sampler = IdealSampler(radius, speed)

    /** The next sample's time. */
    var time = 0f
        private set

    /** The samples of every call so far. */
    val samples = ArrayList<TrackSample>()

    /** The last sample's place. */
    val end: Vector3 get() = Vector3(unit(samples.last()))

    private fun unit(s: TrackSample) = Vector3(s.offset).sub(0f, BASE_HEIGHT, 0f).nor()

    /** The azimuth (degrees) of the last sample. */
    val endAzimuth: Float get() = atan2(-end.z, end.x) * MathUtils.radiansToDegrees

    /** Whether the last sample was inverted. */
    var inverted = false
        private set

    /** Flies [track]'s points (all of them, lead-in and lead-out too) with [perturbation]; [engineRunning] is the engine. Returns the samples flown. */
    fun fly(track: IdealTrack, perturbation: Perturbation = Perturbation(), engineRunning: Boolean = true): List<TrackSample> {
        val flown = sampler.fly(perturbed(track, perturbation), time, engineRunning)
        samples += flown
        time += flown.size * dt
        if (flown.isNotEmpty()) inverted = flown.last().inverted
        return flown
    }

    /** Flies level at the base from the current place to azimuth [to] (going on east), at least [laps] laps, [inverted] or not; the last point is not repeated. */
    fun level(to: Float, laps: Float = 0f, inverted: Boolean = false, from: Float? = null): List<TrackSample> {
        val from = from ?: if (samples.isEmpty()) to else endAzimuth
        var span = wrapDegrees(to - from)
        if (span < 0f) span += 360f
        while (span < laps * 360f) span += 360f
        val points = parallel(0f, from, from + span, Tag.BASE).points.map { it to inverted }
        return flyPoints(points)
    }

    /** Flies the raw [points] (position, inverted). */
    fun flyPoints(points: List<Pair<Vector3, Boolean>>): List<TrackSample> {
        val b = TrackBuilder()
        var current = false
        for ((u, inv) in points) {
            b.inverted = inv
            b.add(listOf(u to Tag.BASE))
            current = inv
        }
        return fly(b.build())
    }

    private fun perturbed(track: IdealTrack, p: Perturbation): List<Pair<Vector3, Boolean>> {
        val raiseBase = Math.toDegrees((p.lowRaise / radius).toDouble()).toFloat()
        val raiseAll = Math.toDegrees((p.raise / radius).toDouble()).toFloat()
        val n = track.points.size
        return track.points.mapIndexed { index, point ->
            var u = Vector3(point.u)
            // the whole-figure raise comes on and goes off gently, not as a step
            val ramp = minOf(1f, index / (0.05f * n), (n - 1 - index) / (0.05f * n))
            val lat = Math.toDegrees(kotlin.math.asin(u.y.coerceIn(-1f, 1f)).toDouble()).toFloat()
            if (lat >= -0.5f && raiseBase != 0f) {
                val weight = max(0f, 1f - lat / 20f)
                u = rotateLatitude(u, raiseBase * weight)
            }
            if (raiseAll != 0f && lat > -0.5f) u = rotateLatitude(u, raiseAll * ramp)
            val turn = p.azimuthShift + p.repeatShift * point.repeat
            if (turn != 0f) u.rotate(Vector3.Y, turn)
            u to point.inverted
        }
    }

    /** [u] moved [degrees] towards the zenith along its meridian. */
    private fun rotateLatitude(u: Vector3, degrees: Float): Vector3 {
        val axis = Vector3(u).crs(Vector3(0f, 1f, 0f))
        if (axis.len2() < 1e-8f) return u
        return Vector3(u).rotate(axis.nor(), degrees).nor()
    }
}
