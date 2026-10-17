/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.utils.BufferUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.nio.ShortBuffer

/** The index buffer holds and uploads indices above 65,535. Opt-in: `-Dabyssus.glTests=true`. */
class IntIndexDataGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    @Test
    fun setsUpdatesAndReadsBackIndicesAbove16Bit() = TestGl.run {
        val data = IntIndexData(true, 8)
        try {
            assertEquals(0, data.numIndices)
            assertEquals(8, data.numMaxIndices)

            data.setIndices(intArrayOf(0, 70_000, 65_536, 1_000_000, 5), 1, 3)
            assertEquals(3, data.numIndices)
            assertEquals(listOf(70_000, 65_536, 1_000_000), data.intBuffer(false).toList())

            data.updateIndices(1, intArrayOf(90_000), 0, 1)
            assertEquals(90_000, data.intBuffer(false).get(1))

            val source = BufferUtils.newIntBuffer(2).apply { put(65_535).put(65_536); flip() }
            data.setIndices(source)
            assertEquals(2, data.numIndices)
            assertEquals("the source position is kept", 0, source.position())
            assertEquals(listOf(65_535, 65_536), data.intBuffer(false).toList())
        } finally {
            data.dispose()
        }
    }

    @Test
    fun uploadsTheFullIntsToTheElementArrayBuffer() = TestGl.run {
        val data = IntIndexData(true, 4)
        try {
            val indices = intArrayOf(3, 70_000, 65_536, 1_000_000)
            data.setIndices(indices, 0, indices.size)
            data.bind()
            val size = BufferUtils.newIntBuffer(1)
            Gdx.gl20.glGetBufferParameteriv(GL20.GL_ELEMENT_ARRAY_BUFFER, GL20.GL_BUFFER_SIZE, size)
            assertEquals("four 32-bit indices", 16, size.get(0))

            val mapped = mapElements()
            assertEquals(indices.toList(), List(4) { mapped.get(it) })
            Gdx.gl30.glUnmapBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER)

            // an update while bound is uploaded instantly
            data.updateIndices(0, intArrayOf(99_999), 0, 1)
            val again = mapElements()
            assertEquals(99_999, again.get(0))
            Gdx.gl30.glUnmapBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER)

            data.unbind()
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
        } finally {
            data.dispose()
        }
    }

    @Test
    fun theShortMembersThrow() {
        TestGl.run { shortMembersThrow() }
    }

    private fun shortMembersThrow() {
        val data = IntIndexData(true, 4)
        try {
            assertThrows(UnsupportedOperationException::class.java) { data.setIndices(shortArrayOf(1), 0, 1) }
            assertThrows(UnsupportedOperationException::class.java) { data.setIndices(ShortBuffer.allocate(1)) }
            assertThrows(UnsupportedOperationException::class.java) { data.updateIndices(0, shortArrayOf(1), 0, 1) }
            assertThrows(UnsupportedOperationException::class.java) { data.getBuffer(false) }
        } finally {
            data.dispose()
        }
    }

    private fun mapElements(): java.nio.IntBuffer =
        (Gdx.gl30.glMapBufferRange(GL20.GL_ELEMENT_ARRAY_BUFFER, 0, 16, GL30.GL_MAP_READ_BIT) as java.nio.ByteBuffer)
            .order(java.nio.ByteOrder.nativeOrder()).asIntBuffer()

    private fun java.nio.IntBuffer.toList(): List<Int> {
        val copy = duplicate()
        return List(copy.remaining()) { copy.get() }
    }
}
