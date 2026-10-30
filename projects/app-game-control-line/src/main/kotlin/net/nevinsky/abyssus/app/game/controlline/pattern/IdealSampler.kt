/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.lib.core.util.wrapDegrees

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import kotlin.math.acos
import kotlin.math.asin
import net.nevinsky.abyssus.lib.core.util.azimuthOf
import kotlin.math.atan2

/** The simulation step the samples are spaced for (s). */
private const val STEP = 1f / 120f

/**
 * Turns an ideal path into the [TrackSample]s a plane flying it at [speed] m/s would give, one every 1/120 s, over a flat
 * ground at y = 0 and a hemisphere of [radius] metres: the way the judge reads the ideal tracks (to know what its own
 * reading of a perfect figure is), and the test stand-in for a flight.
 */
class IdealSampler(private val radius: Float, speed: Float = 22f) {
    private val spacing = speed * STEP / radius

    /** [points] (place, inverted) resampled at equal steps of the flown distance; each keeps the orientation of the nearest point before it. */
    fun resample(points: List<Pair<Vector3, Boolean>>): List<Pair<Vector3, Boolean>> {
        val out = ArrayList<Pair<Vector3, Boolean>>()
        out += Vector3(points[0].first) to points[0].second
        var next = spacing
        var travelled = 0f
        for (i in 1 until points.size) {
            val step = acos(points[i - 1].first.dot(points[i].first).coerceIn(-1f, 1f))
            while (travelled + step >= next) {
                val t = if (step > 0f) (next - travelled) / step else 0f
                out += Vector3(points[i - 1].first).lerp(points[i].first, t).nor() to points[i - 1].second
                next += spacing
            }
            travelled += step
        }
        return out
    }

    /** The samples for [places] (already spaced as [resample] does), starting at [startTime]. */
    fun samples(places: List<Pair<Vector3, Boolean>>, startTime: Float = 0f, engineRunning: Boolean = true): List<TrackSample> =
        places.indices.map { i ->
            sample(
                places[i].first, places[minOf(i + 1, places.size - 1)].first, places[maxOf(i - 1, 0)].first, places[i].second,
                startTime + i * STEP, engineRunning,
            )
        }

    /** The samples of the track [points] flown from time [startTime]. */
    fun fly(points: List<Pair<Vector3, Boolean>>, startTime: Float = 0f, engineRunning: Boolean = true) =
        samples(resample(points), startTime, engineRunning)

    private fun sample(u: Vector3, ahead: Vector3, behind: Vector3, inverted: Boolean, time: Float, engineRunning: Boolean): TrackSample {
        val offset = Vector3(u).scl(radius).add(0f, BASE_HEIGHT, 0f)
        val heading = Vector3(ahead).sub(behind)
        heading.mulAdd(u, -heading.dot(u))
        if (heading.len2() < 1e-12f) heading.set(1f, 0f, 0f).mulAdd(u, -u.x)
        heading.nor()
        val left = Vector3(u).crs(heading).nor()
        val planeUp = if (inverted) left.scl(-1f) else left
        val east = Vector3(0f, 1f, 0f).crs(u)
        val east2 = if (east.len2() > 1e-8f) east.nor() else Vector3(1f, 0f, 0f)
        val up = Vector3(u).crs(east2)
        val climb = atan2(heading.dot(up), heading.dot(east2))
        val sphereUp = Vector3(0f, 1f, 0f).mulAdd(u, -u.y)
        val inv = sphereUp.len2() > 1e-8f && planeUp.dot(sphereUp) < 0f
        val height = offset.y
        return TrackSample(
            time,
            wrapDegrees(azimuthOf(u.x, u.z)),
            asin(u.y.coerceIn(-1f, 1f)) * MathUtils.radiansToDegrees,
            wrapDegrees(climb * MathUtils.radiansToDegrees),
            inv,
            height > 0.5f,
            offset,
            height,
            planeUp,
            engineRunning,
        )
    }
}
