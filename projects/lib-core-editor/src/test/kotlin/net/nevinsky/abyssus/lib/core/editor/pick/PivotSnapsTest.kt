/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.modelmesh.pivotMatrix
import org.junit.Assert.*
import org.junit.Test

class PivotSnapsTest {
    private val snaps = PivotSnaps()

    /** Triangle (0,0,0) (4,0,0) (0,3,0) in the XY plane, hit at [point]. */
    private fun hit(point: Vector3) =
        PartHit(1, "p", 0, point, Vector3(0f, 0f, 0f), Vector3(4f, 0f, 0f), Vector3(0f, 3f, 0f), 1f)

    private fun axis(frame: Matrix4, v: Vector3) = Vector3(v).mul(frame.getRotation(Quaternion(), true))

    private fun near(a: Vector3, b: Vector3, eps: Float = 1e-5f) = assertTrue("$a vs $b", a.dst(b) < eps)

    @Test
    fun edgeSnapPutsThePivotOnTheClosestEdgePoint() {
        // near the bottom edge (y = 0) at x = 1
        val frame = snaps.edge(hit(Vector3(1f, 0.1f, 0f)), Matrix4())
        near(Vector3(1f, 0f, 0f), frame.getTranslation(Vector3()))
        near(Vector3(1f, 0f, 0f), axis(frame, Vector3(1f, 0f, 0f)))
    }

    @Test
    fun edgeXPointsTheWayClosestToTheCurrentX() {
        val reversed = Matrix4().set(Vector3(), Quaternion().setFromAxis(0f, 0f, 1f, 180f))
        val frame = snaps.edge(hit(Vector3(1f, 0.1f, 0f)), reversed)
        near(Vector3(-1f, 0f, 0f), axis(frame, Vector3(1f, 0f, 0f)))
    }

    @Test
    fun edgeTurnsTheFrameAsLittleAsPossible() {
        // hypotenuse edge direction (-4,3,0)/5 or its reverse; current frame is identity (X = +x)
        val frame = snaps.edge(hit(Vector3(2f, 1.5f, 0f)), Matrix4())
        val x = axis(frame, Vector3(1f, 0f, 0f))
        near(Vector3(4f, -3f, 0f).nor(), x)
        // minimal turn: the Z axis stays +z because the turn is about z
        near(Vector3(0f, 0f, 1f), axis(frame, Vector3(0f, 0f, 1f)))
    }

    @Test
    fun faceSnapPutsYOnTheNormalAndProjectsX() {
        // a tilted triangle: normal of (0,0,0) (4,0,0) (0,3,0) is +z
        val frame = snaps.face(hit(Vector3(1f, 1f, 0f)), Matrix4())
        near(Vector3(1f, 1f, 0f), frame.getTranslation(Vector3()))
        near(Vector3(0f, 0f, 1f), axis(frame, Vector3(0f, 1f, 0f)))
        near(Vector3(1f, 0f, 0f), axis(frame, Vector3(1f, 0f, 0f)))
    }

    @Test
    fun faceSnapFallsBackToTheEdgeWhenXIsAlongTheNormal() {
        val alongNormal = Matrix4().set(Vector3(), Quaternion().setFromAxis(0f, 1f, 0f, -90f)) // X -> +z
        val frame = snaps.face(hit(Vector3(1f, 1f, 0f)), alongNormal)
        near(Vector3(0f, 0f, 1f), axis(frame, Vector3(0f, 1f, 0f)))
        near(Vector3(1f, 0f, 0f), axis(frame, Vector3(1f, 0f, 0f)))
    }

    @Test
    fun toFilePivotRoundTripsAFrame() {
        val frames = PivotFrames()
        val entity = Matrix4().set(Vector3(3f, 1f, -2f), Quaternion().setFromAxis(0f, 1f, 0f, 40f))
        val file = Matrix4().set(Vector3(0.5f, 0.2f, 0.1f), Quaternion().setFromAxis(1f, 0f, 0f, 25f))
        val world = Matrix4().set(Vector3(4f, 2f, 1f), Quaternion().setFromAxis(0.3f, 0.5f, 0.2f, 70f))
        val pivot = frames.toFilePivot(entity, file, world)
        val back = frames.worldFrame(entity, file, pivot)
        near(world.getTranslation(Vector3()), back.getTranslation(Vector3()))
        val a = world.getRotation(Quaternion(), true)
        val b = back.getRotation(Quaternion(), true)
        assertTrue(kotlin.math.abs(a.dot(b)) > 1 - 1e-5f)
        // and the node's own frame (no pivot) is the file frame under the entity
        near(Vector3(0f, 0f, 0f).mul(Matrix4(entity).mul(file)), frames.worldFrame(entity, file, null).getTranslation(Vector3()))
        assertNotNull(pivotMatrix(pivot))
    }
}
