/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import net.nevinsky.abyssus.plugin.sceneview.SceneRenderer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The real renderer with cloud shadows: when the map shades the sun instead of the uniform dimming, and when it does
 * not. Opt-in (`-Dabyssus.glTests=true`); works on a small copy of the `Untitled` fixture, never the fixture itself.
 */
class CloudShadowSceneGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val fixture = File("src/test/testData/project/Untitled")
    private val cloudsUuid = "3f2a9c1e-7b4d-4e8a-9c6f-1d2e3b4a5c6d"
    private val origin = Vec3(0f, 0f, 0f)
    private val sun = LightPlacement(
        "sun", LightKind.DIRECTIONAL, Rgba(1f, 1f, 1f, 1f), 2f, origin, Vec3(-0.3f, -0.8f, -0.5f),
    )
    private val spot = LightPlacement(
        "8", LightKind.SPOT, Rgba(1f, 0.5f, 0f, 1f), 3f, Vec3(0f, 5f, 0f), Vec3(0f, -1f, 0f), coneAngle = 40f,
    )

    /** A project with the fixture's terrain and sky, the sky naming a fair cloud asset; deleted after [test]. */
    private fun withProject(test: (SceneRenderParams) -> Unit) {
        val dir = Files.createTempDirectory("cloudshadows").toFile()
        try {
            File(fixture, "Untitled.abss").copyTo(File(dir, "Untitled.abss"))
            File(fixture, "assets/skybox_physical").copyRecursively(File(dir, "assets/skybox_physical"))
            val terrain = fixture.resolve("assets").listFiles()!!.first { it.name.startsWith("terrain_") }
            terrain.copyRecursively(File(dir, "assets/${terrain.name}"))
            val meta = File(dir, "assets/skybox_physical/meta.json")
            meta.writeText(meta.readText().replace("\"sunIntensity\": 20.0", "\"sunIntensity\": 20.0,\n    \"clouds\": \"$cloudsUuid\""))
            File(dir, "assets/clouds_fair").mkdirs()
            File(dir, "assets/clouds_fair/meta.json").writeText(
                """{"format": "abyssus", "formatVersion": 1, "uuid": "$cloudsUuid", "type": "CLOUDS", "additional": {"low": {"level": "LOW", "type": "CUMULUS"}, "mid": {"level": "MID", "type": "ALTOCUMULUS"}}}"""
            )
            val text = File(fixture, "scenes/Main Scene.scene").readText()
            val base = renderParamsOf(parseScene(text), CameraParams.DEFAULT, dir)
            val content = base.content.copy(models = emptyList(), lights = listOf(sun, spot), cameras = emptyList())
            test(base.copy(content = content, fog = null))
        } finally {
            dir.deleteRecursively()
        }
    }

    private class Seen(var shadows: Boolean = false, var sun: Rgba? = null, var spots: Int = 0, var scale: Float = 0f)

    @Test
    fun theMapShadesTheSunInsteadOfTheUniformDimmingAndLeavesOtherLightsAlone() = withProject { p ->
        val seen = Seen()
        var active = false
        val result = GlHarness.render(p, 400) { renderer, frame ->
            if (renderer.cloudShadowsActive) {
                active = true
                seen.shadows = true
                seen.sun = renderer.litLights.directional.single().color
                seen.spots = renderer.litLights.spot.size
                seen.scale = renderer.sunScale
            } else if (!active) Thread.sleep(10)
        }
        assertNull(result.error)
        assertTrue("cloud shadows became active", seen.shadows)
        assertEquals("the sun keeps its full color: no double dimming", Rgba(2f, 2f, 2f, 1f), seen.sun)
        assertEquals("the spot light is still lit", 1, seen.spots)
        assertTrue("the uniform factor stays available for beyond the map: ${seen.scale}", seen.scale in 0.1f..1f)
    }

    @Test
    fun noUsableSunMeansNoCloudShadowsAndNoOtherLightIsShaded() = withProject { p ->
        val noSun = p.copy(content = p.content.copy(lights = listOf(spot)))
        var ever = false
        var spots = 0
        val result = GlHarness.render(noSun, 250) { renderer, _ ->
            ever = ever || renderer.cloudShadowsActive
            spots = renderer.litLights.spot.size
            Thread.sleep(5)
        }
        assertNull(result.error)
        assertFalse("a scene without a directional light has no cloud shadows", ever)
        assertEquals(1, spots)
    }

    @Test
    fun removingTheSkyOrItsCloudsClearsTheShadowsAtOnce() = withProject { p ->
        var activeBefore = false
        var activeAfter = true
        val result = GlHarness.render(p, 450) { renderer, frame ->
            when {
                frame < 300 && renderer.cloudShadowsActive -> activeBefore = true
                frame < 300 && !activeBefore -> Thread.sleep(10)
                frame == 300 -> renderer.params = p.copy(content = p.content.copy(skybox = null))
                frame == 449 -> activeAfter = renderer.cloudShadowsActive
            }
        }
        assertNull(result.error)
        assertTrue("cloud shadows were active first", activeBefore)
        assertFalse("no sky, no cloud shadows", activeAfter)
    }

    @Test
    fun rayTracingGetsTheRealMapOnceForTheSunAndNothingWithoutClouds() = withProject { p ->
        var copy: net.nevinsky.abyssus.lib.raytracing.RaySunVisibility? = null
        var again: net.nevinsky.abyssus.lib.raytracing.RaySunVisibility? = null
        var scale = 0f
        var afterRemoval: net.nevinsky.abyssus.lib.raytracing.RaySunVisibility? = null
        var provided = false
        val result = GlHarness.render(p, 500) { renderer, frame ->
            if (frame == 0) renderer.rayFrameProvider = { context ->
                val ask = context.sunVisibility
                val value = ask?.invoke()
                if (renderer.cloudShadowsActive && copy == null && value != null) {
                    copy = value; again = ask.invoke(); scale = renderer.sunScale
                }
                if (copy != null && renderer.params.content.skybox == null) afterRemoval = value
                provided = true
                null
            }
            when {
                frame < 300 && copy == null -> Thread.sleep(10)
                frame == 300 -> renderer.params = p.copy(content = p.content.copy(skybox = null))
            }
        }
        assertNull(result.error)
        assertTrue("the feed asked for the sun visibility", provided)
        val map = checkNotNull(copy) { "rays never got a cloud shade" }
        assertEquals("sun", map.lightId)
        assertEquals(256, map.size)
        assertSame("asked twice in one frame: the same copy", map, again)
        assertEquals("beyond the map the uniform factor applies", scale, map.outside, 0.05f)
        assertEquals(0.1f, map.edgeFade, 0f)
        assertTrue("the copy holds real shade: darkest ${map.visibility().min()}", map.visibility().min() < 0.95f)
        assertTrue("and only valid shares", map.visibility().all { it in 0f..1f })
        assertNull("no sky, no clouds, no shade for rays", afterRemoval)
    }
}

