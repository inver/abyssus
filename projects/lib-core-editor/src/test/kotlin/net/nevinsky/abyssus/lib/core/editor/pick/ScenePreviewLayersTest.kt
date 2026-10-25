/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.PathAsset
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementAlong
import net.nevinsky.abyssus.lib.core.editor.content.ArrangementPlacement
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.Pose
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.water.ValidWater
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ScenePreviewLayersTest {
    private fun at(x: Float) = PlacementTransform(Vector3(x, 0f, 0f), Quaternion(), Vector3(1f, 1f, 1f))

    private fun base(): SceneContent {
        val path = arrangementAlong(listOf(Vector3(0f, 0f, 0f), Vector3(20f, 0f, 0f))).also {
            it.asset = PathAsset(assetName = "post")
            it.spacing = 10f
            it.slots = mutableListOf(11, 12, 13)
        }
        val posts = listOf(11, 12, 13).map { AssetPlacement(it.toString(), "post", PlacementTransform.IDENTITY) }
        val water = ValidWater(WaterPresets().defaults(WaterPreset.LAKE, "lake", "Lake", 5.0, 2.0, 5.0))
        return SceneContent(
            models = listOf(AssetPlacement("1", "rock", at(0f))) + posts,
            arrangements = listOf(ArrangementPlacement("10", path)),
            water = listOf(water),
        )
    }

    private fun xOf(c: SceneContent, id: String) = c.models.first { it.entityId == id }.transform.position.x

    @Test
    fun nothingPreviewedReturnsTheBaseItself() {
        val c = base()
        val layers = ScenePreviewLayers(SceneViewState())
        assertSame(c, layers.posed(c))
        assertSame(c, layers.apply(c))
    }

    @Test
    fun eachLayerActsAlone() {
        val c = base()
        val state = SceneViewState()
        val layers = ScenePreviewLayers(state)

        state.poses = mapOf("1" to Pose(Vector3(7f, 0f, 0f), Quaternion()))
        assertEquals(7f, xOf(layers.posed(c), "1"), 0f)
        state.poses = emptyMap()

        state.preview = mapOf("1" to DragResult(at(3f), null))
        assertEquals(3f, xOf(layers.apply(c), "1"), 0f)
        state.preview = emptyMap()

        state.waterPreview = mapOf("lake" to Vector3(9f, 4f, 9f))
        assertEquals(9.0, (layers.apply(c).water.single() as ValidWater).settings.x, 0.0)
        state.waterPreview = emptyMap()

        state.pathDraft = PathEdit("10", listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f)))
        assertEquals(5, layers.apply(c).models.count { it.assetName == "post" })
    }

    @Test
    fun layersCombineInOrder() {
        val c = base()
        val state = SceneViewState()
        val layers = ScenePreviewLayers(state)
        state.poses = mapOf("1" to Pose(Vector3(7f, 0f, 0f), Quaternion()))
        state.preview = mapOf("1" to DragResult(at(3f), null))
        state.pathDraft = PathEdit("10", listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f)))
        val shown = layers.apply(layers.posed(c))
        assertEquals("the drag wins over the pose", 3f, xOf(shown, "1"), 0f)
        assertEquals(5, shown.models.count { it.assetName == "post" })
    }

    @Test
    fun theLastResultIsKeptUntilAnInputIsReplaced() {
        val c = base()
        val state = SceneViewState()
        val layers = ScenePreviewLayers(state)
        state.preview = mapOf("1" to DragResult(at(3f), null))
        val first = layers.apply(c)
        assertSame(first, layers.apply(c))
        state.preview = mapOf("1" to DragResult(at(4f), null))
        assertNotSame(first, layers.apply(c))
        assertNotSame(first, layers.apply(base()))
    }
}
