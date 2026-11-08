/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPart
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPartsInfo
import net.nevinsky.abyssus.lib.core.editor.modelmesh.NO_PIVOT
import net.nevinsky.abyssus.lib.core.editor.modelmesh.PartTriangles
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import org.junit.Assert.*
import org.junit.Test

private const val PW = 800
private const val PH = 600

/** Part selection and Edit Pivot gestures over a hand-built frame: real queries and interaction, no GL. */
class SceneInteractionPartsTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private fun camera() = PerspectiveCamera(67f, PW.toFloat(), PH.toFloat()).also {
        it.position.set(0f, 1f, 10f)
        it.lookAt(0f, 1f, 0f)
        it.near = 0.1f
        it.far = 100f
        it.update()
    }

    /** One part `flap`: a square x -1..1, y 0..2 at z 0 made of two triangles. */
    private val flap: ModelPartsInfo = run {
        val triangles = PartTriangles(
            floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 1f, 2f, 0f, -1f, 2f, 0f),
            intArrayOf(0, 1, 2, 0, 2, 3),
        )
        val part = ModelPart("flap", emptyList(), BoundingBox(Vector3(-1f, 0f, -0.01f), Vector3(1f, 2f, 0.01f)), NO_PIVOT)
        ModelPartsInfo(listOf(part), mapOf("flap" to Matrix4()), mapOf("flap" to Matrix4()), mapOf("flap" to triangles))
    }

    private class Fixture(
        val state: SceneViewState,
        val queries: SceneQueries,
        val interaction: SceneInteraction,
        val camera: PerspectiveCamera,
    ) {
        val picked = mutableListOf<Pair<Long, String>>()
        val moves = mutableListOf<Triple<Long, String, Matrix4>>()
        var accept = true

        fun pixel(x: Float, y: Float): Pair<Int, Int> {
            val v = Vector3(x, y, 0f)
            camera.project(v, 0f, 0f, PW.toFloat(), PH.toFloat())
            return v.x.toInt() to (PH - v.y.toInt())
        }

        fun click(x: Float, y: Float, alt: Boolean = false) {
            val (px, py) = pixel(x, y)
            interaction.pressed(px, py, true)
            interaction.released(px, py, true, 1, alt)
        }
    }

    private fun fixture(): Fixture {
        val state = SceneViewState()
        val content = SceneContent(
            models = listOf(
                AssetPlacement(7L, "plane", PlacementTransform.IDENTITY),
                AssetPlacement(8L, "other", PlacementTransform(Vector3(20f, 0f, 0f), Quaternion(), Vector3(1f, 1f, 1f))),
            )
        )
        val box = BoundingBox(Vector3(-1f, 0f, -1f), Vector3(1f, 2f, 1f))
        val frame = FrameSnapshot(
            camera(),
            listOf(snapshotBoxOf(7L, box, Matrix4()), snapshotBoxOf(8L, box, Matrix4().setToTranslation(20f, 0f, 0f))),
            emptyList(),
            3L,
        )
        val queries = SnapshotSceneQueries({ frame }, state, parts = { name -> flap.takeIf { name == "plane" } }) { content }
        val interaction = SceneInteraction(state, queries, OrbitCamera(Vector3(), 10f, 0f, 0.6f)).also {
            it.size = ViewSize(PW, PH, PW, PH)
        }
        return Fixture(state, queries, interaction, frame.camera).also { f ->
            interaction.onPickPart = { id, node -> f.picked += id to node }
            interaction.onPivotMove = { id, node, frame -> f.moves += Triple(id, node, frame); f.accept }
        }
    }

    @Test
    fun altClickOnATriangleSelectsTheEntityAndItsPart() {
        val f = fixture()
        f.click(0f, 1f, alt = true)
        assertEquals(7L, f.state.selectedId)
        assertEquals("flap", f.state.selectedPart)
        assertEquals(listOf(7L to "flap"), f.picked)
    }

    @Test
    fun altClickOnNothingClearsThePartAndKeepsTheEntity() {
        val f = fixture()
        f.click(0f, 1f, alt = true)
        f.click(5f, 5f, alt = true)
        assertEquals(7L, f.state.selectedId)
        assertNull(f.state.selectedPart)
    }

    @Test
    fun aPlainClickOnAnotherEntitySelectsItAndClearsThePartAndEditPivot() {
        val f = fixture()
        f.click(0f, 1f, alt = true)
        f.state.editPivot = true
        f.click(20f, 1f)
        assertEquals(8L, f.state.selectedId)
        assertNull(f.state.selectedPart)
        assertFalse(f.state.editPivot)
    }

    @Test
    fun edgeSnapSetsThePivotOnTheNearestEdgeAndMissesWriteNothing() {
        val f = fixture()
        f.click(0f, 1f, alt = true)
        f.state.editPivot = true
        f.state.pivotSnap = PivotSnap.EDGE
        f.click(-0.9f, 1f)
        val move = f.moves.single()
        assertEquals(7L, move.first)
        assertEquals("flap", move.second)
        val origin = move.third.getTranslation(Vector3())
        assertEquals(-1f, origin.x, 1e-3f)
        assertEquals(1f, origin.y, 0.6f)
        // X is along the left edge (vertical)
        val x = Vector3(1f, 0f, 0f).mul(move.third.getRotation(Quaternion(), true))
        assertEquals(1f, kotlin.math.abs(x.y), 1e-3f)

        f.moves.clear()
        f.click(5f, 5f)
        assertTrue(f.moves.isEmpty())
    }

    @Test
    fun faceSnapPutsYOnTheNormal() {
        val f = fixture()
        f.click(0f, 1f, alt = true)
        f.state.editPivot = true
        f.state.pivotSnap = PivotSnap.FACE
        f.click(0.3f, 0.7f)
        val frame = f.moves.single().third
        val y = Vector3(0f, 1f, 0f).mul(frame.getRotation(Quaternion(), true))
        assertEquals(1f, kotlin.math.abs(y.z), 1e-3f)
    }

    @Test
    fun theGizmoSitsOnThePivotFrameInEditPivot() {
        val f = fixture()
        f.click(0f, 1f, alt = true)
        val entityGizmo = checkNotNull(f.queries.gizmoHandles(PH))
        f.state.editPivot = true
        f.state.pivotPreview = Matrix4().setToTranslation(0.5f, 0.25f, 0f)
        val pivotGizmo = checkNotNull(f.queries.gizmoHandles(PH))
        assertEquals(0f, entityGizmo.origin.x, 1e-4f)
        assertEquals(0.5f, pivotGizmo.origin.x, 1e-4f)
        assertEquals(0.25f, pivotGizmo.origin.y, 1e-4f)
    }

    @Test
    fun editPivotIsOffOnOpenAndNeedsAPartWithAnApplicablePivot() {
        val f = fixture()
        assertFalse(f.interaction.editPivot)
        f.click(0f, 1f) // the entity only
        assertFalse(f.interaction.canEditPivot)
        f.interaction.editPivot = true
        assertFalse(f.interaction.editPivot)

        f.click(0.7f, 1.7f, alt = true)
        assertTrue(f.interaction.canEditPivot)
        f.interaction.editPivot = true
        f.interaction.pivotSnap = PivotSnap.EDGE
        assertTrue(f.interaction.editPivot)
        assertEquals(PivotSnap.EDGE, f.interaction.pivotSnap)

        f.interaction.editPivot = false
        assertEquals(PivotSnap.NONE, f.interaction.pivotSnap)
    }

    @Test
    fun aPartThatIsNotReadYetCannotBeEdited() {
        val f = fixture()
        f.state.selectPart(8L, "flap") // entity 8's model has no parts answer
        assertFalse(f.interaction.canEditPivot)
    }
}
