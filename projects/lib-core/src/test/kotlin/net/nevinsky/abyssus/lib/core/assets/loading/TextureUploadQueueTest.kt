/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.loading

import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.lib.core.assets.loading.TextureUploadQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Needs a GL context (a [Texture] cannot exist without one): opt-in with `-Dabyssus.glTests=true`. */
class TextureUploadQueueTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private fun pixmaps(vararg names: String) = names.associateWith { Pixmap(1, 1, Pixmap.Format.RGBA8888) }

    /** The factory a model uses: the queue hands over the pixmap, the factory disposes it. */
    private fun queueOf(made: MutableList<String>, pixmaps: Map<String, Pixmap>) =
        TextureUploadQueue(pixmaps) { name, pixmap ->
            made += name
            Texture(pixmap).also { pixmap.dispose() }
        }

    @Test
    fun uploadsOneImagePerCall() = TestGl.run {
        val made = mutableListOf<String>()
        val queue = queueOf(made, pixmaps("a", "b", "c"))
        assertFalse(queue.uploadNext())
        assertEquals(1, queue.textures.size)
        assertFalse(queue.uploadNext())
        assertTrue(queue.uploadNext())
        assertEquals(setOf("a", "b", "c"), queue.textures.keys)
        assertEquals(listOf("a", "b", "c"), made)
        assertTrue("nothing left", queue.uploadNext())
        queue.dispose()
    }

    @Test
    fun disposeReleasesTheRemainingPixmapsAndTheUploadedTextures() = TestGl.run {
        val waiting = pixmaps("a", "b")
        val queue = queueOf(mutableListOf(), waiting)
        queue.uploadNext()
        val uploaded = queue.textures.getValue("a")
        queue.dispose()
        assertTrue("the pixmap still waiting is released", waiting.getValue("b").isDisposed)
        assertEquals("the uploaded texture is released", 0, uploaded.textureObjectHandle)
        assertTrue(queue.textures.isEmpty())
    }

    @Test
    fun disposeTwiceIsSafe() = TestGl.run {
        val queue = queueOf(mutableListOf(), pixmaps("a", "b"))
        queue.uploadNext()
        queue.dispose()
        queue.dispose()
        assertTrue(queue.uploadNext())
    }
}
