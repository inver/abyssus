/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.ai.AiFlight
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.FlightRecording
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import org.junit.Assert.*
import org.junit.Test

/**
 * Spec `control-line-runtime-stability`, "A flight's drawn pose does not change its simulation": the same Watch AI
 * routine flown at 60 and at 144 frames a second, with the same fixed physics step, is the same flight.
 */
class FrameRateIndependenceTest {
    private class Flown(val recording: FlightRecording, val score: Int, val time: Float, val end: FlightEnd?)

    private fun fly(framesPerSecond: Int): Flown = AiFlight().use { ai ->
        val frame = 1f / framesPerSecond
        ai.fly(frames = generateSequence { frame }.iterator())
        Flown(ai.judge.recording("Stunter", AssistLevel.MID), ai.judge.sheet.tenths, ai.time, ai.result)
    }

    @Test fun sixtyAndOneHundredFortyFourFramesASecondFlyTheSameRoutine() {
        val slow = fly(60)
        val fast = fly(144)

        assertNotNull("the routine ends", slow.end)
        assertEquals(slow.end, fast.end)
        assertEquals(slow.time, fast.time, 0f)
        assertEquals("the same score", slow.score, fast.score)
        assertTrue("the routine is marked", slow.score > 0)
        assertEquals(slow.recording.count, fast.recording.count)
        assertArrayEquals("the same recorded path", slow.recording.samples, fast.recording.samples, 0f)
        assertEquals(slow.recording.judgements.size, fast.recording.judgements.size)
        slow.recording.judgements.zip(fast.recording.judgements).forEach { (a, b) ->
            assertEquals(a.index, b.index)
            assertEquals(a.tenths, b.tenths)
            assertEquals(a.reason, b.reason)
            assertEquals(a.start, b.start, 0f)
            assertEquals(a.end, b.end, 0f)
            assertEquals(a.deductions, b.deductions)
            assertArrayEquals(a.ideal, b.ideal, 0f)
        }
    }

    @Test fun thePlaneComponentHoldsTheSimulatedPoseBetweenFrames() {
        AiFlight().use { ai ->
            val body = ai.session.plane.getComponent(PositionComponent::class.java)
            // 2885 frames of 1/144 s are 2404 1/120 s steps and a sixth of one: the plane is in the air, and its drawn
            // pose lies between its last two simulated ones
            repeat(2885) { ai.session.advance(1f / 144f) }
            assertTrue("airborne", ai.last!!.height > AIRBORNE_HEIGHT)
            val simulated = Vector3(body.localPosition)
            assertFalse("the drawn pose trails the simulation", ai.session.drawnPosition.epsilonEquals(simulated, 1e-6f))
            ai.session.advance(0f)
            assertTrue("a frame with no step leaves the simulated pose alone", body.localPosition.epsilonEquals(simulated, 0f))
            assertTrue(
                "the drawn transform is at the drawn position",
                ai.session.drawnTransform().getTranslation(Vector3()).epsilonEquals(ai.session.drawnPosition, 1e-4f),
            )
        }
    }
}
