/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Measures native-to-canvas motion. Does not substitute for the sandbox IDE/manual gate. */
class RayFeasibilityPresentationTimingGlTest {
    @Test fun movingInstanceAndCameraReachTheCanvasForThirtySeconds() {
        assumeTrue(GlHarness.enabled && java.lang.Boolean.getBoolean("abyssus.rayTimingTests") &&
            System.getProperty("os.name").startsWith("Mac"))
        val reports = mutableListOf<String>()
        val preview = RayFeasibilityPreview { reports.add(it); println(it) }
        val rendered = GlHarness.render(SceneRenderParams.DEFAULT, 2100, framebufferSize = 1280 to 720) { renderer, index ->
            try {
                assertEquals(1280, renderer.lastWidth)
                assertEquals(720, renderer.lastHeight)
                preview.orbit.yaw += 0.0005f
                preview.draw(renderer.lastWidth, renderer.lastHeight)
                assertNull(preview.failure)
            } finally {
                if (index == 2099) preview.dispose()
            }
        }
        rendered.error?.let { preview.stop(); throw it }
        assertTrue("Expected a 30-second measurement: $reports", reports.isNotEmpty())
        assertTrue(GlHarness.coverage(rendered.image, SceneRenderParams.DEFAULT) > 0.2)
        for (report in reports) {
            fun value(field: String) = Regex("$field=([0-9.]+)").find(report)!!.groupValues[1].toDouble()
            assertTrue(report, value("fps") >= 30)
            assertTrue(report, value("offer-to-draw") < 100)
            assertTrue(report, value("upload/draw") + value("offer") < 8)
        }
    }
}
