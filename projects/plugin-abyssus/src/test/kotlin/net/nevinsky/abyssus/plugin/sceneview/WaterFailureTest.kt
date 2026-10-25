/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import net.nevinsky.abyssus.plugin.sceneview.water.SceneWater
import net.nevinsky.abyssus.plugin.sceneview.water.WaterFrameInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage

/**
 * Water that cannot be drawn never takes the scene down with it: each failure is isolated, logged once, and retried only
 * when something meaningful changes. Opt-in GL checks.
 */
class WaterFailureTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val orbit = OrbitCamera(Vector3(0f, 0f, 0f), 25f, 0.6f, 0.35f)
    private val broken = ShaderStorage().withResources("/shader/scene", SceneRenderer::class.java)
        .withResources("/shader/broken", WaterFailureTest::class.java)

    private fun identical(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    @Test fun aWaterShaderThatDoesNotCompileSkipsTheWaterOnlyAndIsLoggedOnce() {
        val log = RecordingLogger()
        val dry = WaterGlRig.shoot(WaterGlScenes.flatLake(emptyList()), orbit)
        var unavailable = false
        val wet = WaterGlRig.shoot(WaterGlScenes.flatLake(), orbit, frames = 40, log = log, sceneShaders = broken, each = { renderer, _ ->
            unavailable = renderer.waterStatus?.unavailable == true
        })
        assertTrue("the water is reported unavailable", unavailable)
        assertEquals("one report however many frames", 1, log.warnings.count { it.contains("water shader could not be made") })
        assertTrue("the rest of the scene renders exactly as without water", identical(dry.image, wet.image))
        assertNull(wet.renderer.lastWaterPlan)
    }

    @Test fun aReplacedContextTriesTheShaderAgain() {
        val log = RecordingLogger()
        WaterGlRig.shoot(WaterGlScenes.flatLake(), orbit, frames = 40, log = log, sceneShaders = broken, each = { renderer, index ->
            if (index == 20) renderer.create() // what a new canvas context does
        })
        assertEquals("the retry in the new context reports again", 2, log.warnings.count { it.contains("water shader could not be made") })
    }

    @Test fun anInvalidSurfaceIsSkippedAndLoggedOnceWhileTheOthersDraw() {
        val log = RecordingLogger()
        var drawn = -1
        val params = WaterGlScenes.flatLake(listOf(WaterGlScenes.lake())) { _, root ->
            root.putObject("abyssus").putObject("waterSurfaces").putObject("bad").put("name", "Broken").put("preset", "LAKE").put("width", -5)
        }
        val shot = WaterGlRig.shoot(params, orbit, frames = 40, log = log, each = { renderer, _ -> drawn = renderer.waterStatus?.drawnSurfaces ?: -1 })
        assertEquals("the valid surface still draws next to the invalid one", 1, drawn)
        assertTrue(shot.renderer.lastWaterPlan!!.drawOrder.none { it.id == "bad" })
        assertEquals("the invalid one is logged once with its reason", 1, log.warnings.count { it.contains("Broken") && it.contains("skipped") })
        assertEquals("the saved surfaces still include the invalid row", 2, params.content.water.size)
    }

    private fun input(width: Int, height: Int, surface: net.nevinsky.abyssus.lib.core.water.WaterSettings): WaterFrameInput {
        val camera = PerspectiveCamera(60f, width.toFloat(), height.toFloat()).also {
            it.position.set(0f, 20f, 20f); it.lookAt(0f, 0f, 0f); it.near = 0.1f; it.far = 500f; it.update()
        }
        return WaterFrameInput(
            camera, width, height, 0.016f, listOf(surface), Color(0.5f, 0.6f, 0.8f, 1f), Color(0.3f, 0.3f, 0.3f, 1f), null,
            LightSet(emptyList(), emptyList()), emptyList(), emptyList(), null,
        )
    }

    @Test fun targetsThatCannotBeAllocatedLeavePlainTintedWaterUntilTheSizeChanges() {
        val log = RecordingLogger()
        val surface = WaterPresets().defaults(WaterPreset.LAKE, "w", "Lake", 0.0, 0.0, 0.0).copy(width = 80.0, length = 80.0)
        var checked = false
        GlHarness.render(net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams.DEFAULT, 4, orbit, framebufferSize = 64 to 48) { _, index ->
            if (index == 2) {
                val water = SceneWater(ShaderStorage().withResources("/shader/scene", SceneRenderer::class.java), log)
                try {
                    val huge = input(40000, 16, surface)
                    val plan = water.begin(huge)
                    assertNotNull(plan)
                    assertTrue("oversized targets leave the water degraded", water.degraded)
                    water.drawSurfaces(plan!!, huge)
                    assertEquals("the surface is still drawn, tinted", 1, water.drawnSurfaces)
                    assertEquals(GL20.GL_NO_ERROR, com.badlogic.gdx.Gdx.gl.glGetError())
                    assertEquals(1, log.warnings.size)
                    val again = water.begin(huge)
                    assertNotNull(again)
                    assertEquals("the same size is not retried or logged again", 1, log.warnings.size)
                    val normal = input(64, 48, surface)
                    val next = water.begin(normal)
                    assertNotNull(next)
                    assertFalse("a meaningful change retries and succeeds", water.degraded)
                    checked = true
                } finally {
                    water.dispose()
                }
            }
        }.error?.let { throw it }
        assertTrue(checked)
    }
}
