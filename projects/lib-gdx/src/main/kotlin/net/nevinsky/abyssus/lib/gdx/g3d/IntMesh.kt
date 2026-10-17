/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.GdxRuntimeException
import java.nio.IntBuffer
import java.nio.ShortBuffer

/**
 * A stock libGDX [Mesh] with 32-bit indices, so one mesh can hold more than 65,535 vertices. Because it is a stock
 * `Mesh`, it flows through the stock `MeshPart`, `NodePart`, `ModelInstance`, `ModelBatch` and `BaseShader` unchanged.
 *
 * It needs a GL 3.0 context: the vertex data is a [VertexBufferObjectWithVAO] (a plain buffer raises
 * `GL_INVALID_OPERATION` in a core profile). The views that take or return `short` indices ([getIndicesBuffer],
 * `setIndices(ShortArray)`, `getIndices(ShortArray)`, [copy]) throw [UnsupportedOperationException] instead of
 * returning a truncated view; use [setIndices] with an `IntArray` and [intIndices].
 */
open class IntMesh(isStatic: Boolean, maxVertices: Int, maxIndices: Int, attributes: VertexAttributes) :
    Mesh(VertexBufferObjectWithVAO(isStatic, maxVertices, attributes), IntIndexData(isStatic, maxIndices), false) {

    constructor(isStatic: Boolean, maxVertices: Int, maxIndices: Int, vararg attributes: VertexAttribute) :
            this(isStatic, maxVertices, maxIndices, VertexAttributes(*attributes))

    private val indexData: IntIndexData get() = indices as IntIndexData
    private val tmp = Vector3()

    /** How many instances an instanced mesh draws; 0 when it is not instanced. */
    val numInstances: Int get() = instances?.numInstances ?: 0

    /** The 32-bit index buffer. A buffer taken [forWriting] is uploaded on the next bind. */
    fun intIndices(forWriting: Boolean = false): IntBuffer = indexData.intBuffer(forWriting)

    /** Replaces the indices. */
    fun setIndices(indices: IntArray): IntMesh = setIndices(indices, 0, indices.size)

    /** Replaces the indices with [count] values of [indices] from [offset]. */
    fun setIndices(indices: IntArray, offset: Int, count: Int): IntMesh {
        indexData.setIndices(indices, offset, count)
        return this
    }

    /** Copies indices out; [srcOffset] and [count] are in indices. */
    fun getIndices(srcOffset: Int, count: Int, target: IntArray, destOffset: Int) {
        require(count >= 0 && srcOffset >= 0 && srcOffset + count <= numIndices) {
            "Requested $count indices from $srcOffset, the mesh has $numIndices"
        }
        val buffer = indexData.intBuffer(false)
        val position = buffer.position()
        buffer.position(srcOffset)
        buffer.get(target, destOffset, count)
        buffer.position(position)
    }

    override fun render(shader: ShaderProgram?, primitiveType: Int, offset: Int, count: Int, autoBind: Boolean) {
        if (count == 0) {
            return
        }
        if (autoBind) {
            bind(shader)
        }
        val numInstances = if (isInstanced) instances.numInstances else 0
        if (indices.numIndices > 0) {
            if (count + offset > indices.numMaxIndices) {
                throw GdxRuntimeException(
                    "Mesh attempting to access memory outside of the index buffer (count: $count, offset: $offset, " +
                            "max: ${indices.numMaxIndices})"
                )
            }
            if (isInstanced && numInstances > 0) {
                Gdx.gl30.glDrawElementsInstanced(
                    primitiveType, count, GL20.GL_UNSIGNED_INT, offset * Int.SIZE_BYTES, numInstances
                )
            } else {
                Gdx.gl20.glDrawElements(primitiveType, count, GL20.GL_UNSIGNED_INT, offset * Int.SIZE_BYTES)
            }
        } else if (isInstanced && numInstances > 0) {
            Gdx.gl30.glDrawArraysInstanced(primitiveType, offset, count, numInstances)
        } else {
            Gdx.gl20.glDrawArrays(primitiveType, offset, count)
        }
        if (autoBind) {
            unbind(shader)
        }
    }

    /**
     * Stock `unbind` unbinds the vertex array before it disables the instance attributes, which is
     * `GL_INVALID_OPERATION` in a core profile. The instances go first here.
     */
    override fun unbind(shader: ShaderProgram?, locations: IntArray?, instanceLocations: IntArray?) {
        val instanceData = instances
        if (instanceData != null && instanceData.numInstances > 0) {
            instanceData.unbind(shader, instanceLocations)
        }
        vertices.unbind(shader, locations)
        if (indices.numIndices > 0) {
            indices.unbind()
        }
    }

    override fun extendBoundingBox(out: BoundingBox, offset: Int, count: Int, transform: Matrix4?): BoundingBox {
        val max = if (numIndices == 0) numVertices else numIndices
        if (offset < 0 || count < 1 || offset + count > max) {
            throw GdxRuntimeException("Invalid part specified ( offset=$offset, count=$count, max=$max )")
        }
        forEachPosition(offset, count, transform) { out.ext(it) }
        return out
    }

    override fun calculateRadiusSquared(
        centerX: Float, centerY: Float, centerZ: Float, offset: Int, count: Int, transform: Matrix4?
    ): Float {
        if (offset < 0 || count < 1 || offset + count > numIndices) {
            throw GdxRuntimeException("Not enough indices")
        }
        var result = 0f
        forEachPosition(offset, count, transform) {
            val r = it.sub(centerX, centerY, centerZ).len2()
            if (r > result) {
                result = r
            }
        }
        return result
    }

    /** Calls [action] with the (transformed) position of each index of the part; the vector is reused. */
    private inline fun forEachPosition(offset: Int, count: Int, transform: Matrix4?, action: (Vector3) -> Unit) {
        val verts = vertices.getBuffer(false)
        val index = if (numIndices > 0) indexData.intBuffer(false) else null
        val position = getVertexAttribute(VertexAttributes.Usage.Position)
        val positionOffset = position.offset / Float.SIZE_BYTES
        val stride = vertices.attributes.vertexSize / Float.SIZE_BYTES
        val components = position.numComponents
        for (i in offset until offset + count) {
            val vertex = if (index != null) index.get(i) else i
            val at = vertex * stride + positionOffset
            tmp.set(
                verts.get(at),
                if (components > 1) verts.get(at + 1) else 0f,
                if (components > 2) verts.get(at + 2) else 0f
            )
            if (transform != null) {
                tmp.mul(transform)
            }
            action(tmp)
        }
    }

    @Deprecated("16-bit view, not available")
    override fun getIndicesBuffer(): ShortBuffer = unsupported("getIndicesBuffer")

    override fun getIndicesBuffer(forWriting: Boolean): ShortBuffer = unsupported("getIndicesBuffer")

    override fun setIndices(indices: ShortArray?): Mesh = unsupported("setIndices(ShortArray)")

    override fun setIndices(indices: ShortArray?, offset: Int, count: Int): Mesh = unsupported("setIndices(ShortArray)")

    override fun getIndices(indices: ShortArray?) = unsupported("getIndices(ShortArray)")

    override fun getIndices(indices: ShortArray?, destOffset: Int) = unsupported("getIndices(ShortArray)")

    override fun getIndices(srcOffset: Int, indices: ShortArray?, destOffset: Int) =
        unsupported("getIndices(ShortArray)")

    override fun getIndices(srcOffset: Int, count: Int, indices: ShortArray?, destOffset: Int) =
        unsupported("getIndices(ShortArray)")

    override fun copy(isStatic: Boolean, removeDuplicates: Boolean, usage: IntArray?): Mesh = unsupported("copy")

    override fun copy(isStatic: Boolean): Mesh = unsupported("copy")

    private fun unsupported(member: String): Nothing =
        throw UnsupportedOperationException("IntMesh holds 32-bit indices: $member is not available")
}
