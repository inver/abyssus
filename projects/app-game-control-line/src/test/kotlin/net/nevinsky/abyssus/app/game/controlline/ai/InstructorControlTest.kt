/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightControlSource
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The blend of the student's and the rescue's tilts, with scripted sources. */
class InstructorControlTest {
    private val stunter =
        PlaneComponent().apply {
            lineLength = 18f
            wingArea = 0.325f
            liftSlope = 8f
            elevatorEffect = 3f
        }

    private var studentCalls = 0
    private var rescueCalls = 0
    private var released = 0
    private var studentTilt = -1f

    private val control =
        InstructorControl(
            FlightControlSource {
                studentCalls++
                studentTilt
            },
            DangerGauge(Handling(stunter, 1.1f)),
            object : RescueSource {
                override fun tilt(observation: FlightObservation): Float = 0.5f.also { rescueCalls++ }

                override fun release() {
                    released++
                }
            },
        )

    private var time = 10f

    private fun observation(height: Float, vy: Float = 0f): FlightObservation =
        FlightObservation(
                time,
                Vector3(18f, height, 0f),
                Vector3(0f, vy, -22f),
                Vector3(),
                Vector3(0f, 0f, -1f),
                Vector3(0f, 1f, 0f),
                height,
                25f,
                100f,
                true,
                18f,
                null,
            )
            .also { time += 1f / 120f }

    @Test
    fun withNoDangerThePlayerHoldsTheWholeHandleAndTheRescueIsNotAsked() {
        control.tilt(observation(1.5f))
        assertEquals(-1f, control.tilt(observation(1.5f)), 0f)
        assertEquals(2, studentCalls)
        assertEquals(0, rescueCalls)
        assertEquals(0f, control.rescueTilt, 0f)
        assertFalse(control.ghost)
        assertFalse(control.holding)
        assertTrue(released > 0)
    }

    @Test
    fun withAllTheDangerTheInstructorHoldsTheWholeHandle() {
        control.tilt(observation(1.5f))
        val tilt = control.tilt(observation(0.4f, vy = -8f))
        assertEquals(1f, control.share, 0f)
        assertEquals(0.5f, tilt, 1e-6f)
        assertEquals(-1f, control.playerTilt, 0f)
        assertTrue(control.holding)
        assertTrue(control.ghost)
        assertEquals(2, studentCalls)
    }

    @Test
    fun aShareBlendsBothTilts() {
        control.tilt(observation(1.5f))
        // slack lines at 0.8 s have a danger of about half
        val slack = {
            FlightObservation(
                    time,
                    Vector3(18f, 1.5f, 0f),
                    Vector3(0f, 0f, -22f),
                    Vector3(),
                    Vector3(0f, 0f, -1f),
                    Vector3(0f, 1f, 0f),
                    1.5f,
                    0f,
                    100f,
                    true,
                    18f,
                    null,
                )
                .also { time += 1f / 120f }
        }
        var tilt = 0f
        repeat(96) { tilt = control.tilt(slack()) }
        val share = control.share
        assertEquals(0.5f, share, 0.03f)
        assertEquals((1f - share) * -1f + share * 0.5f, tilt, 1e-5f)
    }
}
