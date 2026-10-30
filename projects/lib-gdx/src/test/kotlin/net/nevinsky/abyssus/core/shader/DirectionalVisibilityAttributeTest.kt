/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.math.Matrix4
import org.junit.Assert.*
import org.junit.Test

class DirectionalVisibilityAttributeTest {
    private class FakeMap(handle: Int) : GLTexture(GL20.GL_TEXTURE_2D, handle) {
        override fun getWidth() = 1
        override fun getHeight() = 1
        override fun getDepth() = 0
        override fun isManaged() = false
        override fun reload() = Unit
    }

    private fun attribute(index: Int = 1, outside: Float = 0.8f) =
        DirectionalVisibilityAttribute(FakeMap(3), index, Matrix4().setToTranslation(1f, 2f, 3f), 0.1f, outside)

    @Test
    fun copiesAndComparesByContentAndOwnsItsMatrix() {
        val source = Matrix4().setToTranslation(1f, 2f, 3f)
        val a = DirectionalVisibilityAttribute(FakeMap(3), 1, source)
        source.setToTranslation(9f, 9f, 9f)
        assertEquals(1f, a.worldToMap.`val`[Matrix4.M03], 0f)
        assertEquals(a, a.copy())
        assertEquals(a.hashCode(), a.copy().hashCode())
        assertEquals(0, a.compareTo(a.copy()))
        assertNotEquals(attribute(index = 0), attribute(index = 1))
        assertNotEquals(attribute(outside = 0.5f), attribute(outside = 0.8f))
    }

    @Test
    fun invalidValuesAreRejected() {
        fun fails(block: () -> Unit) = assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
        fails { DirectionalVisibilityAttribute(FakeMap(1), -1, Matrix4()) }
        fails { DirectionalVisibilityAttribute(FakeMap(1), 0, Matrix4(), 0f) }
        fails { DirectionalVisibilityAttribute(FakeMap(1), 0, Matrix4(), 0.6f) }
        fails { DirectionalVisibilityAttribute(FakeMap(1), 0, Matrix4(), 0.1f, 1.5f) }
    }

    @Test
    fun theAttributeGivesItsEnvironmentItsOwnShader() {
        // programs are cached per attribute mask, so an environment with the map never reuses one compiled without it
        val plain = Environment()
        val mapped = Environment().apply { set(attribute()) }
        assertNotEquals(plain.getMask(), mapped.getMask())
        assertEquals(1, java.lang.Long.bitCount(DirectionalVisibilityAttribute.Type))
    }
}
