/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudLevel
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.skyShaders
import net.nevinsky.abyssus.lib.core.assets.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** The cloudy-sky environment a [ProceduralSky] gives other features. Opt-in: `-Dabyssus.glTests=true`. */
class ProceduralSkyTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val skyDir = File(testProject("Untitled"), "assets/skybox_physical")

    private fun sun(degrees: Double) = Vector3(sin(Math.toRadians(degrees)).toFloat(), cos(Math.toRadians(degrees)).toFloat(), 0f)

    private fun clouds(wind: Boolean) = Clouds(
        "clouds",
        CloudMeta(low = CloudBand(CloudType.CUMULUS, CloudLevel.LOW, windX = if (wind) 4f else 0f, windZ = 0f)),
        null,
    )

    /** The fixture sky reading its cloud asset from [asset] on every draw, with scene lighting off. */
    private fun sky(asset: () -> Clouds?) = ProceduralSky(
        PreparedProceduralSky(
            AtmosphereParams(), File(skyDir, "sky.vert").readText(), File(skyDir, "sky.frag").readText(),
            "skybox_physical", "clouds",
        ),
        BuiltAssets { asset() }, skyShaders(),
    )

    @Test
    fun nothingIsBuiltWithoutAConsumerAndTheAmbientIsNotReplaced() = TestGl.run {
        val sky = sky { null }
        try {
            assertNull(sky.environmentCube)
            assertNull(sky.lightingState)
            assertNotNull(sky.updateEnvironment(sun(0.0), 0.0))
            assertNotNull(sky.environmentCube)
            assertFalse(sky.lightsScene)
            assertNull("the environment does not light the scene", sky.lightingState)
        } finally {
            sky.dispose()
        }
    }

    @Test
    fun theRevisionGrowsWhenTheSunTheWindOrTheCloudAssetChange() = TestGl.run {
        var asset: Clouds? = null
        val made = mutableListOf<Clouds>()
        val sky = sky { asset }
        try {
            val first = sky.updateEnvironment(sun(0.0), 0.0)!!
            assertEquals(1L, first.revision)
            assertEquals("same sun, same sky", 1L, sky.updateEnvironment(sun(0.5), 0.1)!!.revision)
            assertEquals("a sun move beyond a degree", 2L, sky.updateEnvironment(sun(2.0), 0.2)!!.revision)

            asset = clouds(wind = true).also(made::add)
            assertEquals("a cloud asset appears", 3L, sky.updateEnvironment(sun(2.0), 0.3)!!.revision)
            assertEquals(3L, sky.updateEnvironment(sun(2.0), 1.0)!!.revision)
            assertEquals("two seconds of drifting clouds", 4L, sky.updateEnvironment(sun(2.0), 2.4)!!.revision)

            asset = clouds(wind = false).also(made::add)
            assertEquals("a reload, now stationary", 5L, sky.updateEnvironment(sun(2.0), 2.5)!!.revision)
            assertEquals("stationary clouds are not rebuilt for time", 5L, sky.updateEnvironment(sun(2.0), 60.0)!!.revision)

            asset = null
            assertEquals("the clouds are removed", 6L, sky.updateEnvironment(sun(2.0), 61.0)!!.revision)
        } finally {
            sky.dispose()
            made.forEach { it.dispose() }
        }
    }

    @Test
    fun releaseAndDisposeFreeTheCube() = TestGl.run {
        val sky = sky { null }
        val handle = sky.updateEnvironment(sun(0.0), 0.0)!!.cube.textureObjectHandle
        assertTrue(Gdx.gl.glIsTexture(handle))
        sky.releaseEnvironment()
        assertNull(sky.environmentCube)
        assertFalse("released", Gdx.gl.glIsTexture(handle))

        val again = sky.updateEnvironment(sun(0.0), 0.0)!!
        assertEquals("built afresh", 1L, again.revision)
        val second = again.cube.textureObjectHandle
        sky.dispose()
        assertFalse("disposed with the sky", Gdx.gl.glIsTexture(second))
    }
}
