/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.pattern.FLIGHT_TIME
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import net.nevinsky.abyssus.app.game.controlline.pattern.SCHEDULE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * The whole Watch AI demonstration with real physics: the bundled, unchanged `Stunter` from rest at its takeoff place,
 * flown by the production bot through the production session, judged by the normal judge. Every manoeuvre in schedule
 * order with a positive mark, the engine stopping under the normal fuel rule, and a landing by 7:00 with the line ends
 * inside the circle. The same under any frame timing and across a pause.
 */
class F2bAiRoutineTest {
    private class Outcome(val report: String, val marks: List<Int>, val end: FlightEnd?, val time: Float, val lineEnds: Float)

    /** Flies the whole routine in [frames]; [paused] says, before each frame, whether the game is paused (no advance). */
    private fun fly(frames: Iterator<Float>, paused: (Float) -> Boolean = { false }): Outcome = AiFlight().use { f ->
        val gated = object : Iterator<Float> {
            override fun hasNext() = true
            override fun next(): Float {
                // a paused game draws frames without advancing the flight: its time stands still
                while (paused(f.time)) {
                    frames.next()
                    pausedFrames++
                    if (pausedFrames > 600) break
                }
                return frames.next()
            }
        }
        f.fly(frames = gated)
        assertComplete(f)
        Outcome(f.describe(), SCHEDULE.map { f.entry(it)?.tenths ?: -1 }, f.result, f.time, f.lineEnds)
    }

    private var pausedFrames = 0

    private fun assertComplete(f: AiFlight) {
        val report = f.describe()
        assertEquals(report, FlightEnd.LANDED, f.result)
        assertTrue(report, f.time <= FLIGHT_TIME)
        assertTrue(report, f.lineEnds <= 18.5f)
        assertTrue(report, f.lastSample?.inverted == false)
        assertTrue(report, f.bot.planner.diagnostics.isEmpty())
        assertEquals("the engine stopped under the fuel rule\n$report", 360f, checkNotNull(f.engineStopped) { report }, 0.05f)
        var last = -1f
        for (figure in SCHEDULE) {
            val entry = checkNotNull(f.entry(figure)) { "${figure.label} undecided\n$report" }
            // positive: recognised with its repeats, not too soon, in time, not after a crash
            assertNull("${figure.label}\n$report", entry.reason)
            assertTrue("${figure.label}\n$report", entry.tenths >= 10)
            // in schedule order
            assertTrue("${figure.label} out of order\n$report", entry.start >= last)
            last = entry.start
        }
        // the bot flew each manoeuvre once, in order, after the take-off
        val flown = f.phases.filter { it.first == BotPhase.FIGURE }.map { it.second }
        assertEquals(report, SCHEDULE.drop(1).dropLast(1), flown)
    }

    @Test
    fun theWholeScheduleFromRestToALandingInsideTheCircle() {
        val outcome = fly(generateSequence { 1f / 60f }.iterator())
        println(outcome.report)
    }

    @Test
    fun irregularFramesAndAPauseInAFigureFlyTheSameRoutine() {
        val regular = fly(generateSequence { 1f / 60f }.iterator())
        val random = Random(42)
        // frames of 4 to 40 ms, and now and then a stall the session clamps
        val irregular = fly(generateSequence { if (random.nextInt(200) == 0) 0.25f else 0.004f + random.nextFloat() * 0.036f }.iterator())
        // paused for 300 frames in the inside loops, at about 1:30
        var pausedAt: Float? = null
        val pauses = fly(generateSequence { 1f / 60f }.iterator()) { time ->
            if (pausedAt == null && time >= 90f) pausedAt = time
            pausedAt != null && pausedFrames < 300
        }
        for ((name, other) in listOf("irregular frames" to irregular, "a pause" to pauses)) {
            assertEquals("$name\n${other.report}", regular.marks, other.marks)
            assertEquals(name, regular.end, other.end)
            assertEquals(name, regular.time, other.time, 0.05f)
            assertEquals(name, regular.lineEnds, other.lineEnds, 0.05f)
        }
        assertEquals(300, pausedFrames)
    }
}
