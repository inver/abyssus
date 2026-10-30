/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import net.nevinsky.abyssus.app.game.controlline.track.TrackSample

/**
 * Marks a flight as it goes (design decision 6): [Flight] feeds it the [TrackSample] of every simulation step, and the
 * flight's end. The F2B pattern judge is one; Free flight and Play have none.
 */
interface FlightJudge {
    /** The plane's place at one step of the flight; the first sample is the start of the ground roll. */
    fun sample(sample: TrackSample)

    /** The flight ended as [end]. */
    fun ended(end: FlightEnd)
}
