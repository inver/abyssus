/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.pick.FrameSnapshot
import net.nevinsky.abyssus.lib.core.editor.pick.SceneTarget
import net.nevinsky.abyssus.lib.core.editor.pick.SceneViewState
import net.nevinsky.abyssus.lib.core.editor.pick.SnapshotBox
import net.nevinsky.abyssus.lib.core.editor.pick.SnapshotSceneQueries
import net.nevinsky.abyssus.lib.core.editor.scene.DummyPlacement
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import org.junit.Assert.assertEquals
import org.junit.Test

/** OpenSpec `scene-picking`, "Dummy markers are picked by screen distance": CPU only, no GL. */
class DummyPickTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // libGDX's frustum update is native
    }

    private val width = 400
    private val height = 300

    // a camera at (0, 0, 10) looking at the origin, where a model's box and its dummy's marker both are
    private val camera = PerspectiveCamera(60f, width.toFloat(), height.toFloat()).apply {
        position.set(0f, 0f, 10f)
        lookAt(0f, 0f, 0f)
        near = 0.1f
        far = 100f
        update()
    }
    private val modelBox = SnapshotBox(9L, BoundingBox(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f)), Matrix4())
    private val snapshot = FrameSnapshot(camera, listOf(modelBox), emptyList(), 1L)
    private val content = SceneContent(
        dummies = listOf(DummyPlacement(20L, "Dummy", PlacementTransform(Vector3(0f, 0f, 1.5f), Quaternion(),
                Vector3(1f, 1f, 1f)), 9L)),
    )

    private fun queries(state: SceneViewState) = SnapshotSceneQueries({ snapshot }, state, sceneContentOf = { content })

    @Test
    fun aShownMarkerOverAModelIsPicked() {
        val state = SceneViewState().apply { showDummies = true }
        assertEquals(SceneTarget.Entity(20L), queries(state).pickTarget(width / 2, height / 2, width, height))
        assertEquals(20L, queries(state).pick(width / 2 + 3, height / 2 - 2, width, height))
    }

    @Test
    fun aHiddenMarkerIsNotPicked() {
        val state = SceneViewState()
        assertEquals(SceneTarget.Entity(9L), queries(state).pickTarget(width / 2, height / 2, width, height))
    }

    @Test
    fun theSelectedDummysMarkerIsPickedWhileTheToggleIsOff() {
        val state = SceneViewState().apply { selection = SceneTarget.Entity(20L) }
        assertEquals(20L, queries(state).pick(width / 2, height / 2, width, height))
    }

    @Test
    fun aClickAwayFromTheMarkerPicksTheModel() {
        val state = SceneViewState().apply { showDummies = true }
        assertEquals(9L, queries(state).pick(width / 2 + 15, height / 2, width, height))
    }
}
