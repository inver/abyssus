/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The danger measure on observations made up for it. */
class DangerGaugeTest {
    private val stunter = PlaneComponent().apply { lineLength = 18f; wingArea = 0.325f; liftSlope = 8f; elevatorEffect = 3f }

    private fun gauge() = DangerGauge(Handling(stunter, 1.1f)).also {
        // a plane that has been in the air
        it.danger(observation(height = 1.5f))
    }

    private fun observation(
        height: Float, vy: Float = 0f, speed: Float = 22f, upright: Boolean = true, tension: Float = 25f,
    ): FlightObservation = FlightObservation(
        10f, Vector3(18f, height, 0f), Vector3(0f, vy, -speed), Vector3(), Vector3(0f, 0f, -1f),
        Vector3(0f, if (upright) 1f else -1f, 0f), height, tension, 100f, true, 18f, null,
    )

    @Test
    fun levelFlightAtTheBaseIsSafeUprightAndInverted() {
        val gauge = gauge()
        assertEquals(0f, gauge.danger(observation(1.5f)), 0f)
        assertEquals(0f, gauge.danger(observation(1.5f, upright = false)), 0f)
    }

    @Test
    fun aDiveAtTheBaseIsDangerousBeforeTheMarginIsGone() {
        val gauge = gauge()
        // 20 degrees down at 22 m/s, at the base
        val danger = gauge.danger(observation(1.5f, vy = -7.5f))
        assertTrue("danger $danger", danger > 0f)
        assertEquals(1f, gauge.danger(observation(0.5f, vy = -7.5f)), 0f)
    }

    @Test
    fun anUprightGlideIsNotADanger() {
        assertEquals(0f, gauge().danger(observation(0.6f, vy = -1f, speed = 12f)), 0f)
    }

    @Test
    fun invertedCloseToTheGroundIsLost() {
        val gauge = gauge()
        assertEquals(1f, gauge.danger(observation(0.4f, upright = false)), 0f)
        assertEquals(0.5f, gauge.danger(observation(0.65f, upright = false)), 1e-4f)
    }

    @Test
    fun slackLinesRiseFromPointFourToOnePointTwoSeconds() {
        val gauge = gauge()
        var danger = 0f
        repeat(36) { danger = gauge.danger(observation(1.5f, tension = 0f)) }
        assertEquals("0.3 s", 0f, danger, 0f)
        repeat(60) { danger = gauge.danger(observation(1.5f, tension = 0f)) }
        assertEquals("0.8 s", 0.5f, danger, 0.03f)
        assertEquals("taut again", 0f, gauge.danger(observation(1.5f)), 0f)
    }

    @Test
    fun theGroundRollIsNotADanger() {
        val gauge = DangerGauge(Handling(stunter, 1.1f))
        assertEquals(0f, gauge.danger(observation(0.1f, vy = -5f, speed = 5f, upright = false)), 0f)
        assertEquals(0f, gauge().danger(observation(0.5f, vy = -3f, speed = 7f)), 0f)
    }
}
