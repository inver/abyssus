/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.GdxRuntimeException
import java.nio.ByteBuffer
import java.nio.IntBuffer

/**
 *
 *
 * In IndexBufferObject wraps OpenGL's index intBuffer functionality to be used in conjunction with VBOs. This class can be
 * seamlessly used with OpenGL ES 1.x and 2.0.
 *
 *
 *
 *
 * Uses indirect Buffers on Android 1.5/1.6 to fix GC invocation due to leaking PlatformAddress instances.
 *
 *
 *
 *
 * You can also use this to store indices for vertex arrays. Do not call [.bind] or [.unbind] in this
 * case but rather use [.getBuffer] to use the intBuffer directly with glDrawElements. You must also create the
 * IndexBufferObject with the second constructor and specify isDirect as true as glDrawElements in conjunction with
 * vertex arrays needs direct buffers.
 *
 *
 *
 *
 * VertexBufferObjects must be disposed via the [.dispose] method when no longer needed
 *
 *
 * @author mzechner, Thorsten Schleinzer
 */
class IndexBufferObject : IndexData {
    private val intBuffer: IntBuffer
    val byteBuffer: ByteBuffer
    val ownsBuffer: Boolean
    var bufferHandle: Int
    val isDirect: Boolean
    var isDirty: Boolean = true
    var isBound: Boolean = false
    val usage: Int

    // used to work around bug: https://android-review.googlesource.com/#/c/73175/
    private val empty: Boolean

    /**
     * Creates a new static IndexBufferObject to be used with vertex arrays.
     *
     * @param maxIndices the maximum number of indices this intBuffer can hold
     */
    constructor(maxIndices: Int) : this(true, maxIndices)

    /**
     * Creates a new IndexBufferObject.
     *
     * @param isStatic   whether the index intBuffer is static
     * @param maxIndices the maximum number of indices this intBuffer can hold
     */
    constructor(isStatic: Boolean, maxIndices: Int) {
        var maxIndices = maxIndices
        empty = maxIndices == 0
        if (empty) {
            maxIndices = 1 // avoid allocating a zero-sized intBuffer because of bug in Android's ART < Android 5.0
        }

        byteBuffer = BufferUtils.newUnsafeByteBuffer(maxIndices * CoreConst.BYTES_IN_VERTEX_COORD)
        isDirect = true

        intBuffer = byteBuffer.asIntBuffer()
        ownsBuffer = true
        intBuffer.flip()
        byteBuffer.flip()
        bufferHandle = Gdx.gl20.glGenBuffer()
        usage = if (isStatic) GL20.GL_STATIC_DRAW else GL20.GL_DYNAMIC_DRAW
    }

    constructor(isStatic: Boolean, data: ByteBuffer) {
        empty = data.limit() == 0
        byteBuffer = data
        isDirect = true

        intBuffer = byteBuffer.asIntBuffer()
        ownsBuffer = false
        bufferHandle = Gdx.gl20.glGenBuffer()
        usage = if (isStatic) GL20.GL_STATIC_DRAW else GL20.GL_DYNAMIC_DRAW
    }

    /**
     * @return the number of indices currently stored in this intBuffer
     */
    override val numIndices: Int
        get() {
        return if (empty) 0 else intBuffer.limit()
    }

    /**
     * @return the maximum number of indices this IndexBufferObject can store.
     */
    override val numMaxIndices: Int
        get() {
        return if (empty) 0 else intBuffer.capacity()
    }

    /**
     *
     *
     * Sets the indices of this IndexBufferObject, discarding the old indices. The count must equal the number of
     * indices to be copied to this IndexBufferObject.
     *
     *
     *
     *
     * This can be called in between calls to [.bind] and [.unbind]. The index data will be updated
     * instantly.
     *
     *
     * @param indices the vertex data
     * @param offset  the offset to start copying the data from
     * @param count   the number of ints to copy
     */
    override fun setIndices(indices: IntArray?, offset: Int, count: Int) {
        isDirty = true
        intBuffer.clear()
        intBuffer.put(indices, offset, count)
        intBuffer.flip()
        byteBuffer.position(0)
        byteBuffer.limit(count shl 1)

        if (isBound) {
            Gdx.gl20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, byteBuffer.limit(), byteBuffer, usage)
            isDirty = false
        }
    }

    override fun setIndices(indices: IntBuffer) {
        isDirty = true
        val pos = indices.position()
        intBuffer.clear()
        intBuffer.put(indices)
        intBuffer.flip()
        indices.position(pos)
        byteBuffer.position(0)
        byteBuffer.limit(intBuffer.limit() shl 1)

        if (isBound) {
            Gdx.gl20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, byteBuffer.limit(), byteBuffer, usage)
            isDirty = false
        }
    }

    override fun updateIndices(targetOffset: Int, indices: IntArray?, offset: Int, count: Int) {
        isDirty = true
        val pos = byteBuffer.position()
        byteBuffer.position(targetOffset * CoreConst.BYTES_IN_VERTEX_COORD)
        BufferUtils.copy(indices, offset, byteBuffer, count)
        byteBuffer.position(pos)
        intBuffer.position(0)

        if (isBound) {
            Gdx.gl20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, byteBuffer.limit(), byteBuffer, usage)
            isDirty = false
        }
    }

    /**
     *
     *
     * Returns the underlying IntBuffer. If you modify the intBuffer contents they wil be uploaded on the call to
     * [.bind]. If you need immediate uploading use [.setIndices].
     *
     *
     * @return the underlying int intBuffer.
     */
    override val buffer: IntBuffer
        get() {
        isDirty = true
        return intBuffer
    }

    /**
     * Binds this IndexBufferObject for rendering with glDrawElements.
     */
    override fun bind() {
        if (bufferHandle == 0) {
            throw GdxRuntimeException("No intBuffer allocated!")
        }

        Gdx.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, bufferHandle)
        if (isDirty) {
            byteBuffer.limit(intBuffer.limit() * CoreConst.BYTES_IN_VERTEX_COORD)
            Gdx.gl20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, byteBuffer.limit(), byteBuffer, usage)
            isDirty = false
        }
        isBound = true
    }

    /**
     * Unbinds this IndexBufferObject.
     */
    override fun unbind() {
        Gdx.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
        isBound = false
    }

    /**
     * Invalidates the IndexBufferObject so a new OpenGL intBuffer handle is created. Use this in case of a context loss.
     */
    override fun invalidate() {
        bufferHandle = Gdx.gl20.glGenBuffer()
        isDirty = true
    }

    /**
     * Disposes this IndexBufferObject and all its associated OpenGL resources.
     */
    override fun dispose() {
        Gdx.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
        Gdx.gl20.glDeleteBuffer(bufferHandle)
        bufferHandle = 0

        if (ownsBuffer) {
            BufferUtils.disposeUnsafeByteBuffer(byteBuffer)
        }
    }
}
