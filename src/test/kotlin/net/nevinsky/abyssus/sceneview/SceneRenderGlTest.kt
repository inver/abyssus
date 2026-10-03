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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

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
    fun groundBelowUsesDrawnModelsAndTerrains() {
        val p = params("Untitled", "Main Scene.scene")
        var checked = false
        val result = GlHarness.render(p, 200) { renderer, frame ->
            if (frame == 199) {
                val ground = renderer.drawnTerrains.single()
                val terrain = TerrainTarget("1", ground.terrain.data, ground.world)
                for (id in listOf("0", "2")) {
                    val entity = renderer.drawnModels.first { it.placement.entityId == id }
                    val footprint = OrientedBox(entity.localBounds, entity.instance.transform!!)
                    val height = ScenePicker.restHeight(footprint, emptyList(), listOf(terrain))!!
                    println("Model $id: terrain=$height lowest=${footprint.bottom}")
                    assertEquals(0f, height, 1e-5f)
                    assertTrue(!ScenePicker.isResting(footprint.bottom, height))
                }
                assertEquals(0f, renderer.groundBelow("0")!!, 1e-5f)
                assertNull(renderer.groundBelow("1"))
                assertNull(renderer.groundBelow("missing"))
                renderer.viewCamera = "4"
                assertNull(renderer.groundBelow("4"))
                checked = true
            }
        }
        assertNull(result.error)
        assertTrue(checked)
    }

    @Test
    fun drawnVersionTracksLoadingAndRemovalButNotAnUnchangedFrame() {
        val p = params("Untitled", "Main Scene.scene")
        var previous = 0L
        var ids = emptySet<String>()
        var loaded = false
        var removed = false
        val result = GlHarness.render(p, 220) { renderer, frame ->
            val now = renderer.drawnModels.map { "m:" + it.placement.entityId }.toSet() +
                renderer.drawnTerrains.map { "t:" + it.placement.entityId }
            if (now != ids) assertTrue(renderer.drawnVersion > previous)
            else assertEquals(previous, renderer.drawnVersion)
            ids = now
            previous = renderer.drawnVersion
            if (frame == 180) {
                loaded = now.size == 4
                renderer.params = p.copy(content = SceneContent.EMPTY)
            }
            if (frame == 219) removed = now.isEmpty()
        }
        assertNull(result.error)
        assertTrue(loaded)
        assertTrue(removed)
    }

    @Test
    fun restoringAModelAfterDropRefreshesAvailabilityWithTheSameDrawnIds() {
        val p = params("Untitled", "Main Scene.scene")
        var interaction: SceneInteraction? = null
        var stage = 0
        var written = 0
        var version = 0L
        val result = GlHarness.render(p, 220) { renderer, _ ->
            val input = interaction ?: SceneInteraction(renderer, OrbitCamera.from(p.camera)).also {
                interaction = it
                renderer.selectedId = "0"
                it.onTransform = { id, edit ->
                    val selected = ScenePreview.selected(renderer.content, id)!!
                    val moved = net.nevinsky.abyssus.sceneview.gizmo.DragResult(selected.transform.copy(position = edit.position!!), selected.direction)
                    renderer.params = p.copy(content = ScenePreview.apply(p.content, id, moved))
                    it.paramsChanged(renderer.params)
                    written++
                    true
                }
            }
            input.frameRendered()
            if (renderer.drawnModels.size == 3 && renderer.drawnTerrains.size == 1) when (stage) {
                0 -> {
                    assertTrue(input.canDrop)
                    version = renderer.drawnVersion
                    input.drop()
                    assertFalse(input.canDrop)
                    input.drop()
                    assertEquals(1, written)
                    stage++
                }
                1 -> {
                    assertFalse(input.canDrop)
                    renderer.params = p // a document re-read after Undo restores the original position
                    input.paramsChanged(p)
                    stage++
                }
                2 -> {
                    assertEquals(version, renderer.drawnVersion)
                    assertTrue(input.canDrop)
                    stage++
                }
            }
        }
        assertNull(result.error)
        assertEquals(3, stage)
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

    /** A copy of `Main Scene` in a temp project whose only asset is an HDR sky of uniform [radiance], named `sky`. */
    private fun uniformHdrSky(radiance: Float, block: (SceneRenderParams) -> Unit) {
        val dir = java.nio.file.Files.createTempDirectory("hdrscene").toFile()
        try {
            val sky = File(dir, "assets/sky")
            net.nevinsky.abyssus.assets.sky.hdr.HdrFixtures.write(File(sky, "sky.hdr"), 64, 32, pixel = net.nevinsky.abyssus.assets.sky.hdr.HdrFixtures.uniform(radiance))
            File(sky, "meta.json").writeText("""{"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{}}""")
            val text = edit(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()) { root ->
                noFog(root); root.put("skyboxEnabled", true); root.put("skyboxName", "sky")
            }
            block(SceneRenderParams.from(parseScene(text), CameraParams.DEFAULT, dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** The grey level of the sky near the top centre of the view, above the horizon and the grid. */
    private fun skyGrey(radiance: Float): Int {
        var grey = -1
        uniformHdrSky(radiance) { p ->
            val r = GlHarness.render(p, 120)
            assertNull(r.error)
            val rgb = r.image.getRGB(r.image.width / 2, r.image.height / 10)
            grey = (rgb shr 8) and 0xff
        }
        return grey
    }

    @Test
    fun hdrMidGreyDrawsAt140() {
        val grey = skyGrey(0.18f)
        assertTrue("mid grey drawn at $grey", grey in 137..143)
    }

    @Test
    fun hdrHighlightsAreDistinct() {
        val (a, b, c) = listOf(1f, 2f, 4f).map(::skyGrey)
        assertTrue("1, 2, 4 drawn at $a, $b, $c", a < b && b < c && c < 255)
        assertTrue("1, 2, 4 drawn at $a, $b, $c", abs(a - 231) <= 3 && abs(b - 245) <= 3 && abs(c - 252) <= 3)
    }

    @Test
    fun hdrSkyStaysBehindModels() {
        fun sky(root: ObjectNode) {
            noFog(root); root.put("skyboxEnabled", true); root.put("skyboxName", "skybox_hdr")
        }
        val p = params("Untitled", "Main Scene.scene") { edit(it, ::sky) }
        var models = 0
        val withSky = GlHarness.render(p, 240) { renderer, _ -> models = renderer.drawnModels.size }
        assertNull(withSky.error)
        assertEquals(3, models)
        // the same view with no entities shows the sky alone; without a sky it shows where the content is
        val skyOnly = GlHarness.render(params("Untitled", "Main Scene.scene") { edit(it) { root -> sky(root); (root.get("ecs") as ObjectNode).putObject("entities") } }, 240)
        val noSky = params("Untitled", "Main Scene.scene") { edit(it) { root -> noFog(root); root.putNull("skyboxName") } }
        val without = GlHarness.render(noSky, 240)
        val c = noSky.clear
        val clear = (Math.round(c.r * 255) shl 16) or (Math.round(c.g * 255) shl 8) or Math.round(c.b * 255)
        var content = 0
        var covered = 0
        for (y in 0 until without.image.height) for (x in 0 until without.image.width) {
            if (without.image.getRGB(x, y) and 0xFFFFFF == clear) continue
            content++
            if (withSky.image.getRGB(x, y) != skyOnly.image.getRGB(x, y)) covered++
        }
        assertTrue("no content drawn", content > 1000)
        assertTrue("the sky hid content: $covered of $content content pixels differ from the sky alone", covered > content * 0.9)
    }

    /**
     * Renders `Main Scene` (no fog, its grey ambient) from a temp copy of `Untitled` whose extra asset `sky` is an HDR sky of
     * [pixel], once per [variants] patch; returns the images. [keep] lists the entity ids to keep (all when null).
     */
    private fun renderWithHdrSky(pixel: (Int, Int) -> FloatArray, keep: Set<String>?, vararg variants: (ObjectNode) -> Unit): List<java.awt.image.BufferedImage> {
        val dir = java.nio.file.Files.createTempDirectory("hdrlit").toFile()
        try {
            val source = File("src/test/testData/project/Untitled")
            File(source, "assets").copyRecursively(File(dir, "assets"))
            val sky = File(dir, "assets/sky")
            net.nevinsky.abyssus.assets.sky.hdr.HdrFixtures.write(File(sky, "sky.hdr"), 64, 32, pixel = pixel)
            File(sky, "meta.json").writeText("""{"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{}}""")
            val text = File(source, "scenes/Main Scene.scene").readText()
            return variants.map { variant ->
                val patched = edit(text) { root ->
                    noFog(root) // the fixture's ambient light is an enabled grey 0.3
                    val entities = root.get("ecs").get("entities") as ObjectNode
                    if (keep != null) entities.fieldNames().asSequence().toList().filter { it !in keep }.forEach(entities::remove)
                    variant(root)
                }
                val r = GlHarness.render(SceneRenderParams.from(parseScene(patched), CameraParams.DEFAULT, dir), 240)
                assertNull(r.error)
                r.image
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private val litBySky: (ObjectNode) -> Unit = { it.put("skyboxEnabled", true); it.put("skyboxName", "sky") }
    private val noSkyAtAll: (ObjectNode) -> Unit = { it.putNull("skyboxName") }
    private val noEntities: (ObjectNode) -> Unit = { it.putNull("skyboxName"); (it.get("ecs") as ObjectNode).putObject("entities") }

    /** Pixels drawn by the content: where the scene without a sky differs from the grid alone. */
    private fun contentPixels(scene: java.awt.image.BufferedImage, gridOnly: java.awt.image.BufferedImage): List<Pair<Int, Int>> = buildList {
        for (y in 0 until scene.height) for (x in 0 until scene.width) if (scene.getRGB(x, y) != gridOnly.getRGB(x, y)) add(x to y)
    }

    private fun channels(rgb: Int) = Triple((rgb shr 16) and 0xff, (rgb shr 8) and 0xff, rgb and 0xff)

    @Test
    fun aBlueSkyTintsTheTerrain() {
        val blue = { _: Int, _: Int -> floatArrayOf(0f, 0f, 1f) }
        val (lit, plain, grid) = renderWithHdrSky(blue, setOf("1"), litBySky, noSkyAtAll, noEntities)
        val terrain = contentPixels(plain, grid)
        assertTrue("terrain drew almost nothing: ${terrain.size}", terrain.size > 1000)
        val blueOnly = terrain.count { (x, y) -> channels(lit.getRGB(x, y)).let { (r, g, b) -> b > 8 && r <= b / 8 && g <= b / 8 } }
        assertTrue("$blueOnly of ${terrain.size} terrain pixels are blue with no grey", blueOnly > terrain.size * 0.9)
    }

    @Test
    fun aRedSkyReplacesTheAmbientOnAModel() {
        val red = { _: Int, _: Int -> floatArrayOf(1f, 0f, 0f) }
        // Model 0 and Model 2 use the default shader
        val (lit, plain, grid) = renderWithHdrSky(red, setOf("0", "2"), litBySky, noSkyAtAll, noEntities)
        val models = contentPixels(plain, grid)
        assertTrue("models drew almost nothing: ${models.size}", models.size > 200)
        val redOnly = models.count { (x, y) -> channels(lit.getRGB(x, y)).let { (r, g, b) -> r > 8 && g <= r / 8 && b <= r / 8 } }
        assertTrue("$redOnly of ${models.size} model pixels are red with no grey", redOnly > models.size * 0.9)
    }

    @Test
    fun pbrModelIsBrighterUnderAnUpperSkyThanUnderALowerOne() {
        // Model 6 uses the PBR shader; the camera looks down on it, so most of what is seen faces up
        val upper = { _: Int, y: Int -> if (y < 16) floatArrayOf(2f, 2f, 2f) else floatArrayOf(0f, 0f, 0f) }
        val lower = { _: Int, y: Int -> if (y < 16) floatArrayOf(0f, 0f, 0f) else floatArrayOf(2f, 2f, 2f) }
        val (litAbove, plain, grid) = renderWithHdrSky(upper, setOf("6"), litBySky, noSkyAtAll, noEntities)
        val (litBelow) = renderWithHdrSky(lower, setOf("6"), litBySky)
        val model = contentPixels(plain, grid)
        assertTrue("Model 6 drew almost nothing: ${model.size}", model.size > 200)
        fun brightness(img: java.awt.image.BufferedImage) = model.sumOf { (x, y) -> channels(img.getRGB(x, y)).let { (r, g, b) -> (r + g + b).toLong() } }
        assertTrue("upper ${brightness(litAbove)} vs lower ${brightness(litBelow)}", brightness(litAbove) > brightness(litBelow) * 1.2)
    }

    @Test
    fun mainSceneIsUnchangedByADisabledHdrSky() {
        val p = params("Untitled", "Main Scene.scene") { edit(it) { root -> noFog(root); root.putNull("skyboxName") } }
        val disabled = params("Untitled", "Main Scene.scene") { edit(it) { root -> noFog(root); root.put("skyboxEnabled", false); root.put("skyboxName", "skybox_hdr") } }
        val a = GlHarness.render(p, 240)
        val b = GlHarness.render(disabled, 240)
        assertNull(a.error ?: b.error)
        var differing = 0
        for (y in 0 until a.image.height) for (x in 0 until a.image.width) if (a.image.getRGB(x, y) != b.image.getRGB(x, y)) differing++
        assertEquals(0, differing)
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
