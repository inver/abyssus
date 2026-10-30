/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefFrame
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefView
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import net.nevinsky.abyssus.app.game.controlline.pattern.Grader
import net.nevinsky.abyssus.app.game.controlline.pattern.HemisphereGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2

class DebriefCameraTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // the camera's frustum update is native
    }

    private val geometry = HemisphereGeometry(18f)
    private val grader = Grader(18f)
    private val pilot = Vector3(3f, 0.2f, -4f)

    /** The frame of [figure] flown with its middle at [azimuth] degrees. */
    private fun frameOf(figure: Figure, azimuth: Float): DebriefFrame {
        val track = grader.idealFor(figure, azimuth)
        return DebriefFrame(track.points.map { geometry.position(it.u) }, 18f)
    }

    private fun check(figure: Figure, azimuth: Float, view: DebriefView) {
        val cameras = Cameras(1280f, 720f)
        val frame = frameOf(figure, azimuth)
        cameras.debrief(view, pilot, frame)
        val camera = cameras.camera
        assertTrue("fov ${camera.fieldOfView}", camera.fieldOfView in DEBRIEF_MIN_FOV..DEBRIEF_MAX_FOV)
        for (p in frame.points) {
            val world = Vector3(pilot).add(p)
            assertTrue("$figure $view at $azimuth: $p outside the frustum (fov ${camera.fieldOfView})", camera.frustum.pointInFrustum(world))
        }
    }

    @Test
    fun everyPointOfLoopsAndOverheadEightsIsInFrameInBothViews() {
        for (view in DebriefView.entries) {
            for (azimuth in listOf(-170f, -60f, 0f, 45f, 120f)) {
                check(Figure.INSIDE_LOOPS, azimuth, view)
                check(Figure.OVERHEAD_EIGHTS, azimuth, view)
            }
        }
    }

    /** The vertical eights reach from the horizon at 90° either side of their middle to the zenith: no view from the pilot's eyes holds them. */
    @Test
    fun everyPointOfTheVerticalEightsIsInFrameFromOutside() {
        for (azimuth in listOf(-170f, -60f, 0f, 45f, 120f)) check(Figure.VERTICAL_EIGHTS, azimuth, DebriefView.OUTSIDE)
    }

    @Test
    fun theOutsideEyeStandsOutsideTheCircleOnTheFiguresAzimuth() {
        val cameras = Cameras(1280f, 720f)
        val frame = frameOf(Figure.VERTICAL_EIGHTS, 70f)
        cameras.debrief(DebriefView.OUTSIDE, pilot, frame)
        val eye = Vector3(cameras.camera.position).sub(pilot)
        assertEquals(pilot.y + OUTSIDE_HEIGHT, cameras.camera.position.y, 1e-3f)
        assertEquals(OUTSIDE_DISTANCE * 18f, Math.hypot(eye.x.toDouble(), eye.z.toDouble()).toFloat(), 1e-3f)
        assertEquals(frame.azimuth, atan2(-eye.z, eye.x) * MathUtils.radiansToDegrees, 0.01f)
        assertTrue(Math.hypot(eye.x.toDouble(), eye.z.toDouble()) > 18.0)
    }

    @Test
    fun thePilotViewIsAtTheEyesLookingAtTheFigure() {
        val cameras = Cameras(1280f, 720f)
        val frame = frameOf(Figure.INSIDE_LOOPS, 0f)
        cameras.debrief(DebriefView.PILOT, pilot, frame)
        assertEquals(pilot.x, cameras.camera.position.x, 1e-4f)
        assertEquals(pilot.y + EYE_HEIGHT, cameras.camera.position.y, 1e-4f)
        val toCentre = Vector3(pilot).add(frame.centre).sub(cameras.camera.position).nor()
        // a loop fits from its middle, so that is where the camera looks
        assertEquals(1f, cameras.camera.direction.dot(toCentre), 1e-3f)
    }

    @Test
    fun otherCamerasGoBackToTheNormalFieldOfView() {
        val cameras = Cameras(1280f, 720f)
        cameras.debrief(DebriefView.PILOT, pilot, frameOf(Figure.VERTICAL_EIGHTS, 0f))
        cameras.flying(pilot, Vector3(10f, 3f, 0f))
        assertEquals(67f, cameras.camera.fieldOfView, 0f)
    }

    @Test
    fun aStraightUpFigureDoesNotBreakTheCamera() {
        val cameras = Cameras(1280f, 720f)
        cameras.debrief(DebriefView.PILOT, pilot, DebriefFrame(listOf(Vector3(0f, 10f, 0f), Vector3(0.5f, 12f, 0.5f))))
        assertTrue(cameras.camera.direction.len() > 0.99f)
        assertTrue(cameras.camera.up.x.isFinite() && cameras.camera.up.y.isFinite() && cameras.camera.up.z.isFinite())
    }
}
