/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
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

    private fun setup(orbit: OrbitCamera = OrbitCamera.from(CameraParams.DEFAULT), ground: ((String) -> Float?)? = null): Setup {
        val renderer = testRenderer().also { it.params = mainParams }
        val interaction = SceneInteraction(renderer, orbit, ground ?: renderer::groundBelow)
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
    private fun movingModel(ground: ((String) -> Float?)? = null): Setup {
        val target = mainParams.content.models.first { it.entityId == "0" }.transform.position
        val s = setup(OrbitCamera(target, 12f, 0.4f, 0.4f), ground)
        s.renderer.selectedId = "0"
        s.renderer.updateCamera(width, height, s.orbit)
        return s
    }

    private fun droppingCamera(height: Float?): Setup = setup(ground = { height }).also { it.renderer.selectedId = "4" }

    @Test fun canDropIsFalseWithNothingSelected() { assertFalse(setup(ground = { 0f }).interaction.canDrop) }
    @Test fun canDropIsFalseWithNothingBelow() { assertFalse(droppingCamera(null).interaction.canDrop) }
    @Test fun canDropIsFalseForAnAlreadyRestingObject() {
        for (delta in listOf(0f, 0.00005f, -0.00005f)) assertFalse(droppingCamera(cameraPosition.y - 0.5f + delta).interaction.canDrop)
    }
    @Test fun canDropIsFalseDuringADrag() {
        val s = movingModel { 0f }
        val tip = screenOf(s.renderer, s.renderer.gizmoHandles(height)!!.tip(GizmoAxis.X))
        s.interaction.pressed(tip.first, tip.second, true)
        assertTrue(s.interaction.isDragging)
        assertFalse(s.interaction.canDrop)
        s.interaction.drop()
        assertTrue(s.interaction.isDragging)
        assertTrue(s.transforms.isEmpty())
    }
    @Test fun dropWithNothingBelowReportsNoTransform() {
        val s = droppingCamera(null); s.interaction.drop(); assertTrue(s.transforms.isEmpty()); assertTrue(s.renderer.preview.isEmpty())
    }
    @Test fun anAlreadyRestingObjectReportsNoTransform() {
        val s = droppingCamera(cameraPosition.y - 0.5f); s.interaction.drop(); assertTrue(s.transforms.isEmpty())
    }
    @Test fun dropReportsAYOnlyPositionTransform() {
        val s = droppingCamera(2f)
        assertTrue(s.interaction.canDrop)
        s.interaction.drop()
        val (id, edit) = s.transforms.single()
        assertEquals("4", id)
        assertEquals(Vec3(cameraPosition.x, 2.5f, cameraPosition.z), edit.position)
        assertNull(edit.rotation); assertNull(edit.direction)
        assertEquals(mainParams.content.cameras.single().rotation, s.renderer.content.cameras.single().rotation)
        assertEquals(edit.position, s.renderer.content.cameras.single().position)
        assertFalse(s.interaction.canDrop)
        s.interaction.drop(); assertEquals(1, s.transforms.size)
    }
    @Test fun dropCanRaiseASunkObject() {
        val s = droppingCamera(cameraPosition.y + 3f); s.interaction.drop()
        assertEquals(cameraPosition.y + 3.5f, s.transforms.single().second.position!!.y, 1e-5f)
    }
    @Test fun terrainsAndTheViewCameraCannotDrop() {
        val s = droppingCamera(0f)
        s.interaction.viewCamera = "4"; assertFalse(s.interaction.canDrop); s.interaction.drop()
        s.interaction.viewCamera = null; s.renderer.selectedId = "1"; assertFalse(s.interaction.canDrop); s.interaction.drop()
        assertTrue(s.transforms.isEmpty())
    }
    @Test fun aLightDropsUsingItsMarkerBottom() {
        val s = setup(ground = { 2f })
        val light = SceneContent.of(parseScene("""{"ecs":{"entities":{"9":{"components":{"TypeComponent":{"type":"LIGHT_POINT"},"LightComponent":{"light":{}},"PositionComponent":{"localPosition":{"x":1,"y":8,"z":3}}}}}}}"""))
        s.renderer.params = mainParams.copy(content = light); s.renderer.selectedId = "9"
        assertTrue(s.interaction.canDrop)
        s.interaction.drop()
        val position = s.transforms.single().second.position!!
        assertEquals(1f, position.x, 0f); assertEquals(3f, position.z, 0f)
        assertEquals(2.3f, position.y, ScenePicker.REST_EPS)
        assertFalse(s.interaction.canDrop)
    }

    @Test fun aRejectedDropDiscardsItsPreview() {
        val s = droppingCamera(0f); s.interaction.onTransform = { _, _ -> false }; s.interaction.drop()
        assertTrue(s.renderer.preview.isEmpty())
    }
    @Test fun aSynchronousDocumentRefreshKeepsTheDroppedPreview() {
        val s = droppingCamera(0f)
        s.interaction.onTransform = { id, edit ->
            val result = net.nevinsky.abyssus.sceneview.gizmo.DragResult(
                ScenePreview.selected(s.renderer.content, id)!!.transform.copy(position = edit.position!!), null)
            val fresh = mainParams.copy(content = ScenePreview.apply(mainParams.content, id, result))
            s.renderer.params = fresh; s.interaction.paramsChanged(fresh); true
        }
        s.interaction.drop(); assertFalse(s.interaction.canDrop)
    }
    @Test fun aParamsChangeRechecksDropAfterTheNextFrameEvenWithUnchangedDrawnIds() {
        var ground: Float? = null
        val s = setup(ground = { ground }); s.renderer.selectedId = "4"
        var changes = 0; s.interaction.onStateChanged = { changes++ }
        s.interaction.frameRendered(1L)
        s.interaction.paramsChanged(mainParams) // query still sees last frame's geometry
        ground = 0f // the next frame updates the same drawn entities
        s.interaction.frameRendered(1L)
        assertEquals(1, changes)
        assertTrue(s.interaction.canDrop)
    }
    @Test fun droppingFromALargeHeightSettlesInOneEdit() {
        val s = droppingCamera(1.234f)
        val original = mainParams.content.cameras.single()
        s.renderer.params = mainParams.copy(content = mainParams.content.copy(
            cameras = listOf(original.copy(position = original.position.copy(y = 10000f)))))
        s.interaction.drop()
        assertEquals(1.734f, s.transforms.single().second.position!!.y, 1e-5f)
        assertFalse(s.interaction.canDrop)
        s.interaction.drop()
        assertEquals(1, s.transforms.size)
    }

    @Test fun canDropTurnsOnWhenTheDrawnListsChange() {
        var ground: Float? = null
        val s = setup(ground = { ground }); s.renderer.selectedId = "4"
        var changes = 0; s.interaction.onStateChanged = { changes++ }
        s.interaction.frameRendered(0L); assertFalse(s.interaction.canDrop)
        ground = 0f; s.interaction.frameRendered(1L)
        assertTrue(s.interaction.canDrop); assertEquals(1, changes)
        s.interaction.frameRendered(2L); assertEquals(1, changes)
        ground = null; s.interaction.frameRendered(3L); assertFalse(s.interaction.canDrop); assertEquals(2, changes)
    }
    @Test fun anUnchangedDrawnVersionFiresNoStateChange() {
        var lookups = 0
        val s = setup(ground = { lookups++; null }); s.renderer.selectedId = "4"
        var changes = 0; s.interaction.onStateChanged = { changes++ }
        s.interaction.frameRendered(1L)
        val before = lookups
        s.interaction.frameRendered(1L)
        assertEquals(before, lookups); assertEquals(0, changes)
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
