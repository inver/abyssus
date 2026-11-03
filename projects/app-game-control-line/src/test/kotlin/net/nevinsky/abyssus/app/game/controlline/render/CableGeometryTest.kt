/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

class CableGeometryTest {
    private val geometry = CableGeometry()

    private fun build(points: List<Vector3>, radius: Float = 0.05f): Pair<FloatArray, ShortArray> {
        val vertices = FloatArray(geometry.vertexCount(points.size) * CABLE_VERTEX_FLOATS)
        val indices = ShortArray(geometry.indexCount(points.size))
        val written = geometry.build(points, FloatArray(points.size) { radius }, vertices, 0, indices, 0)
        assertEquals(indices.size, written)
        return vertices to indices
    }

    private fun position(vertices: FloatArray, v: Int) = Vector3(vertices[v * 6], vertices[v * 6 + 1], vertices[v * 6 + 2])
    private fun normal(vertices: FloatArray, v: Int) = Vector3(vertices[v * 6 + 3], vertices[v * 6 + 4], vertices[v * 6 + 5])

    @Test
    fun aTwoPointCableHasTwoRingsAndTwoTrianglesASide() {
        assertEquals(12, geometry.vertexCount(2))
        assertEquals(36, geometry.indexCount(2))
        assertEquals(36, geometry.indexCount(2).let { (2 - 1) * CABLE_SIDES * 6 })
        assertEquals(3 * CABLE_SIDES, geometry.vertexCount(3))
    }

    @Test
    fun everyRingLiesAtTheRadiusAroundItsPointAcrossTheLine() {
        val points = listOf(Vector3(0f, 1.2f, 0f), Vector3(10f, 1.5f, -6f))
        val (vertices, _) = build(points, 0.05f)
        val axis = Vector3(points[1]).sub(points[0]).nor()
        for (ring in 0..1) for (s in 0 until CABLE_SIDES) {
            val offset = position(vertices, ring * CABLE_SIDES + s).sub(points[ring])
            assertEquals("distance from the axis point", 0.05f, offset.len(), 1e-5f)
            assertEquals("the ring is across the line", 0f, offset.dot(axis), 1e-5f)
        }
    }

    @Test
    fun normalsAreUnitAndPointAwayFromTheLine() {
        val points = listOf(Vector3(0f, 1f, 0f), Vector3(4f, 3f, 2f), Vector3(9f, 3f, 2f))
        val (vertices, _) = build(points)
        for (v in 0 until geometry.vertexCount(points.size)) {
            val n = normal(vertices, v)
            assertEquals(1f, n.len(), 1e-5f)
            val ringPoint = points[v / CABLE_SIDES]
            val offset = position(vertices, v).sub(ringPoint)
            assertTrue("normal $n against offset $offset", n.dot(offset) > 0f)
        }
    }

    @Test
    fun everyTriangleFacesOutwardAndEveryIndexIsInRange() {
        for (points in listOf(
            listOf(Vector3(0f, 1f, 0f), Vector3(10f, 1f, 3f)),
            listOf(Vector3(0f, 0f, 0f), Vector3(0f, 8f, 0f)), // straight up: the frame must not collapse
            listOf(Vector3(0f, 1f, 0f), Vector3(3f, 1f, 1f), Vector3(6f, 2f, 1f), Vector3(9f, 4f, 3f)),
        )) {
            val (vertices, indices) = build(points)
            val count = geometry.vertexCount(points.size)
            for (t in indices.indices step 3) {
                val ids = IntArray(3) { indices[t + it].toInt() }
                ids.forEach { assertTrue("index $it of $count", it in 0 until count) }
                val a = position(vertices, ids[0]); val b = position(vertices, ids[1]); val c = position(vertices, ids[2])
                val face = Vector3(b).sub(a).crs(Vector3(c).sub(a))
                val outward = normal(vertices, ids[0]).add(normal(vertices, ids[1])).add(normal(vertices, ids[2]))
                assertTrue("triangle $t of ${points.size} points faces inward", face.dot(outward) > 0f)
            }
        }
    }

    @Test
    fun theRingsAreCentredOnThePolylineEndToEnd() {
        val points = listOf(Vector3(1f, 2f, 3f), Vector3(4f, 6f, 8f))
        val (vertices, _) = build(points)
        for (ring in 0..1) {
            val centre = Vector3()
            for (s in 0 until CABLE_SIDES) centre.add(position(vertices, ring * CABLE_SIDES + s))
            centre.scl(1f / CABLE_SIDES)
            assertEquals(0f, centre.dst(points[ring]), 1e-5f)
        }
    }

    @Test
    fun aThinWireIsThickenedToAPixelAndATooFatOneIsNot() {
        // 60 degrees, 720 px: at 20 m a pixel is 2 * 20 * tan(30) / 720 = 0.0321 m, so 0.75 px of radius is 0.0241 m
        val far = geometry.radiusAt(wireRadius = 0.0002f, distance = 20f, fovDegrees = 60f, viewportHeight = 720f)
        assertEquals(0.0241f, far, 0.0005f)
        // very close: the wire's own radius is thicker than the pixel rule
        assertEquals(0.0002f, geometry.radiusAt(0.0002f, 0.05f, 60f, 720f), 0f)
        // twice as far, twice as thick (on screen the width stays the same)
        assertEquals(2f * far, geometry.radiusAt(0.0002f, 40f, 60f, 720f), 1e-5f)
    }

    @Test
    fun aCableNeedsTwoPointsAndTheGeometryTwoRadii() {
        try {
            Cable(listOf(Vector3()), 0.001f, Color.WHITE)
            fail("one point was accepted")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            geometry.build(listOf(Vector3(), Vector3(1f, 0f, 0f)), FloatArray(1), FloatArray(100), 0, ShortArray(100), 0)
            fail("a missing radius was accepted")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun theSecondCableOfASharedBufferStartsAfterTheFirst() {
        val points = listOf(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f))
        val vertices = FloatArray(geometry.vertexCount(2) * 2 * CABLE_VERTEX_FLOATS)
        val indices = ShortArray(geometry.indexCount(2) * 2)
        val first = geometry.build(points, floatArrayOf(0.01f, 0.01f), vertices, 0, indices, 0)
        geometry.build(points, floatArrayOf(0.01f, 0.01f), vertices, geometry.vertexCount(2), indices, first)
        assertTrue("the second cable refers to its own vertices", (first until indices.size).all { indices[it] >= geometry.vertexCount(2) })
        assertEquals(0.0f, abs(position(vertices, 0).x), 1e-6f)
    }
}
