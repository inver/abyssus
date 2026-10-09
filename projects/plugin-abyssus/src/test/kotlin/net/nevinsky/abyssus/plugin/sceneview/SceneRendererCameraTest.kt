/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.gdx.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.gdx.editor.scene.cameraDirectionOf
import net.nevinsky.abyssus.lib.gdx.editor.scene.renderParamsOf
import net.nevinsky.abyssus.lib.gdx.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3
import net.nevinsky.abyssus.lib.gdx.editor.content.Quat
import net.nevinsky.abyssus.lib.gdx.editor.content.PlacementTransform

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.editor.parseScene
import net.nevinsky.abyssus.lib.gdx.editor.pick.DragResult
import net.nevinsky.abyssus.lib.gdx.editor.pick.GizmoAxis
import net.nevinsky.abyssus.lib.gdx.editor.pick.GizmoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The renderer's camera, picking and gizmo hits, which work on CPU-side data once a frame's camera is set (no GL). */
class SceneRendererCameraTest {
    private val width = 800
    private val height = 600

    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // the camera's frustum update is native
    }

    private fun renderer(): SceneRenderer {
        val params = renderParamsOf(
            parseScene(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()),
            CameraParams.DEFAULT,
        )
        return testRenderer().also { it.params = params }
    }

    private fun orbitAt(target: Vec3) = OrbitCamera(target, 10f, 0f, 0f)

    @Test
    fun lookingThroughACameraUsesItsPositionDirectionAndLens() {
        val r = renderer()
        r.state.viewCamera = "4"
        r.updateCamera(width, height, OrbitCamera(CameraParams.DEFAULT))
        val cam = r.frameCamera
        assertEquals(-23.56657f, cam.position.x, 1e-4f)
        assertEquals(12.318308f, cam.position.y, 1e-4f)
        assertEquals(-0.84287655f, cam.position.z, 1e-4f)
        // looking at entity 3, at the origin
        val toTarget = Vector3(0f, 0f, 0f).sub(cam.position).nor()
        assertEquals(toTarget.x, cam.direction.x, 1e-4f)
        assertEquals(toTarget.y, cam.direction.y, 1e-4f)
        assertEquals(toTarget.z, cam.direction.z, 1e-4f)
        assertEquals(1f, cam.near, 0f)
        assertEquals(100f, cam.far, 0f)
        assertEquals(67f, cam.fieldOfView, 0f)
    }

    @Test
    fun clearingTheViewCameraRestoresTheOrbitEye() {
        val r = renderer()
        val orbit = OrbitCamera(CameraParams.DEFAULT)
        r.updateCamera(width, height, orbit)
        val before = Vector3(r.frameCamera.position)
        r.state.viewCamera = "4"
        r.updateCamera(width, height, orbit)
        r.state.viewCamera = null
        r.updateCamera(width, height, orbit)
        assertEquals(before, r.frameCamera.position)
        val eye = orbit.position()
        assertEquals(eye.x, r.frameCamera.position.x, 0f)
        assertEquals(eye.y, r.frameCamera.position.y, 0f)
        assertEquals(eye.z, r.frameCamera.position.z, 0f)
    }

    @Test
    fun aMissingViewCameraFallsBackToTheOrbit() {
        val r = renderer()
        val orbit = OrbitCamera(CameraParams.DEFAULT)
        r.state.viewCamera = "99"
        r.updateCamera(width, height, orbit)
        assertEquals(orbit.position().x, r.frameCamera.position.x, 0f)
    }

    @Test
    fun clickingTheCameraBodyPicksIt() {
        val r = renderer()
        val p = r.params.content.cameras.single().position
        r.updateCamera(width, height, orbitAt(p))
        assertEquals("4", r.queries.pick(width / 2, height / 2, width, height))
        assertNull(r.queries.pick(5, 5, width, height))
    }

    private fun screenOf(r: SceneRenderer, p: Vec3): Pair<Int, Int> {
        val v = Vector3(p.x, p.y, p.z)
        r.frameCamera.project(v, 0f, 0f, width.toFloat(), height.toFloat())
        return v.x.toInt() to (height - v.y.toInt())
    }

    @Test
    fun theMoveGizmoOfTheSelectionIsHitAtItsArrowTips() {
        val r = renderer()
        val model = r.params.content.models.first { it.entityId == "0" }
        r.state.selectedId = "0"
        r.state.gizmoMode = GizmoMode.MOVE
        r.updateCamera(width, height, orbitAt(model.transform.position))
        val handles = r.queries.gizmoHandles(height)!!
        for (axis in GizmoAxis.entries) {
            val (x, y) = screenOf(r, handles.tip(axis))
            assertEquals(axis, r.queries.gizmoHit(x, y, width, height))
        }
        assertNull(r.queries.gizmoHit(2, 2, width, height))
    }

    @Test
    fun noSelectionMeansNoGizmo() {
        val r = renderer()
        r.updateCamera(width, height, orbitAt(Vec3(0f, 0f, 0f)))
        assertNull(r.queries.gizmoHandles(height))
        assertNull(r.queries.gizmoHit(width / 2, height / 2, width, height))
    }

    @Test
    fun aLookAtCameraHasNoRotateHandlesButMoves() {
        val r = renderer()
        val p = r.params.content.cameras.single().position
        r.state.selectedId = "4"
        r.updateCamera(width, height, orbitAt(p))
        r.state.gizmoMode = GizmoMode.ROTATE
        assertNull(r.queries.gizmoHandles(height))
        r.state.gizmoMode = GizmoMode.MOVE
        assertTrue(r.queries.gizmoHandles(height) != null)
    }

    @Test
    fun aPreviewMovesTheEntityOverTheSceneContent() {
        val r = renderer()
        val original = r.params.content.models.first { it.entityId == "0" }
        val moved = original.transform.copy(position = Vec3(1f, 2f, 3f))
        r.state.preview = mapOf("0" to DragResult(moved, null))
        assertEquals(Vec3(1f, 2f, 3f), r.content.models.first { it.entityId == "0" }.transform.position)
        assertEquals(original.transform.position, r.params.content.models.first { it.entityId == "0" }.transform.position)
        r.state.preview = emptyMap()
        assertEquals(original, r.content.models.first { it.entityId == "0" })
    }

    @Test
    fun movingTheLookAtTargetTurnsTheCameraFrustum() {
        val r = renderer()
        val camera = r.params.content.cameras.single()
        val before = cameraDirectionOf(camera, r.params.content.entityPositions)
        val target = r.params.content.entityPositions.getValue("3")
        val moved = PlacementTransform(Vec3(target.x, target.y + 20f, target.z), Quat.IDENTITY, Vec3(1f, 1f, 1f))
        r.state.preview = mapOf("3" to DragResult(moved, null))
        val c = r.content
        val after = cameraDirectionOf(c.cameras.single(), c.entityPositions)
        assertTrue(after.y > before.y + 0.5f)
    }
}
