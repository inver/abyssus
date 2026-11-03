/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.app.game.controlline.components.CONTROL_LINE_COMPONENTS
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransformAttribute
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/**
 * The field drawn through the PBR pipeline on a real GL context (opt-in, `-Dabyssus.glTests=true`): which attributes the
 * models are drawn with, that the sky is not transformed, and that sky light changes the lit surfaces and only when the sky
 * opts in. Each run draws a copy of the bundled project at a fixed time step.
 */
class FieldPipelineGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val W = TestGl.WINDOW_WIDTH
    private val H = TestGl.WINDOW_HEIGHT

    private class Drawn(
        val image: Pixmap, val hadTransform: Boolean, val hadSkyLight: Boolean, val messages: List<String>,
        val combined: com.badlogic.gdx.math.Matrix4 = com.badlogic.gdx.math.Matrix4(),
    ) {
        /** Where the world point [p] lands on the picture: pixels from the left and from the top. */
        fun screen(p: Vector3): Pair<Int, Int> {
            val ndc = Vector3(p).prj(combined)
            return Math.round((ndc.x + 1f) / 2f * (TestGl.WINDOW_WIDTH - 1)) to Math.round((1f - ndc.y) / 2f * (TestGl.WINDOW_HEIGHT - 1))
        }

        /** Writes the picture to `build/pipeline-shots/[name].png`, for a person to look at. */
        fun save(name: String) {
            val dir = File("build/pipeline-shots").apply { mkdirs() }
            PixmapIO.writePNG(Gdx.files.absolute(File(dir, "$name.png").absolutePath), image)
        }

        /** The red, green and blue of the pixel at ([x], [y]) counted from the top. */
        fun rgb(x: Int, y: Int): IntArray {
            val p = image.getPixel(x, y)
            return intArrayOf(p ushr 24 and 255, p ushr 16 and 255, p ushr 8 and 255)
        }
    }

    /**
     * One run: loads a copy of the project (edited by [editProject]), draws [frames] frames with [settings] into the test
     * window's default framebuffer (as the game does: the renderer's shadow pass leaves the default framebuffer bound) and
     * reads the last. [flying] is the pilot's view of the parked plane; otherwise the view looks up into the sky.
     */
    private fun draw(
        settings: FieldRenderSettings, flying: Boolean, frames: Int = 120, waitForSkyLight: Boolean = false,
        cablesFor: (PerspectiveCamera) -> List<Cable> = { emptyList() }, editProject: (File) -> Unit = {},
    ): Drawn {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        val project = Files.createTempDirectory("pipeline-control-line").toFile()
        File(checkNotNull(System.getProperty("controlLine.project"))).copyRecursively(project, overwrite = true)
        editProject(project)
        val messages = mutableListOf<String>()
        val log = warningsTo(messages)
        val ctx = BaseCtx(project.path, "/shader", ControlLineGame::class.java, log)
            .also { it.componentRegistry.registerAll(CONTROL_LINE_COMPONENTS) }
        val loader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)
        val renderer = FieldRenderer(ctx, requested = settings)
        try {
            val field = FieldScene(requireNotNull(loader.load("Field.scene")) { messages.joinToString("\n") })
            val cameras = Cameras(W.toFloat(), H.toFloat())
            val pilot = field.position(checkNotNull(field.pilot)).getTransform().getTranslation(Vector3())
            val plane = field.position(checkNotNull(field.entity(field.planes.first().entityId))).getTransform().getTranslation(Vector3())
            val sky = PerspectiveCamera(60f, W.toFloat(), H.toFloat()).apply {
                position.set(pilot).add(0f, 1.6f, 0f)
                direction.set(0.3f, 0f, -1f).nor().rotate(Vector3(1f, 0f, 0.3f).nor(), 30f)
                up.set(Vector3.Y); near = 0.5f; far = 20_000f; update()
            }
            fun frame(seconds: Float) {
                if (flying) cameras.flying(pilot, plane)
                Gdx.gl.glViewport(0, 0, W, H)
                val camera = if (flying) cameras.camera else sky
                renderer.draw(field, camera, seconds = seconds, cables = cablesFor(camera as PerspectiveCamera))
            }
            var settled = 0
            var count = 0
            while (settled < 30) {
                frame(0f)
                // everything built and nothing still loading (textures arrive after the models and terrains)
                settled = if (renderer.loaded(field) && !ctx.assetStorage.isLoading()) settled + 1 else 0
                if (settled == 0) Thread.sleep(5)
                check(++count < 20_000) { "the field never loaded: $messages" }
            }
            repeat(frames) { frame(1f / 60f) }
            var extra = 0
            while (waitForSkyLight && !renderer.modelEnvironment.has(EnvironmentLightAttribute.Type)) {
                frame(1f / 60f)
                check(++extra < 3_000) { "the sky light never got built: $messages" }
            }
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, W, H)
            // the read is bottom-up as RGBA: keep it upright
            val upright = Pixmap(W, H, Pixmap.Format.RGBA8888)
            for (y in 0 until H) for (x in 0 until W) upright.drawPixel(x, y, pixmap.getPixel(x, H - 1 - y))
            pixmap.dispose()
            return Drawn(
                upright, renderer.modelEnvironment.has(OutputTransformAttribute.Type),
                renderer.modelEnvironment.has(EnvironmentLightAttribute.Type), messages.toList(),
                (if (flying) cameras.camera else sky).combined.cpy(),
            )
        } finally {
            renderer.dispose()
            ctx.executor.shutdownNow()
            project.deleteRecursively()
        }
    }

    private fun meanDifference(a: Drawn, b: Drawn): Double {
        var total = 0L
        var n = 0
        for (y in 0 until H step 2) for (x in 0 until W step 2) {
            val p = a.rgb(x, y); val q = b.rgb(x, y)
            for (c in 0..2) total += abs(p[c] - q[c])
            n += 3
        }
        return total.toDouble() / n
    }

    @Test
    fun theModelsAreDrawnWithTheTransformAndTheSkyLightOnlyWhenAsked() = TestGl.run(600) {
        val pipeline = draw(FieldRenderSettings(), flying = true, waitForSkyLight = true)
        assertTrue("the transform", pipeline.hadTransform)
        assertTrue("the sky light of the opted-in field sky", pipeline.hadSkyLight)
        val legacy = draw(FieldRenderSettings.LEGACY, flying = true)
        pipeline.save("pilot-pipeline")
        legacy.save("pilot-legacy")
        assertFalse(legacy.hadTransform)
        assertFalse(legacy.hadSkyLight)
        assertEquals(emptyList<String>(), pipeline.messages.filter { "lighting build failed" in it })
    }

    private fun worstSkyDifference(a: Drawn, b: Drawn): Int {
        var worst = 0
        for (y in 10 until 90 step 3) for (x in 100 until 220 step 3) {
            val p = a.rgb(x, y); val q = b.rgb(x, y)
            for (c in 0..2) worst = maxOf(worst, abs(p[c] - q[c]))
        }
        return worst
    }

    @Test
    fun theSkyIsDrawnAsItAlwaysWasWhateverTheTransform() = TestGl.run(600) {
        // the view looks up into the sky only: the pipeline leaves it untouched, up to the sky's own run-to-run noise
        val off = draw(FieldRenderSettings.LEGACY, flying = false)
        val again = draw(FieldRenderSettings.LEGACY, flying = false)
        val transformOnly = draw(FieldRenderSettings(skyLight = false), flying = false)
        val noise = worstSkyDifference(off, again)
        val transform = worstSkyDifference(off, transformOnly)
        assertTrue("the transform changed the sky by $transform/255 (the sky's own noise is $noise/255)", transform <= noise + 1)
        val full = draw(FieldRenderSettings(), flying = false)
        val pipeline = worstSkyDifference(off, full)
        assertTrue("the pipeline changed the sky by $pipeline/255 (the sky's own noise is $noise/255)", pipeline <= noise + 1)
    }

    @Test
    fun skyLightChangesTheLitSurfacesAndASkyThatDoesNotOptInKeepsTheFlatAmbient() = TestGl.run(900) {
        val lit = draw(FieldRenderSettings(), flying = true, waitForSkyLight = true)
        val flat = draw(FieldRenderSettings(skyLight = false), flying = true)
        val notOptedIn = draw(FieldRenderSettings(), flying = true) { project ->
            val meta = File(project, "assets/skybox_physical/meta.json")
            meta.writeText(meta.readText().replace("\"lightsScene\": true", "\"lightsScene\": false"))
        }
        assertFalse("a sky that does not opt in lights nothing", notOptedIn.hadSkyLight)
        val sky = meanDifference(lit, flat)
        assertTrue("sky light changed the picture by only %.2f/255".format(sky), sky > 0.5)
        val same = meanDifference(notOptedIn, flat)
        assertTrue("without opt-in the picture is the flat-ambient one: differs by %.2f/255".format(same), same < 0.5)
        assertEquals(emptyList<String>(), notOptedIn.messages.filter { "ailed" in it })
    }

    /** A cable of [at] metres ahead of the pilot's eyes, [half] metres either side of the middle of the view, [drop] metres lower. */
    private fun cableAcross(camera: PerspectiveCamera, ahead: Float, half: Float, drop: Float): Cable {
        val right = Vector3(camera.direction).crs(Vector3.Y).nor()
        val middle = Vector3(camera.position).mulAdd(camera.direction, ahead).add(0f, -drop, 0f)
        return Cable(listOf(Vector3(middle).mulAdd(right, -half), Vector3(middle).mulAdd(right, half)), 0.0004f, com.badlogic.gdx.graphics.Color.WHITE)
    }

    /** The largest channel difference between the two pictures within one pixel of ([x], [y]). */
    private fun nearbyDifference(a: Drawn, b: Drawn, x: Int, y: Int): Int {
        var worst = 0
        for (dy in -1..1) for (dx in -1..1) {
            val px = (x + dx).coerceIn(0, W - 1); val py = (y + dy).coerceIn(0, H - 1)
            val p = a.rgb(px, py); val q = b.rgb(px, py)
            for (c in 0..2) worst = maxOf(worst, abs(p[c] - q[c]))
        }
        return worst
    }

    /** The distance in pixels from ([x], [y]) to the segment between two points of the picture. */
    private fun distanceToSegment(x: Int, y: Int, from: Pair<Int, Int>, to: Pair<Int, Int>): Float {
        val dx = (to.first - from.first).toFloat(); val dy = (to.second - from.second).toFloat()
        val t = (((x - from.first) * dx + (y - from.second) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return kotlin.math.hypot(x - (from.first + t * dx), y - (from.second + t * dy))
    }

    @Test
    fun aCableRunsBetweenItsEndsAndIsHiddenBehindTheGround() = TestGl.run(900) {
        val without = draw(FieldRenderSettings(), flying = true)
        var placed: Cable? = null
        val across = draw(FieldRenderSettings(), flying = true, cablesFor = { camera ->
            listOf(cableAcross(camera, ahead = 3f, half = 0.8f, drop = 0f).also { placed = it })
        })
        across.save("pilot-cable")
        val cable = checkNotNull(placed)
        val from = across.screen(cable.points[0])
        val to = across.screen(cable.points[1])
        // along the cable the picture changed, and more than a few pixels from it nothing did (the cable casts no shadow)
        for (t in listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)) {
            val (x, y) = across.screen(Vector3(cable.points[0]).lerp(cable.points[1], t))
            assertTrue("no cable at $t of the way, ($x, $y) between $from and $to", nearbyDifference(without, across, x, y) > 12)
        }
        for (y in 0 until H) for (x in 0 until W) {
            if (nearbyDifference(without, across, x, y) > 12) {
                assertTrue("a change at ($x, $y) far from the cable", distanceToSegment(x, y, from, to) < 4f)
            }
        }
        // under the pavement the ground hides it
        val buried = draw(FieldRenderSettings(), flying = true, cablesFor = { listOf(cableAcross(it, ahead = 3f, half = 0.8f, drop = 4f)) })
        var changed = 0
        for (y in 0 until H) for (x in 0 until W) if (nearbyDifference(without, buried, x, y) > 12) changed++
        assertEquals("a cable under the ground showed on $changed pixels", 0, changed)
    }

    private fun mean(d: Drawn, x0: Int, y0: Int, x1: Int, y1: Int): DoubleArray {
        val sum = DoubleArray(3)
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { val p = d.rgb(x, y); for (c in 0..2) sum[c] += p[c]; n++ }
        return DoubleArray(3) { sum[it] / n }
    }

    private fun linear(display: Double): Double = net.nevinsky.abyssus.lib.gdx.shader.srgbToLinear((display / 255.0).toFloat()).toDouble()

    /** The spec's midday look, measured: sun-lit surfaces are not clipped, shadowed ones not black, and materials stay apart. */
    @Test
    fun theMiddayFieldIsNeitherClippedNorBlackAndItsMaterialsStayApart() = TestGl.run(600) {
        val shot = draw(FieldRenderSettings(), flying = true)
        shot.save("pilot-midday")
        var white = 0
        var black = 0
        for (y in 0 until H) for (x in 0 until W) {
            val p = shot.rgb(x, y)
            if (p.min() >= 250) white++
            if (p.max() <= 5) black++
        }
        val total = (W * H).toDouble()
        assertTrue("clipped to white: ${white / total}", white / total < 0.01)
        assertTrue("crushed to black: ${black / total}", black / total < 0.01)
        // the asphalt of the pad and the wall of the building behind it differ by 10% in linear brightness, or by hue
        val asphalt = mean(shot, 20, 100, 140, 160)
        val wall = mean(shot, 5, 10, 40, 60)
        val asphaltLinear = asphalt.map(::linear).average()
        val wallLinear = wall.map(::linear).average()
        val apart = abs(asphaltLinear - wallLinear) / maxOf(asphaltLinear, wallLinear) >= 0.10
        assertTrue("asphalt ${asphalt.toList()} and wall ${wall.toList()} look alike", apart)
    }
}
