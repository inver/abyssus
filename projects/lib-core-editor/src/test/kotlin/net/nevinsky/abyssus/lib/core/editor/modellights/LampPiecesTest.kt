/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modellights

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LampPiecesTest {
    @Test
    fun separateLampsAreSeparatePieces() {
        val pieces = lampPieces(materialGeometries(lampsAt(0f, 5f, 10f)).single())
        assertEquals(3, pieces.size)
        assertEquals(listOf(0.5f, 5.5f, 10.5f), pieces.map { it.center.x })
        assertEquals(0.5f, pieces[0].center.y, 1e-6f)
        assertEquals(1f, pieces[0].size.x, 1e-6f)
    }

    @Test
    fun touchingLampsAreOnePiece() {
        // the second quad starts where the first ends, so its left edge welds to the first's right edge
        val pieces = lampPieces(materialGeometries(lampsAt(0f, 1f)).single())
        assertEquals(1, pieces.size)
        assertEquals(1f, pieces.single().center.x, 1e-6f)
        assertEquals(2f, pieces.single().size.x, 1e-6f)
    }

    @Test
    fun nodeTransformsApply() {
        val pieces = lampPieces(materialGeometries(lampsAt(0f, 5f, rootScale = 0.01f)).single())
        assertEquals(0.055f, pieces[1].center.x, 1e-6f)
        assertEquals(0.01f, pieces[1].size.x, 1e-6f)
    }

    @Test
    fun entityMoveAndScaleApply() {
        val piece = lampPieces(materialGeometries(lampsAt(0f)).single()).single()
        val placed = piece.placed(Matrix4().set(Vector3(10f, 0f, 0f), Quaternion(), Vector3(2f, 2f, 2f)))
        assertEquals(11f, placed.center.x, 1e-5f)
        assertEquals(2f, placed.size.x, 1e-5f)
    }

    @Test
    fun emissiveColorIsKeptAndAMaterialWithoutGeometryIsAbsent() {
        val geometries = materialGeometries(lampsAt(0f))
        assertEquals(listOf("lamp"), geometries.map { it.materialId })
        assertNotNull(geometries.single().emissive)
        assertNull(geometries.firstOrNull { it.materialId == "unused" })
    }

    @Test
    fun aTriangleOutsideTheVertexDataIsDropped() {
        val data = lampsAt(0f)
        data.meshes[0].parts[0].indices = intArrayOf(0, 1, 2, 0, 2, 99)
        val geometry = materialGeometries(data).single()
        assertEquals(3, geometry.triangles.size)
        assertTrue(lampPieces(geometry).isNotEmpty())
    }
}

class LampMaterialChoicesTest {
    @Test
    fun emissiveMaterialsComeFirstWithTheirLampCounts() {
        val triangle = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val plain = MaterialGeometry("Metal", null, triangle, intArrayOf(0, 1, 2))
        val empty = MaterialGeometry("Empty", null, FloatArray(0), IntArray(0))
        val emissive = materialGeometries(lampsAt(0f, 5f, 10f)).single()

        val choices = lampMaterialChoices(listOf(plain, emissive, empty))

        assertEquals(listOf("lamp", "Metal"), choices.map { it.materialId })
        assertEquals(listOf(3, 1), choices.map { it.lamps })
    }
}
