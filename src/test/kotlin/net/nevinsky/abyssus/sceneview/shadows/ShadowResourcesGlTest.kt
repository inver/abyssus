/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.shadows

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.sceneview.GlHarness
import net.nevinsky.abyssus.sceneview.SceneRenderParams
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class ShadowResourcesGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    @Test fun allocatesAtlasFallsBackAndRestoresFramebufferViewportAndScissor() {
        var checked = false
        var beforeScissor = false
        val result = GlHarness.render(SceneRenderParams.DEFAULT, 2) { _, frame ->
            if (frame != 0) return@render
            val unsupported = ShadowResources(100_000)
            assertFalse(unsupported.available)
            unsupported.dispose()
            val originalGl = Gdx.gl
            val originalGl20 = Gdx.gl20
            val incomplete = object : GL20 by originalGl {
                override fun glCheckFramebufferStatus(target: Int) = GL20.GL_FRAMEBUFFER_UNSUPPORTED
            }
            try {
                Gdx.gl = incomplete
                Gdx.gl20 = incomplete
                val failed = ShadowResources(256)
                assertFalse("Incomplete framebuffer must leave lighting usable", failed.available)
                assertNull(failed.atlas)
                failed.dispose()
            } finally {
                Gdx.gl = originalGl
                Gdx.gl20 = originalGl20
            }
            val resources = ShadowResources(256)
            try {
                assertTrue(resources.available)
                val viewport = BufferUtils.newIntBuffer(4)
                beforeScissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)
                Gdx.gl.glViewport(3, 4, 120, 90)
                Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
                Gdx.gl.glScissor(5, 6, 70, 60)
                val success = resources.render(ShadowTile(0, 0, 0, 128, 256)) {
                    assertFalse(Gdx.gl.glIsEnabled(GL20.GL_DITHER))
                    Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                }
                assertTrue(success)
                Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport)
                assertArrayEquals(intArrayOf(3, 4, 120, 90), IntArray(4) { viewport.get(it) })
                assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST))
                val box = BufferUtils.newIntBuffer(4)
                Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, box)
                assertArrayEquals(intArrayOf(5, 6, 70, 60), IntArray(4) { box.get(it) })
                checked = true
            } finally {
                resources.dispose()
                if (!beforeScissor) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
            }
        }
        assertNull(result.error)
        assertTrue(checked)
    }

    @Test fun failedPassRestoresCallerFramebufferAndResourcesCanBeRebuilt() {
        var checked = false
        val result = GlHarness.render(SceneRenderParams.DEFAULT, 2) { _, frame ->
            if (frame != 0) return@render
            val caller = FrameBuffer(Pixmap.Format.RGBA8888, 64, 64, true)
            val resources = ShadowResources(256)
            try {
                caller.bind()
                Gdx.gl.glDepthMask(false)
                Gdx.gl.glDepthFunc(GL20.GL_GREATER)
                Gdx.gl.glEnable(GL20.GL_BLEND)
                assertFalse(resources.render(ShadowTile(0, 0, 0, 128, 256)) { error("simulated depth failure") })
                val value = BufferUtils.newIntBuffer(1)
                Gdx.gl.glGetIntegerv(0x8CA6, value)
                assertEquals(caller.framebufferHandle, value.get(0))
                Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_FUNC, value)
                assertEquals(GL20.GL_GREATER, value.get(0))
                val write = BufferUtils.newByteBuffer(1)
                Gdx.gl.glGetBooleanv(GL20.GL_DEPTH_WRITEMASK, write)
                assertEquals(0, write.get(0).toInt())
                assertTrue(Gdx.gl.glIsEnabled(GL20.GL_BLEND))
                resources.dispose()
                assertNull(resources.attribute(emptyList()))
                assertFalse(resources.render(ShadowTile(0, 0, 0, 128, 256)) {})
                val rebuilt = ShadowResources(256)
                try {
                    assertTrue(rebuilt.available)
                    assertTrue(rebuilt.render(ShadowTile(0, 0, 0, 128, 256)) {})
                    rebuilt.abandon()
                    assertNull(rebuilt.atlas)
                    assertNull(rebuilt.attribute(emptyList()))
                } finally { rebuilt.dispose() }
                checked = true
            } finally {
                Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
                Gdx.gl.glDepthMask(true)
                Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
                Gdx.gl.glDisable(GL20.GL_BLEND)
                resources.dispose()
                caller.dispose()
            }
        }
        assertNull(result.error)
        assertTrue(checked)
    }
}
