/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class ProceduralSkyLightingGlTest {
    @Before
    fun requireGl() = assumeTrue(TestGl.enabled)

    private val noon = Vector3(0f, 1f, 0f)
    private val frame = 1.0 / 60

    /** Updates until [done] holds, at most [limit] frames; returns the frames used. */
    private fun ProceduralSkyLighting.until(
        limit: Int = 40, sun: Vector3 = noon, clouds: Clouds? = null, time: Double = 0.0, done: () -> Boolean,
    ): Int {
        for (i in 1..limit) {
            update(sun, clouds, time, true, frame)
            if (done()) return i
        }
        fail("not done after $limit frames")
        return limit
    }

    @Test
    fun theFirstBuildTakesFramesAndKeepsTheAmbientMeanwhile() = TestGl.run {
        val lighting = ProceduralSkyLighting(AtmosphereParams(), ShaderStorage(), "sky")
        try {
            lighting.update(noon, null, 0.0, true, frame)
            assertNull("one frame renders only the cube", lighting.current)
            val frames = lighting.until { lighting.current != null }
            assertTrue("one build step per frame, got $frames", frames in 5..12)
            assertNull(lighting.next)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            val ambient = lighting.current!!.ambient
            assertTrue("blue sky light from above: ${ambient.toList()}", ambient[8] > ambient[6])
        } finally {
            lighting.dispose()
        }
    }

    @Test
    fun aMovedSunFadesANewEnvironmentInOverOneSecond() = TestGl.run {
        val lighting = ProceduralSkyLighting(AtmosphereParams(), ShaderStorage(), "sky")
        try {
            lighting.until { lighting.current != null }
            val first = lighting.current
            val sunset = Vector3(1f, 0.05f, 0f).nor()
            lighting.until(sun = sunset) { lighting.next != null }
            assertSame(first, lighting.current)
            assertEquals(0f, lighting.fade, 1f / 30)
            lighting.until(limit = 100, sun = sunset) { lighting.next == null }
            assertNotSame(first, lighting.current)
            assertEquals(0f, lighting.fade, 0f)
            val ambient = lighting.current!!.ambient
            assertNotEquals(first!!.ambient.toList(), ambient.toList())
        } finally {
            lighting.dispose()
        }
    }

    @Test
    fun aCloudReloadWithoutWindRebuildsButTimeAloneDoesNot() = TestGl.run {
        val lighting = ProceduralSkyLighting(AtmosphereParams(), ShaderStorage(), "sky")
        val overcast = CloudMeta(low = CloudMeta.CloudBand(CloudType.STRATUS, coverage = 1f, windX = 0f, windZ = 0f))
        val first = Clouds("weather", overcast, null)
        val reloaded = Clouds("weather", overcast.copy(low = overcast.low!!.copy(coverage = 0.2f)), null)
        try {
            lighting.until(clouds = first) { lighting.current != null }
            val built = lighting.current
            repeat(30) { lighting.update(noon, first, 100.0 + it, true, frame) }
            assertSame("stationary clouds do not rebuild", built, lighting.current)
            assertNull(lighting.next)
            lighting.until(clouds = reloaded, time = 100.0) { lighting.next != null }
            lighting.until(limit = 100, clouds = reloaded, time = 100.0) { lighting.next == null }
            assertNotSame(built, lighting.current)
            assertNotEquals(built!!.ambient.toList(), lighting.current!!.ambient.toList())
        } finally {
            lighting.dispose(); first.dispose(); reloaded.dispose()
        }
    }

    @Test
    fun aFailedRefreshKeepsTheLastEnvironmentAndLogsIt() = TestGl.run {
        val log = RecordingLogger()
        val original = Gdx.gl
        var failing = false
        val lighting = ProceduralSkyLighting(AtmosphereParams(), ShaderStorage(), "sky", log)
        try {
            lighting.until { lighting.current != null }
            val first = lighting.current
            Gdx.gl = Proxy.newProxyInstance(GL20::class.java.classLoader, arrayOf(GL20::class.java)) { _, method, args ->
                if (failing && method.name == "glCheckFramebufferStatus") 0 else method.invoke(original, *(args ?: emptyArray()))
            } as GL20
            failing = true
            val sunset = Vector3(1f, 0.05f, 0f).nor()
            repeat(10) { lighting.update(sunset, null, 0.0, true, frame) }
            assertSame(first, lighting.current)
            assertNull(lighting.next)
            assertTrue("failure logged", log.warnings.isNotEmpty())
            Gdx.gl = original
            failing = false
        } finally {
            Gdx.gl = original
            lighting.dispose()
        }
    }

    @Test
    fun hiddenViewsDoNotBuildAndAbandonForgetsEverything() = TestGl.run {
        val lighting = ProceduralSkyLighting(AtmosphereParams(), ShaderStorage(), "sky")
        try {
            repeat(30) { lighting.update(noon, null, 0.0, false, frame) }
            assertNull(lighting.current)
            lighting.until { lighting.current != null }
            lighting.abandon()
            assertNull(lighting.current)
            assertNull(lighting.next)
            lighting.until { lighting.current != null }
            Unit
        } finally {
            lighting.dispose()
        }
    }
}
