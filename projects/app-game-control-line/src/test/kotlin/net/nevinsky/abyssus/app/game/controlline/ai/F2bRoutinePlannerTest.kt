/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.pattern.BASE_HEIGHT
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import net.nevinsky.abyssus.app.game.controlline.pattern.GuideInput
import net.nevinsky.abyssus.app.game.controlline.pattern.place
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The routine planner without physics: a stand-in plane flies exactly the course it is given, and the judge's progress
 * is whatever the test says.
 */
class F2bRoutinePlannerTest {
    private val radius = 18f

    /** A plane that flies whatever course it is given, perfectly, at [speed] m/s, starting at rest at azimuth 0. */
    private class Ghost(private val radius: Float, var speed: Float = 20f) {
        var u: Vector3 = place(-4f, 0f)
        var heading = Vector3(0f, 0f, -1f)
        var time = 0f
        var engineRunning = true

        /** When false, it stays where it is (a plane that does not follow its course). */
        var moving = true

        fun observe(): FlightObservation = FlightObservation(
            time, Vector3(u).scl(radius).add(0f, BASE_HEIGHT, 0f), Vector3(heading).scl(speed), Vector3(), Vector3(heading),
            Vector3(u).crs(heading), 1.5f, 20f, if (engineRunning) 100f else 0f, engineRunning, radius, null,
        )

        private var flying: Course? = null
        private var along = 0f

        /** Moves on [course]: along a path by the distance flown, along a parallel from the nearest place on it; then aims as the pilot does. */
        fun follow(course: Course) {
            move(course)
            course.aim(u, 0f)
        }

        private fun move(course: Course) {
            time += PHYSICS_STEP
            if (!moving) return
            val step = speed * PHYSICS_STEP
            if (course is PathCourse) {
                if (course !== flying) along = course.travelled
                flying = course
                along += step
                val i = course.indexAt(along)
                val j = minOf(i + 1, course.points.size - 1)
                val span = course.distance[j] - course.distance[i]
                val t = if (span > 0f) ((along - course.distance[i]) / span).coerceIn(0f, 1f) else 0f
                u = Vector3(course.points[i]).lerp(course.points[j], t).nor()
                if (j > i) heading = tangentAt(u, Vector3(course.points[j]).sub(course.points[i]))
                return
            }
            flying = course
            val aim = course.aim(u, 0f)
            heading = Vector3(aim.tangent)
            val angle = step / radius
            u = Vector3(aim.point).scl(cos(angle)).mulAdd(heading, sin(angle)).nor()
            heading = tangentAt(u, heading)
        }
    }

    private class Judge {
        var pauseLaps: Float? = null
        fun input() = GuideInput(Figure.REVERSE_WINGOVER, pauseLaps, false, null, true)
    }

    private class Run(radius: Float, schedule: List<Figure>) {
        val judge = Judge()
        val planner = F2bRoutinePlanner(radius, judge::input, schedule = schedule)
        val ghost = Ghost(radius)

        /** Each (phase, figure) in the order the planner went through them, with when. */
        val phases = ArrayList<Triple<BotPhase, Figure?, Float>>()

        fun step() {
            val course = planner.course(ghost.observe())
            if (phases.lastOrNull()?.let { it.first != planner.phase || it.second != planner.figure } != false) {
                phases += Triple(planner.phase, planner.figure, ghost.time)
            }
            ghost.follow(course)
        }

        fun fly(seconds: Float, until: () -> Boolean = { false }) {
            val end = ghost.time + seconds
            while (ghost.time < end && !until()) step()
        }
    }

    @Test
    fun theTakeoffThenEachManoeuvreAfterItsPause() {
        val run = Run(radius, listOf(Figure.TAKEOFF, Figure.REVERSE_WINGOVER, Figure.INSIDE_LOOPS))
        run.judge.pauseLaps = 2f
        run.fly(120f)
        assertEquals(
            listOf(
                BotPhase.TAKEOFF to Figure.TAKEOFF, BotPhase.BASE to Figure.REVERSE_WINGOVER, BotPhase.FIGURE to Figure.REVERSE_WINGOVER,
                BotPhase.BASE to Figure.INSIDE_LOOPS, BotPhase.FIGURE to Figure.INSIDE_LOOPS,
                // the schedule given is done: level on the base from then on
                BotPhase.BASE to Figure.INSIDE_LOOPS,
            ),
            run.phases.map { it.first to it.second },
        )
        assertTrue(run.planner.diagnostics.isEmpty())
    }

    @Test
    fun theNextManoeuvreWaitsForTheJudgesPause() {
        val run = Run(radius, listOf(Figure.TAKEOFF, Figure.INSIDE_LOOPS))
        run.judge.pauseLaps = BOT_PAUSE_LAPS - 0.01f
        run.fly(120f)
        assertEquals(BotPhase.BASE, run.planner.phase)
        run.judge.pauseLaps = BOT_PAUSE_LAPS
        run.fly(1f)
        assertEquals(BotPhase.FIGURE, run.planner.phase)
    }

    @Test
    fun evenWithTheJudgesPauseDoneItFliesLevelLongEnoughToEndTheLastBurst() {
        val run = Run(radius, listOf(Figure.TAKEOFF, Figure.INSIDE_LOOPS))
        run.judge.pauseLaps = 5f
        run.fly(120f) { run.planner.phase == BotPhase.FIGURE }
        val base = run.phases.first { it.first == BotPhase.BASE }.third
        val figure = run.phases.first { it.first == BotPhase.FIGURE }.third
        val degrees = (figure - base) * run.ghost.speed / radius * 180f / Math.PI.toFloat()
        assertTrue("flew $degrees° on the base", degrees >= 170f)
    }

    @Test
    fun aManoeuvrePushedFromInvertedFlightPaysForTheClockwiseFlyingBeforeIt() {
        val run = Run(radius, listOf(Figure.TAKEOFF, Figure.OUTSIDE_LOOPS))
        run.judge.pauseLaps = 2.2f
        run.fly(120f)
        assertEquals("1.5 laps is not enough", BotPhase.BASE, run.planner.phase)
        run.judge.pauseLaps = 2.6f
        run.fly(30f) { run.planner.phase != BotPhase.BASE }
        assertEquals(BotPhase.TURN_OVER, run.planner.phase)
        // over onto the base clockwise, the pause, the loops pushed from it, the pause, back over, and on along the base
        run.fly(120f)
        assertEquals(
            listOf(BotPhase.TURN_OVER, BotPhase.BASE, BotPhase.FIGURE, BotPhase.BASE, BotPhase.TURN_OVER, BotPhase.BASE),
            run.phases.dropWhile { it.first != BotPhase.TURN_OVER }.map { it.first },
        )
    }

    @Test
    fun theEightsCrossingIsFlownThroughNotJumpedTo() {
        val run = Run(radius, listOf(Figure.TAKEOFF, Figure.HORIZONTAL_EIGHTS))
        run.judge.pauseLaps = 2f
        run.fly(200f) { run.planner.phase == BotPhase.FIGURE }
        run.fly(60f) { run.planner.phase != BotPhase.FIGURE }
        val start = run.phases.first { it.first == BotPhase.FIGURE }.third
        val flown = (run.ghost.time - start) * run.ghost.speed
        val length = PathCourse(radius, F2bFigures(radius).horizontalEights(0f)).length
        assertEquals("the whole course, once", length, flown, 0.05f * length)
    }

    @Test
    fun aPlaneThatDoesNotFollowItsCourseIsRecoveredAndTheRoutineGoesOn() {
        val run = Run(radius, listOf(Figure.TAKEOFF, Figure.INSIDE_LOOPS, Figure.INSIDE_SQUARES))
        run.judge.pauseLaps = 2f
        run.fly(200f) { run.planner.phase == BotPhase.FIGURE }
        run.ghost.moving = false
        run.fly(5f)
        assertEquals(BotPhase.RECOVERY, run.planner.phase)
        assertEquals(1, run.planner.diagnostics.size)
        assertTrue(run.planner.diagnostics.single(), run.planner.diagnostics.single().contains(Figure.INSIDE_LOOPS.label))
        run.ghost.moving = true
        run.fly(60f) { run.planner.figure == Figure.INSIDE_SQUARES && run.planner.phase == BotPhase.FIGURE }
        assertEquals(Figure.INSIDE_SQUARES, run.planner.figure)
        assertEquals(BotPhase.FIGURE, run.planner.phase)
    }

    @Test
    fun afterTheLastManoeuvreItWaitsForTheEngineThenGlides() {
        val run = Run(radius, listOf(Figure.TAKEOFF, Figure.LANDING))
        run.fly(60f)
        assertEquals(BotPhase.FUEL_WAIT, run.planner.phase)
        run.ghost.engineRunning = false
        run.fly(1f)
        assertEquals(BotPhase.GLIDE, run.planner.phase)
    }

    @Test
    fun neitherHalfOfTheBotChangesWhatItIsGiven() {
        val stunter = PlaneComponent().apply { lineLength = radius; wingArea = 0.325f; liftSlope = 8f; elevatorEffect = 3f }
        val judge = Judge().also { it.pauseLaps = 2f }
        val bot = f2bBot(stunter, 1.1f, judge::input)
        val ghost = Ghost(radius)
        repeat(3000) {
            val o = ghost.observe()
            val copies = listOf(o.offset, o.velocity, o.angularVelocity, o.nose, o.up).map(::Vector3)
            bot.tilt(o)
            assertEquals(copies, listOf(o.offset, o.velocity, o.angularVelocity, o.nose, o.up))
            ghost.follow(bot.planner.course(o))
        }
    }
}
