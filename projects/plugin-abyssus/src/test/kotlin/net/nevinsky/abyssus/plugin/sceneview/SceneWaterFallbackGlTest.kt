/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.water.WaterSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File

/**
 * The bounded reflection budget and its fallback: more levels than reflection views still draw every surface, the choice
 * is stable, coplanar surfaces share one reflection and draw in a fixed order, and a cloudy sky is what the fallback
 * shows. Opt-in GL checks.
 */
class SceneWaterFallbackGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val orbit = OrbitCamera(Vector3(0f, 0f, 0f), 80f, 0.5f, 0.6f)

    private fun lake(id: String, x: Double, level: Double, size: Double = 40.0): WaterSettings =
        WaterGlScenes.lake(level).copy(id = id, name = id, x = x, z = 0.0, width = size, length = size)

    private fun identical(a: BufferedImage, b: BufferedImage): Boolean {
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    @Test fun moreLevelsThanReflectionViewsStillDrawEverySurfaceAndTheChoiceIsStable() {
        val surfaces = listOf(lake("a", -50.0, 0.0, 50.0), lake("b", 0.0, 1.0, 36.0), lake("c", 50.0, 2.0, 22.0))
        val chosen = mutableListOf<List<Long>>()
        var passes = 0
        var drawn = 0
        var targets = 0
        val shot = WaterGlRig.shoot(WaterGlScenes.flatLake(surfaces, withModels = false), orbit, frames = 40, each = { renderer, index ->
            val plan = renderer.lastWaterPlan
            if (plan != null && index > 10) chosen += plan.reflected.map { it.key }
            passes = renderer.waterStatus?.reflectionPasses ?: 0
            drawn = renderer.waterStatus?.drawnSurfaces ?: 0
            targets = renderer.waterStatus?.resourceTargets ?: 0
        })
        val plan = shot.renderer.lastWaterPlan!!
        assertEquals("two reflected levels", 2, plan.reflected.size)
        assertEquals("one level falls back", 1, plan.fallback.size)
        assertEquals(2, passes)
        assertEquals("every visible surface is drawn", 3, drawn)
        assertEquals("the same levels reflect on every frame", 1, chosen.toSet().size)
        assertEquals("the targets are the shared ones plus one per reflected level", 3 + 2, targets)
    }

    @Test fun coplanarSurfacesShareOneReflectionAndDrawInAFixedOrder() {
        val a = lake("a", -10.0, 0.0, 50.0)
        val b = lake("b", 10.0, 0.0, 50.0).copy(tintR = 0.6, tintG = 0.1, tintB = 0.1)
        var passes = 0
        var drawn = 0
        fun render(order: List<WaterSettings>): WaterShot = WaterGlRig.shoot(WaterGlScenes.flatLake(order, withModels = false), orbit, each = { renderer, _ ->
            passes = renderer.waterStatus?.reflectionPasses ?: 0
            drawn = renderer.waterStatus?.drawnSurfaces ?: 0
        })
        val first = render(listOf(a, b))
        val plan = first.renderer.lastWaterPlan!!
        assertEquals("one level, one reflection", 1, plan.reflected.size)
        assertEquals(2, plan.reflected[0].surfaces.size)
        assertEquals(1, passes)
        assertEquals(2, drawn)
        val second = render(listOf(b, a))
        assertTrue("saved order does not change the picture where they overlap", identical(first.image, second.image))
    }

    private val cloudsUuid = "3f2a9c1e-7b4d-4e8a-9c6f-1d2e3b4a5c6d"

    /** A project with the fixture's terrain (flat) and procedural sky, the sky naming a cloud asset unless [clouds] is false. */
    private fun cloudyProject(clouds: Boolean, surfaces: List<WaterSettings>): SceneRenderParams {
        val dir = WaterGlScenes.project { _, _ -> -2f }
        File(File("src/test/testData/project/Untitled"), "assets/skybox_physical").copyRecursively(File(dir, "assets/skybox_physical"), overwrite = true)
        if (clouds) {
            val meta = File(dir, "assets/skybox_physical/meta.json")
            meta.writeText(meta.readText().replace("\"sunIntensity\": 20.0", "\"sunIntensity\": 20.0,\n    \"clouds\": \"$cloudsUuid\""))
            File(dir, "assets/clouds_fair").mkdirs()
            File(dir, "assets/clouds_fair/meta.json").writeText(
                """{"format": "abyssus", "formatVersion": 1, "uuid": "$cloudsUuid", "type": "CLOUDS", "additional": {"low": {"level": "LOW", "type": "CUMULUS"}, "mid": {"level": "MID", "type": "ALTOCUMULUS"}}}""",
            )
        }
        val params = WaterGlScenes.params(dir, *surfaces.toTypedArray())
        return params.copy(content = params.content.copy(models = emptyList(), cameras = emptyList()))
    }

    private fun fallbackBrightness(params: SceneRenderParams): Pair<Double, Boolean> {
        var lease = false
        val shot = WaterGlRig.shoot(params, OrbitCamera(Vector3(50f, 0f, 0f), 70f, 0.4f, 0.4f), frames = 320, each = { renderer, _ -> lease = renderer.skyLeaseActive })
        // the smallest lake is the one without a reflection view: read its centre
        val screen = com.badlogic.gdx.math.Vector3(50f, 2f, 0f)
        shot.renderer.frameCamera.project(screen, 0f, 0f, shot.image.width.toFloat(), shot.image.height.toFloat())
        val luminance = WaterGlRig.luminance(WaterGlRig.rgb(shot.image, screen.x.toInt(), (shot.image.height - screen.y).toInt()))
        return luminance to lease
    }

    @Test fun aCloudySkyIsWhatTheFallbackReflectsAndTheLeaseIsHeldOnlyForThat() {
        val three = listOf(lake("a", -50.0, 0.0, 50.0), lake("b", 0.0, 1.0, 36.0), lake("c", 50.0, 2.0, 22.0))
        val (cloudy, cloudyLease) = fallbackBrightness(cloudyProject(clouds = true, surfaces = three))
        val (clear, clearLease) = fallbackBrightness(cloudyProject(clouds = false, surfaces = three))
        assertTrue("the view holds the sky lease while a surface shows the cloudy sky", cloudyLease)
        assertFalse("no clouds, no lease", clearLease)
        assertTrue("the fallback shows the cloudy sky, not a cloudless one ($cloudy vs $clear)", kotlin.math.abs(cloudy - clear) > 0.01)
        val (_, singleLease) = fallbackBrightness(cloudyProject(clouds = true, surfaces = listOf(lake("c", 50.0, 2.0, 22.0))))
        assertFalse("with every level reflected, nothing needs the cloudy cube", singleLease)
    }
}
