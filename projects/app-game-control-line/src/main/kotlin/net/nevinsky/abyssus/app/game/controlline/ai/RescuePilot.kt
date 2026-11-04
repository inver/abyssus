/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.flight.FlightControlSource
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.pattern.BASE_HEIGHT
import net.nevinsky.abyssus.lib.core.util.azimuthOf

/**
 * A course that has not moved on for this long (s) has lost the plane: the rescue builds it again from where the plane
 * is.
 */
private const val NO_PROGRESS = 3f

/** A path course with less than this much (m) left is flown. */
private const val FLOWN = 2f

/** The instructor's pilot half: a tilt that flies the plane back to safe flight, and a way to forget its course. */
interface RescueSource : FlightControlSource {
    /** The share went back to 0: the next rescue starts from where the plane is then. */
    fun release() {}
}

/** What the rescue flies: level at the base, over onto the upright base, or the landing glide. */
enum class RescuePhase {
    LEVEL,
    TURN_OVER,
    GLIDE,
}

/**
 * Flies the plane back to safe flight (design decision 3 of `add-control-line-trainer`), through [pilot], on a course
 * of its own that the planner and the judge never see. While the engine runs: level on the base in the direction the
 * plane flies. With the engine stopped: over onto the upright base if it flies clockwise, then the landing glide.
 */
class RescuePilot(
    private val radius: Float,
    private val pilot: F2bPilot,
    private val figures: F2bFigures = F2bFigures(radius),
) : RescueSource {
    /** The course being flown, or null before the rescue starts and after [release]. */
    var course: Course? = null
        private set

    var phase = RescuePhase.LEVEL
        private set

    private var engineRan = true
    private var progressAt = 0f
    private var progressTime = 0f

    override fun tilt(observation: FlightObservation): Float {
        val o = observation
        val u = Vector3(o.offset).sub(0f, BASE_HEIGHT, 0f).nor()
        val current = course
        if (current == null || o.engineRunning != engineRan) {
            engineRan = o.engineRunning
            start(o, u)
        } else if (current is PathCourse) {
            if (current.travelled > progressAt + 0.5f) {
                progressAt = current.travelled
                progressTime = o.time
            }
            if (current.remaining < FLOWN && phase == RescuePhase.TURN_OVER) {
                start(o, u, upright = true)
            } else if (o.time - progressTime > NO_PROGRESS) {
                start(o, u, upright = phase != RescuePhase.TURN_OVER)
            }
        }
        return pilot.tilt(o, checkNotNull(course))
    }

    override fun release() {
        course = null
        phase = RescuePhase.LEVEL
    }

    /** Builds the course for the plane of [o] at [u]; [upright] says it is upright now (no turn-over needed). */
    private fun start(o: FlightObservation, u: Vector3, upright: Boolean? = null) {
        val clockwise = Vector3(0f, 1f, 0f).crs(u).dot(o.velocity) < 0f
        val azimuth = azimuthOf(u.x, u.z)
        val next: Course
        if (o.engineRunning) {
            phase = RescuePhase.LEVEL
            next = Parallel(radius, 0f, clockwise)
        } else if (clockwise && upright != true) {
            phase = RescuePhase.TURN_OVER
            next = PathCourse(radius, figures.turnOver(azimuth, clockwise = false))
        } else {
            phase = RescuePhase.GLIDE
            next = PathCourse(radius, figures.landing(azimuth))
        }
        course = next
        progressAt = 0f
        progressTime = o.time
    }
}

/** A rescue pilot for a plane with [handling], on lines of [radius] metres. */
fun rescuePilot(radius: Float, handling: Handling): RescuePilot = RescuePilot(radius, F2bPilot(handling))
