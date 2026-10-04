/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.model

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.math.Matrix4
import org.junit.Assert.*
import org.junit.Test

class RayModelSkinningTest {
    @Test fun weightedJointsDeformPositionAndDirectionsWithoutChangingSharedSource() {
        val mesh = mesh()
        val original = mesh.vertices()
        val first = Matrix4().setToTranslation(2f, 0f, 0f).`val`
        val second = Matrix4().setToTranslation(0f, 4f, 0f).`val`
        val deformed = RayModelSkinning().deform(mesh, listOf(first, second))
        assertArrayEquals(floatArrayOf(1.5f, 5f, 3f), deformed.copyOfRange(0, 3), 0.00001f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), deformed.copyOfRange(3, 6), 0.00001f)
        assertArrayEquals(original, mesh.vertices(), 0f)
        assertArrayEquals(original.copyOfRange(6, 10), deformed.copyOfRange(6, 10), 0f)
        assertArrayEquals(original, RayModelSkinning().deform(mesh, listOf(Matrix4().`val`, Matrix4().`val`)), 0.00001f)
    }

    @Test fun directionsUseTheSameSkinMatrixAsRasterAndAreNormalized() {
        val values = mesh().vertices().apply { this[3] = 1f; this[4] = 1f }
        val mesh = mesh(values)
        val result = RayModelSkinning().deform(mesh, listOf(Matrix4().setToScaling(2f, 1f, 1f).`val`, Matrix4().setToScaling(2f, 1f, 1f).`val`))
        assertEquals(2f / kotlin.math.sqrt(5f), result[3], 0.00001f)
        assertEquals(1f / kotlin.math.sqrt(5f), result[4], 0.00001f)
    }

    @Test fun invalidJointAndPaletteAreRejectedBeforePublishingGeometry() {
        assertThrows(IllegalArgumentException::class.java) { RayModelSkinning().deform(mesh(), listOf(Matrix4().`val`)) }
        assertThrows(IllegalArgumentException::class.java) { RayModelSkinning().deform(mesh(), listOf(FloatArray(16) { Float.NaN }, Matrix4().`val`)) }
        assertThrows(IllegalArgumentException::class.java) { RayModelSkinning().deform(mesh(mesh().vertices().apply { this[6] = 0.5f }), listOf(Matrix4().`val`, Matrix4().`val`)) }
    }

    private fun mesh(values: FloatArray = floatArrayOf(1f, 2f, 3f, 0f, 1f, 0f, 0f, .25f, 1f, .75f)) = RayModelMesh(
        "skin", values, 40,
        listOf(attribute(Usage.Position, 3, 0), attribute(Usage.Normal, 3, 12), attribute(Usage.BoneWeight, 2, 24), attribute(Usage.BoneWeight, 2, 32, 1)),
        emptyList(),
    )
    private fun attribute(usage: Int, components: Int, offset: Int, unit: Int = 0) =
        RayModelVertexAttribute(usage, components, GL20.GL_FLOAT, false, unit, "channel", offset)
}
