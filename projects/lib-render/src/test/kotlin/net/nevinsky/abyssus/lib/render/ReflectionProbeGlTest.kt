/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.lib.render.post.ReflectionProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lwjgl.opengl.GL32C.*

/**
 * The reflection probe on a real context (opt-in GL; OpenSpec `control-line-pbr-rendering`, "Interiors reflect
 * themselves"): a room lit one colour all round gives an environment of that light, the probe gives up cleanly when its
 * scene fails, and it leaves the caller's framebuffer bound.
 */
class ReflectionProbeGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val light = floatArrayOf(0.8f, 0.4f, 0.2f)

    /** Runs [probe] to its end, each face "drawn" as a room of [light]; the steps it took and the environment. */
    private fun run(probe: ReflectionProbe): Pair<Int, HdrEnvironment?> {
        var steps = 0
        while (true) {
            steps++
            val done =
                probe.step { _ ->
                    glClearColor(light[0], light[1], light[2], 1f)
                    glClear(GL_COLOR_BUFFER_BIT)
                }
            if (done != null || !probe.busy) {
                return steps to done
            }
            check(steps < 100) { "the probe never finished" }
        }
    }

    /** The mean RGB of face 0 of the cube [texture] at mip 0, read back as floats. */
    private fun meanOfFace(texture: Int): FloatArray {
        glBindTexture(GL_TEXTURE_CUBE_MAP, texture)
        val w = glGetTexLevelParameteri(GL_TEXTURE_CUBE_MAP_POSITIVE_X, 0, GL_TEXTURE_WIDTH)
        val texels = FloatArray(w * w * 4)
        glGetTexImage(GL_TEXTURE_CUBE_MAP_POSITIVE_X, 0, GL_RGBA, GL_FLOAT, texels)
        glBindTexture(GL_TEXTURE_CUBE_MAP, 0)
        return FloatArray(3) { c -> (0 until w * w).sumOf { texels[it * 4 + c].toDouble() }.toFloat() / (w * w) }
    }

    @Test
    fun aRoomOfOneLightGivesAnEnvironmentOfThatLight(): Unit = TestGl.run {
        val probe = ReflectionProbe(ShaderStorage(), size = 32)
        val framebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        var environment: HdrEnvironment? = null
        try {
            probe.start(Vector3(1f, 2f, 3f))
            assertTrue(probe.busy)
            val (steps, done) = run(probe)
            environment = done
            assertNotNull("the probe failed: ${probe.failure}", environment)
            assertFalse(probe.busy)
            // three frames for the faces, then the prefilter a few steps at a time
            assertTrue("the probe took $steps steps", steps in 4..10)
            assertEquals(framebuffer, glGetInteger(GL_FRAMEBUFFER_BINDING))
            val irradiance = meanOfFace(environment!!.irradiance.textureObjectHandle)
            val specular = meanOfFace(environment.specular.textureObjectHandle)
            for (c in 0..2) {
                assertEquals("irradiance channel $c", light[c], irradiance[c], light[c] * 0.1f)
                assertEquals("specular channel $c", light[c], specular[c], light[c] * 0.1f)
            }
            assertEquals(GL_NO_ERROR, glGetError())
        } finally {
            environment?.dispose()
            probe.dispose()
        }
    }

    @Test
    fun aFailingSceneLeavesNoProbe(): Unit = TestGl.run {
        val probe = ReflectionProbe(ShaderStorage(), size = 16)
        val framebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        try {
            probe.start(Vector3())
            val done = probe.step { error("the scene cannot be drawn") }
            assertNull(done)
            assertFalse(probe.busy)
            assertEquals("the scene cannot be drawn", probe.failure)
            assertEquals(framebuffer, glGetInteger(GL_FRAMEBUFFER_BINDING))
            // a new probe starts afresh
            probe.start(Vector3())
            assertNull(probe.failure)
            val again = run(probe).second
            assertNotNull(again)
            again?.dispose()
        } finally {
            probe.dispose()
        }
    }
}
