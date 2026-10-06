/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GdxRuntimeTest {
    private fun context(frame: GdxFrame = GdxFrame()) =
        GdxRuntime.newContext(frame, stubOf(GL20::class.java), null, stubOf(Files::class.java))

    @Test
    fun installsGlobalsOnlyInsideBlock() {
        val before = Gdx.graphics
        val frame = GdxFrame().apply { tick(640, 480) }
        val ctx = context(frame)
        GdxRuntime.withContext(ctx) {
            assertSame(ctx.graphics, Gdx.graphics)
            assertSame(ctx.gl20, Gdx.gl)
            assertEquals(640, Gdx.graphics.width)
            assertEquals(480, Gdx.graphics.height)
            assertNotNull(Gdx.app)
        }
        assertSame(before, Gdx.graphics)
    }

    @Test
    fun restoresGlobalsWhenBlockThrows() {
        val before = Gdx.gl20
        try {
            GdxRuntime.withContext(context()) { error("boom") }
        } catch (_: IllegalStateException) {
        }
        assertSame(before, Gdx.gl20)
    }

    @Test
    fun nestedContextsRestoreInOrder() {
        val outer = context()
        val inner = context()
        GdxRuntime.withContext(outer) {
            GdxRuntime.withContext(inner) { assertSame(inner.graphics, Gdx.graphics) }
            assertSame(outer.graphics, Gdx.graphics)
        }
        assertNull(Gdx.graphics)
    }

    @Test
    fun stubReturnsPrimitiveDefaultsAndIdentityHash() {
        val gl = stubOf(GL20::class.java)
        assertEquals(0, gl.glGetError())
        assertEquals(System.identityHashCode(gl), gl.hashCode())
    }

    @Test
    fun frameTickComputesDelta() {
        val frame = GdxFrame()
        frame.tick(10, 10, 1_000_000_000L)
        assertEquals(0f, frame.deltaSeconds, 0f)
        frame.tick(10, 10, 1_500_000_000L)
        assertEquals(0.5f, frame.deltaSeconds, 1e-6f)
    }
}
