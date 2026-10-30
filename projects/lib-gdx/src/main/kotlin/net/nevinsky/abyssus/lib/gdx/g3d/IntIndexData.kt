/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.IndexData
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.GdxRuntimeException
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer

/**
 * Stock libGDX [IndexData] over a 32-bit element buffer, so a mesh can address more than 65,535 vertices. The members
 * that take or return `short` indices throw [UnsupportedOperationException]: a stock utility that reads them fails
 * loudly instead of reading a truncated view. Use the `Int` members instead.
 */
class IntIndexData(isStatic: Boolean, maxIndices: Int) : IndexData {
    // a zero-sized buffer is avoided (it breaks on some drivers), the index count reports 0 for it
    private val empty = maxIndices == 0
    private val byteBuffer: ByteBuffer = BufferUtils.newUnsafeByteBuffer(maxOf(maxIndices, 1) * BYTES)
    private val intBuffer: IntBuffer = byteBuffer.asIntBuffer()
    private val usage = if (isStatic) GL20.GL_STATIC_DRAW else GL20.GL_DYNAMIC_DRAW
    private var bufferHandle = Gdx.gl20.glGenBuffer()
    private var dirty = true
    private var bound = false

    init {
        intBuffer.flip()
        byteBuffer.flip()
    }

    override fun getNumIndices(): Int = if (empty) 0 else intBuffer.limit()

    override fun getNumMaxIndices(): Int = if (empty) 0 else intBuffer.capacity()

    /** Replaces the indices with [count] values of [indices] from [offset]. */
    fun setIndices(indices: IntArray, offset: Int, count: Int) {
        dirty = true
        intBuffer.clear()
        intBuffer.put(indices, offset, count)
        intBuffer.flip()
        byteBuffer.position(0)
        byteBuffer.limit(count * BYTES)
        uploadIfBound()
    }

    /** Replaces the indices with the remaining values of [indices]; its position is kept. */
    fun setIndices(indices: IntBuffer) {
        dirty = true
        val position = indices.position()
        intBuffer.clear()
        intBuffer.put(indices)
        intBuffer.flip()
        indices.position(position)
        byteBuffer.position(0)
        byteBuffer.limit(intBuffer.limit() * BYTES)
        uploadIfBound()
    }

    /** Overwrites [count] indices from [targetOffset] with [indices] from [offset]. */
    fun updateIndices(targetOffset: Int, indices: IntArray, offset: Int, count: Int) {
        dirty = true
        val position = byteBuffer.position()
        byteBuffer.position(targetOffset * BYTES)
        BufferUtils.copy(indices, offset, byteBuffer, count)
        byteBuffer.position(position)
        intBuffer.position(0)
        uploadIfBound()
    }

    /** The backing buffer. A buffer taken [forWriting] is uploaded on the next [bind]. */
    fun intBuffer(forWriting: Boolean): IntBuffer {
        dirty = dirty or forWriting
        return intBuffer
    }

    override fun bind() {
        if (bufferHandle == 0) {
            throw GdxRuntimeException("No index buffer allocated!")
        }
        Gdx.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, bufferHandle)
        if (dirty) {
            upload()
        }
        bound = true
    }

    override fun unbind() {
        Gdx.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
        bound = false
    }

    override fun invalidate() {
        bufferHandle = Gdx.gl20.glGenBuffer()
        dirty = true
    }

    override fun dispose() {
        Gdx.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
        Gdx.gl20.glDeleteBuffer(bufferHandle)
        bufferHandle = 0
        BufferUtils.disposeUnsafeByteBuffer(byteBuffer)
    }

    private fun uploadIfBound() {
        if (bound) {
            upload()
        }
    }

    private fun upload() {
        byteBuffer.limit(intBuffer.limit() * BYTES)
        Gdx.gl20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, byteBuffer.limit(), byteBuffer, usage)
        dirty = false
    }

    override fun setIndices(indices: ShortArray?, offset: Int, count: Int) = unsupported()

    override fun setIndices(indices: ShortBuffer?) = unsupported()

    override fun updateIndices(targetOffset: Int, indices: ShortArray?, offset: Int, count: Int) = unsupported()

    @Deprecated("16-bit view, not available")
    override fun getBuffer(): ShortBuffer = unsupported()

    override fun getBuffer(forWriting: Boolean): ShortBuffer = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("IntIndexData holds 32-bit indices; use the IntArray / IntBuffer members")

    private companion object {
        const val BYTES = Int.SIZE_BYTES
    }
}
