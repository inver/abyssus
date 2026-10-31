/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Planar reflections in a real frame: models and sky seen through the surface, moving with the displayed pose, stronger
 * at grazing angles, and free of editor overlays. Opt-in GL checks.
 */
class SceneWaterReflectionGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val orbit = OrbitCamera(Vector3(0f, 0f, 0f), 25f, 0.6f, 0.35f)

    private fun shoot(params: SceneRenderParams, orbit: OrbitCamera = this.orbit, configure: (SceneRenderer) -> Unit = {}) =
        WaterGlRig.shoot(params, orbit, configure = configure)

    /** The mean absolute colour difference of two reflection images around the pixel of the world point [at] seen by the main camera. */
    private fun differenceAround(shot: WaterShot, other: WaterShot, at: Vector3, radius: Int = 3): Double {
        val camera = shot.renderer.frameCamera
        val screen = Vector3(at)
        camera.project(screen, 0f, 0f, shot.image.width.toFloat(), shot.image.height.toFloat())
        val a = shot.reflection.getValue(0)
        val b = other.reflection.getValue(0)
        val px = (screen.x * a.width / shot.image.width).toInt()
        val py = ((shot.image.height - screen.y) * a.height / shot.image.height).toInt()
        var total = 0.0
        var count = 0
        for (dy in -radius..radius) for (dx in -radius..radius) {
            val x = (px + dx).coerceIn(0, a.width - 1)
            val y = (py + dy).coerceIn(0, a.height - 1)
            val (r1, g1, b1) = a.rgb(x, y)
            val (r2, g2, b2) = b.rgb(x, y)
            total += (kotlin.math.abs(r1 - r2) + kotlin.math.abs(g1 - g2) + kotlin.math.abs(b1 - b2)) / 3.0
            count++
        }
        return total / count
    }

    private fun centreOf(shot: WaterShot, entityId: String): Vector3 = Vector3(shot.modelCentres.getValue(entityId))

    @Test fun aModelAboveTheLevelAppearsMirroredBelowItInTheReflection() {
        val with = shoot(WaterGlScenes.flatLake())
        val without = shoot(WaterGlScenes.flatLake(withModels = false))
        for (id in listOf("2", "6")) {
            val centre = centreOf(with, id)
            assertTrue("model $id stands above the level", centre.y > 0f)
            val mirrored = Vector3(centre.x, -centre.y, centre.z)
            // the pixel where the main camera sees the mirror image holds the model in the reflection and sky or floor without it
            val seen = differenceAround(with, without, mirrored)
            assertTrue("model $id shows in the reflection at its mirror image ($seen)", seen > 12.0)
            // where the model itself stands, the reflection (a view from beneath) shows nothing of it
            val atModel = differenceAround(with, without, centre)
            assertTrue("model $id does not appear at its own position ($atModel < $seen)", atModel < seen)
        }
    }

    @Test fun geometryBelowTheLevelNeverReachesTheReflection() {
        val high = shoot(WaterGlScenes.flatLake(listOf(WaterGlScenes.lake(level = 6.0))))
        val none = shoot(WaterGlScenes.flatLake(listOf(WaterGlScenes.lake(level = 6.0)), withModels = false))
        val camera = high.renderer.frameCamera
        var different = 0
        val a = high.reflection.getValue(0)
        val b = none.reflection.getValue(0)
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val (r1, g1, b1) = a.rgb(x, y)
            val (r2, g2, b2) = b.rgb(x, y)
            if (kotlin.math.abs(r1 - r2) + kotlin.math.abs(g1 - g2) + kotlin.math.abs(b1 - b2) > 18) different++
        }
        assertTrue("models and floor are all below the level: nothing reaches the reflection ($different of ${a.width * a.height}, ${camera.far})", different < a.width * a.height / 500)
    }

    private val clear = Color(0.92f, 0.94f, 0.98f, 1f)

    /** The scene with no sky, a bright clear colour and no models: a reflection shows the clear colour wherever nothing stands. */
    private fun bareScene(): SceneRenderParams {
        // murky, still water: the floor does not show through, so what the water shows is its tint and the reflection
        val murky = WaterGlScenes.lake().copy(clarity = 0.3, waveAmplitude = 0.0, foamAmount = 0.0)
        val params = WaterGlScenes.flatLake(listOf(murky), withModels = false) { _, root -> root.put("skyboxEnabled", false) }
        return params.copy(clear = clear)
    }

    @Test fun withoutASkyTheReflectionShowsTheClearColourAndWithOneItShowsTheSky() {
        val plain = shoot(bareScene())
        val reflection = plain.reflection.getValue(0)
        val (r, g, b) = reflection.rgb(reflection.width / 2, reflection.height * 9 / 10)
        assertEquals(Math.round(clear.r * 255f).toFloat(), r.toFloat(), 3f)
        assertEquals(Math.round(clear.g * 255f).toFloat(), g.toFloat(), 3f)
        assertEquals(Math.round(clear.b * 255f).toFloat(), b.toFloat(), 3f)
        val sky = shoot(WaterGlScenes.flatLake(withModels = false) { _, root -> root.put("skyboxEnabled", true).put("skyboxName", "skybox_default") })
        val skyReflection = sky.reflection.getValue(0)
        val (sr, sg, sb) = skyReflection.rgb(skyReflection.width / 2, skyReflection.height * 9 / 10)
        assertTrue("a cube sky shows in the reflection (${listOf(sr, sg, sb)})", kotlin.math.abs(sr - r) + kotlin.math.abs(sg - g) + kotlin.math.abs(sb - b) > 30)
    }

    @Test fun everySkyKindShowsInTheReflection() {
        val reference = shoot(bareScene()).reflection.getValue(0)
        val clearAt = reference.rgb(reference.width / 2, reference.height * 9 / 10)
        fun skyAt(patch: (java.io.File, com.fasterxml.jackson.databind.node.ObjectNode) -> Unit): Triple<Int, Int, Int> {
            val shot = shoot(WaterGlScenes.flatLake(withModels = false, patch = patch))
            val reflection = shot.reflection.getValue(0)
            return reflection.rgb(reflection.width / 2, reflection.height * 9 / 10)
        }
        fun apart(a: Triple<Int, Int, Int>) = kotlin.math.abs(a.first - clearAt.first) + kotlin.math.abs(a.second - clearAt.second) + kotlin.math.abs(a.third - clearAt.third)
        val cube = skyAt { _, root -> root.put("skyboxEnabled", true).put("skyboxName", "skybox_default") }
        val procedural = skyAt { dir, root ->
            java.io.File(java.io.File("src/test/testData/project/Untitled"), "assets/skybox_physical").copyRecursively(java.io.File(dir, "assets/skybox_physical"), overwrite = true)
            root.put("skyboxEnabled", true).put("skyboxName", "skybox_physical")
        }
        val hdr = skyAt { dir, root ->
            val sky = java.io.File(dir, "assets/sky")
            net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrFixtures.writeExr(java.io.File(sky, "sky.exr"), 64, 32, pixel = { _, _ -> floatArrayOf(2.0f, 0.1f, 0.1f) })
            java.io.File(sky, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{"file":"sky.exr"}}""")
            root.put("skyboxEnabled", true).put("skyboxName", "sky")
        }
        assertTrue("the cube sky shows ($cube vs clear $clearAt)", apart(cube) > 30)
        assertTrue("the procedural sky shows ($procedural vs clear $clearAt)", apart(procedural) > 30)
        assertTrue("the HDR sky shows ($hdr vs clear $clearAt)", apart(hdr) > 30)
    }

    @Test fun aSelectionItsGizmoAndTheWaterBelowNeverReachTheReflection() {
        val plain = shoot(WaterGlScenes.flatLake())
        val selected = shoot(WaterGlScenes.flatLake()) { renderer -> renderer.state.selectedId = "2" }
        val a = plain.reflection.getValue(0)
        val b = selected.reflection.getValue(0)
        assertTrue("the highlight and gizmo are editor overlays", a.bytes.contentEquals(b.bytes))
        assertTrue("while the main view does show them", (0 until plain.image.height).any { y -> (0 until plain.image.width).any { x -> plain.image.getRGB(x, y) != selected.image.getRGB(x, y) } })
    }

    @Test fun sceneFogIsAppliedOnceToTheWaterByDistance() {
        val fogged = WaterGlScenes.flatLake(listOf(WaterGlScenes.lake(level = 0.0).copy(width = 2000.0, length = 2000.0, clarity = 0.3, waveAmplitude = 0.0)), withModels = false, keepFog = true) { _, root ->
            root.put("fogEnabled", true)
            root.putObject("fog").put("density", 0.01).put("gradient", 1.0).putObject("color").put("r", 1.0).put("g", 0.0).put("b", 0.0).put("a", 1.0)
        }
        val view = OrbitCamera(Vector3(0f, 0f, 0f), 30f, 0.2f, 0.12f)
        val shot = shoot(fogged, view)
        val eye = view.position()
        val length = Math.hypot((0f - eye.x).toDouble(), (0f - eye.z).toDouble()).toFloat()
        fun redShareAt(distance: Float): Double {
            val p = Vector3(eye.x + (0f - eye.x) / length * distance, 0f, eye.z + (0f - eye.z) / length * distance)
            shot.renderer.frameCamera.project(p, 0f, 0f, shot.image.width.toFloat(), shot.image.height.toFloat())
            val (r, g, b) = WaterGlRig.rgb(shot.image, p.x.toInt().coerceIn(0, shot.image.width - 1), (shot.image.height - p.y).toInt().coerceIn(0, shot.image.height - 1))
            return r.toDouble() / (r + g + b + 1)
        }
        val near = redShareAt(25f)
        val far = redShareAt(320f)
        assertTrue("the far water carries the fog colour, the near water hardly ($far vs $near)", far > near + 0.2)
        assertTrue("fog at the far end is nearly the fog colour ($far)", far > 0.8)
    }

    @Test fun reflectionGrowsStrongerAtGrazingAngles() {
        fun waterBrightness(pitch: Float): Double {
            val shot = shoot(bareScene(), OrbitCamera(Vector3(0f, 0f, 0f), 30f, 0.3f, pitch))
            val camera = shot.renderer.frameCamera
            val centre = Vector3(0f, 0f, 0f)
            camera.project(centre, 0f, 0f, shot.image.width.toFloat(), shot.image.height.toFloat())
            return WaterGlRig.luminance(WaterGlRig.rgb(shot.image, centre.x.toInt(), (shot.image.height - centre.y).toInt()))
        }
        val overhead = waterBrightness(1.45f)
        val grazing = waterBrightness(0.12f)
        assertTrue("overhead $overhead, grazing $grazing", grazing > overhead + 0.15)
    }

    @Test fun theReflectionFollowsTheDisplayedPoseOfAMovedModel() {
        val base = WaterGlScenes.flatLake()
        val before = shoot(base)
        val model = base.content.models.first { it.entityId == "2" }
        val moved = shoot(base) { renderer ->
            renderer.state.preview = mapOf(
                "2" to net.nevinsky.abyssus.lib.core.editor.pick.DragResult(
                    model.transform.copy(position = Vector3(model.transform.position.x + 5f, model.transform.position.y, model.transform.position.z)), null,
                ),
            )
        }
        val centre = centreOf(before, "2")
        val oldSpot = Vector3(centre.x, -centre.y, centre.z)
        val newSpot = Vector3(centre.x + 5f, -centre.y, centre.z)
        assertTrue("the reflection left its old place", differenceAround(before, moved, oldSpot) > 8.0)
        assertTrue("and arrived at the new one", differenceAround(before, moved, newSpot) > 8.0)
    }

    @Test fun anAnimatedModelMovesInItsReflectionWithoutASecondAdvance() {
        val dir = java.io.File("src/test/testData/project/Animated")
        val root = net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parseObject(java.io.File(dir, "scenes/Main.scene").readText())
        val base = net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf(
            net.nevinsky.abyssus.lib.core.editor.parseScene(net.nevinsky.abyssus.lib.core.editor.document.SceneJson().compact(root)),
            net.nevinsky.abyssus.lib.core.editor.scene.CameraParams.DEFAULT, dir,
        )
        val position = base.content.models.first().transform.position
        val lake = WaterGlScenes.lake(level = position.y - 0.3.toDouble()).copy(x = position.x.toDouble(), z = position.z.toDouble())
        val text = net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parseObject(java.io.File(dir, "scenes/Main.scene").readText())
        text.putObject("abyssus").putObject("waterSurfaces").set<com.fasterxml.jackson.databind.node.ObjectNode>("w1", net.nevinsky.abyssus.lib.core.editor.water.WaterCodec().node(lake))
        text.put("fogEnabled", false)
        val wet = net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf(
            net.nevinsky.abyssus.lib.core.editor.parseScene(net.nevinsky.abyssus.lib.core.editor.document.SceneJson().compact(text)),
            net.nevinsky.abyssus.lib.core.editor.scene.CameraParams.DEFAULT, dir,
        )
        val target = Vector3(position.x, position.y, position.z)
        val orbit = OrbitCamera(target, 14f, 0.4f, 0.4f)
        var early: TargetImage? = null
        var late: TargetImage? = null
        WaterGlRig.shoot(wet, orbit, frames = 90, each = { renderer, index ->
            if (index == 70) early = renderer.waterStatus?.targets?.reflection(0)?.let(WaterGlRig::read)
            if (index == 89) late = renderer.waterStatus?.targets?.reflection(0)?.let(WaterGlRig::read)
        })
        val a = early!!
        val b = late!!
        var differing = 0
        for (i in 0 until a.width * a.height) if (kotlin.math.abs((a.bytes[i * 4].toInt() and 255) - (b.bytes[i * 4].toInt() and 255)) > 6) differing++
        assertTrue("the animation shows in the reflection ($differing pixels changed)", differing > 20)
    }
}
