/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.app.game.controlline.track.TrackSample

/** A track flown between two stretches of level flight at the base, read as tokens, as the judge reads a burst. */
class BurstFlight(val track: IdealTrack, perturbation: Perturbation = Perturbation(), radius: Float = 18f) {
    val sphere = HemisphereGeometry(radius)
    val path = PathTokens(radius)
    val samples: List<TrackSample>

    init {
        val flight = SyntheticFlight(radius)
        val start = sphere.azimuth(track.points.first().u) + perturbation.azimuthShift
        flight.level(to = start, from = start - 40f, inverted = track.points.first().inverted)
        flight.fly(track, perturbation)
        flight.level(to = flight.endAzimuth + 40f, inverted = track.points.last().inverted)
        samples = flight.samples
        for (s in samples) path.feed(s)
        path.flush()
    }

    /** The tokens of the burst: those between the level pauses at the two ends. */
    val burst: List<Token>
        get() {
            val all = path.tokens
            var from = 0
            while (from < all.size && isPause(all[from])) from++
            var to = all.size
            while (to > from && isPause(all[to - 1])) to--
            return all.subList(from, to)
        }

    private fun isPause(token: Token) = token is Level && kotlin.math.abs(token.azimuthSpan) >= PAUSE_AZIMUTH
}
