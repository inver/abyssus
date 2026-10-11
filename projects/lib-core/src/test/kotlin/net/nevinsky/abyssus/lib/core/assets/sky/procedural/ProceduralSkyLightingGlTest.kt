/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudLevel
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HDR_BUILD_STEPS
import net.nevinsky.abyssus.lib.core.assets.skyShaders
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The scene lighting of a procedural sky, frame by frame. Opt-in: `-Dabyssus.glTests=true`. */
class ProceduralSkyLightingGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private fun sun(degrees: Double) =
        Vector3(sin(Math.toRadians(degrees)).toFloat(), cos(Math.toRadians(degrees)).toFloat(), 0f)

    private fun clouds(wind: Boolean) = Clouds(
        "clouds",
        CloudMeta(
            CloudTechnique.SHELLS,
            low = CloudBand(CloudType.CUMULUS, CloudLevel.LOW, windX = if (wind) 4f else 0f, windZ = 0f),
        ),
        null,
    )

    private class Frames(val lighting: ProceduralSkyLighting) {
        var time = 0.0
        var sun = Vector3(0f, 1f, 0f)

        /** Runs [count] frames of 0.1 s; returns how many it took until [until] held (or [count]). */
        fun run(count: Int, until: () -> Boolean = { false }): Int {
            for (i in 1..count) {
                lighting.update(sun, time)
                time += 0.1
                if (until()) return i
            }
            return count
        }
    }

    private fun inGl(block: () -> Unit) = TestGl.run(block)

    private fun lighting(log: RecordingLogger = RecordingLogger(), clouds: () -> Clouds? = { null }) =
        ProceduralSkyLighting(AtmosphereParams(), skyShaders(), clouds, log)

    @Test
    fun theSceneKeepsItsAmbientUntilTheFirstBuildCompletes() {
        inGl {
            val lighting = lighting()
            val frames = Frames(lighting)
            try {
                frames.run(1)
                assertNull("nothing yet", lighting.state)
                val taken = frames.run(HDR_BUILD_STEPS + 5) { lighting.state != null }
                assertNotNull("lit after $taken frames", lighting.state)
                assertTrue("one step per frame: $taken", taken in HDR_BUILD_STEPS - 1..HDR_BUILD_STEPS + 2)
                assertNull(lighting.state!!.next)
                assertEquals(0f, lighting.state!!.weight, 0f)
            } finally {
                lighting.dispose()
            }
        }
    }

    @Test
    fun aMovedSunFadesInANewLightingAndReleasesTheOldOne() {
        inGl {
            val lighting = lighting()
            val frames = Frames(lighting)
            try {
                frames.run(20) { lighting.state != null }
                val first = lighting.state!!.current
                val firstHandle = first.irradiance.textureObjectHandle
                frames.sun = sun(30.0)
                frames.run(30) { lighting.state?.next != null }
                val fading = lighting.state!!
                assertNotNull("a next lighting", fading.next)
                assertSame(first, fading.current)
                frames.run(5)
                val mid = lighting.state!!.weight
                assertTrue("fading, weight $mid", mid > 0f && mid < 1f)
                val state = lighting.state!!
                val blended = state.blendedAmbient()
                for (i in 0 until 18) {
                    val expected = state.current.ambient[i] + (state.next!!.ambient[i] - state.current.ambient[i]) * state.weight
                    assertEquals("axis colour $i is the current one mixed toward the next by the weight", expected, blended[i], 1e-5f)
                }
                assertTrue("the diffuse light really changed with the sun", (0 until 18).any { abs(state.next!!.ambient[it] - state.current.ambient[it]) > 1e-3f })
                frames.run(20) { lighting.state?.next == null }
                assertNull(lighting.state!!.next)
                assertNotSame(first, lighting.state!!.current)
                assertFalse("the superseded lighting is released", Gdx.gl.glIsTexture(firstHandle))
            } finally {
                lighting.dispose()
            }
        }
    }

    @Test
    fun aSunWithinADegreeDoesNotRebuild() {
        inGl {
            val lighting = lighting()
            val frames = Frames(lighting)
            try {
                frames.run(20) { lighting.state != null }
                val first = lighting.state!!.current
                frames.sun = sun(0.5)
                frames.run(40)
                assertSame(first, lighting.state!!.current)
                assertNull(lighting.state!!.next)
                assertFalse(lighting.isBuilding)
            } finally {
                lighting.dispose()
            }
        }
    }

    @Test
    fun aReloadedStationaryCloudAssetRefreshesTheLighting() {
        inGl {
            var asset: Clouds? = null
            val lighting = lighting { asset }
            val frames = Frames(lighting)
            val made = mutableListOf<Clouds>()
            try {
                frames.run(20) { lighting.state != null }
                val first = lighting.state!!.current
                frames.run(60)
                assertSame("stationary clear sky: nothing to refresh", first, lighting.state!!.current)
                asset = clouds(wind = false).also(made::add)
                frames.run(60) { lighting.state?.next != null }
                assertNotNull("the new weather is built", lighting.state!!.next)
                frames.run(30) { lighting.state?.next == null }
                val second = lighting.state!!.current
                assertNotSame(first, second)
                asset = clouds(wind = false).also(made::add) // reloaded: a different object, same weather
                frames.run(60) { lighting.state?.next != null }
                assertNotNull("a reload refreshes even without wind", lighting.state!!.next)
            } finally {
                lighting.dispose()
                made.forEach { it.dispose() }
            }
        }
    }

    @Test
    fun driftingCloudsRefreshWithTime() {
        inGl {
            val asset = clouds(wind = true)
            val lighting = lighting { asset }
            val frames = Frames(lighting)
            try {
                frames.run(20) { lighting.state != null }
                val taken = frames.run(60) { lighting.state?.next != null }
                assertNotNull("rebuilt within about two seconds of cloud time (took $taken frames)", lighting.state!!.next)
            } finally {
                lighting.dispose()
                asset.dispose()
            }
        }
    }

    @Test
    fun aFirstBuildFailureKeepsTheAmbientAndLogsOnce() {
        inGl {
            val log = RecordingLogger()
            val lighting = lighting(log)
            val frames = Frames(lighting)
            val gl30 = Gdx.gl30
            try {
                Gdx.gl30 = null
                frames.run(10)
                assertNull(lighting.state)
                assertFalse(lighting.isBuilding)
                assertEquals(1, log.warnings.size)
            } finally {
                Gdx.gl30 = gl30
                lighting.dispose()
            }
        }
    }

    @Test
    fun aLaterFailureKeepsTheLastLighting() {
        inGl {
            val log = RecordingLogger()
            val lighting = lighting(log)
            val frames = Frames(lighting)
            val gl30 = Gdx.gl30
            try {
                frames.run(20) { lighting.state != null }
                val first = lighting.state!!.current
                frames.sun = sun(30.0)
                Gdx.gl30 = null
                frames.run(10)
                Gdx.gl30 = gl30
                assertSame(first, lighting.state!!.current)
                assertNull(lighting.state!!.next)
                assertEquals(1, log.warnings.size)
                assertTrue("lighting is still alive", Gdx.gl.glIsTexture(first.irradiance.textureObjectHandle))
            } finally {
                Gdx.gl30 = gl30
                lighting.dispose()
            }
        }
    }

    @Test
    fun disposeReleasesEverything() {
        inGl {
            val lighting = lighting()
            val frames = Frames(lighting)
            frames.run(20) { lighting.state != null }
            frames.sun = sun(30.0)
            frames.run(15) { lighting.state?.next != null }
            val handles = lighting.state!!.let { s ->
                listOfNotNull(s.current, s.next).flatMap { listOf(it.specular, it.irradiance) }.map { it.textureObjectHandle }
            }
            lighting.dispose()
            assertNull(lighting.state)
            handles.forEach { assertFalse("texture $it released", Gdx.gl.glIsTexture(it)) }
        }
    }
}
