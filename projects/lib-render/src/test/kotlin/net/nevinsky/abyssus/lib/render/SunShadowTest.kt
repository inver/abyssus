/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.graphics.OrthographicCamera
import net.nevinsky.abyssus.lib.render.shadow.sunShadowBias
import org.junit.Assert.assertEquals
import org.junit.Test

class SunShadowTest {
    @Test
    fun biasUsesAQuarterTexelAndGuardsAnEmptyDepthRange() {
        val camera = OrthographicCamera().apply { viewportWidth = 40f; near = 1f; far = 101f }
        assertEquals(0.25f * 40f / 100f / 1024, sunShadowBias(camera, 1024), 0f)
        camera.far = camera.near
        assertEquals(0.25f * 40f / 0.01f / 1024, sunShadowBias(camera, 1024), 0f)
    }
}
