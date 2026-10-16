/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Matrix4

/**
 * The vertex attributes an instance carries its `mat4` world transform in, for stock `Mesh.enableInstancedRendering`.
 * The shaders declare one `attribute mat4 a_instance;`. A `mat4` attribute takes four consecutive locations, and stock
 * `InstanceBufferObject.bind` binds each attribute at `location + attribute.unit`, so the matrix is four `vec4`
 * attributes with the same alias and the units 0 to 3.
 */
object InstanceAttributes {
    /** The alias the shaders declare as `attribute mat4`. */
    const val ALIAS: String = "a_instance"

    /** The columns of a `mat4`, one attribute each. */
    const val COLUMNS: Int = 4

    /** The floats one instance transform takes. */
    const val FLOATS: Int = 16

    /** The attributes to hand to `Mesh.enableInstancedRendering`: four columns, units 0 to 3. */
    fun of(): Array<VertexAttribute> = Array(COLUMNS) { column ->
        VertexAttribute(VertexAttributes.Usage.Generic, 4, com.badlogic.gdx.graphics.GL20.GL_FLOAT, false, ALIAS, column)
    }

    /** Copies [matrix] into [target] at [offset]; `Matrix4.val` is already the column-major layout `mat4` reads. */
    fun write(matrix: Matrix4, target: FloatArray, offset: Int) {
        require(offset >= 0 && offset + FLOATS <= target.size) {
            "an instance transform needs $FLOATS floats at $offset, the array holds ${target.size}"
        }
        matrix.`val`.copyInto(target, offset)
    }
}
