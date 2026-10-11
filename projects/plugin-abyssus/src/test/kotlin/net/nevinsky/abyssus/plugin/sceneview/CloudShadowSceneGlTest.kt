/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf
import net.nevinsky.abyssus.plugin.sceneview.skybox.SunDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Cloud shadows through the real renderer. Opt-in: `-Dabyssus.glTests=true`. */
class CloudShadowSceneGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val cloudsUuid = "3f2a9c1e-7b4d-4e8a-9c6f-1d2e3b4a5c6d"

    private fun edit(text: String, change: (ObjectNode) -> Unit): String =
        SceneJson().compact(SceneJson().parseObject(text).also(change))

    /**
     * A copy of `Untitled` whose `skybox_physical` names a fair `CLOUDS` asset, and `Main Scene` (no fog) with its sky
     * enabled and [patch] applied; [block] gets the parameters and the project folder.
     */
    private fun withCloudyScene(patch: (ObjectNode) -> Unit = {}, block: (SceneRenderParams) -> Unit) {
        val dir = Files.createTempDirectory("cloudshadow").toFile()
        try {
            val source = File("src/test/testData/project/Untitled")
            File(source, "assets").copyRecursively(File(dir, "assets"))
            val meta = File(dir, "assets/skybox_physical/meta.json")
            meta.writeText(meta.readText().replace("\"sunIntensity\": 20.0", "\"sunIntensity\": 20.0,\n    \"clouds\": \"$cloudsUuid\""))
            File(dir, "assets/clouds_fair").mkdirs()
            File(dir, "assets/clouds_fair/meta.json").writeText(
                """{"format": "abyssus", "formatVersion": 1, "version": 1, "lastModified": 0, "uuid": "$cloudsUuid", "type": "CLOUDS", "additional": {"low": {"level": "LOW", "type": "CUMULUS", "coverage": 0.5}}}"""
            )
            val text = edit(File(source, "scenes/Main Scene.scene").readText()) { root ->
                root.put("fogEnabled", false)
                root.put("skyboxEnabled", true)
                root.put("skyboxName", "skybox_physical")
                patch(root)
            }
            block(renderParamsOf(parseScene(text), CameraParams.DEFAULT, dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun untilBuilt(renderer: SceneRenderer) = (renderer.drawnTerrains.isNotEmpty() && renderer.cloudShadowsActive)

    private class Stats(
        val active: Boolean,
        val lit: LightSet,
        val authored: LightSet,
        val scale: Float,
        val sunId: String?,
    )

    private fun run(params: SceneRenderParams, frames: Int = 300, time: Double? = null): Pair<Stats, java.awt.image.BufferedImage> {
        var stats: Stats? = null
        val result = GlHarness.render(params, frames) { renderer, frame ->
            if (time != null) renderer.skyClock.fix(time)
            if (frame == frames - 1) {
                val sunId = SunDirection.sunLight(renderer.content.lights)?.entityId
                stats = Stats(renderer.cloudShadowsActive, renderer.litLights, LightSet(renderer.litLights.directional, renderer.litLights.point, renderer.litLights.spot), renderer.sunScale, sunId)
            }
        }
        assertNull(result.error)
        return stats!! to result.image
    }

    @Test
    fun cloudShadowsReplaceTheUniformDimmingAndShadeOnlyTheSun() = withCloudyScene { params ->
        var checked = false
        val result = GlHarness.render(params, 300) { renderer, frame ->
            if (frame != 299) return@render
            assertTrue("cloud shadows apply", renderer.cloudShadowsActive)
            val sunId = SunDirection.sunLight(renderer.content.lights)!!.entityId
            val authored = renderer.content.lights.first { it.entityId == sunId }
            val lit = renderer.litLights
            val sun = lit.directional.first { it.entityId == sunId }
            // no double dimming: the lights keep the whole sun, while the uniform scale stays available beyond the map
            assertEquals(authored.color.r * authored.intensity, sun.color.r, 1e-4f)
            assertTrue("uniform scale ${renderer.sunScale}", renderer.sunScale in 0.1f..1f)
            checked = true
        }
        assertNull(result.error)
        assertTrue(checked)
    }

    @Test
    fun theShadowsMoveWithTheCloudsAndOnlyThey() = withCloudyScene({ root ->
        // the terrain alone, so differences come from the sun's visibility
        val ecs = root.get("ecs") as ObjectNode
        ecs.fieldNames().asSequence().toList().filter { it != "1" && it != "7" && it != "10" && it != "4" }.forEach(ecs::remove)
    }) { params ->
        val (a, early) = run(params, time = 0.0)
        val (b, late) = run(params, time = 900.0)
        assertTrue("active at both times", a.active && b.active)
        var differing = 0
        for (y in 0 until early.height) for (x in 0 until early.width) if (early.getRGB(x, y) != late.getRGB(x, y)) differing++
        assertTrue("the pattern moved ($differing pixels differ)", differing > early.width * early.height / 50)
    }

    @Test
    fun withoutAUsableSunNothingElseIsShaded() = withCloudyScene({ root ->
        val ecs = root.get("ecs") as ObjectNode
        listOf("7", "10").forEach(ecs::remove)
    }) { params ->
        val (stats, _) = run(params)
        assertFalse("no directional light, no cloud shadow", stats.active)
        assertNull(stats.sunId)
    }

    @Test
    fun withoutCloudsThereAreNoCloudShadows() = withCloudyScene({ root -> root.put("skyboxName", "skybox_default") }) { params ->
        val (stats, _) = run(params)
        assertFalse(stats.active)
        assertEquals(1f, stats.scale, 0f)
    }

    @Test
    fun aSkyWithoutACloudAssetHasNoCloudShadows() = withCloudyScene({ root -> root.putNull("skyboxName") }) { params ->
        val (stats, _) = run(params)
        assertFalse(stats.active)
    }

    @Test
    fun switchingToASkyWithoutCloudsClearsTheShadows() = withCloudyScene { params ->
        val without = params.copy(content = params.content.copy(skybox = "skybox_default"))
        var before = false
        var after = true
        val result = GlHarness.render(params, 360) { renderer, frame ->
            if (frame == 250) before = renderer.cloudShadowsActive
            if (frame == 260) renderer.params = without
            if (frame == 359) after = renderer.cloudShadowsActive
        }
        assertNull(result.error)
        assertTrue("shadows before", before)
        assertFalse("none after the clouds went", after)
    }

    @Test
    fun theShaderSeesTheSunByItsIndexInTheRenderedLights() = withCloudyScene { params ->
        var index = -2
        var expected = -3
        val result = GlHarness.render(params, 300) { renderer, frame ->
            if (frame != 299) return@render
            val sunId = SunDirection.sunLight(renderer.content.lights)!!.entityId
            expected = renderer.litLights.directional.indexOfFirst { it.entityId == sunId }
            index = renderer.cloudVisibilityIndex
        }
        assertNull(result.error)
        assertNotNull(expected)
        assertTrue("the sun is rendered", expected >= 0)
        assertEquals(expected, index)
    }

    @Test
    fun theRayViewGetsTheCloudMapOnlyWhenStaleAndTheBlendedProceduralAmbient() = withCloudyScene { params ->
        var first: net.nevinsky.abyssus.lib.core.editor.ray.RayCloudShadowInput? = null
        var again: net.nevinsky.abyssus.lib.core.editor.ray.RayCloudShadowInput? = null
        var later: net.nevinsky.abyssus.lib.core.editor.ray.RayCloudShadowInput? = null
        var sky1: net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot? = null
        var sky2: net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot? = null
        var sky3: net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot? = null
        var phase = 0
        val result = GlHarness.render(params, 330) { renderer, frame ->
            if (frame == 250) {
                renderer.rayFrameProvider = { context ->
                    when (phase) {
                        0 -> { first = context.cloudShadow?.invoke(); sky1 = context.bakedSky?.invoke() }
                        1 -> { again = context.cloudShadow?.invoke(); sky2 = context.bakedSky?.invoke() }
                        2 -> { later = context.cloudShadow?.invoke(); sky3 = context.bakedSky?.invoke() }
                    }
                    null
                }
            }
            if (frame in 251..329) {
                // one capture per phase: the sky clock is held, then moved on by more than the refresh period
                if (frame == 270) phase = 1
                if (frame == 290) { renderer.skyClock.fix(renderer.skyClock.seconds + 5.0); phase = 2 }
                if (frame == 329) renderer.rayFrameProvider = null
            }
        }
        assertNull(result.error)
        assertNotNull("the map was captured", first)
        assertEquals("unchanged clouds and sun: the same capture, not a new read-back", first!!.revision, again!!.revision)
        assertTrue("two seconds on, drifting clouds refresh it", later!!.revision > first!!.revision)
        assertEquals(sky1, sky2)
        assertTrue("the baked sky is refreshed with the clouds", sky3 !== sky2)
    }
}
