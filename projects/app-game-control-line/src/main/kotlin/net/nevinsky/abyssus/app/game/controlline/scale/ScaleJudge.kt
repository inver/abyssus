/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.scale

import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flight.FlightJudge
import net.nevinsky.abyssus.app.game.controlline.track.Laps
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import kotlin.math.abs

/**
 * The Scale judge (design decision 2): counts the laps the plane flies while airborne with their times and elevations,
 * and marks them with [scaleMarks]. A lap still being flown does not count, in the marks or in [laps]. Lives on the
 * game's main thread, fed by the flight's steps.
 */
class ScaleJudge : FlightJudge {
    private val counter = Laps()
    private val times = ArrayList<Float>()
    private var lapStart: Float? = null
    private var elevationSum = 0.0
    private var elevationCount = 0
    private var lapElevationSum = 0.0
    private var lapElevationCount = 0
    private var end: FlightEnd? = null

    /** The time of each lap completed, in seconds. */
    val lapTimes: List<Float> get() = times

    /** The laps completed while airborne. */
    val laps: Int get() = times.size

    /** The marks so far; the landing scores once the flight has ended as a landing. */
    val marks: ScaleMarks
        get() = scaleMarks(times, if (elevationCount == 0) 0f else (elevationSum / elevationCount).toFloat(), end == FlightEnd.LANDED)

    override fun sample(sample: TrackSample) {
        if (sample.airborne) {
            if (lapStart == null) lapStart = sample.time
            lapElevationSum += abs(sample.elevation)
            lapElevationCount++
        }
        if (counter.sample(sample) > 0) {
            times += sample.time - (lapStart ?: sample.time)
            lapStart = sample.time
            elevationSum += lapElevationSum
            elevationCount += lapElevationCount
            lapElevationSum = 0.0
            lapElevationCount = 0
        }
    }

    override fun ended(end: FlightEnd) {
        this.end = end
    }
}
