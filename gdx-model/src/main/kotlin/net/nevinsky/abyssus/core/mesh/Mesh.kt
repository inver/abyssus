/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.core.mesh

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.*
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import net.nevinsky.abyssus.core.IndexBufferObject
import net.nevinsky.abyssus.core.CoreConst
import net.nevinsky.abyssus.core.IndexData
import java.nio.Buffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.sqrt

/**
 *
 *
 * A Mesh holds vertices composed of attributes specified by a [VertexAttributes] instance. The vertices are held
 * either in VRAM in form of vertex buffer objects or in RAM in form of vertex arrays. The former variant is more
 * performant and is preferred over vertex arrays if hardware supports it.
 *
 *
 *
 *
 * Meshes are automatically managed. If the OpenGL context is lost all vertex buffer objects get invalidated and must be
 * reloaded when the context is recreated. This only happens on Android when a user switches to another application or
 * receives an incoming call. A managed Mesh will be reloaded automagically so you don't have to do this manually.
 *
 *
 *
 *
 * A Mesh consists of vertices and optionally indices which specify which vertices define a triangle. Each vertex is
 * composed of attributes such as position, normal, color or texture coordinate. Note that not all of this attributes
 * must be given, except for position which is non-optional. Each attribute has an alias which is used when rendering a
 * Mesh in OpenGL ES 2.0. The alias is used to bind a specific vertex attribute to a shader attribute. The shader source
 * and the alias of the attribute must match exactly for this to work.
 *
 *
 * @author mzechner, Dave Clayton <contact></contact>@redskyforge.com>, Xoppa
 */
class Mesh : Disposable {
    val vertices: VertexData
    val indices: IndexData
    var autoBind: Boolean = true

    var instances: InstanceData? = null
    /**
     * @return Indicates whether this mesh uses instancing.
     */
    var isInstanced: Boolean = false

    /**
     * Creates a new Mesh with the given attributes.
     *
     * @param isStatic    whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices  the maximum number of indices this mesh can hold
     * @param attributes  the [VertexAttribute]s. Each vertex attribute defines one property of a vertex such as
     * position, normal or texture coordinate
     */
    constructor(isStatic: Boolean, maxVertices: Int, maxIndices: Int, vararg attributes: VertexAttribute?) {
        vertices = makeVertexBuffer(isStatic, maxVertices, VertexAttributes(*attributes))
        indices = IndexBufferObject(isStatic, maxIndices)
    }

    /**
     * Creates a new Mesh with the given attributes.
     *
     * @param isStatic    whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices  the maximum number of indices this mesh can hold
     * @param attributes  the [VertexAttributes]. Each vertex attribute defines one property of a vertex such as
     * position, normal or texture coordinate
     */
    constructor(isStatic: Boolean, maxVertices: Int, maxIndices: Int, attributes: VertexAttributes) {
        vertices = makeVertexBuffer(isStatic, maxVertices, attributes)
        indices = IndexBufferObject(isStatic, maxIndices)
    }

    /**
     * Creates a new Mesh with the given attributes. Adds extra optimizations for dynamic (frequently modified) meshes.
     *
     * @param staticVertices whether vertices of this mesh are static or not. Allows for internal optimizations.
     * @param staticIndices  whether indices of this mesh are static or not. Allows for internal optimizations.
     * @param maxVertices    the maximum number of vertices this mesh can hold
     * @param maxIndices     the maximum number of indices this mesh can hold
     * @param attributes     the [VertexAttributes]. Each vertex attribute defines one property of a vertex such
     * as position, normal or texture coordinate
     * @author Jaroslaw Wisniewski <j.wisniewski></j.wisniewski>@appsisle.com>
     */
    constructor(
        staticVertices: Boolean, staticIndices: Boolean, maxVertices: Int, maxIndices: Int,
        attributes: VertexAttributes
    ) {
        vertices = makeVertexBuffer(staticVertices, maxVertices, attributes)
        indices = IndexBufferObject(staticIndices, maxIndices)
    }

    private fun makeVertexBuffer(isStatic: Boolean, maxVertices: Int, vertexAttributes: VertexAttributes): VertexData {
        if (Gdx.gl30 != null) {
            return VertexBufferObjectWithVAO(isStatic, maxVertices, vertexAttributes)
        } else {
            return VertexBufferObject(isStatic, maxVertices, vertexAttributes)
        }
    }

    fun enableInstancedRendering(isStatic: Boolean, maxInstances: Int, vararg attributes: VertexAttribute?): Mesh {
        if (!isInstanced) {
            isInstanced = true
            instances = InstanceBufferObject(isStatic, maxInstances, *attributes)
        } else {
            throw GdxRuntimeException(
                "Trying to enable InstancedRendering on same Mesh instance twice."
                        + " Use disableInstancedRendering to clean up old InstanceData first"
            )
        }
        return this
    }

    fun disableInstancedRendering(): Mesh {
        if (isInstanced) {
            isInstanced = false
            instances!!.dispose()
            instances = null
        }
        return this
    }

    /**
     * Sets the instance data of this Mesh. The attributes are assumed to be given in float format.
     *
     * @param instanceData the instance data.
     * @param offset       the offset into the vertices array
     * @param count        the number of floats to use
     * @return the mesh for invocation chaining.
     */
    fun setInstanceData(instanceData: FloatArray?, offset: Int, count: Int): Mesh {
        if (instances != null) {
            this.instances!!.setInstanceData(instanceData!!, offset, count)
        } else {
            throw GdxRuntimeException("An InstanceBufferObject must be set before setting instance data!")
        }
        return this
    }

    /**
     * Sets the instance data of this Mesh. The attributes are assumed to be given in float format.
     *
     * @param instanceData the instance data.
     * @return the mesh for invocation chaining.
     */
    fun setInstanceData(instanceData: FloatArray): Mesh {
        if (instances != null) {
            this.instances!!.setInstanceData(instanceData, 0, instanceData.size)
        } else {
            throw GdxRuntimeException("An InstanceBufferObject must be set before setting instance data!")
        }
        return this
    }

    /**
     * Sets the instance data of this Mesh. The attributes are assumed to be given in float format.
     *
     * @param instanceData the instance data.
     * @param count        the number of floats to use
     * @return the mesh for invocation chaining.
     */
    fun setInstanceData(instanceData: FloatBuffer?, count: Int): Mesh {
        if (instances != null) {
            this.instances!!.setInstanceData(instanceData, count)
        } else {
            throw GdxRuntimeException("An InstanceBufferObject must be set before setting instance data!")
        }
        return this
    }

    /**
     * Sets the instance data of this Mesh. The attributes are assumed to be given in float format.
     *
     * @param instanceData the instance data.
     * @return the mesh for invocation chaining.
     */
    fun setInstanceData(instanceData: FloatBuffer): Mesh {
        if (instances != null) {
            this.instances!!.setInstanceData(instanceData, instanceData.limit())
        } else {
            throw GdxRuntimeException("An InstanceBufferObject must be set before setting instance data!")
        }

        return this
    }

    /**
     * Update (a portion of) the instance data. Does not resize the backing buffer.
     *
     * @param targetOffset the offset in number of floats of the mesh part.
     * @param source       the instance data to update the mesh part with
     */
    fun updateInstanceData(targetOffset: Int, source: FloatArray): Mesh {
        return updateInstanceData(targetOffset, source, 0, source.size)
    }

    /**
     * Update (a portion of) the instance data. Does not resize the backing buffer.
     *
     * @param targetOffset the offset in number of floats of the mesh part.
     * @param source       the instance data to update the mesh part with
     * @param sourceOffset the offset in number of floats within the source array
     * @param count        the number of floats to update
     */
    fun updateInstanceData(targetOffset: Int, source: FloatArray?, sourceOffset: Int, count: Int): Mesh {
        this.instances!!.updateInstanceData(targetOffset, source, sourceOffset, count)
        return this
    }

    /**
     * Update (a portion of) the instance data. Does not resize the backing buffer.
     *
     * @param targetOffset the offset in number of floats of the mesh part.
     * @param source       the instance data to update the mesh part with
     * @param sourceOffset the offset in number of floats within the source array
     * @param count        the number of floats to update
     */
    /**
     * Update (a portion of) the instance data. Does not resize the backing buffer.
     *
     * @param targetOffset the offset in number of floats of the mesh part.
     * @param source       the instance data to update the mesh part with
     */
    @JvmOverloads
    fun updateInstanceData(
        targetOffset: Int,
        source: FloatBuffer?,
        sourceOffset: Int = 0,
        count: Int = source!!.limit()
    ): Mesh {
        this.instances!!.updateInstanceData(targetOffset, source, sourceOffset, count)
        return this
    }

    /**
     * Sets the vertices of this Mesh. The attributes are assumed to be given in float format.
     *
     * @param vertices the vertices.
     * @return the mesh for invocation chaining.
     */
    fun setVertices(vertices: FloatArray): Mesh {
        this.vertices.setVertices(vertices, 0, vertices.size)

        return this
    }

    /**
     * Sets the vertices of this Mesh. The attributes are assumed to be given in float format.
     *
     * @param vertices the vertices.
     * @param offset   the offset into the vertices array
     * @param count    the number of floats to use
     * @return the mesh for invocation chaining.
     */
    fun setVertices(vertices: FloatArray?, offset: Int, count: Int): Mesh {
        this.vertices.setVertices(vertices!!, offset, count)

        return this
    }

    /**
     * Update (a portion of) the vertices. Does not resize the backing buffer.
     *
     * @param targetOffset the offset in number of floats of the mesh part.
     * @param source       the vertex data to update the mesh part with
     */
    fun updateVertices(targetOffset: Int, source: FloatArray): Mesh {
        return updateVertices(targetOffset, source, 0, source.size)
    }

    /**
     * Update (a portion of) the vertices. Does not resize the backing buffer.
     *
     * @param targetOffset the offset in number of floats of the mesh part.
     * @param source       the vertex data to update the mesh part with
     * @param sourceOffset the offset in number of floats within the source array
     * @param count        the number of floats to update
     */
    fun updateVertices(targetOffset: Int, source: FloatArray?, sourceOffset: Int, count: Int): Mesh {
        this.vertices.updateVertices(targetOffset, source!!, sourceOffset, count)
        return this
    }

    /**
     * Copies the vertices from the Mesh to the float array. The float array must be large enough to hold all the Mesh's
     * vertices.
     *
     * @param vertices the array to copy the vertices to
     */
    fun getVertices(vertices: FloatArray): FloatArray {
        return getVertices(0, -1, vertices)
    }

    /**
     * Copies the the remaining vertices from the Mesh to the float array. The float array must be large enough to hold
     * the remaining vertices.
     *
     * @param srcOffset the offset (in number of floats) of the vertices in the mesh to copy
     * @param vertices  the array to copy the vertices to
     */
    fun getVertices(srcOffset: Int, vertices: FloatArray): FloatArray {
        return getVertices(srcOffset, -1, vertices)
    }

    /**
     * Copies the specified vertices from the Mesh to the float array. The float array must be large enough to hold
     * count vertices.
     *
     * @param srcOffset the offset (in number of floats) of the vertices in the mesh to copy
     * @param count     the amount of floats to copy
     * @param vertices  the array to copy the vertices to
     */
    fun getVertices(srcOffset: Int, count: Int, vertices: FloatArray): FloatArray {
        return getVertices(srcOffset, count, vertices, 0)
    }

    /**
     * Copies the specified vertices from the Mesh to the float array. The float array must be large enough to hold
     * destOffset+count vertices.
     *
     * @param srcOffset  the offset (in number of floats) of the vertices in the mesh to copy
     * @param count      the amount of floats to copy
     * @param vertices   the array to copy the vertices to
     * @param destOffset the offset (in floats) in the vertices array to start copying
     */
    fun getVertices(srcOffset: Int, count: Int, vertices: FloatArray, destOffset: Int): FloatArray {
        // TODO: Perhaps this method should be vertexSize aware??
        var count = count
        val max = this.numVertices * this.vertexSize / 4
        if (count == -1) {
            count = max - srcOffset
            if (count > vertices.size - destOffset) {
                count = vertices.size - destOffset
            }
        }
        if (srcOffset < 0 || count <= 0 || (srcOffset + count) > max || destOffset < 0 || destOffset >= vertices.size) {
            throw IndexOutOfBoundsException()
        }
        require((vertices.size - destOffset) >= count) { "not enough room in vertices array, has " + vertices.size + " floats, needs " + count }
        val pos = this.verticesBuffer.position()
        (this.verticesBuffer as Buffer).position(srcOffset)
        this.verticesBuffer.get(vertices, destOffset, count)
        (this.verticesBuffer as Buffer).position(pos)
        return vertices
    }

    /**
     * Sets the indices of this Mesh
     *
     * @param indices the indices
     * @return the mesh for invocation chaining.
     */
    fun setIndices(indices: IntArray): Mesh {
        this.indices.setIndices(indices, 0, indices.size)

        return this
    }

    /**
     * Sets the indices of this Mesh.
     *
     * @param indices the indices
     * @param offset  the offset into the indices array
     * @param count   the number of indices to copy
     * @return the mesh for invocation chaining.
     */
    fun setIndices(indices: IntArray?, offset: Int, count: Int): Mesh {
        this.indices.setIndices(indices, offset, count)

        return this
    }

    /**
     * Copies the indices from the Mesh to the int array. The int array must be large enough to hold all the Mesh's
     * indices.
     *
     * @param indices the array to copy the indices to
     */
    fun getIndices(indices: IntArray) {
        getIndices(indices, 0)
    }

    /**
     * Copies the indices from the Mesh to the int array. The int array must be large enough to hold destOffset + all
     * the Mesh's indices.
     *
     * @param indices    the array to copy the indices to
     * @param destOffset the offset in the indices array to start copying
     */
    fun getIndices(indices: IntArray, destOffset: Int) {
        getIndices(0, indices, destOffset)
    }

    /**
     * Copies the remaining indices from the Mesh to the int array. The int array must be large enough to hold
     * destOffset + all the remaining indices.
     *
     * @param srcOffset  the zero-based offset of the first index to fetch
     * @param indices    the array to copy the indices to
     * @param destOffset the offset in the indices array to start copying
     */
    fun getIndices(srcOffset: Int, indices: IntArray, destOffset: Int) {
        getIndices(srcOffset, -1, indices, destOffset)
    }

    /**
     * Copies the indices from the Mesh to the int array. The int array must be large enough to hold destOffset + count
     * indices.
     *
     * @param srcOffset  the zero-based offset of the first index to fetch
     * @param count      the total amount of indices to copy
     * @param indices    the array to copy the indices to
     * @param destOffset the offset in the indices array to start copying
     */
    fun getIndices(srcOffset: Int, count: Int, indices: IntArray, destOffset: Int) {
        var count = count
        val max = this.numIndices
        if (count < 0) {
            count = max - srcOffset
        }
        require(!(srcOffset < 0 || srcOffset >= max || srcOffset + count > max)) { "Invalid range specified, offset: " + srcOffset + ", count: " + count + ", max: " + max }
        require((indices.size - destOffset) >= count) { "not enough room in indices array, has " + indices.size + " int, needs " + count }
        val pos = this.indicesBuffer.position()
        (this.indicesBuffer as Buffer).position(srcOffset)
        this.indicesBuffer.get(indices, destOffset, count)
        (this.indicesBuffer as Buffer).position(pos)
    }

    val numIndices: Int
        /**
         * @return the number of defined indices
         */
        get() = indices.numIndices

    val numVertices: Int
        /**
         * @return the number of defined vertices
         */
        get() = vertices.getNumVertices()

    val maxVertices: Int
        /**
         * @return the maximum number of vertices this mesh can hold
         */
        get() = vertices.getNumMaxVertices()

    val maxIndices: Int
        /**
         * @return the maximum number of indices this mesh can hold
         */
        get() = indices.numMaxIndices

    val vertexSize: Int
        /**
         * @return the size of a single vertex in bytes
         */
        get() = vertices.getAttributes().vertexSize

    /**
     * Binds the underlying [VertexBufferObject] and [IndexBufferObject] if indices where given. Use this
     * with OpenGL ES 2.0 and when auto-bind is disabled.
     *
     * @param shader    the shader (does not bind the shader)
     * @param locations array containing the attribute locations.
     */
    /**
     * Binds the underlying [VertexBufferObject] and [IndexBufferObject] if indices where given. Use this
     * with OpenGL ES 2.0 and when auto-bind is disabled.
     *
     * @param shader the shader (does not bind the shader)
     */
    @JvmOverloads
    fun bind(shader: ShaderProgram?, locations: IntArray? = null) {
        vertices.bind(shader, locations)
        if (instances != null && instances!!.getNumInstances() > 0) {
            instances!!.bind(shader, locations)
        }
        if (indices.numIndices > 0) {
            indices.bind()
        }
    }

    /**
     * Unbinds the underlying [VertexBufferObject] and [IndexBufferObject] is indices were given. Use this
     * with OpenGL ES 1.x and when auto-bind is disabled.
     *
     * @param shader    the shader (does not unbind the shader)
     * @param locations array containing the attribute locations.
     */
    /**
     * Unbinds the underlying [VertexBufferObject] and [IndexBufferObject] is indices were given. Use this
     * with OpenGL ES 1.x and when auto-bind is disabled.
     *
     * @param shader the shader (does not unbind the shader)
     */
    @JvmOverloads
    fun unbind(shader: ShaderProgram?, locations: IntArray? = null) {
        vertices.unbind(shader, locations)
        if (instances != null && instances!!.getNumInstances() > 0) {
            instances!!.unbind(shader, locations)
        }
        if (indices.numIndices > 0) {
            indices.unbind()
        }
    }

    /**
     *
     *
     * Renders the mesh using the given primitive type. offset specifies the offset into either the vertex buffer or the
     * index buffer depending on whether indices are defined. count specifies the number of vertices or indices to use
     * thus count / #vertices per primitive primitives are rendered.
     *
     *
     *
     *
     * This method will automatically bind each vertex attribute as specified at construction time via
     * [VertexAttributes] to the respective shader attributes. The binding is based on the alias defined for each
     * VertexAttribute.
     *
     *
     *
     *
     * This method must only be called after the [ShaderProgram.bind] method has been called!
     *
     *
     *
     *
     * This method is intended for use with OpenGL ES 2.0 and will throw an IllegalStateException when OpenGL ES 1.x is
     * used.
     *
     *
     * @param shader        the shader to be used
     * @param primitiveType the primitive type
     * @param offset        the offset into the vertex or index buffer
     * @param count         number of vertices or indices to use
     * @param autoBind      overrides the autoBind member of this Mesh
     */
    /**
     *
     *
     * Renders the mesh using the given primitive type. If indices are set for this mesh then getNumIndices() /
     * #vertices per primitive primitives are rendered. If no indices are set then getNumVertices() / #vertices per
     * primitive are rendered.
     *
     *
     *
     *
     * This method will automatically bind each vertex attribute as specified at construction time via
     * [VertexAttributes] to the respective shader attributes. The binding is based on the alias defined for each
     * VertexAttribute.
     *
     *
     *
     *
     * This method must only be called after the [ShaderProgram.bind] method has been called!
     *
     *
     *
     *
     * This method is intended for use with OpenGL ES 2.0 and will throw an IllegalStateException when OpenGL ES 1.x is
     * used.
     *
     *
     * @param primitiveType the primitive type
     */
    @JvmOverloads
    fun render(
        shader: ShaderProgram?,
        primitiveType: Int,
        offset: Int = 0,
        count: Int = if (indices.numMaxIndices > 0) this.numIndices else this.numVertices,
        autoBind: Boolean = this.autoBind
    ) {
        if (count == 0) {
            return
        }

        if (autoBind) {
            bind(shader)
        }

        draw(primitiveType, offset, count)

        if (autoBind) {
            unbind(shader)
        }
    }

    private fun draw(primitiveType: Int, offset: Int, count: Int) {
        var numInstances = 0
        if (isInstanced) {
            numInstances = instances!!.getNumInstances()
        }

        if (indices.numIndices > 0) {
            if (count + offset > indices.numMaxIndices) {
                throw GdxRuntimeException(
                    ("Mesh attempting to access memory outside of the index buffer (count: " + count
                            + ", offset: " + offset + ", max: " + indices.numMaxIndices + ")")
                )
            }

            if (isInstanced && numInstances > 0) {
                Gdx.gl30.glDrawElementsInstanced(
                    primitiveType, count, GL20.GL_UNSIGNED_INT,
                    offset * CoreConst.BYTES_IN_VERTEX_COORD, numInstances
                )
            } else {
                Gdx.gl20.glDrawElements(
                    primitiveType, count, GL20.GL_UNSIGNED_INT,
                    offset * CoreConst.BYTES_IN_VERTEX_COORD
                )
            }
        } else {
            if (isInstanced && numInstances > 0) {
                Gdx.gl30.glDrawArraysInstanced(primitiveType, offset, count, numInstances)
            } else {
                Gdx.gl20.glDrawArrays(primitiveType, offset, count)
            }
        }
    }

    /**
     * Frees all resources associated with this Mesh
     */
    override fun dispose() {
        vertices.dispose()
        if (instances != null) {
            instances!!.dispose()
        }
        indices.dispose()
    }

    /**
     * Returns the first [VertexAttribute] having the given [Usage].
     *
     * @param usage the Usage.
     * @return the VertexAttribute or null if no attribute with that usage was found.
     */
    fun getVertexAttribute(usage: Int): VertexAttribute? {
        val attributes = vertices.getAttributes()
        val len = attributes.size()
        for (i in 0..<len) {
            if (attributes.get(i).usage == usage) {
                return attributes.get(i)
            }
        }

        return null
    }

    val vertexAttributes: VertexAttributes
        /**
         * @return the vertex attributes of this Mesh
         */
        get() = vertices.getAttributes()

    val verticesBuffer: FloatBuffer
        /**
         * @return the backing FloatBuffer holding the vertices. Does not have to be a direct buffer on Android!
         */
        get() = vertices.getBuffer(true)

    /**
     * Calculates the [BoundingBox] of the vertices contained in this mesh. In case no vertices are defined yet a
     * [GdxRuntimeException] is thrown. This method creates a new BoundingBox instance.
     *
     * @return the bounding box.
     */
    fun calculateBoundingBox(): BoundingBox {
        val bbox = BoundingBox()
        calculateBoundingBox(bbox)
        return bbox
    }

    /**
     * Calculates the [BoundingBox] of the vertices contained in this mesh. In case no vertices are defined yet a
     * [GdxRuntimeException] is thrown.
     *
     * @param bbox the bounding box to store the result in.
     */
    fun calculateBoundingBox(bbox: BoundingBox) {
        val numVertices = this.numVertices
        if (numVertices == 0) {
            throw GdxRuntimeException("No vertices defined")
        }

        val verts = vertices.getBuffer(false)
        bbox.inf()
        val posAttrib = getVertexAttribute(VertexAttributes.Usage.Position)
        val offset = posAttrib!!.offset / 4
        val vertexSize = vertices.getAttributes().vertexSize / 4
        var idx = offset

        when (posAttrib.numComponents) {
            1 -> {
                var i = 0
                while (i < numVertices) {
                    bbox.ext(verts.get(idx), 0f, 0f)
                    idx += vertexSize
                    i++
                }
            }

            2 -> {
                var i = 0
                while (i < numVertices) {
                    bbox.ext(verts.get(idx), verts.get(idx + 1), 0f)
                    idx += vertexSize
                    i++
                }
            }

            3 -> {
                var i = 0
                while (i < numVertices) {
                    bbox.ext(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2))
                    idx += vertexSize
                    i++
                }
            }
        }
    }

    /**
     * Calculate the [BoundingBox] of the specified part.
     *
     * @param out    the bounding box to store the result in.
     * @param offset the start index of the part.
     * @param count  the amount of indices the part contains.
     * @return the value specified by out.
     */
    fun calculateBoundingBox(out: BoundingBox, offset: Int, count: Int): BoundingBox {
        return extendBoundingBox(out.inf(), offset, count)
    }

    /**
     * Calculate the [BoundingBox] of the specified part.
     *
     * @param out    the bounding box to store the result in.
     * @param offset the start index of the part.
     * @param count  the amount of indices the part contains.
     * @return the value specified by out.
     */
    fun calculateBoundingBox(out: BoundingBox, offset: Int, count: Int, transform: Matrix4?): BoundingBox {
        return extendBoundingBox(out.inf(), offset, count, transform)
    }

    private val tmpV = Vector3()

    /**
     * Extends the specified [BoundingBox] with the specified part.
     *
     * @param out    the bounding box to store the result in.
     * @param offset the start of the part.
     * @param count  the size of the part.
     * @return the value specified by out.
     */
    /**
     * Extends the specified [BoundingBox] with the specified part.
     *
     * @param out    the bounding box to store the result in.
     * @param offset the start index of the part.
     * @param count  the amount of indices the part contains.
     * @return the value specified by out.
     */
    @JvmOverloads
    fun extendBoundingBox(out: BoundingBox, offset: Int, count: Int, transform: Matrix4? = null): BoundingBox {
        val numIndices = this.numIndices
        val numVertices = this.numVertices
        val max = if (numIndices == 0) numVertices else numIndices
        if (offset < 0 || count < 1 || offset + count > max) {
            throw GdxRuntimeException(
                "Invalid part specified ( offset=" + offset + ", count=" + count + ", max=" + max + " )"
            )
        }

        val verts = vertices.getBuffer(false)
        val index = indices.buffer
        val posAttrib = getVertexAttribute(VertexAttributes.Usage.Position)
        val posoff = posAttrib!!.offset / 4
        val vertexSize = vertices.getAttributes().vertexSize / 4
        val end = offset + count

        when (posAttrib.numComponents) {
            1 -> if (numIndices > 0) {
                var i = offset
                while (i < end) {
                    val idx = index.get(i) * vertexSize + posoff
                    tmpV.set(verts.get(idx), 0f, 0f)
                    if (transform != null) {
                        tmpV.mul(transform)
                    }
                    out.ext(tmpV)
                    i++
                }
            } else {
                var i = offset
                while (i < end) {
                    val idx = i * vertexSize + posoff
                    tmpV.set(verts.get(idx), 0f, 0f)
                    if (transform != null) {
                        tmpV.mul(transform)
                    }
                    out.ext(tmpV)
                    i++
                }
            }

            2 -> if (numIndices > 0) {
                var i = offset
                while (i < end) {
                    val idx = index.get(i) * vertexSize + posoff
                    tmpV.set(verts.get(idx), verts.get(idx + 1), 0f)
                    if (transform != null) {
                        tmpV.mul(transform)
                    }
                    out.ext(tmpV)
                    i++
                }
            } else {
                var i = offset
                while (i < end) {
                    val idx = i * vertexSize + posoff
                    tmpV.set(verts.get(idx), verts.get(idx + 1), 0f)
                    if (transform != null) {
                        tmpV.mul(transform)
                    }
                    out.ext(tmpV)
                    i++
                }
            }

            3 -> if (numIndices > 0) {
                var i = offset
                while (i < end) {
                    val idx = index.get(i) * vertexSize + posoff
                    tmpV.set(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2))
                    if (transform != null) {
                        tmpV.mul(transform)
                    }
                    out.ext(tmpV)
                    i++
                }
            } else {
                var i = offset
                while (i < end) {
                    val idx = i * vertexSize + posoff
                    tmpV.set(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2))
                    if (transform != null) {
                        tmpV.mul(transform)
                    }
                    out.ext(tmpV)
                    i++
                }
            }
        }
        return out
    }

    /**
     * Calculates the squared radius of the bounding sphere around the specified center for the specified part.
     *
     * @param centerX The X coordinate of the center of the bounding sphere
     * @param centerY The Y coordinate of the center of the bounding sphere
     * @param centerZ The Z coordinate of the center of the bounding sphere
     * @param offset  the start index of the part.
     * @param count   the amount of indices the part contains.
     * @return the squared radius of the bounding sphere.
     */
    fun calculateRadiusSquared(
        centerX: Float, centerY: Float, centerZ: Float, offset: Int,
        count: Int,
        transform: Matrix4?
    ): Float {
        val numIndices = this.numIndices
        if (offset < 0 || count < 1 || offset + count > numIndices) {
            throw GdxRuntimeException("Not enough indices")
        }

        val verts = vertices.getBuffer(false)
        val index = indices.buffer
        val posAttrib = getVertexAttribute(VertexAttributes.Usage.Position)
        val posoff = posAttrib!!.offset / 4
        val vertexSize = vertices.getAttributes().vertexSize / 4
        val end = offset + count

        var result = 0f

        when (posAttrib.numComponents) {
            1 -> {
                var i = offset
                while (i < end) {
                    val idx = index.get(i) * vertexSize + posoff
                    tmpV.set(verts.get(idx), 0f, 0f)
                    if (transform != null) {
                        tmpV.mul(transform)
                    }
                    val r = tmpV.sub(centerX, centerY, centerZ).len2()
                    if (r > result) {
                        result = r
                    }
                    i++
                }
            }

            2 -> {
                var i = offset
                while (i < end) {
                    val idx = index.get(i) * vertexSize + posoff
                    tmpV.set(verts.get(idx), verts.get(idx + 1), 0f)
                    if (transform != null) {
                        tmpV.mul(transform)
                    }
                    val r = tmpV.sub(centerX, centerY, centerZ).len2()
                    if (r > result) {
                        result = r
                    }
                    i++
                }
            }

            3 -> {
                var i = offset
                while (i < end) {
                    val idx = index.get(i) * vertexSize + posoff
                    tmpV.set(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2))
                    if (transform != null) {
                        tmpV.mul(transform)
                    }
                    val r = tmpV.sub(centerX, centerY, centerZ).len2()
                    if (r > result) {
                        result = r
                    }
                    i++
                }
            }
        }
        return result
    }

    /**
     * Calculates the radius of the bounding sphere around the specified center for the specified part.
     *
     * @param centerX The X coordinate of the center of the bounding sphere
     * @param centerY The Y coordinate of the center of the bounding sphere
     * @param centerZ The Z coordinate of the center of the bounding sphere
     * @param offset  the start index of the part.
     * @param count   the amount of indices the part contains.
     * @return the radius of the bounding sphere.
     */
    /**
     * Calculates the squared radius of the bounding sphere around the specified center for the specified part.
     *
     * @param centerX The X coordinate of the center of the bounding sphere
     * @param centerY The Y coordinate of the center of the bounding sphere
     * @param centerZ The Z coordinate of the center of the bounding sphere
     * @return the squared radius of the bounding sphere.
     */
    /**
     * Calculates the squared radius of the bounding sphere around the specified center for the specified part.
     *
     * @param centerX The X coordinate of the center of the bounding sphere
     * @param centerY The Y coordinate of the center of the bounding sphere
     * @param centerZ The Z coordinate of the center of the bounding sphere
     * @param offset  the start index of the part.
     * @param count   the amount of indices the part contains.
     * @return the squared radius of the bounding sphere.
     */
    @JvmOverloads
    fun calculateRadius(
        centerX: Float, centerY: Float, centerZ: Float, offset: Int = 0, count: Int = this.numIndices,
        transform: Matrix4? = null
    ): Float {
        return sqrt(calculateRadiusSquared(centerX, centerY, centerZ, offset, count, transform).toDouble()).toFloat()
    }

    /**
     * Calculates the squared radius of the bounding sphere around the specified center for the specified part.
     *
     * @param center The center of the bounding sphere
     * @param offset the start index of the part.
     * @param count  the amount of indices the part contains.
     * @return the squared radius of the bounding sphere.
     */
    fun calculateRadius(center: Vector3, offset: Int, count: Int, transform: Matrix4?): Float {
        return calculateRadius(center.x, center.y, center.z, offset, count, transform)
    }

    /**
     * Calculates the squared radius of the bounding sphere around the specified center for the specified part.
     *
     * @param center The center of the bounding sphere
     * @param offset the start index of the part.
     * @param count  the amount of indices the part contains.
     * @return the squared radius of the bounding sphere.
     */
    fun calculateRadius(center: Vector3, offset: Int, count: Int): Float {
        return calculateRadius(center.x, center.y, center.z, offset, count, null)
    }

    /**
     * Calculates the squared radius of the bounding sphere around the specified center for the specified part.
     *
     * @param center The center of the bounding sphere
     * @return the squared radius of the bounding sphere.
     */
    fun calculateRadius(center: Vector3): Float {
        return calculateRadius(center.x, center.y, center.z, 0, this.numIndices, null)
    }

    val indicesBuffer: IntBuffer
        /**
         * @return the backing IntBuffer holding the indices. Does not have to be a direct buffer on Android!
         */
        get() = indices.buffer

}
