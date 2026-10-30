/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.pattern.BASE_HEIGHT
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import net.nevinsky.abyssus.app.game.controlline.pattern.GuideInput
import net.nevinsky.abyssus.app.game.controlline.pattern.PAUSE_LAPS
import net.nevinsky.abyssus.app.game.controlline.pattern.SCHEDULE
import net.nevinsky.abyssus.lib.core.util.azimuthOf
import net.nevinsky.abyssus.lib.core.util.wrapDegrees

/** What the bot is doing, for the HUD and for diagnostics. */
enum class BotPhase { TAKEOFF, BASE, FIGURE, TURN_OVER, FUEL_WAIT, GLIDE, RECOVERY }

/** The pause the bot flies between manoeuvres, in laps: the judge's 1½ and a little more, measured by the judge itself. */
const val BOT_PAUSE_LAPS = PAUSE_LAPS + 0.08f

/**
 * After a turn-over onto the base clockwise the bot flies on this far (degrees) more than the turn-over's own lead-out:
 * the judge takes half a wingover as over only after 300° of level flight inverted.
 */
private const val TURN_OVER_PAUSE = 270f

/**
 * The azimuth (degrees) a turn-over onto the base clockwise and the pause after it take off the judge's pause, with a
 * margin: the turn-over's lead-in (anticlockwise) and the figure's (clockwise) cancel, its lead-out and the pause do not.
 */
private const val CLOCKWISE_COST = LEAD_OUT + TURN_OVER_PAUSE + 15f

/** Level flight of this much (degrees) or more ends a burst for the judge (it needs 150°). */
private const val PAUSE = 170f

/** A course that has not moved on for this long (s) has lost the plane: the bot recovers to the base. */
private const val NO_PROGRESS = 3f

/**
 * The routine half of the bot (design decision 2): what to fly, from the plane's flown progress and a read-only view of
 * the judge, which it never changes. It flies the take-off, then each manoeuvre of the schedule in order after the pause
 * the judge needs, waits on the base for the engine to stop, and glides to a landing.
 *
 * It keeps its own count of what it has flown and moves on when its course is flown, not on the clock. It waits for the
 * judge only between manoeuvres: the next one starts when the judge's pause since the last one is long enough (so a
 * manoeuvre the judge did not take still leaves a real pause). When the plane loses its course it records why and flies
 * the base, and the routine goes on with the next manoeuvre; nothing is skipped silently, and what the judge made of it
 * stands.
 */
class F2bRoutinePlanner(
    private val radius: Float,
    private val judge: () -> GuideInput,
    private val figures: F2bFigures = F2bFigures(radius),
    /** The manoeuvres to fly, in order: the whole schedule, or (for tests) the take-off and some of it. */
    private val schedule: List<Figure> = SCHEDULE,
) {
    private val steps: MutableList<Step> = plan()
    private var index = 0
    private var current: Course? = null

    /** What the bot does now. */
    var phase = BotPhase.TAKEOFF
        private set

    /** The manoeuvre the bot is flying or about to fly, or null. */
    var figure: Figure? = Figure.TAKEOFF
        private set

    /** Why the bot gave up a course, in order, for diagnostics. */
    val diagnostics: MutableList<String> = ArrayList()

    // the plane's place, kept the way the judge keeps it: the azimuth flown, not counting the zenith
    private var flown = 0f
    private var lastAzimuth: Float? = null
    private var stepStart = 0f
    private var progressAt = 0f
    private var progressTime = 0f
    private var time = 0f

    /** The course for the plane of [o]; call once per fixed step. */
    fun course(o: FlightObservation): Course {
        time = o.time
        val u = Vector3(o.offset).sub(0f, BASE_HEIGHT, 0f).nor()
        val azimuth = azimuthOf(u.x, u.z)
        val previous = lastAzimuth
        if (previous != null && latitudeOf(u) < 85f) flown += wrapDegrees(azimuth - previous)
        lastAzimuth = azimuth
        while (true) {
            val step = steps.getOrNull(index) ?: return done()
            if (current == null) {
                current = step.start(azimuth, o)
                stepStart = flown
                progressAt = 0f
                progressTime = time
                phase = step.phase
                figure = step.figure ?: figure
            }
            val course = checkNotNull(current)
            if (step.done(course, o)) {
                next()
                continue
            }
            if (course is PathCourse && lost(course)) {
                diagnostics += "%.1f s: lost the %s course of %s at %.0f%%".format(time, phase, figure?.label, 100f * course.travelled / course.length)
                recover(o)
                continue
            }
            return course
        }
    }

    /** After the last step (a schedule without the landing): the base, upright, from then on. */
    private fun done(): Course {
        phase = BotPhase.BASE
        return current ?: Parallel(radius, 0f).also { current = it }
    }

    private fun next() {
        index++
        current = null
    }

    /** Whether [course] has not moved on for [NO_PROGRESS] seconds. */
    private fun lost(course: PathCourse): Boolean {
        if (course.travelled > progressAt + 0.5f) {
            progressAt = course.travelled
            progressTime = time
        }
        return time - progressTime > NO_PROGRESS
    }

    /**
     * Gives up the current course: half a lap on the base in the direction the plane flies (and over onto the upright
     * base when that is clockwise), then on with the next manoeuvre's steps.
     */
    private fun recover(o: FlightObservation) {
        val u = Vector3(o.offset).sub(0f, BASE_HEIGHT, 0f).nor()
        val clockwise = Vector3(0f, 1f, 0f).crs(u).dot(o.velocity) < 0f
        val stuck = steps[index].figure
        index++
        while (index < steps.size && stuck != null && steps[index].figure == stuck) index++
        val recovery = ArrayList<Step>()
        recovery += Base(BotPhase.RECOVERY, null, clockwise) { flownHere -> kotlin.math.abs(flownHere) >= 180f }
        if (clockwise) recovery += Fly(BotPhase.RECOVERY, null) { figures.turnOver(it, clockwise = false) }
        steps.addAll(index, recovery)
        current = null
    }

    // the steps

    private abstract inner class Step(val phase: BotPhase, val figure: Figure?) {
        abstract fun start(azimuth: Float, o: FlightObservation): Course
        abstract fun done(course: Course, o: FlightObservation): Boolean
    }

    /** Flies [build]'s course from the plane's azimuth to its end. */
    private inner class Fly(phase: BotPhase, figure: Figure?, private val build: (Float) -> List<Vector3>) : Step(phase, figure) {
        override fun start(azimuth: Float, o: FlightObservation): Course = PathCourse(radius, build(azimuth))
        override fun done(course: Course, o: FlightObservation) = (course as PathCourse).remaining < 2f
    }

    /** Level on the base until [until]. */
    private inner class Base(
        phase: BotPhase, figure: Figure?, private val clockwise: Boolean, private val until: (Float) -> Boolean,
    ) : Step(phase, figure) {
        override fun start(azimuth: Float, o: FlightObservation): Course = Parallel(radius, 0f, clockwise)
        override fun done(course: Course, o: FlightObservation) = until(flown - stepStart)
    }

    /**
     * The base, upright, until the judge's pause since the last manoeuvre it took is [laps] laps, and long enough here
     * for the judge to have taken the burst before it.
     */
    private fun pauseFor(figure: Figure, laps: Float = BOT_PAUSE_LAPS) = Base(BotPhase.BASE, figure, false) { flownHere ->
        val j = judge()
        val pause = j.pauseLaps ?: 0f
        flownHere >= PAUSE && pause >= laps
    }

    /** The base, inverted, for [degrees] of azimuth. */
    private fun inverted(figure: Figure, degrees: Float, phase: BotPhase = BotPhase.BASE) =
        Base(phase, figure, true) { flownHere -> -flownHere >= degrees }

    /** Every step of [schedule]'s manoeuvres in order; each manoeuvre's steps start and end on the base, upright. */
    private fun plan(): MutableList<Step> = schedule.flatMapTo(ArrayList()) { stepsOf(it) }

    private fun stepsOf(figure: Figure): List<Step> = when (figure) {
        // the judge decides the take-off 3 laps after the start of the roll; the course ends after that
        Figure.TAKEOFF -> listOf(Fly(BotPhase.TAKEOFF, figure, figures::takeoff))
        Figure.REVERSE_WINGOVER -> plain(figure, figures::reverseWingover)
        Figure.INSIDE_LOOPS -> plain(figure, figures::insideLoops)
        // over onto the base clockwise, two laps and a little more, and back over
        Figure.INVERTED_LAPS -> listOf(
            pauseFor(figure),
            Fly(BotPhase.TURN_OVER, figure) { figures.turnOver(it, clockwise = true) },
            inverted(figure, 720f + 30f, BotPhase.FIGURE),
            Fly(BotPhase.TURN_OVER, figure) { figures.turnOver(it, clockwise = false) },
        )
        Figure.OUTSIDE_LOOPS -> clockwise(figure, figures::outsideLoops)
        Figure.INSIDE_SQUARES -> plain(figure, figures::insideSquares)
        Figure.OUTSIDE_SQUARES -> clockwise(figure, figures::outsideSquares)
        Figure.INSIDE_TRIANGLES -> plain(figure, figures::insideTriangles)
        Figure.HORIZONTAL_EIGHTS -> plain(figure, figures::horizontalEights)
        Figure.SQUARE_EIGHTS -> plain(figure, figures::squareEights)
        Figure.VERTICAL_EIGHTS -> plain(figure, figures::verticalEights)
        Figure.HOURGLASS -> plain(figure, figures::hourglass)
        Figure.OVERHEAD_EIGHTS -> plain(figure, figures::overheadEights)
        Figure.CLOVER -> plain(figure, figures::clover)
        // the engine stops under the normal fuel rule; then the glide to the ground
        Figure.LANDING -> listOf(FuelWait(), Glide())
    }

    /** The pause, then [build]'s figure from the base upright. */
    private fun plain(figure: Figure, build: (Float) -> List<Vector3>) = listOf(pauseFor(figure), Fly(BotPhase.FIGURE, figure, build))

    /**
     * A figure pushed from the base clockwise: the longer pause that pays for the azimuth flown clockwise before it, over
     * onto the base clockwise, the pause in which the judge takes that turn-over as over, the figure, the pause that ends
     * it, and back over.
     */
    private fun clockwise(figure: Figure, build: (Float) -> List<Vector3>) = listOf(
        pauseFor(figure, BOT_PAUSE_LAPS + CLOCKWISE_COST / 360f),
        Fly(BotPhase.TURN_OVER, figure) { figures.turnOver(it, clockwise = true) },
        inverted(figure, TURN_OVER_PAUSE),
        Fly(BotPhase.FIGURE, figure, build),
        inverted(figure, PAUSE),
        Fly(BotPhase.TURN_OVER, figure) { figures.turnOver(it, clockwise = false) },
    )

    private inner class FuelWait : Step(BotPhase.FUEL_WAIT, Figure.LANDING) {
        override fun start(azimuth: Float, o: FlightObservation): Course = Parallel(radius, 0f)
        override fun done(course: Course, o: FlightObservation) = !o.engineRunning
    }

    private inner class Glide : Step(BotPhase.GLIDE, Figure.LANDING) {
        override fun start(azimuth: Float, o: FlightObservation): Course = PathCourse(radius, figures.landing(azimuth))
        override fun done(course: Course, o: FlightObservation) = false
    }
}
