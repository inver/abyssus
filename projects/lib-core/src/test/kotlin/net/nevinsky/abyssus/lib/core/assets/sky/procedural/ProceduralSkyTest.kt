/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.skyShaders
import net.nevinsky.abyssus.lib.core.assets.testProject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** The cloudy-sky environment a procedural sky hands to other features. Opt-in: `-Dabyssus.glTests=true`. */
class ProceduralSkyTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val skyDir = File(testProject("Untitled"), "assets/skybox_physical")
    private val noon = Vector3(0f, 1f, 0f)
    private val frame = 1.0 / 60

    /** The fixture sky, naming the cloud asset held in [cloud] (reloaded by replacing it), with `lightsScene` as given. */
    private fun sky(cloud: Array<Clouds?>, lightsScene: Boolean = false) = ProceduralSky(
        PreparedProceduralSky(
            AtmosphereParams(), File(skyDir, "sky.vert").readText(), File(skyDir, "sky.frag").readText(),
            "skybox_physical", "weather", lightsScene,
        ),
        BuiltAssets { name -> cloud[0]?.takeIf { it.name == name } }, skyShaders(),
    )

    private fun ProceduralSky.frames(count: Int, time: Double = 0.0) = repeat(count) { updateLighting(noon, time, true, frame) }

    private val still = CloudMeta(low = CloudBand(CloudType.STRATUS, coverage = 1f, windX = 0f, windZ = 0f))

    @Test
    fun nothingIsBuiltWithoutAConsumerOrOptIn() = TestGl.run {
        val cloud = arrayOf<Clouds?>(Clouds("weather", still, null))
        val sky = sky(cloud)
        try {
            assertNull(sky.lighting)
            sky.frames(40)
            assertNull("no consumer, nothing built", sky.lighting)
            assertNull(sky.lightingEnvironment)
        } finally {
            sky.dispose(); cloud[0]?.dispose()
        }
    }

    @Test
    fun aLeaseBuildsTheEnvironmentWithoutReplacingTheAmbientAndReleasesIt() = TestGl.run {
        val cloud = arrayOf<Clouds?>(Clouds("weather", still, null))
        val sky = sky(cloud)
        try {
            val lease = sky.acquireEnvironment()
            assertNull(lease.environment)
            assertEquals(0L, lease.revision)
            sky.frames(30)
            assertNotNull("the consumer gets the cloudy cube", lease.environment)
            assertEquals(1L, lease.revision)
            assertNull("the scene's ambient is not replaced without lightsScene", sky.lightingEnvironment)
            val second = sky.acquireEnvironment()
            assertSame("consumers share one environment", lease.environment, second.environment)
            lease.close()
            lease.close()
            assertNotNull("one lease still holds it", second.environment)
            second.close()
            assertNull("released with the last consumer", sky.lighting)
            assertNull(second.environment)
            sky.frames(10)
            assertNull("and not rebuilt for nobody", sky.lighting)
            assertEquals(0L, lease.revision)
        } finally {
            sky.dispose(); cloud[0]?.dispose()
        }
    }

    @Test
    fun theEnvironmentFollowsCloudReloadAndWindAndEveryChangeBumpsTheRevision() = TestGl.run {
        val cloud = arrayOf<Clouds?>(Clouds("weather", still, null))
        val sky = sky(cloud)
        try {
            sky.acquireEnvironment().use { lease ->
                sky.frames(30)
                val first = lease.environment!!
                val firstAmbient = first.ambient.copyOf()
                assertEquals(1L, lease.revision)
                sky.frames(30, time = 500.0)
                assertSame("stationary clouds do not change it", first, lease.environment)
                assertEquals(1L, lease.revision)
                val old = cloud[0]
                cloud[0] = Clouds("weather", still.copy(low = still.low!!.copy(coverage = 0.1f)), null)
                sky.frames(100, time = 500.0)
                old?.dispose()
                assertNotSame("a reload rebuilds it", first, lease.environment)
                assertEquals(2L, lease.revision)
                assertNotEquals(firstAmbient.toList(), lease.environment!!.ambient.toList())
            }
        } finally {
            sky.dispose(); cloud[0]?.dispose()
        }
    }

    @Test
    fun anOptedInSkyKeepsItsLightingWhateverTheLeasesDoAndSharesIt() = TestGl.run {
        val cloud = arrayOf<Clouds?>(Clouds("weather", still, null))
        val sky = sky(cloud, lightsScene = true)
        try {
            assertNotNull(sky.lighting)
            sky.frames(30)
            val lit = sky.lightingEnvironment
            assertNotNull(lit)
            sky.acquireEnvironment().use { assertSame("the lease shares the scene lighting", lit, it.environment) }
            assertNotNull("closing the last lease keeps an opted-in sky's lighting", sky.lighting)
            assertSame(lit, sky.lightingEnvironment)
        } finally {
            sky.dispose(); cloud[0]?.dispose()
        }
    }

    @Test
    fun disposingTheSkyReleasesWhatALeaseStillHolds() = TestGl.run {
        val cloud = arrayOf<Clouds?>(Clouds("weather", still, null))
        val sky = sky(cloud)
        val lease = sky.acquireEnvironment()
        try {
            sky.frames(30)
            assertNotNull(lease.environment)
        } finally {
            sky.dispose(); cloud[0]?.dispose()
        }
        assertNull("a disposed sky hands out nothing", lease.environment)
        lease.close()
    }
}
