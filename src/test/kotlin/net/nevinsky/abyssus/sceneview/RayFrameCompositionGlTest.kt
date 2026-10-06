/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Rgba
import net.nevinsky.abyssus.editor.content.PlacementTransform
import net.nevinsky.abyssus.editor.content.AssetPlacement

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.raytracing.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL32C.*

/** Experimental Metal-to-GL transfer. Full editor/fog/grid composition remains task 4.2. */
class RayFrameCompositionGlTest {
    @Test fun sceneCompositionUpscalesToFramebufferAndOccludesTheGridWithNativeDepth() {
        assumeTrue(GlHarness.enabled)
        val rendered = GlHarness.render(SceneRenderParams.DEFAULT, 3, framebufferSize = 1280 to 720) { renderer, index ->
            if (index == 0) renderer.rayFrameProvider = { context ->
                RaySceneDisplay(RayFrame(RayFrameKey(1, 1, 1, 1), 2, 2,
                    FloatArray(16) { if (it % 4 == 0 || it % 4 == 3) 1f else 0f }, FloatArray(4)),
                    RayDisplayMetadata.capture(context))
            }
            if (index > 0) {
                assertTrue(renderer.presentedRayFrame)
                assertEquals(1280, renderer.lastWidth)
                assertEquals(720, renderer.lastHeight)
                val pixel = BufferUtils.newByteBuffer(4)
                glReadPixels(640, 360, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel)
                assertEquals(255, pixel.get(0).toInt() and 255)
                assertEquals(0, pixel.get(1).toInt() and 255)
                val depth = BufferUtils.newFloatBuffer(1)
                glReadPixels(640, 360, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, depth)
                assertEquals(0f, depth.get(0), 0f)
                assertEquals(GL_NO_ERROR, glGetError())
            }
        }
        rendered.error?.let { throw it }
    }

    @Test fun mismatchedOutputOrDeletedContentFallsBackBeforePresentingTheFrame() {
        assumeTrue(GlHarness.enabled)
        val rendered = GlHarness.render(SceneRenderParams.DEFAULT, 3) { renderer, index ->
            renderer.rayFrameProvider = { context ->
                val metadata = RayDisplayMetadata.capture(context)
                val stale = if (index == 0) metadata.copy(width = metadata.width + 1)
                else metadata.copy(content = metadata.content.copy(models = listOf(AssetPlacement("deleted", "model", PlacementTransform.IDENTITY))))
                RaySceneDisplay(RayFrame(RayFrameKey(1, 1, 1, 1), 1, 1, floatArrayOf(1f, 0f, 0f, 1f), floatArrayOf(0f)), stale)
            }
            if (index > 0) assertFalse(renderer.presentedRayFrame)
        }
        rendered.error?.let { throw it }
    }

    @Test fun alreadyShadedFogAndSkyColorsAreTransferredWithoutAnotherFogPass() {
        assumeTrue(GlHarness.enabled)
        val params = SceneRenderParams.DEFAULT.copy(fog = FogParams(Rgba(1f, 0f, 0f, 1f), 0.8f, 2f))
        val rendered = GlHarness.render(params, 2) { renderer, index ->
            if (index == 0) renderer.rayFrameProvider = { context ->
                RaySceneDisplay(RayFrame(RayFrameKey(1, 1, 1, 1), 1, 1,
                    floatArrayOf(0.2f, 0.4f, 0.6f, 1f), floatArrayOf(0f)), RayDisplayMetadata.capture(context))
            }
            else {
                val pixel = BufferUtils.newByteBuffer(4)
                glReadPixels(renderer.lastWidth / 2, renderer.lastHeight / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel)
                assertTrue((pixel.get(0).toInt() and 255) in 50..52)
                assertTrue((pixel.get(1).toInt() and 255) in 101..103)
                assertTrue((pixel.get(2).toInt() and 255) in 152..154)
            }
        }
        rendered.error?.let { throw it }
    }

    @Test fun uploadPreservesCallerStateAndBottomLeftPixelsAcrossResize() {
        assumeTrue(GlHarness.enabled)
        var presenter: RayFramePresenter? = null
        val rendered = GlHarness.render(SceneRenderParams.DEFAULT, 2) { renderer, index ->
            val pass = presenter ?: RayFramePresenter().also { presenter = it }
            val w = if (index == 0) 2 else 4
            val color = FloatArray(w * 2 * 4) { i ->
                when (i % 4) {
                    0 -> if (i / 4 < w) 1f else 0f
                    1 -> if (i / 4 >= w) 1f else 0f
                    3 -> 1f
                    else -> 0f
                }
            }
            val frame = RayFrame(RayFrameKey(1, 1, 1, 1), w, 2, color, FloatArray(w * 2) { 0.25f })
            val unpack = glGetInteger(GL_UNPACK_ROW_LENGTH)
            val oldDepth = glGetInteger(GL_DEPTH_FUNC)
            val oldMask = glGetBoolean(GL_DEPTH_WRITEMASK)
            val oldScissor = glIsEnabled(GL_SCISSOR_TEST)
            try {
                glPixelStorei(GL_UNPACK_ROW_LENGTH, 7)
                glDepthFunc(GL_GREATER)
                glDepthMask(false)
                glEnable(GL_SCISSOR_TEST)
                pass.draw(frame)
                assertEquals(7, glGetInteger(GL_UNPACK_ROW_LENGTH))
                assertEquals(GL_GREATER, glGetInteger(GL_DEPTH_FUNC))
                assertFalse(glGetBoolean(GL_DEPTH_WRITEMASK))
                assertTrue(glIsEnabled(GL_SCISSOR_TEST))
                fun pixel(y: Int): IntArray {
                    val bytes = BufferUtils.newByteBuffer(4)
                    glReadPixels(renderer.lastWidth / 2, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, bytes)
                    return IntArray(4) { bytes.get(it).toInt() and 255 }
                }
                assertArrayEquals(intArrayOf(255, 0, 0, 255), pixel(renderer.lastHeight / 4))
                assertArrayEquals(intArrayOf(0, 255, 0, 255), pixel(renderer.lastHeight * 3 / 4))
                assertEquals(GL_NO_ERROR, glGetError())
            } finally {
                glPixelStorei(GL_UNPACK_ROW_LENGTH, unpack)
                glDepthFunc(oldDepth)
                glDepthMask(oldMask)
                if (!oldScissor) glDisable(GL_SCISSOR_TEST)
                if (index == 1) pass.dispose()
            }
        }
        rendered.error?.let { throw it }
    }

    @Test fun nativeColorAndProjectedDepthReachTheExistingCanvas() {
        assumeTrue(GlHarness.enabled && System.getProperty("os.name").startsWith("Mac"))
        // Native work is off the AWT thread. Only host frame upload/drawing runs in GlHarness's Gdx context.
        val provider = MetalRayBackendFactory()
        val result = provider.probe()
        assertTrue("Metal required for this opt-in experiment: $result",result is RayCapability.Available)
        val nativeFrame = (result as RayCapability.Available).backend.use { backend ->
            backend.openSession("gl",RayLimits()).use { session ->
                val mesh = RaySliceMesh(floatArrayOf(-2f,-2f,0f, 2f,-2f,0f, 0f,2f,0f),intArrayOf(0,1,2))
                val camera = RaySliceCamera(listOf(0f,0f,2f),listOf(0f,0f,-1f),listOf(0f,1f,0f),60f,0.1f,100f)
                val transform = listOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)
                session.submit(RayRequest(RayFrameKey(1,1,1,1),1,1,camera,listOf(mesh),listOf(RaySliceInstance(0,transform,listOf(1f,0f,0f)))))
                val deadline = System.nanoTime()+5_000_000_000L
                var frame: RayFrame? = null
                while (frame == null && System.nanoTime() < deadline) {
                    frame = session.poll()
                    if (frame == null) Thread.sleep(1)
                }
                checkNotNull(frame) { "Metal frame timed out" }
            }
        }
        var presenter: RayFramePresenter? = null
        val rendered = GlHarness.render(SceneRenderParams.DEFAULT,3) { renderer, frame ->
            if (presenter == null) presenter = RayFramePresenter()
            val activeBefore = integer(GL20.GL_ACTIVE_TEXTURE)
            val programBefore = integer(GL20.GL_CURRENT_PROGRAM)
            val viewportBefore = IntArray(4).also { values ->
                val buffer = BufferUtils.newIntBuffer(4)
                Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,buffer)
                for (i in 0..3) values[i] = buffer.get(i)
            }
            presenter!!.draw(nativeFrame)
            assertEquals(activeBefore,integer(GL20.GL_ACTIVE_TEXTURE))
            assertEquals(programBefore,integer(GL20.GL_CURRENT_PROGRAM))
            val color = BufferUtils.newByteBuffer(4)
            Gdx.gl.glReadPixels(renderer.lastWidth/2,renderer.lastHeight/2,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,color)
            assertTrue((color.get(0).toInt() and 255) in 25..26)
            assertEquals(0,color.get(1).toInt() and 255)
            val depth = BufferUtils.newFloatBuffer(1)
            GL11.glReadPixels(renderer.lastWidth/2,renderer.lastHeight/2,1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth)
            assertEquals(nativeFrame.depthValues()[0],depth.get(0),0.00001f)
            val viewportAfter = BufferUtils.newIntBuffer(4)
            Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,viewportAfter)
            for (i in 0..3) assertEquals(viewportBefore[i],viewportAfter.get(i))
            assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError())
            if (frame == 2) presenter!!.dispose()
        }
        rendered.error?.let { throw it }
    }

    private fun integer(name: Int): Int = BufferUtils.newIntBuffer(1).let { Gdx.gl.glGetIntegerv(name,it); it.get(0) }
}
