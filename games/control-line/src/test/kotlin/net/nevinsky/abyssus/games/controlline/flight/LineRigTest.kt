/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flight

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.games.controlline.track.wrapDegrees
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LineRigTest {
    private fun FieldFlight.anchors() = rig.lines.map { line -> Vector3().also { a -> line.anchors(a, Vector3()) } to Vector3().also { b -> line.anchors(Vector3(), b) } }

    @Test
    fun takeoffPlacesThePlaneAtItsLineLengthAlongTheCircleWithTautLines() = FieldFlight("Trainer").use { f ->
        val length = f.settings.lineLength
        assertEquals(2, f.rig.lines.size)
        // the longer line is exactly its length: taut
        val longest = f.anchors().maxOf { (a, b) -> a.dst(b) }
        assertEquals(length, longest, 0.01f)
        // on the circle at azimuth 0, on the ground, nose along the circle (-Z there), inboard wing towards the pilot
        assertEquals(0f, f.rig.azimuthOfPlane(), 0.5f)
        assertTrue("height ${f.height}", f.height < 0.3f)
        val nose = f.rotation.transform(Vector3(0f, 0f, 1f))
        assertTrue("nose $nose", nose.z < -0.95f)
        val inboard = f.rotation.transform(Vector3(1f, 0f, 0f))
        assertTrue("inboard wing $inboard", inboard.x < -0.95f)
        // with the engine running the lines pull at once
        f.fly(0.5f)
        assertTrue("tension ${f.flight.tension}", f.flight.tension >= CONTROL_TENSION)
    }

    @Test
    fun afterHalfALapTheHandleFacesThePlaneAndTheLinesEndAtTheLeadouts() = FieldFlight("Trainer").use { f ->
        var steps = 0
        while (abs(wrapDegrees(f.rig.azimuthOfPlane() - 180f)) > 2f && steps++ < 20 * 120) f.step()
        assertTrue("never reached half a lap", steps < 20 * 120)
        f.step()

        val pilot = f.pilot.getComponent(PositionComponent::class.java)
        val facing = pilot.localRotation.transform(Vector3(0f, 0f, 1f))
        val toPlane = Vector3(f.position).sub(pilot.localPosition).also { it.y = 0f }.nor()
        assertTrue("pilot faces $facing, the plane is towards $toPlane", facing.dot(toPlane) > 0.999f)

        val transform = f.plane.getComponent(PositionComponent::class.java).getTransform()
        val leadouts = listOf(f.settings.leadoutUp, f.settings.leadoutDown).map { Vector3(it).mul(transform) }
        val handle = f.rig.handle
        val spacing = f.rig.pilotSettings.handleSpacing
        val handlePoints = listOf(Vector3(handle).add(0f, spacing / 2, 0f), Vector3(handle).sub(0f, spacing / 2, 0f))
        f.anchors().forEachIndexed { i, (a, b) ->
            assertEquals("line $i leaves the handle", 0f, a.dst(handlePoints[i]), 0.01f)
            assertEquals("line $i ends at its leadout", 0f, b.dst(leadouts[i]), 0.01f)
        }
    }

    @Test
    fun belowOneNewtonTheElevatorStaysNeutral() = FieldFlight("Trainer") { thrust = 0f }.use { f ->
        f.fly(0.2f)
        assertTrue(f.flight.tension < CONTROL_TENSION)
        f.rig.tilt = 1f
        assertEquals(0f, f.rig.elevator, 0f)
        f.fly(0.2f, tilt = -1f)
        assertEquals(0f, f.rig.elevator, 0f)
    }

    @Test
    fun tautLinesPassTheHandleToTheElevator() = FieldFlight("Trainer").use { f ->
        f.fly(3f)
        assertTrue(f.flight.tension >= CONTROL_TENSION)
        f.rig.tilt = 1f
        assertEquals(ELEVATOR_THROW, f.rig.elevator, 1e-6f)
        f.rig.tilt = -0.5f
        assertEquals(-0.5f * ELEVATOR_THROW, f.rig.elevator, 1e-6f)
    }
}
