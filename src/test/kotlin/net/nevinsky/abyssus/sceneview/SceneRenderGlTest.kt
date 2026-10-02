/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.filetype.SceneJson
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.parseScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Real GL rendering of the test projects. Opt-in: `./gradlew test -Dabyssus.glTests=true`. */
class SceneRenderGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private fun params(project: String, scene: String, patch: (String) -> String = { it }): SceneRenderParams {
        val dir = File("src/test/testData/project/$project")
        val text = patch(File(dir, "scenes/$scene").readText())
        return SceneRenderParams.from(parseScene(text), CameraParams.DEFAULT, dir)
    }

    private fun edit(text: String, change: (ObjectNode) -> Unit): String = SceneJson.compact(SceneJson.parseObject(text).also(change))

    private fun noFog(root: ObjectNode) {
        root.put("fogEnabled", false)
    }

    private fun untilLoaded(want: (SceneRenderer) -> Boolean): (SceneRenderer, Int) -> Unit = { r, _ ->
        if (!want(r)) Thread.sleep(10)
    }

    @Test
    fun mainSceneDrawsModelsAndTerrain() {
        val p = params("Untitled", "Main Scene.scene") { edit(it, ::noFog) }
        var models = 0
        var terrains = 0
        val r = GlHarness.render(p, 240) { renderer, _ ->
            models = renderer.drawnModels.size
            terrains = renderer.drawnTerrains.size
        }
        assertNull(r.error)
        assertEquals(3, models)
        assertEquals(1, terrains)
        assertTrue("scene drew almost nothing: ${GlHarness.coverage(r.image, p)}", GlHarness.coverage(r.image, p) > 0.2)
    }

    @Test
    fun brokenAssetsDoNotStopTheRest() {
        // one model's asset folder does not exist, the others still draw
        val p = params("Untitled", "Main Scene.scene") { it.replace("model_900f6f61-6384-434a-be81-56ce303fbb56", "model_missing") }
        var models = 0
        val r = GlHarness.render(p, 240) { renderer, _ -> models = renderer.drawnModels.size }
        assertNull(r.error)
        assertEquals(2, models)
    }

    @Test
    fun animatedModelsGetTheirOwnLoopingAnimation() {
        val p = params("Animated", "Main.scene")
        var entities = emptyList<ModelEntity>()
        val seen = HashSet<Float>()
        val r = GlHarness.render(p, 120) { renderer, _ ->
            entities = renderer.drawnModels.toList()
            entities.firstOrNull()?.animation?.current?.let { seen += it.time }
        }
        assertNull(r.error)
        assertEquals(2, entities.size)
        val a = entities[0].animation
        val b = entities[1].animation
        assertNotNull(a)
        assertNotNull(b)
        assertNotSame(a, b)
        assertNotSame(entities[0].instance, entities[1].instance)
        assertTrue("animation did not advance: $seen", seen.size > 3)
        assertTrue(entities[0].instance.animations.size > 0)
    }

    @Test
    fun staticModelsHaveNoAnimation() {
        val p = params("Untitled", "Main Scene.scene")
        var animated = -1
        GlHarness.render(p, 200) { renderer, _ -> animated = renderer.drawnModels.count { it.animation != null } }
        assertEquals(0, animated)
    }

    @Test
    fun skyboxReplacesTheClearColorBackground() {
        val p = params("Untitled", "Main Scene.scene") { edit(it) { root -> noFog(root); root.put("skyboxName", "skybox_default") } }
        assertEquals("skybox_default", p.content.skybox)
        val withSky = GlHarness.render(p, 200)
        assertNull(withSky.error)
        val noSky = params("Untitled", "Main Scene.scene") { edit(it) { root -> noFog(root); root.putNull("skyboxName") } }
        val without = GlHarness.render(noSky, 200)
        // the top-left corner is sky in one and plain clear color in the other
        assertTrue(withSky.image.getRGB(2, 2) != without.image.getRGB(2, 2))
    }

    @Test
    fun proceduralSkyCompilesAndDraws() {
        val p = params("Untitled", "Main Scene.scene") { edit(it) { root -> noFog(root); root.put("skyboxName", "skybox_physical") } }
        val withSky = GlHarness.render(p, 200)
        assertNull(withSky.error)
        val noSky = params("Untitled", "Main Scene.scene") { edit(it) { root -> noFog(root); root.putNull("skyboxName") } }
        val without = GlHarness.render(noSky, 200)
        assertTrue(withSky.image.getRGB(2, 2) != without.image.getRGB(2, 2))
        val top = withSky.image.getRGB(withSky.image.width / 2, 2)
        assertTrue("the sky near the top should be bluer than red: ${Integer.toHexString(top)}", (top and 0xff) > ((top shr 16) and 0xff))
    }

    @Test
    fun lightEntitiesChangeTheShading() {
        val base = params("Untitled", "Main Scene.scene") { edit(it, ::noFog) }
        val light = """{"archetype":1,"components":{"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},"LightComponent":{"light":{"color":{"r":1,"g":1,"b":1,"a":1},"intensity":1.0}},"PositionComponent":{"localRotation":{"x":-0.7071,"w":0.7071}}}}"""
        val lit = params("Untitled", "Main Scene.scene") {
            edit(it) { root ->
                noFog(root)
                (root.get("ecs").get("entities") as ObjectNode).set<com.fasterxml.jackson.databind.JsonNode>("99", SceneJson.parse(light))
            }
        }
        assertEquals(1, lit.content.lights.size)
        val a = GlHarness.render(base, 200)
        val b = GlHarness.render(lit, 200)
        assertNull(a.error)
        assertNull(b.error)
        fun brightness(img: java.awt.image.BufferedImage): Long {
            var sum = 0L
            for (y in 0 until img.height step 4) for (x in 0 until img.width step 4) {
                val c = img.getRGB(x, y)
                sum += ((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)
            }
            return sum
        }
        assertTrue("a directional light should brighten the scene", brightness(b.image) > brightness(a.image))
    }

    @Test
    fun clickingFindsTheModelsAndTheTerrain() {
        val p = params("Untitled", "Main Scene.scene") { edit(it, ::noFog) }
        val found = HashSet<String>()
        val r = GlHarness.render(p, 240) { renderer, frame ->
            if (frame == 239) {
                val w = renderer.lastWidth
                val h = renderer.lastHeight
                for (y in 0 until h step h / 25) for (x in 0 until w step w / 40) renderer.pick(x, y, w, h)?.let { found += it }
            }
        }
        assertNull(r.error)
        // entities 0, 2 and 6 are models, 1 is the terrain, 4 is the camera (see Main Scene.scene)
        assertTrue("picked only $found", found.containsAll(setOf("0", "2", "6", "1")))
        assertTrue("picked something else: $found", (found - setOf("0", "2", "6", "1", "4")).isEmpty())
    }

    @Test
    fun anAbandonedContextIsReplacedByFreshlyLoadedAssets() {
        // what happens when the view is hidden (context abandoned, renderer not disposed) and shown again
        val p = params("Untitled", "Main Scene.scene") { edit(it, ::noFog) }
        val firstModels = ArrayList<Any>()
        var afterRecreate = -1
        var sameObjects = true
        val r = GlHarness.render(p, 300) { renderer, frame ->
            if (frame == 150) {
                firstModels.addAll(renderer.drawnModels.map { it.model })
                renderer.create()
                afterRecreate = renderer.drawnModels.size
            }
            if (frame == 299) sameObjects = renderer.drawnModels.any { m -> firstModels.any { it === m.model } }
        }
        assertNull(r.error)
        assertEquals(3, firstModels.size)
        assertEquals("create() must forget the models of the old context", 0, afterRecreate)
        assertTrue("models must be rebuilt, not reused", !sameObjects)
    }

    @Test
    fun theSceneIsGrayedWithASpinnerWhileAssetsLoad() {
        val p = params("Untitled", "Main Scene.scene") { edit(it, ::noFog) }
        val gate = java.util.concurrent.CountDownLatch(1)
        val slow = java.util.concurrent.Executor { job -> Thread { gate.await(); job.run() }.also { it.isDaemon = true }.start() }
        val sawLoading = java.util.concurrent.atomic.AtomicInteger()
        var loadingFrame: java.awt.image.BufferedImage? = null
        val loaded = GlHarness.render(p, 200, executor = slow) { renderer, frame ->
            if (renderer.loading) sawLoading.incrementAndGet()
            if (frame == 20) gate.countDown() // let the assets through after twenty grayed frames
        }
        assertNull(loaded.error)
        assertTrue("the overlay was never shown", sawLoading.get() >= 15)
        assertTrue("the overlay must go away once everything is loaded", sawLoading.get() < 150)
        val still = GlHarness.render(p, 20, executor = { }) { _, _ -> }
        assertNull(still.error)
        loadingFrame = still.image
        // nothing could load, so the view stays veiled: gray, no scene colors
        val c = loadingFrame.getRGB(loadingFrame.width / 4, loadingFrame.height / 4)
        val r = (c shr 16) and 255
        val g = (c shr 8) and 255
        val b = c and 255
        assertTrue("expected a gray veil, got $r,$g,$b", kotlin.math.abs(r - g) < 40 && kotlin.math.abs(g - b) < 40)
        // the spinner's ring is brighter than the veil somewhere near the center
        var brightest = 0
        val cx = loadingFrame.width / 2
        val cy = loadingFrame.height / 2
        for (y in cy - 80..cy + 80) for (x in cx - 80..cx + 80) brightest = maxOf(brightest, loadingFrame.getRGB(x, y) and 255)
        assertTrue("no spinner pixels near the center (brightest $brightest)", brightest > 200)
    }

    @Test
    fun loadingNeverFreezesTheRenderLoopForLong() {
        // a texture shared by many materials must be loaded once, and big uploads are spread over frames:
        // before that, building one model blocked the loop (and the spinner) for 0.5 to 1.5 seconds
        val p = params("Untitled", "Main Scene.scene") { edit(it, ::noFog) }
        var last = System.nanoTime()
        var worst = 0L
        val r = GlHarness.render(p, 150) { _, frame ->
            val now = System.nanoTime()
            if (frame > 0) worst = maxOf(worst, now - last) // frame 0 compiles the shaders
            last = now
        }
        assertNull(r.error)
        assertTrue("the longest frame took ${worst / 1_000_000} ms", worst < 450_000_000L)
    }

    @Test
    fun mainSceneDrawsItsCameraMarker() {
        val p = params("Untitled", "Main Scene.scene") { edit(it, ::noFog) }
        var markers = -1
        val r = GlHarness.render(p, 60) { renderer, _ -> markers = renderer.drawnCameraMarkers }
        assertNull(r.error)
        assertEquals(1, markers)
    }

    @Test
    fun aSelectedModelRendersItsGizmoWithoutErrors() {
        val p = params("Untitled", "Main Scene.scene") { edit(it, ::noFog) }
        for (mode in net.nevinsky.abyssus.sceneview.gizmo.GizmoMode.entries) {
            var gizmo = false
            val r = GlHarness.render(p, 120) { renderer, _ ->
                renderer.selectedId = "0"
                renderer.gizmoMode = mode
                gizmo = renderer.drewGizmo
            }
            assertNull(r.error)
            assertTrue("no $mode gizmo drawn", gizmo)
        }
    }

    @Test
    fun lookingThroughTheCameraHidesItsOwnMarker() {
        val p = params("Untitled", "Main Scene.scene") { edit(it, ::noFog) }
        var markers = -1
        val r = GlHarness.render(p, 60) { renderer, _ ->
            renderer.viewCamera = "4"
            markers = renderer.drawnCameraMarkers
        }
        assertNull(r.error)
        assertEquals(0, markers)
    }
}
