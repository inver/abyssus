/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightControlSource
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import net.nevinsky.abyssus.app.game.controlline.pattern.GuideInput
import net.nevinsky.abyssus.app.game.controlline.pattern.SCHEDULE

/**
 * The AI pilot of Watch AI: the [planner] says what to fly and the [pilot] steers along it, through the same handle a
 * player holds. Made fresh for every attempt; it keeps no state between flights.
 */
class F2bBot(val planner: F2bRoutinePlanner, val pilot: F2bPilot) : FlightControlSource {
    override fun tilt(observation: FlightObservation): Float = pilot.tilt(observation, planner.course(observation))
}

/**
 * The Watch AI pilot of a plane with [plane]'s settings and [mass] kg, on lines of its own length, reading [judge]'s
 * progress (never changing it).
 */
fun f2bBot(plane: PlaneComponent, mass: Float, judge: () -> GuideInput, schedule: List<Figure> = SCHEDULE): F2bBot =
    F2bBot(F2bRoutinePlanner(plane.lineLength, judge, schedule = schedule), F2bPilot(Handling(plane, mass)))
