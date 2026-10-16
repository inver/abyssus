/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Matrix4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceAttributesTest {
    @Test
    fun aMat4IsFourVec4AttributesWithTheSameAliasAndUnitsZeroToThree() {
        val attributes = InstanceAttributes.of()
        assertEquals(4, attributes.size)
        assertEquals(List(4) { "a_instance" }, attributes.map { it.alias })
        assertEquals(listOf(0, 1, 2, 3), attributes.map { it.unit })
        assertTrue(attributes.all { it.numComponents == 4 && it.usage == VertexAttributes.Usage.Generic })
    }

    @Test
    fun theKeysAreDistinctSoStockBaseShaderKeepsEveryColumn() {
        assertEquals(4, InstanceAttributes.of().map { it.key }.toSet().size)
    }

    @Test
    fun theColumnsLayOutBackToBackInOneInstanceStride() {
        val layout = VertexAttributes(*InstanceAttributes.of())
        assertEquals(InstanceAttributes.FLOATS * Float.SIZE_BYTES, layout.vertexSize)
        assertEquals(listOf(0, 16, 32, 48), InstanceAttributes.of().indices.map { layout.get(it).offset })
    }

    @Test
    fun writeCopiesTheColumnMajorValuesAtTheOffset() {
        val matrix = Matrix4().setToTranslation(1f, 2f, 3f)
        val target = FloatArray(InstanceAttributes.FLOATS + 2)
        InstanceAttributes.write(matrix, target, 2)
        assertArrayEquals(matrix.`val`, target.copyOfRange(2, target.size), 0f)
        assertEquals(0f, target[0], 0f)
    }

    @Test
    fun writeChecksTheBounds() {
        val matrix = Matrix4()
        assertThrows(IllegalArgumentException::class.java) { InstanceAttributes.write(matrix, FloatArray(15), 0) }
        assertThrows(IllegalArgumentException::class.java) { InstanceAttributes.write(matrix, FloatArray(16), 1) }
        assertThrows(IllegalArgumentException::class.java) { InstanceAttributes.write(matrix, FloatArray(16), -1) }
    }
}
