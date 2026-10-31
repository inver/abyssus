/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.vitrine

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitrineLayoutTest {
    private fun table(x: Float, z: Float, yaw: Float = 0f, top: Float = 0.75f) = VitrineTable(Vector3(x, 0f, z), yaw, top)

    /** A box-shaped plane [halfHeight] tall around its origin, its lowest point [offset] below the origin. */
    private fun plane(halfHeight: Float, offset: Float = halfHeight, rotation: Quaternion = Quaternion()) = VitrinePlane(
        Vector3(4f, 0.1f, 0f), rotation,
        listOf(Vector3(-0.5f, -offset, -1f), Vector3(0.5f, -offset + 2 * halfHeight, 1f)),
    )

    private fun lowestOf(pose: VitrinePose, plane: VitrinePlane) = plane.points!!.minOf { pose.rotation.transform(Vector3(it)).y + pose.position.y }

    @Test
    fun aPlaneRestsOnTheTableTopWhateverItsSize() {
        val tables = listOf(table(30f, 0f), table(30f, 3f), table(30f, 6f))
        val planes = listOf(plane(0.05f), plane(0.2f), plane(0.4f, offset = 0.1f))
        val poses = vitrinePoses(planes, tables)
        for (i in planes.indices) assertEquals("plane $i", 0.75f, lowestOf(poses[i]!!, planes[i]), 1e-4f)
    }

    @Test
    fun eachPlaneStandsOnItsOwnTableInOrder() {
        val tables = listOf(table(30f, 0f), table(30f, 3f))
        val poses = vitrinePoses(listOf(plane(0.1f), plane(0.1f)), tables)
        assertEquals(0f, poses[0]!!.position.z, 1e-4f)
        assertEquals(3f, poses[1]!!.position.z, 1e-4f)
        assertEquals(30f, poses[1]!!.position.x, 1e-4f)
    }

    @Test
    fun aPlaneAddedTakesTheNextFreeTable() {
        val tables = listOf(table(30f, 0f), table(30f, 3f), table(30f, 6f))
        val two = vitrinePoses(listOf(plane(0.1f), plane(0.1f)), tables)
        val three = vitrinePoses(listOf(plane(0.1f), plane(0.1f), plane(0.1f)), tables)
        assertEquals(two[0]!!.position, three[0]!!.position)
        assertEquals(6f, three[2]!!.position.z, 1e-4f)
    }

    @Test
    fun planesBeyondTheLastTableStandOnTheGroundInLineWithThem() {
        val tables = listOf(table(30f, 0f), table(30f, 3f))
        val planes = listOf(plane(0.1f), plane(0.1f), plane(0.1f), plane(0.1f))
        val poses = vitrinePoses(planes, tables)
        // the third continues the row, 3 m after the last table, and its lowest point is on the ground
        assertEquals(6f, poses[2]!!.position.z, 1e-4f)
        assertEquals(9f, poses[3]!!.position.z, 1e-4f)
        assertEquals(30f, poses[3]!!.position.x, 1e-4f)
        assertEquals(0f, lowestOf(poses[2]!!, planes[2]), 1e-4f)
        assertTrue(poses.all { it != null })
    }

    @Test
    fun withASingleTableTheGroundPlanesAreSpacedByTheDefault() {
        val poses = vitrinePoses(listOf(plane(0.1f), plane(0.1f)), listOf(table(30f, 0f)))
        assertEquals(GROUND_SPACING, poses[1]!!.position.z, 1e-4f)
    }

    @Test
    fun withoutTablesNothingMoves() {
        assertEquals(listOf(null, null), vitrinePoses(listOf(plane(0.1f), plane(0.1f)), emptyList()))
    }

    @Test
    fun aPlaneFacesTheWayItsTableDoesAndKeepsItsTilt() {
        val yaw = 90f * MathUtils.degreesToRadians
        // authored facing -90 degrees with the nose 5 degrees up
        val authored = Quaternion().setFromAxisRad(Vector3.Y, -yaw).mul(Quaternion().setFromAxis(Vector3.X, -5f))
        val planes = listOf(plane(0.1f, rotation = authored))
        val pose = vitrinePoses(planes, listOf(table(30f, 0f, yaw)))[0]!!
        val nose = pose.rotation.transform(Vector3(0f, 0f, 1f))
        // facing along +X (table yaw 90 degrees), tilted up by the authored 5 degrees
        assertEquals(MathUtils.cosDeg(5f), nose.x, 1e-3f)
        assertEquals(MathUtils.sinDeg(5f), nose.y, 1e-3f)
        assertEquals(0f, nose.z, 1e-3f)
    }

    @Test
    fun aPlaneWithoutAModelRestsOnItsOrigin() {
        val poses = vitrinePoses(listOf(VitrinePlane(Vector3(), Quaternion(), null)), listOf(table(30f, 0f)))
        assertEquals(0.75f, poses[0]!!.position.y, 1e-4f)
        assertNull(vitrinePoses(listOf(VitrinePlane(Vector3(), Quaternion(), null)), emptyList())[0])
    }
}
