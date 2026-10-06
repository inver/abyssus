/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core

import com.badlogic.gdx.utils.Disposable
import java.nio.IntBuffer

/**
 * An IndexData instance holds index data. Can be either a plain int buffer or an OpenGL buffer object.
 *
 * @author mzechner
 */
interface IndexData : Disposable {
    /**
     * @return the number of indices currently stored in this buffer
     */
    val numIndices: Int

    /**
     * @return the maximum number of indices this IndexBufferObject can store.
     */
    val numMaxIndices: Int

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
     * @param indices the index data
     * @param offset  the offset to start copying the data from
     * @param count   the number of ints to copy
     */
    fun setIndices(indices: IntArray?, offset: Int, count: Int)

    /**
     * Copies the specified indices to the indices of this IndexBufferObject, discarding the old indices. Copying start
     * at the current [IntBuffer.position] of the specified buffer and copied the
     * [IntBuffer.remaining] amount of indices. This can be called in between calls to [.bind] and
     * [.unbind]. The index data will be updated instantly.
     *
     * @param indices the index data to copy
     */
    fun setIndices(indices: IntBuffer)

    /**
     * Update (a portion of) the indices.
     *
     * @param targetOffset offset in indices buffer
     * @param indices      the index data
     * @param offset       the offset to start copying the data from
     * @param count        the number of ints to copy
     */
    fun updateIndices(targetOffset: Int, indices: IntArray?, offset: Int, count: Int)

    /**
     *
     *
     * Returns the underlying IntBuffer. If you modify the buffer contents they wil be uploaded on the call to
     * [.bind]. If you need immediate uploading use [.setIndices].
     *
     *
     * @return the underlying int buffer.
     */
    val buffer: IntBuffer

    /**
     * Binds this IndexBufferObject for rendering with glDrawElements.
     */
    fun bind()

    /**
     * Unbinds this IndexBufferObject.
     */
    fun unbind()

    /**
     * Invalidates the IndexBufferObject so a new OpenGL buffer handle is created. Use this in case of a context loss.
     */
    fun invalidate()

    /**
     * Disposes this IndexDatat and all its associated OpenGL resources.
     */
    override fun dispose()
}
