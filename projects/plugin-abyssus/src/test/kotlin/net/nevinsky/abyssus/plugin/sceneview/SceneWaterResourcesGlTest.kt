/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.water.WaterPassPlan
import net.nevinsky.abyssus.lib.core.water.WaterPassPlanner
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import net.nevinsky.abyssus.plugin.sceneview.water.WaterResources
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

/**
 * The offscreen targets of a view's water passes in a real GL context: allocated for what a plan needs, reused until
 * the size changes, restoring the caller's framebuffer and state, skipping an unusable size once, rebuilt after the
 * context is abandoned and released on dispose. Opt-in GL checks.
 */
class SceneWaterResourcesGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val presets = WaterPresets()
    private val planner = WaterPassPlanner()

    private fun plan(width: Int, height: Int, levels: Int = 2): WaterPassPlan {
        val surfaces = (0 until levels).map { presets.defaults(WaterPreset.LAKE, "s$it", "s$it", 0.0, it.toDouble(), 0.0) }
        return planner.plan(surfaces, 100.0, width, height, { 1.0 + it.level })
    }

    /** Runs [block] on frame 2 of a trivial scene, inside the GL context the renderer draws in. */
    private fun inGl(block: () -> Unit) {
        val params = net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams.DEFAULT
        var failure: Throwable? = null
        val result = GlHarness.render(params, 4, OrbitCamera(Vector3(0f, 0f, 0f), 10f, 0f, 0.5f), framebufferSize = 64 to 48) { _, index ->
            if (index == 2) try { block() } catch (e: Throwable) { failure = e }
        }
        result.error?.let { throw it }
        failure?.let { throw it }
    }

    private fun ints(name: Int, count: Int): IntArray {
        val buffer = BufferUtils.newIntBuffer(count)
        Gdx.gl.glGetIntegerv(name, buffer)
        return IntArray(count) { buffer.get(it) }
    }

    @Test fun allocatesWhatThePlanNeedsAndNothingMore() = inGl {
        val log = RecordingLogger()
        val resources = WaterResources(log)
        try {
            assertTrue(resources.ensure(plan(800, 600, levels = 2), 800, 600))
            assertEquals(3 + 2, resources.targetCount)
            assertEquals(800 to 600, resources.sceneColor!!.width to resources.sceneColor!!.height)
            assertEquals(800 to 600, resources.sceneDepth!!.width to resources.sceneDepth!!.height)
            assertEquals(800 to 600, resources.terrainDepth!!.width to resources.terrainDepth!!.height)
            assertEquals(400 to 300, resources.reflection(0)!!.width to resources.reflection(0)!!.height)
            assertNotNull(resources.reflection(1))
            assertNull(resources.reflection(2))
            assertEquals(emptyList<String>(), log.warnings)
        } finally {
            resources.dispose()
        }
    }

    @Test fun reusesTargetsUntilTheSizeChangesAndReleasesSurplusReflections() = inGl {
        val resources = WaterResources(RecordingLogger())
        try {
            assertTrue(resources.ensure(plan(640, 480), 640, 480))
            val depth = resources.sceneDepth
            val reflection = resources.reflection(0)
            assertTrue(resources.ensure(plan(640, 480), 640, 480))
            assertSame("the same size keeps the targets", depth, resources.sceneDepth)
            assertSame(reflection, resources.reflection(0))
            assertTrue(resources.ensure(plan(320, 240), 320, 240))
            assertNotSame("a new size makes new targets", depth, resources.sceneDepth)
            assertEquals(320 to 240, resources.sceneDepth!!.width to resources.sceneDepth!!.height)
            assertEquals(160 to 120, resources.reflection(0)!!.width to resources.reflection(0)!!.height)
            val depthAtThisSize = resources.sceneDepth
            assertTrue(resources.ensure(plan(320, 240, levels = 1), 320, 240))
            assertNull("an unneeded reflection target is released", resources.reflection(1))
            assertSame("and the other targets are kept", depthAtThisSize, resources.sceneDepth)
            assertEquals(3 + 1, resources.targetCount)
        } finally {
            resources.dispose()
        }
    }

    @Test fun aPassRestoresTheCallersFramebufferViewportAndState() = inGl {
        val resources = WaterResources(RecordingLogger())
        val callers = FrameBuffer(Pixmap.Format.RGBA8888, 40, 30, true)
        try {
            assertTrue(resources.ensure(plan(64, 48, levels = 1), 64, 48))
            callers.bind()
            Gdx.gl.glViewport(3, 4, 20, 10)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glEnable(GL20.GL_CULL_FACE)
            Gdx.gl.glDepthFunc(GL20.GL_GREATER)
            val binding = ints(0x8CA6, 1)[0]
            val viewport = ints(GL20.GL_VIEWPORT, 4)
            var inside = -1
            val ok = resources.render(resources.reflection(0)!!, Color(0.2f, 0.4f, 0.6f, 1f)) {
                inside = ints(0x8CA6, 1)[0]
                Gdx.gl.glDisable(GL20.GL_BLEND)
                Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
                Gdx.gl.glDisable(GL20.GL_CULL_FACE)
                Gdx.gl.glFrontFace(GL20.GL_CW)
                Gdx.gl.glViewport(0, 0, 1, 1)
            }
            assertTrue(ok)
            assertTrue("the pass drew into its own target", inside != binding)
            assertEquals("the caller's framebuffer is bound again", binding, ints(0x8CA6, 1)[0])
            assertEquals(viewport.toList(), ints(GL20.GL_VIEWPORT, 4).toList())
            assertTrue(Gdx.gl.glIsEnabled(GL20.GL_BLEND))
            assertFalse(Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST))
            assertTrue(Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE))
            assertEquals(GL20.GL_GREATER, ints(GL20.GL_DEPTH_FUNC, 1)[0])
            assertEquals("the winding is back to counter-clockwise", GL20.GL_CCW, ints(0x0B46, 1)[0])
        } finally {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
            callers.dispose()
            resources.dispose()
        }
    }

    @Test fun aFailingPassIsReportedNotThrownAndStillRestoresState() = inGl {
        val resources = WaterResources(RecordingLogger())
        try {
            assertTrue(resources.ensure(plan(64, 48, levels = 1), 64, 48))
            val binding = ints(0x8CA6, 1)[0]
            Gdx.gl.glEnable(GL20.GL_BLEND)
            val ok = resources.render(resources.reflection(0)!!, Color(0f, 0f, 0f, 1f)) {
                Gdx.gl.glDisable(GL20.GL_BLEND)
                error("boom")
            }
            assertFalse(ok)
            assertEquals(binding, ints(0x8CA6, 1)[0])
            assertTrue(Gdx.gl.glIsEnabled(GL20.GL_BLEND))
        } finally {
            resources.dispose()
        }
    }

    @Test fun anUnusableSizeIsSkippedAndLoggedOnceUntilTheSizeChanges() = inGl {
        val log = RecordingLogger()
        val resources = WaterResources(log)
        try {
            assertFalse(resources.ensure(plan(64, 48), 0, 0))
            assertTrue(resources.failed)
            assertFalse(resources.ensure(plan(64, 48), 0, 0))
            assertEquals("logged once", 1, log.warnings.size)
            assertEquals(0, resources.targetCount)
            assertTrue("a meaningful change retries", resources.ensure(plan(64, 48), 64, 48))
            assertFalse(resources.failed)
            assertEquals(1, log.warnings.size)
        } finally {
            resources.dispose()
        }
    }

    @Test fun anAbandonedContextIsForgottenWithoutGlAndRebuilt() = inGl {
        val resources = WaterResources(RecordingLogger())
        try {
            assertTrue(resources.ensure(plan(64, 48), 64, 48))
            val old = resources.sceneDepth
            resources.abandon()
            assertEquals("every reference is dropped", 0, resources.targetCount)
            assertNull(resources.sceneColor)
            assertTrue("the next frame rebuilds them", resources.ensure(plan(64, 48), 64, 48))
            assertNotNull(resources.sceneDepth)
            assertNotSame(old, resources.sceneDepth)
            assertEquals(5, resources.targetCount)
        } finally {
            resources.dispose()
        }
    }

    @Test fun disposeReleasesEveryTarget() = inGl {
        val resources = WaterResources(RecordingLogger())
        assertTrue(resources.ensure(plan(64, 48), 64, 48))
        resources.dispose()
        assertEquals(0, resources.targetCount)
        assertNull(resources.reflection(0))
    }

    @Test fun theSceneCopyReadsTheCurrentFramebufferAndRefusesAWrongSize() = inGl {
        val resources = WaterResources(RecordingLogger())
        val source = FrameBuffer(Pixmap.Format.RGBA8888, 64, 48, false)
        try {
            assertTrue(resources.ensure(plan(64, 48), 64, 48))
            source.bind()
            Gdx.gl.glClearColor(1f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            assertTrue(resources.captureScene(64, 48))
            assertFalse("a different size has no copy", resources.captureScene(32, 24))
        } finally {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
            source.dispose()
            resources.dispose()
        }
    }
}
