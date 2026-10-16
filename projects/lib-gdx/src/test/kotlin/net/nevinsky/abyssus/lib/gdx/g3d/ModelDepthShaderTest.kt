/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDepthShaderTest {
    @Test fun blendedMaterialsAreExcludedFromTheDepthPass() {
        val renderable = Renderable().apply { material = Material() }
        assertTrue(ModelDepthShader.castsShadow(renderable))
        renderable.material!!.set(BlendingAttribute(true, 0.5f))
        assertFalse(ModelDepthShader.castsShadow(renderable))
        renderable.material!!.set(BlendingAttribute(false, 1f))
        assertTrue(ModelDepthShader.castsShadow(renderable))
    }
}
