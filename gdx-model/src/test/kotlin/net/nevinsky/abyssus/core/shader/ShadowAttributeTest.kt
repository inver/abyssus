/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.shader

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import net.nevinsky.abyssus.core.Renderable
import org.junit.Assert.*
import org.junit.Test

class ShadowAttributeTest {
    private class Atlas : GLTexture(GL20.GL_TEXTURE_2D, 17) {
        override fun getWidth() = 64
        override fun getHeight() = 64
        override fun getDepth() = 0
        override fun isManaged() = false
        override fun reload() = Unit
    }

    @Test fun copyPreservesLightIdentityAndOwnsProjectionData() {
        val light = DirectionalLight()
        val view = ShadowAtlasView(Matrix4(), Vector4(0f, 0f, 0.25f, 0.25f))
        val record = ShadowLightRecord("42", ShadowLightKind.DIRECTIONAL, light, listOf(view), Vector3(), 10f)
        val attribute = ShadowAtlasAttribute(Atlas(), listOf(record))
        val copy = attribute.copy() as ShadowAtlasAttribute
        assertSame(light, copy.records.single().light)
        assertEquals("42", copy.records.single().lightId)
        view.matrix.setToTranslation(4f, 5f, 6f)
        assertEquals(0f, copy.records.single().views.single().matrix.`val`[Matrix4.M03], 0f)
    }

    @Test fun distinctLightRecordsDoNotDependOnListOrder() {
        val a = DirectionalLight()
        val b = DirectionalLight()
        fun record(id: String, light: DirectionalLight) = ShadowLightRecord(id, ShadowLightKind.DIRECTIONAL, light,
            listOf(ShadowAtlasView(Matrix4(), Vector4(0f, 0f, 1f, 1f))), Vector3(), 10f)
        val attribute = ShadowAtlasAttribute(Atlas(), listOf(record("9", b), record("4", a)))
        assertEquals("4", attribute.recordFor(a)?.lightId)
        assertEquals("9", attribute.recordFor(b)?.lightId)
        assertNull(attribute.recordFor(DirectionalLight()))
    }

    @Test fun blendedMaterialsAreExcludedFromTheDepthPass() {
        val renderable = Renderable().apply { material = Material() }
        assertTrue(ModelDepthShader.castsShadow(renderable))
        renderable.material!!.set(BlendingAttribute(true, 0.5f))
        assertFalse(ModelDepthShader.castsShadow(renderable))
        renderable.material!!.set(BlendingAttribute(false, 1f))
        assertTrue(ModelDepthShader.castsShadow(renderable))
    }
}
