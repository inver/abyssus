/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelImage
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelMaterial
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelMesh
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelMeshPart
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelNode
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelNodePart
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelSnapshot
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayTextureSampler
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayTextureWrap
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayTerrainSnapshot
import net.nevinsky.abyssus.lib.raytracing.*
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Matrix4
import org.junit.Assert.*
import org.junit.Test

class RaySceneConverterTest {
    private val white = net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelColor(1f, 1f, 1f, 1f)
    private val image = RayModelImage(2, 2, byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1))

    private val positionAttr = net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelVertexAttribute(
        VertexAttributes.Usage.Position, 3, GL20.GL_FLOAT, false, 0, "position", 0)
    private val mesh = RayModelMesh("mesh", floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 0f, 1f, 0f), 12,
        listOf(positionAttr), listOf(RayModelMeshPart("part", GL20.GL_TRIANGLES, intArrayOf(0, 1, 2))))
    private val material = RayModelMaterial("mat", false, null, white, null, null, null, 20f, 1f, null, null, null, true,
        net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelAlphaMode.OPAQUE, 0.5f, emptyList())
    private val node = RayModelNode("node",
        net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelVector(0f, 0f, 0f),
        net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelRotation(0f, 0f, 0f, 1f),
        net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelVector(1f, 1f, 1f),
        listOf(RayModelNodePart("part", "mat", emptyMap())), emptyList())
    private val snapshot = RayModelSnapshot(listOf(mesh), listOf(material), listOf(node),
        mapOf("image.png" to image))

    @Test fun convertsModelIntoMeshesMaterialsTexturesAndInstances() {
        val converter = RaySceneConverter()
        converter.model("inst-1", Matrix4(), snapshot)
        assertEquals(1, converter.meshes.size)
        assertEquals(1, converter.materials.size)
        assertEquals(1, converter.textures.size)
        assertEquals(1, converter.instances.size)
        assertEquals(1, converter.transforms.size)
        assertEquals("inst-1/0/0:model:inst-1:part:mat", converter.topology.firstOrNull())
    }

    @Test fun unchangedAssetReusesMeshIdentity() {
        val converter = RaySceneConverter()
        converter.model("a", Matrix4(), snapshot)
        val first = converter.meshes.first()
        converter.model("b", Matrix4(), snapshot)
        assertSame(first, converter.meshes.last())
        assertEquals(2, converter.instances.size)
    }

    @Test fun convertsTerrain() {
        // 3 vertices * 8 floats/vertex = 24 floats; one triangle (0,1,2)
        val terrain = RayTerrainSnapshot(4, 4, 1f, floatArrayOf(0f, 0f, 0f, 0f),
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,   // v0: position, normal, uv
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,   // v1
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),   // v2
            intArrayOf(0, 1, 2), null, emptyMap())
        val converter = RaySceneConverter()
        converter.terrain("terr-1", Matrix4(), terrain)
        assertEquals(1, converter.meshes.size)
        assertEquals(1, converter.materials.size)
        assertEquals(1, converter.instances.size)
        assertEquals("terr-1:terrain:terr-1", converter.topology.firstOrNull())
    }

    @Test fun rejectsOverBudgetScene() {
        val converter = RaySceneConverter(RaySnapshotLimits(maxInstances = 1000, maxTriangles = 1000, maxBytes = 1))
        assertThrows(IllegalArgumentException::class.java) { converter.model("a", Matrix4(), snapshot) }
    }
}
