/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.AtmosphereParams
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.PreparedProceduralSky
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralLightingCube
import net.nevinsky.abyssus.lib.core.assets.skyShaders
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

private const val SIZE = 160

/** The CRC of the volumetric cirrus image before heaped bands got their own shaping: it must not change. */
private const val CIRRUS_CHECKSUM = 2430506748L

/**
 * The three cloud techniques drawn over the fixture's procedural sky into an offscreen target and read back. Opt-in:
 * `-Dabyssus.glTests=true`.
 */
class CloudTechniqueGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val skyDir = File(testProject("Untitled"), "assets/skybox_physical")
    private val noon = Vector3(0.3f, 0.85f, -0.4f).nor()
    private val sunset = Vector3(0f, 0.03f, -1f).nor()
    private val noise by lazy { CloudNoiseGenerator().generate() }

    private fun clouds(vararg bands: CloudBand, technique: CloudTechnique = CloudTechnique.LAYERED) =
        CloudMeta(
            technique,
            low = bands.firstOrNull { it.level == CloudLevel.LOW },
            mid = bands.firstOrNull { it.level == CloudLevel.MID },
            high = bands.firstOrNull { it.level == CloudLevel.HIGH })

    /** Scattered cumulus: clear sky between clouds, so the techniques' cloud masks can be compared. */
    private val scattered = clouds(CloudBand(CloudType.CUMULUS, CloudLevel.LOW, coverage = 0.45f))

    /**
     * The fixture's procedural sky drawing [clouds] as its cloud asset `clouds_test` (built here, with [cloudNoise])
     * read from the asset storage as the real loader does; null: a sky that names no cloud asset. GL thread; the cloud
     * asset's textures go with the test's context.
     */
    private fun sky(
        clouds: CloudMeta?, log: RecordingLogger = RecordingLogger(), cloudNoise: CloudNoise? = noise,
        factory: ((CloudTechnique) -> CloudRenderer)? = null,
    ): ProceduralSky {
        val asset = clouds?.let { Clouds("clouds_test", it, cloudNoise) }
        return ProceduralSky(
            PreparedProceduralSky(
                AtmosphereParams(), File(skyDir, "sky.vert").readText(), File(skyDir, "sky.frag").readText(),
                "skybox_physical", asset?.name,
            ),
            BuiltAssets { name -> asset?.takeIf { it.name == name } }, skyShaders(), log, factory,
        )
    }

    /** A camera at the origin looking [elevation] degrees above the horizon toward -Z, 90 degrees wide. */
    private fun camera(elevation: Float = 60f, yaw: Float = 0f, size: Int = SIZE) =
        PerspectiveCamera(90f, size.toFloat(), size.toFloat()).apply {
            near = 0.1f
            far = 100f
            direction.set(0f, 0f, -1f).rotate(Vector3.X, elevation).rotate(Vector3.Y, yaw)
            up.set(Vector3.Y)
            update()
        }

    private class Image(val width: Int, val height: Int, val rgb: IntArray) {
        fun red(i: Int) = rgb[i] shr 16 and 255
        fun green(i: Int) = rgb[i] shr 8 and 255
        fun blue(i: Int) = rgb[i] and 255
        fun at(x: Int, y: Int) = y * width + x
    }

    /** Draws [draw] into a fresh [size]-square target with a depth buffer and reads it back. GL thread. */
    private fun capture(size: Int = SIZE, draw: () -> Unit): Image {
        val buffer = FrameBuffer(Pixmap.Format.RGBA8888, size, size, true)
        try {
            buffer.begin()
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthMask(false)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            draw()
            Gdx.gl.glDepthMask(true)
            val pixels = BufferUtils.newByteBuffer(size * size * 4)
            Gdx.gl.glReadPixels(0, 0, size, size, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels)
            buffer.end()
            return Image(size, size, IntArray(size * size) { i ->
                ((pixels.get(i * 4).toInt() and 255) shl 16) or ((pixels.get(i * 4 + 1).toInt() and 255) shl 8) or
                        (pixels.get(i * 4 + 2).toInt() and 255)
            })
        } finally {
            buffer.dispose()
        }
    }

    private fun render(
        clouds: CloudMeta?,
        technique: CloudTechnique? = null,
        sun: Vector3 = noon,
        elevation: Float = 60f,
        yaw: Float = 0f,
        frames: Int = 1,
    ): Image {
        var image: Image? = null
        TestGl.run {
            val sky = sky(clouds)
            try {
                val camera = camera(elevation, yaw)
                repeat(frames - 1) { capture { sky.draw(camera, SkyFrame(sun, 30.0, technique)) } }
                image = capture { sky.draw(camera, SkyFrame(sun, 30.0, technique)) }
            } finally {
                sky.dispose()
            }
        }
        return image!!
    }

    /** The luminance (0.2126 R + 0.7152 G + 0.0722 B) of pixel [i]. */
    private fun luminance(image: Image, i: Int) =
        0.2126f * image.red(i) + 0.7152f * image.green(i) + 0.0722f * image.blue(i)

    /** The mean luminance of the pixels [mask] selects; there must be some. */
    private fun meanLuminance(image: Image, mask: BooleanArray): Double {
        val picked = mask.indices.filter { mask[it] }
        check(picked.isNotEmpty()) { "no pixels selected" }
        return picked.sumOf { luminance(image, it).toDouble() } / picked.size
    }

    /** The mean red-to-blue ratio of the pixels [pixels] select. */
    private fun redness(image: Image, pixels: List<Int>): Double {
        check(pixels.isNotEmpty()) { "no pixels selected" }
        val red = pixels.sumOf { image.red(it) }
        val blue = pixels.sumOf { image.blue(it) }
        return red.toDouble() / maxOf(1, blue)
    }

    /** The mean of the lowest and highest decile of the covered pixels' luminance in [image]/[clear]. */
    private fun deciles(image: Image, clear: Image): Pair<Double, Double> {
        val mask = mask(image, clear)
        val luminances = mask.indices.filter { mask[it] }.map { luminance(image, it).toDouble() }.sorted()
        check(luminances.size >= 20) { "too few covered pixels: ${luminances.size}" }
        val decile = maxOf(1, luminances.size / 10)
        return luminances.take(decile).average() to luminances.takeLast(decile).average()
    }

    /** The pixels where [image] differs from [clear] (the same sky without clouds) by more than [threshold]. */
    private fun mask(image: Image, clear: Image, threshold: Int = 24): BooleanArray =
        BooleanArray(image.rgb.size) { i ->
            val d = Math.abs(image.red(i) - clear.red(i)) + Math.abs(image.green(i) - clear.green(i)) + Math.abs(
                image.blue(i) - clear.blue(i)
            )
            d > threshold
        }

    /**
     * How well two techniques' images [a] and [b] of the same sky agree on where clouds are: of the pixels one of them
     * covers solidly, the share the other covers at least faintly, the lower of both ways. Soft edges and thin detail
     * may differ between techniques; the regions may not.
     */
    private fun agreement(a: Image, b: Image, clear: Image): Double {
        fun covered(solid: BooleanArray, faint: BooleanArray): Double {
            val n = solid.count { it }
            return if (n == 0) 1.0 else solid.indices.count { solid[it] && faint[it] }.toDouble() / n
        }
        return minOf(covered(mask(a, clear, 60), mask(b, clear, 8)), covered(mask(b, clear, 60), mask(a, clear, 8)))
    }

    private fun share(mask: BooleanArray) = mask.count { it }.toDouble() / mask.size

    @Test
    fun layered() {
        val cloudless = render(null)
        assertTrue(
            "no clouds",
            render(clouds(CloudBand(CloudType.CUMULUS, CloudLevel.LOW, coverage = 0f))).rgb.contentEquals(cloudless.rgb)
        )
        assertTrue("a cloud asset without bands", render(CloudMeta()).rgb.contentEquals(cloudless.rgb))
        assertTrue("clouds left out of the frame", run {
            var image: Image? = null
            TestGl.run {
                val sky = sky(scattered)
                image = capture { sky.draw(camera(), SkyFrame(noon, 30.0, clouds = false)) }
                sky.dispose()
            }
            image!!.rgb.contentEquals(cloudless.rgb)
        })

        val overcast = render(clouds(CloudBand(CloudType.STRATUS, CloudLevel.LOW, coverage = 1f)), elevation = 90f)
        val clearZenith = render(null, elevation = 90f)
        val centre = overcast.at(SIZE / 2, SIZE / 2)
        assertTrue("the zenith is covered", mask(overcast, clearZenith).let { m -> m[centre] && share(m) > 0.95 })
        assertTrue(
            "overcast is grey-white, not blue: ${overcast.red(centre)} ${overcast.green(centre)} ${overcast.blue(centre)}",
            overcast.red(centre) > overcast.blue(centre) * 0.75,
        )

        val thick = clouds(CloudBand(CloudType.STRATUS, CloudLevel.LOW, coverage = 1f))
        val day = render(thick, elevation = 10f)
        val evening = render(thick, sun = sunset, elevation = 10f)
        fun warmth(image: Image) = (0 until image.rgb.size).sumOf { image.red(it) }.toDouble() / maxOf(
            1,
            (0 until image.rgb.size).sumOf { image.blue(it) })
        assertTrue(
            "sunset clouds are warmer: ${warmth(evening)} vs ${warmth(day)}",
            warmth(evening) > warmth(day) * 1.2
        )

        assertModelStaysInFront()
    }

    @Test
    fun thickCloudsStayLuminous() {
        // a dense overcast (stratus at coverage 1, density 2) hides the bright atmosphere, so the zenith shade is the
        // cloud's own; the threshold sits between the pre-change 189.3 (the old clipped single term) and the current
        // 218.1 (the multiple-scattering sum)
        val thick = clouds(CloudBand(CloudType.STRATUS, CloudLevel.LOW, coverage = 1f, density = 2f))
        val overcast = render(thick, elevation = 90f)
        val luminance = meanLuminance(overcast, BooleanArray(overcast.rgb.size) { true })
        assertTrue("the overcast zenith luminance $luminance stays above the old flat shade", luminance >= 205.0)
    }

    private fun assertModelStaysInFront() {
        var image: Image? = null
        TestGl.run {
            val sky = sky(clouds(CloudBand(CloudType.STRATUS, CloudLevel.LOW, coverage = 1f)))
            val program = ShaderProgram(
                "attribute vec2 a_position;\nvoid main() { gl_Position = vec4(a_position, 0.5, 1.0); }",
                "void main() { gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0); }",
            )
            val model = Mesh(true, 3, 0, VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)).apply {
                setVertices(floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, 0f, 0.5f))
            }
            try {
                image = capture {
                    sky.draw(camera(90f), SkyFrame(noon, 30.0))
                    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
                    Gdx.gl.glDepthMask(true)
                    program.bind()
                    model.render(program, GL20.GL_TRIANGLES)
                }
            } finally {
                model.dispose()
                program.dispose()
                sky.dispose()
            }
        }
        val centre = image!!.at(SIZE / 2, SIZE / 2)
        assertEquals("the model is drawn over the clouds", 0xFF0000, image.rgb[centre])
    }

    @Test
    fun cloudSurfacesShowDepth() {
        // measured with the new shaders: darkest decile 87.1, brightest 232.4, ratio 0.37
        val clear = render(null, elevation = 60f)
        val lowSun = Vector3(0.6f, 0.25f, -0.75f).nor()
        val image = render(scattered, sun = lowSun, elevation = 60f)
        val (darkest, brightest) = deciles(image, clear)
        assertTrue(
            "the darkest decile $darkest is not at least 25% darker than the brightest decile $brightest",
            darkest <= brightest * 0.75,
        )
    }

    @Test
    fun silverLining() {
        // measured with the new shaders: sunward 107.8 vs away 86.2 (1.25x), redness 1.74 vs 1.46 (1.19x)
        val clear = render(null, sun = sunset, elevation = 10f, yaw = 45f)
        val image = render(scattered, sun = sunset, elevation = 10f, yaw = 45f)
        val mask = mask(image, clear)
        val covered = mask.indices.filter { mask[it] }
        check(covered.size > 200) { "too few covered pixels: ${covered.size}" }
        // with this yaw the sun sits in the right half of the frame
        val around = covered.filter { it % SIZE >= SIZE / 2 }
        val opposite = covered.filter { it % SIZE < SIZE / 2 }
        val sunward = meanLuminance(image, BooleanArray(SIZE * SIZE) { it in around })
        val away = meanLuminance(image, BooleanArray(SIZE * SIZE) { it in opposite })
        val sunwardRedness = redness(image, around)
        val awayRedness = redness(image, opposite)
        assertTrue("the sun side $sunward is not brighter than the far side $away", sunward >= away * 1.05)
        assertTrue("the sun side is not redder: $sunwardRedness vs $awayRedness", sunwardRedness >= awayRedness * 1.05)
    }

    @Test
    fun lightingCubeLeavesVisibleShellsUnchanged() = TestGl.run {
        val sky = sky(scattered)
        val cube = ProceduralLightingCube(AtmosphereParams())
        try {
            val view = camera(80f)
            val frame = SkyFrame(noon, 30.0, CloudTechnique.SHELLS)
            val before = capture { sky.draw(view, frame) }
            val after = capture {
                cube.render(noon, sky.cloudAsset, 30.0)
                sky.draw(view, frame)
            }
            assertArrayEquals(before.rgb, after.rgb)
            assertEquals(CloudTechnique.SHELLS, sky.drawnTechnique)
        } finally { cube.dispose(); sky.dispose() }
    }

    @Test
    fun shells() {
        // looking up: at grazing angles a slab seen through its thickness rightly covers more than a flat layer
        val clear = render(null, elevation = 80f)
        val layered = render(scattered, CloudTechnique.LAYERED, elevation = 80f)
        val shells = render(scattered, CloudTechnique.SHELLS, elevation = 80f)
        val covered = share(mask(layered, clear))
        assertTrue("some sky is covered: $covered", covered in 0.05..0.95)
        val agree = agreement(layered, shells, clear)
        assertTrue("shells cover the same regions as layered: $agree", agree >= 0.9)
    }

    @Test
    fun volumetric() {
        val clear = render(null, elevation = 80f)
        val layered = render(scattered, CloudTechnique.LAYERED, elevation = 80f)
        val volumetric = render(scattered, CloudTechnique.VOLUMETRIC, elevation = 80f, frames = 8)
        val agree = agreement(layered, volumetric, clear)
        assertTrue("volumetric clouds cover the same regions as layered: $agree", agree >= 0.9)

        var drawn: CloudTechnique? = null
        var resetsAfterTurn = 0
        var resetsAfterNudge = 0
        var leaked: Image? = null
        var clearAfterResize: Image? = null
        TestGl.run {
            val volumetricRenderer = arrayOfNulls<VolumetricClouds>(1)
            val sky = sky(scattered, factory = { technique ->
                check(technique == CloudTechnique.VOLUMETRIC)
                VolumetricClouds(skyShaders(), CloudField()).also { volumetricRenderer[0] = it }
            })
            try {
                val frame = SkyFrame(noon, 30.0, CloudTechnique.VOLUMETRIC)
                repeat(4) { capture { sky.draw(camera(60f), frame) } }
                drawn = sky.drawnTechnique
                val renderer = volumetricRenderer[0]!!
                val before = renderer.historyResets
                capture { sky.draw(camera(60f, yaw = 2f), frame) }
                resetsAfterNudge = renderer.historyResets - before
                capture { sky.draw(camera(60f, yaw = 40f), frame) }
                resetsAfterTurn = renderer.historyResets - before - resetsAfterNudge

                // a resized view with no clouds left in its sky must show none of the previous history
                val empty = sky(clouds(CloudBand(CloudType.CUMULUS, CloudLevel.LOW, coverage = 0f)), factory = {
                    VolumetricClouds(
                        skyShaders(),
                        CloudField()
                    ).also { volumetricRenderer[0] = it }
                })
                try {
                    repeat(3) { capture { empty.draw(camera(60f), frame) } }
                    leaked = capture(SIZE * 2) { empty.draw(camera(60f, size = SIZE * 2), frame) }
                } finally {
                    empty.dispose()
                }
                val cloudless = sky(null)
                clearAfterResize = capture(SIZE * 2) { cloudless.draw(camera(60f, size = SIZE * 2), frame) }
                cloudless.dispose()
            } finally {
                sky.dispose()
            }
        }
        assertEquals(CloudTechnique.VOLUMETRIC, drawn)
        assertEquals("a small turn keeps the history", 0, resetsAfterNudge)
        assertEquals("a camera jump resets the history", 1, resetsAfterTurn)
        assertTrue("no history leaks after a resize", leaked!!.rgb.contentEquals(clearAfterResize!!.rgb))
    }

    @Test
    fun volumetricIsStableAcrossFrames() = TestGl.run {
        // measured with the new shaders: the mean per-channel frame-to-frame delta is about 0.02 (of 255)
        val sky = sky(scattered, factory = { technique ->
            check(technique == CloudTechnique.VOLUMETRIC)
            VolumetricClouds(skyShaders(), CloudField())
        })
        try {
            val camera = camera(80f)
            val frame = SkyFrame(noon, 30.0, CloudTechnique.VOLUMETRIC)
            repeat(6) { capture { sky.draw(camera, frame) } }
            val first = capture { sky.draw(camera, frame) }
            val second = capture { sky.draw(camera, frame) }
            assertEquals(CloudTechnique.VOLUMETRIC, sky.drawnTechnique)
            assertTrue("clouds are drawn", sky.hasClouds)
            val deltas = (0 until 3).map { channel ->
                (0 until first.rgb.size).sumOf { i ->
                    val (a, b) = when (channel) {
                        0 -> first.red(i) to second.red(i)
                        1 -> first.green(i) to second.green(i)
                        else -> first.blue(i) to second.blue(i)
                    }
                    Math.abs(a - b).toDouble()
                } / first.rgb.size
            }
            assertTrue("the dithered light march shimmers: $deltas", deltas.all { it < 4.0 })
        } finally {
            sky.dispose()
        }
    }

    @Test
    fun techniquesLightAlike() {
        // measured with the new shaders: layered 221.7, shells 215.3, volumetric 216.4 (within 3%)
        val clear = render(null, elevation = 60f)
        val means = listOf(CloudTechnique.LAYERED, CloudTechnique.SHELLS, CloudTechnique.VOLUMETRIC).map { technique ->
            val image = render(
                scattered, technique, elevation = 60f,
                frames = if (technique == CloudTechnique.VOLUMETRIC) 8 else 1,
            )
            technique.key to meanLuminance(image, mask(image, clear))
        }
        val lowest = means.minOf { it.second }
        val highest = means.maxOf { it.second }
        assertTrue("mean covered luminance per technique $means", highest <= lowest * 1.25)
    }

    /** Writes [image] to `build/cloud-images/[name].png` (flipped upright) so a person can look at what was drawn. */
    private fun save(image: Image, name: String) {
        val pixmap = Pixmap(image.width, image.height, Pixmap.Format.RGB888)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val i = image.at(x, image.height - 1 - y)
            pixmap.drawPixel(x, y, (image.red(i) shl 24) or (image.green(i) shl 16) or (image.blue(i) shl 8) or 255)
        }
        val file = File("build/cloud-images/$name.png").apply { parentFile.mkdirs() }
        PixmapIO.writePNG(com.badlogic.gdx.files.FileHandle(file), pixmap)
        pixmap.dispose()
    }

    private fun volumetric(type: CloudType, level: CloudLevel, coverage: Float) =
        clouds(CloudBand(type, level, coverage = coverage), technique = CloudTechnique.VOLUMETRIC)

    @Test
    fun heapedVolumetricCloudsHaveBrightSunlitTopsGreyBasesAndAWhiteRim() {
        val heaped = volumetric(CloudType.CUMULUS, CloudLevel.LOW, 0.45f)
        val clear = render(null)
        val image = render(heaped, CloudTechnique.VOLUMETRIC, elevation = 40f, frames = 24)
        save(image, "volumetric-heaped")
        val (base, top) = deciles(image, clear)
        assertTrue("a sunlit top is near white: $top", top > 205)
        assertTrue("the base is a darker grey than the top: $base vs $top", base < top * 0.85)
        // looking toward the sun, the rim of a cloud is clearly lighter than the sky behind it
        val sunward = render(heaped, CloudTechnique.VOLUMETRIC, sun = noon, elevation = 55f, yaw = 0f, frames = 24)
        val covered = mask(sunward, clear, 60)
        val edge = covered.indices.filter { i ->
            covered[i] && (i % SIZE in 1 until SIZE - 1) && (!covered[i - 1] || !covered[i + 1])
        }
        assertTrue("clouds with an edge: ${edge.size}", edge.size > 20)
        val rim = edge.map { luminance(sunward, it) }.average()
        val behind = edge.map { luminance(clear, it) }.average()
        assertTrue("the rim shines: edge $rim against the sky behind it $behind", rim > behind + 25)
    }

    @Test
    fun aVolumetricCirrusBandIsDrawnAsBefore() {
        val image = render(volumetric(CloudType.CIRRUS, CloudLevel.HIGH, 0.5f), CloudTechnique.VOLUMETRIC, elevation = 70f, frames = 8)
        val checksum = java.util.zip.CRC32().also { crc -> image.rgb.forEach { crc.update(it) } }.value
        println("cirrus volumetric checksum $checksum")
        assertEquals("the image of a cirrus band drawn volumetrically", CIRRUS_CHECKSUM, checksum)
    }

    @Test
    fun cumulusHasBrightTopsGreyBasesAndABlueSky() {
        val cumulus = clouds(
            CloudBand(CloudType.CUMULUS, CloudLevel.LOW, coverage = 0.45f), technique = CloudTechnique.CUMULUS,
        )
        val clear = render(null)
        val image = render(cumulus, CloudTechnique.CUMULUS, elevation = 40f)
        save(image, "cumulus")
        val covered = mask(image, clear, 60)
        assertTrue("clouds are drawn", share(covered) > 0.05)
        val (base, top) = deciles(image, clear)
        assertTrue("a sunlit top is near white: $top", top > 215)
        assertTrue("the base is a darker grey than the top: $base vs $top", base < top * 0.8)
        val open = mask(image, clear, 24)
        val sky = open.indices.filter { !open[it] }
        assertTrue("clear sky remains: ${sky.size}", sky.size > 200)
        assertTrue("the sky outside clouds is blue", sky.sumOf { image.blue(it) } > sky.sumOf { image.red(it) } * 1.3)
    }

    @Test
    fun fallbackChain() {
        fun failing(vararg broken: CloudTechnique): (CloudTechnique) -> CloudRenderer = { technique ->
            if (technique in broken) throw IllegalStateException("$technique is broken")
            when (technique) {
                CloudTechnique.LAYERED -> LayeredClouds(skyShaders(), CloudField())
                CloudTechnique.CUMULUS -> CumulusClouds(skyShaders(), CloudField())
                CloudTechnique.SHELLS -> ShellClouds(skyShaders(), CloudField())
                CloudTechnique.VOLUMETRIC -> VolumetricClouds(skyShaders(), CloudField())
            }
        }

        val cases = listOf(
            arrayOf<CloudTechnique>() to CloudTechnique.VOLUMETRIC,
            arrayOf(CloudTechnique.VOLUMETRIC) to CloudTechnique.SHELLS,
            arrayOf(CloudTechnique.VOLUMETRIC, CloudTechnique.SHELLS) to CloudTechnique.CUMULUS,
            arrayOf(CloudTechnique.VOLUMETRIC, CloudTechnique.SHELLS, CloudTechnique.CUMULUS) to CloudTechnique.LAYERED,
            arrayOf(
                CloudTechnique.VOLUMETRIC, CloudTechnique.SHELLS, CloudTechnique.CUMULUS, CloudTechnique.LAYERED,
            ) to null,
        )
        val cloudless = render(null)
        for ((broken, expected) in cases) {
            val log = RecordingLogger()
            var drawn: CloudTechnique? = CloudTechnique.LAYERED
            var image: Image? = null
            var hasClouds = true
            TestGl.run {
                val sky = sky(scattered, log, factory = failing(*broken))
                try {
                    repeat(3) {
                        image = capture { sky.draw(camera(), SkyFrame(noon, 30.0, CloudTechnique.VOLUMETRIC)) }
                    }
                    drawn = sky.drawnTechnique
                    hasClouds = sky.hasClouds
                } finally {
                    sky.dispose()
                }
            }
            assertEquals("with ${broken.toList()} broken", expected, drawn)
            assertEquals("each broken technique is logged once: ${log.warnings}", broken.size, log.warnings.size)
            if (expected == null) {
                assertTrue("the atmosphere still draws", image!!.rgb.contentEquals(cloudless.rgb))
                assertEquals(false, hasClouds)
            }
        }

        // a cloud asset without noise textures: volumetric clouds are unavailable, and shells are drawn instead
        val log = RecordingLogger()
        var drawn: CloudTechnique? = null
        TestGl.run {
            val sky = sky(scattered, log, cloudNoise = null)
            try {
                repeat(2) { capture { sky.draw(camera(), SkyFrame(noon, 30.0, CloudTechnique.VOLUMETRIC)) } }
                drawn = sky.drawnTechnique
            } finally {
                sky.dispose()
            }
        }
        assertEquals(CloudTechnique.SHELLS, drawn)
        assertEquals(1, log.warnings.size)
        assertTrue(log.warnings.single(), log.warnings.single().contains("volumetric"))
        assertNull(null)
    }
}
