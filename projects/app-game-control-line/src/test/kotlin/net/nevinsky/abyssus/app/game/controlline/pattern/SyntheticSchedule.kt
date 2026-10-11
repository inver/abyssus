/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd

/**
 * A whole F2B flight made of ideal tracks, with level laps at the base between them: the test stand-in for a pilot who
 * flies the schedule. Each [figure] is built at the anchor that puts its first point where the last one ended, after
 * a pause of at least [pauseLaps] laps.
 */
class SyntheticSchedule(private val radius: Float = 18f, private val pauseLaps: Float = 1.6f) {
    val flight = SyntheticFlight(radius)
    val tracks = IdealTracks(radius)
    val judge = PatternJudge(radius)
    private val sphere = HemisphereGeometry(radius)
    private var fed = 0

    /** Hands the samples flown since the last call to the judge. */
    fun feed() {
        while (fed < flight.samples.size) judge.sample(flight.samples[fed++])
    }

    fun end(end: FlightEnd) {
        feed()
        judge.ended(end)
    }

    fun takeoff(roll: Float = 8f, perturbation: Perturbation = Perturbation()) {
        flight.fly(tracks.takeoff(0f, roll), perturbation)
        feed()
    }

    /** Level laps at the base, [laps] at least, upright or [inverted]. */
    fun pause(laps: Float = pauseLaps, inverted: Boolean = false) {
        flight.level(to = flight.endAzimuth, laps = laps, inverted = inverted)
        feed()
    }

    /** Pauses until the flight has run [seconds]. */
    fun fillTo(seconds: Float, inverted: Boolean = false) {
        while (flight.time < seconds) pause(1f, inverted)
    }

    /** Flies the track [build] makes after a pause: [build] takes the anchor that puts the track's first point where the flight is. */
    fun figure(
        perturbation: Perturbation = Perturbation(), laps: Float = pauseLaps, engineRunning: Boolean = true,
        pauseInverted: Boolean? = null, build: (Float) -> IdealTrack,
    ) {
        val first = build(0f).points.first()
        pause(laps, pauseInverted ?: first.inverted)
        val anchor = flight.endAzimuth - sphere.azimuth(first.u)
        flight.fly(build(anchor), perturbation, engineRunning)
        feed()
    }

    /** The whole schedule, flown perfectly, up to (not including) the landing. */
    fun everythingBeforeTheLanding(skip: Set<Figure> = emptySet(), time: Float? = null) {
        takeoff()
        if (Figure.REVERSE_WINGOVER !in skip) figure { tracks.reverseWingover(it) }
        if (Figure.INSIDE_LOOPS !in skip) figure { tracks.loops(it, 3) }
        if (Figure.INVERTED_LAPS !in skip) figure(pauseInverted = false) { tracks.invertedLaps(it, 2) }
        if (Figure.OUTSIDE_LOOPS !in skip) figure { tracks.loops(it, 3, inverted = true) }
        if (Figure.INSIDE_SQUARES !in skip) figure { tracks.squares(it, 2) }
        if (Figure.OUTSIDE_SQUARES !in skip) figure { tracks.squares(it, 2, inverted = true) }
        if (Figure.INSIDE_TRIANGLES !in skip) figure { tracks.triangles(it, 2) }
        if (Figure.HORIZONTAL_EIGHTS !in skip) figure { tracks.horizontalEights(it, 2, connected = true) }
        if (Figure.SQUARE_EIGHTS !in skip) figure { tracks.squareEights(it, 2, connected = true) }
        if (Figure.VERTICAL_EIGHTS !in skip) figure { tracks.verticalEights(it, 2) }
        if (Figure.HOURGLASS !in skip) figure { tracks.hourglass(it) }
        if (Figure.OVERHEAD_EIGHTS !in skip) figure { tracks.overheadEights(it, 2, connected = true) }
        time?.let { fillTo(it) }
        if (Figure.CLOVER !in skip) figure { tracks.clover(it, connected = true) }
    }

    /** The landing: a pause, then the glide with the engine stopped. */
    fun landing() {
        figure(engineRunning = false) { tracks.landing(it) }
    }
}
