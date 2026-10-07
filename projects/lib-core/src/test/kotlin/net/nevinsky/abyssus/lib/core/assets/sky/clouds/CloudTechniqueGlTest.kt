/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.AtmosphereParams
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.PreparedProceduralSky
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.lib.core.assets.skyShaders
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.testing.RecordingLogger
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

private const val SIZE = 160

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
        CloudSettings(technique, bands.associateBy { it.level })

    /** Scattered cumulus: clear sky between clouds, so the techniques' cloud masks can be compared. */
    private val scattered = clouds(CloudBand(CloudLevel.LOW, CloudType.CUMULUS, coverage = 0.45f))

    /**
     * The fixture's procedural sky drawing [clouds] as its cloud asset `clouds_test` (built here, with [cloudNoise])
     * read from the asset storage as the real loader does; null: a sky that names no cloud asset. GL thread; the cloud
     * asset's textures go with the test's context.
     */
    private fun sky(
        clouds: CloudSettings?, log: RecordingLogger = RecordingLogger(), cloudNoise: CloudNoise? = noise,
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
    private fun camera(elevation: Float = 60f, yaw: Float = 0f, size: Int = SIZE) = PerspectiveCamera(90f, size.toFloat(), size.toFloat()).apply {
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
        clouds: CloudSettings?, technique: CloudTechnique? = null, sun: Vector3 = noon, elevation: Float = 60f, frames: Int = 1,
    ): Image {
        var image: Image? = null
        TestGl.run {
            val sky = sky(clouds)
            try {
                val camera = camera(elevation)
                repeat(frames - 1) { capture { sky.draw(camera, SkyFrame(sun, 30.0, technique)) } }
                image = capture { sky.draw(camera, SkyFrame(sun, 30.0, technique)) }
            } finally {
                sky.dispose()
            }
        }
        return image!!
    }

    /** The pixels where [image] differs from [clear] (the same sky without clouds) by more than [threshold]. */
    private fun mask(image: Image, clear: Image, threshold: Int = 24): BooleanArray = BooleanArray(image.rgb.size) { i ->
        val d = Math.abs(image.red(i) - clear.red(i)) + Math.abs(image.green(i) - clear.green(i)) + Math.abs(image.blue(i) - clear.blue(i))
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
        assertTrue("no clouds", render(clouds(CloudBand(CloudLevel.LOW, CloudType.CUMULUS, coverage = 0f))).rgb.contentEquals(cloudless.rgb))
        assertTrue("a cloud asset without bands", render(CloudSettings()).rgb.contentEquals(cloudless.rgb))
        assertTrue("clouds left out of the frame", run {
            var image: Image? = null
            TestGl.run {
                val sky = sky(scattered)
                image = capture { sky.draw(camera(), SkyFrame(noon, 30.0, clouds = false)) }
                sky.dispose()
            }
            image!!.rgb.contentEquals(cloudless.rgb)
        })

        val overcast = render(clouds(CloudBand(CloudLevel.LOW, CloudType.STRATUS, coverage = 1f)), elevation = 90f)
        val clearZenith = render(null, elevation = 90f)
        val centre = overcast.at(SIZE / 2, SIZE / 2)
        assertTrue("the zenith is covered", mask(overcast, clearZenith).let { m -> m[centre] && share(m) > 0.95 })
        assertTrue(
            "overcast is grey-white, not blue: ${overcast.red(centre)} ${overcast.green(centre)} ${overcast.blue(centre)}",
            overcast.red(centre) > overcast.blue(centre) * 0.75,
        )

        val thick = clouds(CloudBand(CloudLevel.LOW, CloudType.STRATUS, coverage = 1f))
        val day = render(thick, elevation = 10f)
        val evening = render(thick, sun = sunset, elevation = 10f)
        fun warmth(image: Image) = (0 until image.rgb.size).sumOf { image.red(it) }.toDouble() / maxOf(1, (0 until image.rgb.size).sumOf { image.blue(it) })
        assertTrue("sunset clouds are warmer: ${warmth(evening)} vs ${warmth(day)}", warmth(evening) > warmth(day) * 1.2)

        assertModelStaysInFront()
    }

    private fun assertModelStaysInFront() {
        var image: Image? = null
        TestGl.run {
            val sky = sky(clouds(CloudBand(CloudLevel.LOW, CloudType.STRATUS, coverage = 1f)))
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
        assertEquals("the model is drawn over the clouds", 0xFF0000, image!!.rgb[centre])
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
                val empty = sky(clouds(CloudBand(CloudLevel.LOW, CloudType.CUMULUS, coverage = 0f)), factory = { VolumetricClouds(skyShaders(), CloudField()).also { volumetricRenderer[0] = it } })
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
    fun fallbackChain() {
        fun failing(vararg broken: CloudTechnique): (CloudTechnique) -> CloudRenderer = { technique ->
            if (technique in broken) throw IllegalStateException("$technique is broken")
            when (technique) {
                CloudTechnique.LAYERED -> LayeredClouds(skyShaders(), CloudField())
                CloudTechnique.SHELLS -> ShellClouds(skyShaders(), CloudField())
                CloudTechnique.VOLUMETRIC -> VolumetricClouds(skyShaders(), CloudField())
            }
        }
        val cases = listOf(
            arrayOf<CloudTechnique>() to CloudTechnique.VOLUMETRIC,
            arrayOf(CloudTechnique.VOLUMETRIC) to CloudTechnique.SHELLS,
            arrayOf(CloudTechnique.VOLUMETRIC, CloudTechnique.SHELLS) to CloudTechnique.LAYERED,
            arrayOf(CloudTechnique.VOLUMETRIC, CloudTechnique.SHELLS, CloudTechnique.LAYERED) to null,
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
                    repeat(3) { image = capture { sky.draw(camera(), SkyFrame(noon, 30.0, CloudTechnique.VOLUMETRIC)) } }
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
