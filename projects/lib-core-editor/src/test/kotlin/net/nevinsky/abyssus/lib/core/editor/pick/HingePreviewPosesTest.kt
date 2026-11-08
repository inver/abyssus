/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPartsInfo
import net.nevinsky.abyssus.lib.core.editor.scene.ContentHierarchy
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import org.junit.Assert.*
import org.junit.Test

class HingePreviewPosesTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    @Test
    fun anEntityBoundToThePreviewedNodeSwingsAboutTheNodeXAxis() {
        // the plane at the origin; node "flap" at (0, 0, 1) in the model; a dummy bound to it at 1 m along +Y
        val info = ModelPartsInfo(
            emptyList(), mapOf("flap" to Matrix4().setToTranslation(0f, 0f, 1f)), emptyMap(), emptyMap(),
        )
        val dummyWorld = Matrix4().setToTranslation(0f, 1f, 1f)
        val content = SceneContent(
            models = listOf(AssetPlacement(7L, "plane", PlacementTransform.IDENTITY)),
            hierarchy = ContentHierarchy(
                parents = mapOf(9L to 7L),
                worlds = mapOf(7L to Matrix4(), 9L to dummyWorld),
                nodes = mapOf(9L to "flap"),
            ),
        )
        val poses = HingePreviewPoses().poses(content, HingePreview(7L, "flap", 90f)) { info.takeIf { _ -> true } }
        val pose = poses.getValue(9L)
        // +Y turned 90 degrees about +X becomes +Z: the dummy ends up at (0, 0, 2)
        assertEquals(0f, pose.position.x, 1e-5f)
        assertEquals(0f, pose.position.y, 1e-5f)
        assertEquals(2f, pose.position.z, 1e-5f)
        assertEquals(0.7071068f, pose.rotation.x, 1e-5f)
    }

    @Test
    fun entitiesBoundToOtherNodesAndWithoutAPreviewStayPut() {
        val info = ModelPartsInfo(emptyList(), mapOf("flap" to Matrix4(), "wing" to Matrix4()), emptyMap(), emptyMap())
        val content = SceneContent(
            models = listOf(AssetPlacement(7L, "plane", PlacementTransform.IDENTITY)),
            hierarchy = ContentHierarchy(
                parents = mapOf(9L to 7L, 10L to 7L),
                worlds = mapOf(7L to Matrix4(), 9L to Matrix4(), 10L to Matrix4()),
                nodes = mapOf(9L to "wing", 10L to "flap"),
            ),
        )
        val poses = HingePreviewPoses().poses(content, HingePreview(7L, "flap", 20f)) { info }
        assertEquals(setOf(10L), poses.keys)
        assertTrue(HingePreviewPoses().poses(content, HingePreview(7L, "missing", 20f)) { info }.isEmpty())
        assertTrue(HingePreviewPoses().poses(content, HingePreview(7L, "flap", 20f)) { null }.isEmpty())
    }
}
