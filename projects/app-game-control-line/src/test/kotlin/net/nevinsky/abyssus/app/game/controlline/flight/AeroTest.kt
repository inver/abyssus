/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class AeroTest {
    private val aero = Aero()
    private val plane = PlaneComponent()

    /** The forces with the nose along +Z and the air coming at [alphaDegrees] from below, at [speed]. */
    private fun at(speed: Float, alphaDegrees: Float = 4f): AeroForces {
        val a = alphaDegrees * MathUtils.degreesToRadians
        val velocity = Vector3(0f, -sin(a), cos(a)).scl(speed)
        return aero.forces(plane, Quaternion(), velocity, Vector3(), 0f, 0f, AeroForces())
    }

    @Test
    fun zeroAirspeedGivesZeroLiftAndDrag() {
        val f = at(0f)
        assertEquals(0f, f.lift, 0f)
        assertEquals(0f, f.drag, 0f)
        assertEquals(0f, f.force.len(), 0f)
        assertEquals(0f, f.torque.len(), 0f)
    }

    @Test
    fun liftGrowsWithTheSquareOfSpeed() {
        val slow = at(10f)
        val fast = at(20f)
        assertTrue(slow.lift > 0f)
        assertEquals(4f, fast.lift / slow.lift, 1e-4f)
        assertEquals(4f, fast.drag / slow.drag, 1e-4f)
    }

    @Test
    fun liftPointsUpAndDragBackInLevelFlight() {
        val f = at(20f, alphaDegrees = 0f)
        // at zero angle of attack there is no lift; at 4 degrees the force has an up component
        assertEquals(0f, f.lift, 1e-5f)
        val climbing = at(20f)
        assertTrue(climbing.force.y > 0f)
        assertTrue(climbing.force.z < 0f)
    }

    @Test
    fun pastFifteenDegreesLiftStopsGrowingAndDragDoubles() {
        val stall = 15f * MathUtils.degreesToRadians
        val past = 20f * MathUtils.degreesToRadians
        assertEquals(aero.liftCoefficient(plane, stall), aero.liftCoefficient(plane, past), 1e-6f)
        assertEquals(aero.liftCoefficient(plane, -stall), aero.liftCoefficient(plane, -past), 1e-6f)
        val cl = plane.liftSlope * stall
        val unstalled = plane.zeroLiftDrag + cl * cl / (Math.PI.toFloat() * ASPECT_RATIO)
        assertEquals(unstalled, aero.dragCoefficient(plane, stall), 1e-6f)
        assertEquals(2f * unstalled, aero.dragCoefficient(plane, past), 1e-6f)
    }

    @Test
    fun noThrustAfterTheFuelTime() {
        plane.thrust = 6f
        plane.fuelTime = 60f
        assertEquals(6f, aero.thrust(plane, 0f), 0f)
        assertEquals(6f, aero.thrust(plane, 59.99f), 0f)
        assertEquals(0f, aero.thrust(plane, 60f), 0f)
        assertEquals(0f, aero.thrust(plane, 200f), 0f)
    }

    @Test
    fun lineDragMatchesTheFormula() {
        plane.lineDiameter = 0.0004f
        plane.lineLength = 18f
        val v = 22f
        assertEquals(1.225f * 0.0004f * 18f * v * v / 8f, aero.lineDrag(plane, v), 1e-6f)
        assertEquals(2f * aero.lineDrag(plane, v), at(v).lineDrag, 1e-5f)
    }

    @Test
    fun fullUpPitchesTheNoseUp() {
        val velocity = Vector3(0f, 0f, 20f)
        val up = aero.forces(plane, Quaternion(), velocity, Vector3(), ELEVATOR_THROW, 0f, AeroForces())
        val down = aero.forces(plane, Quaternion(), velocity, Vector3(), -ELEVATOR_THROW, 0f, AeroForces())
        // nose-up is a turn about -X
        assertTrue(up.torque.x < 0f)
        assertTrue(down.torque.x > 0f)
    }

    @Test
    fun thrustPullsAlongTheNoseTurnedOutward() {
        plane.thrust = 5f
        plane.engineOffset = 0f
        val f = aero.forces(plane, Quaternion(), Vector3(), Vector3(), 0f, 5f, AeroForces())
        assertEquals(Vector3(0f, 0f, 5f), f.force)
        plane.engineOffset = 10f
        val turned = aero.forces(plane, Quaternion(), Vector3(), Vector3(), 0f, 5f, AeroForces())
        assertTrue("outward is away from the inboard wing (+X)", turned.force.x < 0f)
    }
}
