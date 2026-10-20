/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class FlightControlSourceTest {
    /** Records every observation and answers with [tilt] of it. */
    private class Recorder(val tilt: (FlightObservation) -> Float = { 0f }) : FlightControlSource {
        val seen = ArrayList<FlightObservation>()
        override fun tilt(observation: FlightObservation): Float {
            seen += observation
            return tilt.invoke(observation)
        }
    }

    @Test
    fun theFirstObservationIsThePlaneAtRestAtTakeoff() {
        val recorder = Recorder()
        fieldSession("Stunter", control = recorder).use { it.advance(PHYSICS_STEP) }
        val first = recorder.seen.single()
        assertEquals(0f, first.time, 0f)
        assertEquals(0f, first.tension, 0f)
        assertEquals(0f, first.velocity.len(), 1e-3f)
        assertEquals(18f, first.lineLength, 0f)
        assertEquals(360f, first.fuelLeft, 0f)
        assertTrue(first.engineRunning)
        assertNull(first.end)
        assertTrue("on the ground: ${first.height}", first.height < 0.5f)
        assertEquals("near the circle", 18f, first.offset.len(), 1f)
    }

    @Test
    fun theSourceIsAskedOncePerFixedStepWithConsecutiveTimes() {
        val recorder = Recorder()
        fieldSession("Stunter", control = recorder).use { session ->
            repeat(60) { session.advance(1f / 60f) }
        }
        assertEquals(120, recorder.seen.size)
        recorder.seen.forEachIndexed { i, o -> assertEquals("step $i", i * PHYSICS_STEP, o.time, 1e-4f) }
    }

    @Test
    fun tiltIsClampedAndNotFiniteIsNeutral() {
        fieldSession("Stunter", control = { 5f }).use { session ->
            session.advance(PHYSICS_STEP)
            assertEquals(1f, session.rig.tilt, 0f)
        }
        fieldSession("Stunter", control = { -3f }).use { session ->
            session.advance(PHYSICS_STEP)
            assertEquals(-1f, session.rig.tilt, 0f)
        }
        fieldSession("Stunter", control = { Float.NaN }).use { session ->
            session.advance(PHYSICS_STEP)
            assertEquals(0f, session.rig.tilt, 0f)
        }
    }

    @Test
    fun observationsAreCopiesTheSourceMayKeep() {
        val recorder = Recorder()
        fieldSession("Stunter", control = recorder).use { session -> repeat(30) { session.advance(0.1f) } }
        val first = recorder.seen.first().offset
        val last = recorder.seen.last().offset
        assertTrue("the plane moved, and the kept vectors with it", first.dst(last) > 1f)
    }

    @Test
    fun theHumanHandleRampsOneStepAtATimeAsBefore() {
        val input = HandleInput().also { it.key("W", true) }
        val reference = HandleInput().also { it.key("W", true) }
        val tilts = ArrayList<Float>()
        fieldSession("Stunter", control = HumanControl(input)).use { session ->
            repeat(30) {
                session.advance(PHYSICS_STEP)
                tilts += session.rig.tilt
            }
        }
        tilts.forEachIndexed { i, t ->
            reference.update(PHYSICS_STEP)
            assertEquals("step $i", reference.tilt, t, 0f)
        }
        assertEquals(1f, tilts.last(), 0f)
    }

    @Test
    fun regularAndIrregularFramesGiveTheSameFlightAtEqualSimulatedTime() {
        fun fly(frames: List<Float>): List<FlightObservation> {
            val recorder = Recorder { o -> if (o.time > 6f && o.time < 6.1f) 0.05f else 0f }
            fieldSession("Stunter", control = recorder).use { session ->
                frames.forEach(session::advance)
                assertNull("the flight goes on: ${session.flight.result} at ${session.flight.time} s", session.flight.result)
            }
            return recorder.seen
        }
        val total = 10f
        val regular = fly(List(600) { 1f / 60f })
        val random = Random(7)
        val irregular = ArrayList<Float>()
        var sum = 0f
        while (sum < total - 0.05f) {
            val frame = (0.004f + random.nextFloat() * 0.04f).coerceAtMost(total - sum)
            irregular += frame
            sum += frame
        }
        irregular += total - sum
        val other = fly(irregular)
        assertEquals("both flew 10 s", 10f, regular.last().time, 0.02f)
        assertEquals(regular.size, other.size)
        for (i in regular.indices) {
            assertEquals("time of step $i", regular[i].time, other[i].time, 1e-4f)
            assertTrue("place of step $i", regular[i].offset.epsilonEquals(other[i].offset, 1e-3f))
        }
    }
}
