/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.parseScene
import net.nevinsky.abyssus.sceneview.gizmo.GizmoAxis
import net.nevinsky.abyssus.sceneview.gizmo.GizmoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Click, drag and key handling of the scene view, on a renderer whose camera was set by a headless frame (no GL). */
class SceneInteractionTest {
    private val width = 800
    private val height = 600

    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val mainParams = SceneRenderParams.from(
        parseScene(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()),
        CameraParams.DEFAULT,
    )

    private class Setup(val renderer: SceneRenderer, val orbit: OrbitCamera, val interaction: SceneInteraction) {
        val picked = mutableListOf<String>()
        val transforms = mutableListOf<Pair<String, TransformEdit>>()
    }

    private fun setup(orbit: OrbitCamera = OrbitCamera.from(CameraParams.DEFAULT)): Setup {
        val renderer = testRenderer().also { it.params = mainParams }
        val interaction = SceneInteraction(renderer, orbit)
        interaction.size = ViewSize(width, height, width, height)
        renderer.updateCamera(width, height, orbit)
        return Setup(renderer, orbit, interaction).also { s ->
            interaction.onPick = { s.picked += it }
            interaction.onTransform = { id, edit -> s.transforms.add(id to edit); true }
        }
    }

    private fun screenOf(r: SceneRenderer, p: Vec3): Pair<Int, Int> {
        val v = Vector3(p.x, p.y, p.z)
        r.frameCamera.project(v, 0f, 0f, width.toFloat(), height.toFloat())
        return v.x.toInt() to (height - v.y.toInt())
    }

    private val cameraPosition get() = mainParams.content.cameras.single().position

    /** A scene view looking at entity 0 with the Move gizmo selected on it. */
    private fun movingModel(): Setup {
        val target = mainParams.content.models.first { it.entityId == "0" }.transform.position
        val s = setup(OrbitCamera(target, 12f, 0.4f, 0.4f))
        s.renderer.selectedId = "0"
        s.renderer.updateCamera(width, height, s.orbit)
        return s
    }

    @Test
    fun aClickOnTheCameraBodySelectsItAndReportsThePick() {
        val s = setup(OrbitCamera(cameraPosition, 10f, 0f, 0f))
        s.interaction.pressed(width / 2, height / 2, true)
        s.interaction.released(width / 2, height / 2, true)
        assertEquals("4", s.interaction.selectedId)
        assertEquals(listOf("4"), s.picked)
    }

    @Test
    fun aClickOnEmptySpaceClearsTheSelection() {
        val s = setup(OrbitCamera(cameraPosition, 10f, 0f, 0f))
        s.renderer.selectedId = "4"
        s.interaction.pressed(5, 5, true)
        s.interaction.released(5, 5, true)
        assertNull(s.interaction.selectedId)
        assertTrue(s.picked.isEmpty())
    }

    @Test
    fun aSelectedEntityThatLeavesTheSceneIsDeselected() {
        val s = setup()
        s.renderer.selectedId = "0"
        s.interaction.paramsChanged(SceneRenderParams.DEFAULT)
        assertNull(s.interaction.selectedId)
    }

    @Test
    fun theModeIsMoveFirstAndSwitches() {
        val s = setup()
        assertEquals(GizmoMode.MOVE, s.interaction.mode)
        s.interaction.mode = GizmoMode.ROTATE
        assertEquals(GizmoMode.ROTATE, s.renderer.gizmoMode)
        s.interaction.mode = GizmoMode.MOVE
        assertEquals(GizmoMode.MOVE, s.renderer.gizmoMode)
    }

    @Test
    fun theSelectorListsFreeCameraAndTheSceneCamerasByName() {
        assertEquals(listOf("Free camera", "Camera 4"), cameraChoices(mainParams.content, "Free camera").map { it.label })
        assertEquals(listOf(null, "4"), cameraChoices(mainParams.content, "Free camera").map { it.id })
        val unnamed = SceneContent.of(parseScene("""{"ecs":{"entities":{"8":{"components":{"CameraComponent":{}}}}}}"""))
        assertEquals("8", cameraChoices(unnamed, "Free camera")[1].label)
    }

    @Test
    fun removingTheLookedThroughCameraSwitchesBackToTheFreeView() {
        val s = setup()
        var changed = 0
        s.interaction.onStateChanged = { changed++ }
        s.interaction.viewCamera = "4"
        s.interaction.paramsChanged(SceneRenderParams.DEFAULT)
        assertNull(s.interaction.viewCamera)
        assertTrue(changed >= 2)
    }

    @Test
    fun draggingAMoveArrowMovesTheObjectAndWritesOnceOnRelease() {
        val s = movingModel()
        val handles = s.renderer.gizmoHandles(height)!!
        val origin = handles.origin
        val tip = screenOf(s.renderer, handles.tip(GizmoAxis.X))
        val farther = screenOf(s.renderer, Vec3(origin.x + 2 * handles.size, origin.y, origin.z))
        val yaw = s.orbit.yaw
        val pitch = s.orbit.pitch
        s.interaction.pressed(tip.first, tip.second, true)
        assertTrue(s.interaction.isDragging)
        s.interaction.dragged(farther.first, farther.second, true)
        // shown live over the scene before anything is written
        assertEquals(origin.x + handles.size, s.renderer.content.models.first { it.entityId == "0" }.transform.position.x, handles.size * 0.1f)
        assertTrue(s.transforms.isEmpty())
        s.interaction.released(farther.first, farther.second, true)
        assertEquals(1, s.transforms.size)
        val (id, edit) = s.transforms.single()
        assertEquals("0", id)
        assertEquals(origin.x + handles.size, edit.position!!.x, handles.size * 0.1f)
        assertEquals(origin.y, edit.position!!.y, 1e-3f)
        assertEquals(origin.z, edit.position!!.z, 1e-3f)
        assertNull(edit.rotation)
        // the drag did not touch the orbit view
        assertEquals(yaw, s.orbit.yaw, 0f)
        assertEquals(pitch, s.orbit.pitch, 0f)
    }

    @Test
    fun draggingOnEmptySpaceOrbitsAndWritesNothing() {
        val s = movingModel()
        val yaw = s.orbit.yaw
        s.interaction.pressed(5, 5, true)
        assertFalse(s.interaction.isDragging)
        s.interaction.dragged(60, 5, true)
        s.interaction.released(60, 5, true)
        assertTrue(s.orbit.yaw != yaw)
        assertTrue(s.transforms.isEmpty())
        assertEquals("0", s.interaction.selectedId)
    }

    @Test
    fun aPressAndReleaseOnAHandleWithoutMovingWritesNothing() {
        val s = movingModel()
        val tip = screenOf(s.renderer, s.renderer.gizmoHandles(height)!!.tip(GizmoAxis.X))
        s.interaction.pressed(tip.first, tip.second, true)
        s.interaction.released(tip.first, tip.second, true)
        assertTrue(s.transforms.isEmpty())
        assertEquals("0", s.interaction.selectedId)
        assertTrue(s.renderer.preview.isEmpty())
    }

    @Test
    fun escapeCancelsADragAndWritesNothing() {
        val s = movingModel()
        val handles = s.renderer.gizmoHandles(height)!!
        val tip = screenOf(s.renderer, handles.tip(GizmoAxis.X))
        val farther = screenOf(s.renderer, Vec3(handles.origin.x + 2 * handles.size, handles.origin.y, handles.origin.z))
        val start = mainParams.content.models.first { it.entityId == "0" }.transform.position
        s.interaction.pressed(tip.first, tip.second, true)
        s.interaction.dragged(farther.first, farther.second, true)
        s.interaction.escape()
        assertFalse(s.interaction.isDragging)
        assertEquals(start, s.renderer.content.models.first { it.entityId == "0" }.transform.position)
        s.interaction.dragged(farther.first + 10, farther.second, true)
        s.interaction.released(farther.first, farther.second, true)
        assertTrue(s.transforms.isEmpty())
        assertEquals("0", s.interaction.selectedId)
    }

    @Test
    fun aRejectedWriteDropsThePreview() {
        val s = movingModel()
        s.interaction.onTransform = { _, _ -> false }
        val handles = s.renderer.gizmoHandles(height)!!
        val tip = screenOf(s.renderer, handles.tip(GizmoAxis.X))
        val farther = screenOf(s.renderer, Vec3(handles.origin.x + 2 * handles.size, handles.origin.y, handles.origin.z))
        s.interaction.pressed(tip.first, tip.second, true)
        s.interaction.dragged(farther.first, farther.second, true)
        s.interaction.released(farther.first, farther.second, true)
        assertTrue(s.renderer.preview.isEmpty())
    }

    @Test
    fun rotatingARingWritesTheRotation() {
        val s = movingModel()
        s.interaction.mode = GizmoMode.ROTATE
        val handles = s.renderer.gizmoHandles(height)!!
        val o = handles.origin
        // a quarter turn about Y: from +X on the ring to +Z... in screen terms, between two points of the Y ring
        val from = screenOf(s.renderer, Vec3(o.x + handles.size, o.y, o.z))
        val to = screenOf(s.renderer, Vec3(o.x, o.y, o.z + handles.size))
        s.interaction.pressed(from.first, from.second, true)
        s.interaction.dragged(to.first, to.second, true)
        s.interaction.released(to.first, to.second, true)
        val (_, edit) = s.transforms.single()
        assertNull(edit.position)
        val q = edit.rotation!!
        assertTrue("rotated about Y: $q", kotlin.math.abs(q.y) > 0.5f && kotlin.math.abs(q.x) < 0.2f && kotlin.math.abs(q.z) < 0.2f)
    }

    @Test
    fun lookingThroughACameraFreezesOrbitPanAndZoomButNotSelection() {
        val s = setup(OrbitCamera(cameraPosition, 10f, 0f, 0f))
        s.interaction.viewCamera = "4"
        s.renderer.updateCamera(width, height, s.orbit)
        val yaw = s.orbit.yaw
        val target = s.orbit.target
        val distance = s.orbit.distance
        s.interaction.pressed(5, 5, true)
        s.interaction.dragged(80, 40, true)
        s.interaction.released(80, 40, true)
        s.interaction.pressed(5, 5, false)
        s.interaction.dragged(80, 40, false)
        s.interaction.released(80, 40, false)
        s.interaction.wheel(3f)
        assertEquals(yaw, s.orbit.yaw, 0f)
        assertEquals(target, s.orbit.target)
        assertEquals(distance, s.orbit.distance, 0f)
        assertTrue(s.transforms.isEmpty())
    }
}
