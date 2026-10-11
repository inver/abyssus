/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathHandlesTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val camera = PerspectiveCamera(67f, 800f, 600f).also {
        it.position.set(50f, 60f, 110f)
        it.lookAt(50f, 0f, 40f)
        it.near = 0.1f
        it.far = 300f
        it.update()
    }
    private val projection = ScreenProjection(camera, 800, 600)
    private val points = listOf(Vec3(20f, 0f, 50f), Vec3(50f, 0f, 60f), Vec3(80f, 0f, 50f))

    private fun pixel(p: Vec3): ScreenPoint = projection.at(p)!!

    @Test
    fun aPointAndAMiddleProjectToTheScreen() {
        val p = pixel(Vec3(50f, 0f, 40f))
        assertEquals(400f, p.x, 1f)
        assertEquals(300f, p.y, 1f)
    }

    @Test
    fun aPointBehindTheCameraDoesNotProject() {
        assertNull(projection.at(Vec3(50f, 60f, 300f)))
    }

    @Test
    fun aPointHandleIsHitWithinTheRadius() {
        val handles = PathHandles(points, closed = false)
        val s = pixel(points[1])
        assertEquals(PathHit.Point(1), handles.hit(projection, s.x + 5, s.y - 5))
        assertNull(handles.hit(projection, s.x + 40, s.y))
    }

    @Test
    fun aMiddleHandleIsHitWhereNoPointIs() {
        val handles = PathHandles(points, closed = false)
        val s = pixel(handles.middle(0))
        assertEquals(PathHit.Middle(0), handles.hit(projection, s.x, s.y))
        assertEquals(2, handles.segmentCount)
    }

    @Test
    fun aPointHandleWinsOverAMiddleHandleUnderTheSamePixel() {
        val near = listOf(Vec3(50f, 0f, 50f), Vec3(50.2f, 0f, 50f))
        val handles = PathHandles(near, closed = false)
        val s = pixel(near[0])
        assertTrue(handles.hit(projection, s.x, s.y) is PathHit.Point)
    }

    @Test
    fun aClosedLineHasAClosingSegment() {
        val handles = PathHandles(points, closed = true)
        assertEquals(3, handles.segmentCount)
        val s = pixel(handles.middle(2))
        assertEquals(PathHit.Middle(2), handles.hit(projection, s.x, s.y))
    }

    @Test
    fun theLineIsHitNearASegmentOnly() {
        val handles = PathHandles(points, closed = false)
        val on = pixel(Vec3(35f, 0f, 55f))
        assertTrue(handles.lineDistance(projection, on.x, on.y + 3)!! < PATH_LINE_PX)
        assertTrue(handles.lineDistance(projection, on.x, on.y + 100)!! > PATH_LINE_PX)
        assertNotNull(handles.lineDistance(projection, 0f, 0f))
    }
}
