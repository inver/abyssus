/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.pick.ScenePicker
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.water.WaterSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * What the water passes draw into their offscreen targets: the terrain at its world transform, geometry above the level
 * only, and none of the editor's grid and markers, the water itself or blended models. Opt-in GL checks:
 * `./gradlew :plugin-abyssus:test -Dabyssus.glTests=true`.
 */
class SceneWaterPassGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val orbit get() = OrbitCamera(Vec3(0f, 0f, 0f), 25f, 0.6f, 0.35f)
    private fun lake(level: Double = 0.0, tint: Triple<Double, Double, Double>? = null): WaterSettings = WaterGlScenes.lake(level, tint)

    private fun scene(
        water: WaterSettings = lake(),
        terrainDy: Double = 0.0,
        withModels: Boolean = true,
        patch: (File, com.fasterxml.jackson.databind.node.ObjectNode) -> Unit = { _, _ -> },
    ): SceneRenderParams = WaterGlScenes.flatLake(listOf(water), terrainDy, withModels, patch = patch)

    private fun shoot(params: SceneRenderParams, configure: (SceneRenderer) -> Unit = {}) =
        WaterGlRig.shoot(params, orbit, configure = configure)

    /** The linear view depth, as a share of the far plane, of the plane y = [y] along the pixel's ray. */
    private fun planeDepth(shot: WaterShot, x: Int, yFromTop: Int, planeY: Float): Float {
        val camera = shot.renderer.frameCamera
        val ray = ScenePicker().pickRay(camera, x * shot.image.width / shot.sceneDepth!!.width, yFromTop * shot.image.height / shot.sceneDepth.height, shot.image.width, shot.image.height)
        val t = (planeY - ray.origin.y) / ray.direction.y
        val hit = Vector3(ray.origin).mulAdd(ray.direction, t)
        return Vector3(hit).sub(camera.position).dot(camera.direction) / camera.far
    }

    @Test fun theTerrainDepthTargetHoldsTheTerrainAtItsWorldTransform() {
        val shot = shoot(scene(terrainDy = -5.0))
        val depth = shot.terrainDepth!!
        // the floor is flat at -2 in its own space and the terrain entity sits 5 lower: a plane at y = -7
        for ((x, y) in listOf(depth.width / 2 to depth.height * 3 / 4, depth.width / 4 to depth.height * 3 / 4, depth.width * 3 / 4 to depth.height * 7 / 8)) {
            assertEquals("pixel $x,$y", planeDepth(shot, x, y, -7f), depth.depth(x, y), 6e-5f)
        }
        val shifted = shoot(scene(terrainDy = 0.0))
        assertTrue(
            "the terrain depth follows the terrain's transform",
            kotlin.math.abs(shifted.terrainDepth!!.depth(depth.width / 2, depth.height * 3 / 4) - depth.depth(depth.width / 2, depth.height * 3 / 4)) > 0.001f,
        )
    }

    @Test fun theSceneDepthIsNearerThanTheTerrainWhereAModelStands() {
        val shot = shoot(scene())
        val scene = shot.sceneDepth!!
        val terrain = shot.terrainDepth!!
        var nearer = 0
        var farther = 0
        for (y in 0 until scene.height step 4) for (x in 0 until scene.width step 4) {
            val s = scene.depth(x, y)
            val t = terrain.depth(x, y)
            if (s < t - 1e-4f) nearer++
            if (s > t + 1e-4f) farther++
        }
        assertTrue("models are in the scene depth", nearer > 40)
        assertEquals("nothing is behind the terrain", 0, farther)
    }

    private fun differing(a: TargetImage, b: TargetImage): Double {
        require(a.width == b.width && a.height == b.height)
        var differ = 0
        for (i in 0 until a.width * a.height) {
            val j = i * 4
            if (kotlin.math.abs((a.bytes[j].toInt() and 255) - (b.bytes[j].toInt() and 255)) > 6 ||
                kotlin.math.abs((a.bytes[j + 1].toInt() and 255) - (b.bytes[j + 1].toInt() and 255)) > 6 ||
                kotlin.math.abs((a.bytes[j + 2].toInt() and 255) - (b.bytes[j + 2].toInt() and 255)) > 6
            ) differ++
        }
        return differ.toDouble() / (a.width * a.height)
    }

    @Test fun theReflectionKeepsOnlyGeometryAboveTheLevel() {
        val above = shoot(scene(lake(level = 0.0))).reflection.getValue(0)
        val allBelow = shoot(scene(lake(level = 6.0))).reflection.getValue(0)
        val noModels = shoot(scene(lake(level = 6.0), withModels = false)).reflection.getValue(0)
        assertTrue("models above the level are in the reflection", differing(above, noModels) > 0.01)
        assertTrue("models below the level are clipped out of it", differing(allBelow, noModels) < 0.002)
    }

    @Test fun theReflectionHasNeitherGridMarkersNorWater() {
        val plain = shoot(scene(lake(tint = Triple(0.03, 0.22, 0.25)))).reflection.getValue(0)
        val stripped = shoot(
            scene(lake(tint = Triple(0.9, 0.1, 0.1)).copy(clarity = 80.0), patch = { _, root ->
                // no camera entity: no camera marker; its absence must not matter to the reflection
                val tree = net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree(root)
                (tree.entities() as com.fasterxml.jackson.databind.node.ObjectNode).remove("4")
            }),
            configure = { it.display = it.display.copy(grid = false) },
        ).reflection.getValue(0)
        assertEquals("grid, markers and the water's own colour never reach the reflection", 0.0, differing(plain, stripped), 0.0005)
    }

    @Test fun blendedModelsAreDrawnAfterTheWaterAndStayOutOfEveryTarget() {
        var quad = ""
        val shot = shoot(scene(patch = { dir, root ->
            WaterGlScenes.addBlendedAsset(dir)
            quad = WaterGlScenes.placeBlended(root, 0.0, 0.0, 3.0)
        }))
        // the reflection is the same with and without the red quad: the other models have red parts of their own
        val withoutQuad = shoot(scene())
        assertEquals("no blended pixels in the reflection", 0.0, differing(shot.reflection.getValue(0), withoutQuad.reflection.getValue(0)), 0.0002)
        val reflection = shot.reflection.getValue(0)
        // the scene depth equals the terrain depth where the quad stands: it is not in the depth targets
        var inDepth = 0
        val scene = shot.sceneDepth!!
        val terrain = shot.terrainDepth!!
        val camera = shot.renderer.frameCamera
        val centre = Vector3(0f, 1f, 3f)
        camera.project(centre, 0f, 0f, shot.image.width.toFloat(), shot.image.height.toFloat())
        val px = (centre.x * scene.width / shot.image.width).toInt()
        val py = ((shot.image.height - centre.y) * scene.height / shot.image.height).toInt()
        for (dy in -3..3) for (dx in -3..3) if (scene.depth(px + dx, py + dy) < terrain.depth(px + dx, py + dy) - 1e-4f) inDepth++
        // other models may overlap that area; the blended quad must not make the scene nearer than its neighbours do
        val noQuadDepth = withoutQuad.sceneDepth!!
        var differs = 0
        for (dy in -3..3) for (dx in -3..3) if (kotlin.math.abs(scene.depth(px + dx, py + dy) - noQuadDepth.depth(px + dx, py + dy)) > 1e-4f) differs++
        assertEquals("the blended quad is absent from the scene depth", 0, differs)
        // yet the final frame shows it, drawn after the water
        var visible = 0
        for (y in 0 until shot.image.height) for (x in 0 until shot.image.width) {
            val (r, g, b) = WaterGlRig.rgb(shot.image, x, y)
            if (r > 120 && g < 90 && b < 90 && r - g > 70) visible++
        }
        assertTrue("the blended quad is in the frame ($visible red pixels, entity $quad, $inDepth)", visible > 100)
    }
}
