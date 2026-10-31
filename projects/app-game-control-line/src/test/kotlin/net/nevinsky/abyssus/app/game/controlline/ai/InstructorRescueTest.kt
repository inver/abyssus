/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.flight.FlightControlSource
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.flight.fieldSession
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import net.nevinsky.abyssus.app.game.controlline.pattern.SCHEDULE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The instructor through the production flight model: a dive held at full down, and a good flight it must leave alone. */
class InstructorRescueTest {
    /** When the dive starts (s of flight): after the take-off and two laps on the base. */
    private val diveAt = 28f

    /** How long (s) the handle is held full down. */
    private val diveFor = 3f

    private class Run(val instructor: InstructorControl, val session: net.nevinsky.abyssus.app.game.controlline.flight.FlightSession) {
        var releasedAt: Float? = null
        var calmAt: Float? = null
        var shareZeroAt: Float? = null
        var calmFor = 0f
        var minHeight = Float.MAX_VALUE
    }

    private fun instructed(plane: String, diveAt: Float, diveFor: Float): Run {
        lateinit var instructor: InstructorControl
        lateinit var run: Run
        val session = fieldSession(plane) { settings, mass ->
            val handling = Handling(settings, mass)
            val bot = f2bBot(settings, mass, { error("Free flight has no judge") }, listOf(Figure.TAKEOFF))
            // the student flies the take-off and the base like the bot, except for the dive
            val student = FlightControlSource { o ->
                if (o.time in diveAt..(diveAt + diveFor)) -1f else bot.pilot.tilt(o, bot.planner.course(o))
            }
            instructor = InstructorControl(student, DangerGauge(handling), rescuePilot(settings.lineLength, handling))
            val watching = FlightControlSource { o ->
                val tilt = instructor.tilt(o)
                if (o.time > diveAt) run.minHeight = minOf(run.minHeight, o.height)
                if (o.time >= diveAt + diveFor && run.releasedAt == null) run.releasedAt = o.time
                if (run.releasedAt != null) {
                    // the start of the calm the share fell in: danger is 0 from there to the share's return to 0
                    run.calmAt = if (instructor.danger > 0f) null else run.calmAt ?: o.time
                    if (run.shareZeroAt == null && run.calmAt != null && instructor.share == 0f) {
                        run.shareZeroAt = o.time
                        run.calmFor = o.time - run.calmAt!!
                    }
                }
                tilt
            }
            null to watching
        }
        run = Run(instructor, session)
        return run
    }

    @Test
    fun everyBundledPlaneIsSavedFromADiveHeldAtFullDown() {
        for (plane in listOf("Mig-3", "Racer", "Stunter", "Trainer")) {
            val run = instructed(plane, diveAt, diveFor)
            while (run.session.flight.result == null && run.session.flight.time < diveAt + 12f) run.session.advance(1f / 60f)
            val at = run.session.flight.time
            assertNull("$plane ended by ${run.session.flight.result} at $at", run.session.flight.result)
            assertTrue("$plane: no takeover", run.instructor.takeovers(at).isNotEmpty())
            assertTrue("$plane: the share did not return to 0 (lowest ${run.minHeight} m)", run.shareZeroAt != null)
            // a second of calm, then the fall: over half a second at most, less from a smaller share
            assertTrue("$plane: handback took ${run.calmFor} s", run.calmFor in 1f..1.51f)
        }
    }

    /**
     * Watch AI dives down the meridian and pulls out a few centimetres short of what the danger rule allows, so a
     * flight that dives at the plane's limit is Assisted (`control-line-instructor`). What the instructor must leave
     * alone is the take-off and level flight, and when it does take over, it is in a figure, never on the base.
     */
    @Test
    fun theTakeOffAndTheBaseNeedNoTakeoverAndAnyFirstTakeoverIsInAFigure() {
        val plain = AiFlight(schedule = listOf(Figure.TAKEOFF)).use { f ->
            f.fly(limit = 60f)
            f.entry(Figure.TAKEOFF)?.tenths
        }
        AiFlight(schedule = listOf(Figure.TAKEOFF), instruct = true).use { f ->
            f.fly(limit = 60f)
            assertEquals(f.describe(), null, f.result)
            assertTrue(f.describe(), checkNotNull(f.instructor).takeovers(f.time).isEmpty())
            assertEquals(plain, f.entry(Figure.TAKEOFF)?.tenths)
        }
        AiFlight(instruct = true).use { f ->
            val instructor = checkNotNull(f.instructor)
            var phase: BotPhase? = null
            f.fly(limit = 120f) {
                if (phase == null && instructor.holding) phase = f.bot.planner.phase
                phase != null
            }
            assertTrue("first takeover during $phase\n${f.describe()}", phase == null || phase in listOf(BotPhase.FIGURE, BotPhase.TURN_OVER))
        }
    }
}
