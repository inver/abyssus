/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.arrange.PathCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private val points = listOf(Vector3(20f, 0f, 50f), Vector3(50f, 0f, 60f), Vector3(80f, 0f, 50f))

    private fun pixel(p: Vector3): Vector2 = projection.at(p)!!

    @Test
    fun aPointProjectsToTheScreen() {
        val p = pixel(Vector3(50f, 0f, 40f))
        assertEquals(400f, p.x, 1f)
        assertEquals(300f, p.y, 1f)
    }

    @Test
    fun aPointBehindTheCameraDoesNotProject() {
        assertNull(projection.at(Vector3(50f, 60f, 300f)))
    }

    @Test
    fun aPointHandleIsHitWithinTheRadius() {
        val handles = PathHandles(points, closed = false)
        val s = pixel(points[1])
        // a click on an edge of the diamond grabs the point, a click well away from it does not
        val edge = pixel(Vector3(points[1].x + PATH_POINT_HALF, points[1].y, points[1].z))
        assertEquals(PathHit.Point(1), handles.hit(projection, edge.x, edge.y))
        assertNull(handles.hit(projection, s.x, s.y + 150))
    }

    @Test
    fun theCurveBetweenPointsIsHitWithItsSpanAndPoint() {
        val handles = PathHandles(points, closed = false)
        val on = pixel(PathCurve.at(points, false, 0, 0.5f))
        val hit = handles.hit(projection, on.x, on.y + 2) as PathHit.Line
        assertEquals(0, hit.span)
        assertEquals(PathCurve.at(points, false, 0, 0.5f).x, hit.at.x, 1f)
    }
}
