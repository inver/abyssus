/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.pick.DragResult
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf
import net.nevinsky.abyssus.lib.core.editor.water.WaterCodec
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File

/**
 * The frame is updated once (models posed, animations advanced, previews applied) and then drawn as often as the water
 * needs: the main view, a reflection per level and the depth passes all show the same posed scene, and a scene without
 * visible water renders as it did before. Opt-in GL checks.
 */
class SceneFrameSnapshotTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val presets = WaterPresets()

    private fun withWater(project: String, scene: String, level: Double, x: Double, z: Double): SceneRenderParams {
        val dir = File("src/test/testData/project/$project")
        val root = SceneJson().parseObject(File(dir, "scenes/$scene").readText())
        val lake = presets.defaults(WaterPreset.LAKE, "w1", "Lake", x, level, z).copy(width = 200.0, length = 200.0)
        root.putObject("abyssus").putObject("waterSurfaces").set<ObjectNode>("w1", WaterCodec().node(lake))
        root.put("fogEnabled", false)
        return renderParamsOf(parseScene(SceneJson().compact(root)), CameraParams.DEFAULT, dir)
    }

    private fun withoutWater(project: String, scene: String): SceneRenderParams {
        val dir = File("src/test/testData/project/$project")
        val root = SceneJson().parseObject(File(dir, "scenes/$scene").readText()).also { it.put("fogEnabled", false) }
        return renderParamsOf(parseScene(SceneJson().compact(root)), CameraParams.DEFAULT, dir)
    }

    @Test fun anAnimationAdvancesOnceDespiteTheReflectionAndDepthPasses() {
        val dry = withoutWater("Animated", "Main.scene")
        val first = dry.content.models.first().transform.position
        val wet = withWater("Animated", "Main.scene", first.y - 0.3, first.x.toDouble(), first.z.toDouble())
        fun advance(params: SceneRenderParams): Pair<Float, Int> {
            var step = 0f
            var passes = 0
            val orbit = OrbitCamera(Vector3(first.x, first.y, first.z), 14f, 0.4f, 0.4f)
            val r = GlHarness.render(params, 70, orbit, framebufferSize = 480 to 320) { renderer, index ->
                if (index < 60) {
                    Thread.sleep(15)
                } else if (index == 60) {
                    val animation = renderer.drawnModels.first().animation!!
                    val before = animation.current!!.time
                    renderer.render(renderer.lastWidth, renderer.lastHeight, orbit, 0.05f)
                    step = animation.current!!.time - before
                    passes = (renderer.waterStatus?.let { it.reflectionPasses + it.depthPasses + it.drawnSurfaces } ?: 0)
                }
            }
            r.error?.let { throw it }
            return step to passes
        }
        val (dryStep, dryPasses) = advance(dry)
        val (wetStep, wetPasses) = advance(wet)
        assertEquals("a render advances the animation by its delta", 0.05f, dryStep, 1e-4f)
        assertEquals("water draws extra passes without advancing the animation again", 0.05f, wetStep, 1e-4f)
        assertEquals(0, dryPasses)
        assertTrue("the water frame had several draws ($wetPasses)", wetPasses >= 3)
    }

    private fun image(params: SceneRenderParams, configure: (SceneRenderer) -> Unit = {}): WaterShot =
        WaterGlRig.shoot(params, OrbitCamera(Vector3(0f, 0f, 0f), 25f, 0.6f, 0.35f), configure = configure)

    private fun identical(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    @Test fun aTransformPreviewShowsTheSamePoseInTheViewAndTheReflection() {
        val base = withWater("Untitled", "Main Scene.scene", 0.0, 0.0, 0.0)
        val model = base.content.models.first { it.entityId == "2" }
        val moved = Vector3(model.transform.position.x + 4f, model.transform.position.y, model.transform.position.z - 2f)
        val previewed = image(base) { renderer ->
            renderer.state.preview = mapOf("2" to DragResult(model.transform.copy(position = moved), null))
        }
        // the same scene with the entity really placed there
        val dir = File("src/test/testData/project/Untitled")
        val root = SceneJson().parseObject(File(dir, "scenes/Main Scene.scene").readText())
        val position = net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree(root).components("2")!!.get("PositionComponent").get("localPosition") as ObjectNode
        position.put("x", moved.x.toDouble()).put("z", moved.z.toDouble())
        val lake = presets.defaults(WaterPreset.LAKE, "w1", "Lake", 0.0, 0.0, 0.0).copy(width = 200.0, length = 200.0)
        root.putObject("abyssus").putObject("waterSurfaces").set<ObjectNode>("w1", WaterCodec().node(lake))
        root.put("fogEnabled", false)
        val placed = image(renderParamsOf(parseScene(SceneJson().compact(root)), CameraParams.DEFAULT, dir))
        val unmoved = image(base)
        assertTrue("the reflection follows the preview", !sameBytes(previewed.reflection.getValue(0), unmoved.reflection.getValue(0)))
        assertTrue("the reflection of a preview is that of the moved entity", sameBytes(previewed.reflection.getValue(0), placed.reflection.getValue(0)))
        assertTrue("the depth targets agree too", sameBytes(previewed.sceneDepth!!, placed.sceneDepth!!))
    }

    private fun sameBytes(a: TargetImage, b: TargetImage) = a.width == b.width && a.height == b.height && a.bytes.contentEquals(b.bytes)

    @Test fun aSceneWithoutVisibleWaterRendersAsBefore() {
        val plain = withoutWater("Untitled", "Main Scene.scene")
        val reference = image(plain)
        assertNull(reference.renderer.lastWaterPlan)
        fun variant(edit: (ObjectNode) -> Unit): SceneRenderParams {
            val dir = File("src/test/testData/project/Untitled")
            val root = SceneJson().parseObject(File(dir, "scenes/Main Scene.scene").readText())
            edit(root)
            root.put("fogEnabled", false)
            return renderParamsOf(parseScene(SceneJson().compact(root)), CameraParams.DEFAULT, dir)
        }
        val lake = presets.defaults(WaterPreset.LAKE, "w1", "Lake", 0.0, 0.0, 0.0)
        val variants = mapOf(
            "an empty extension" to variant { it.putObject("abyssus") },
            "an empty container" to variant { it.putObject("abyssus").putObject("waterSurfaces") },
            "a hidden surface" to variant { it.putObject("abyssus").putObject("waterSurfaces").set<ObjectNode>("w1", WaterCodec().node(lake.copy(enabled = false))) },
            "an invalid surface" to variant { it.putObject("abyssus").putObject("waterSurfaces").putObject("bad").put("preset", "LAKE").put("width", -3) },
            "unrelated extension data" to variant { it.putObject("abyssus").put("other", 1) },
        )
        for ((name, params) in variants) {
            val shot = image(params)
            assertNull("$name draws no water", shot.renderer.lastWaterPlan)
            assertTrue("$name renders exactly as a scene without water", identical(reference.image, shot.image))
        }
    }

    @Test fun aCameraAtOrBelowTheSurfaceRendersTheSceneWithoutWater() {
        val plain = image(withoutWater("Untitled", "Main Scene.scene"))
        val dir = File("src/test/testData/project/Untitled")
        val root = SceneJson().parseObject(File(dir, "scenes/Main Scene.scene").readText())
        val lake = presets.defaults(WaterPreset.LAKE, "w1", "Lake", 0.0, 60.0, 0.0).copy(width = 400.0, length = 400.0)
        root.putObject("abyssus").putObject("waterSurfaces").set<ObjectNode>("w1", WaterCodec().node(lake))
        root.put("fogEnabled", false)
        val under = image(renderParamsOf(parseScene(SceneJson().compact(root)), CameraParams.DEFAULT, dir))
        assertNull("no water above a camera below it", under.renderer.lastWaterPlan)
        assertTrue(identical(plain.image, under.image))
    }
}
