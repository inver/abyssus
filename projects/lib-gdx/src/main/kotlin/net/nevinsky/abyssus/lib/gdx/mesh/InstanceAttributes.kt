/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.mesh

import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Matrix4

/**
 * The four `vec4` vertex attributes an instance carries its `mat4` world transform in. A mesh with them (see
 * [Mesh.enableInstancedRendering]) is drawn by the `instancedFlag` variants of the shaders, which build the world
 * matrix from `mat4(a_instance0, a_instance1, a_instance2, a_instance3)` instead of reading `u_worldTrans`.
 */
object InstanceAttributes {
    /** The attribute aliases, in the column order the shaders' `mat4(...)` constructor takes them. */
    val ALIASES: List<String> = listOf("a_instance0", "a_instance1", "a_instance2", "a_instance3")

    /** The floats one instance transform takes. */
    const val FLOATS: Int = 16

    /** The attributes to hand to [Mesh.enableInstancedRendering]. */
    fun of(): Array<VertexAttribute> = ALIASES.map {
        VertexAttribute(VertexAttributes.Usage.Generic, 4, it)
    }.toTypedArray()

    /** Copies [matrix] into [target] at [offset]; `Matrix4.val` is already the column-major layout `mat4` reads. */
    fun write(matrix: Matrix4, target: FloatArray, offset: Int) {
        require(offset + FLOATS <= target.size) {
            "an instance transform needs $FLOATS floats at $offset, the array holds ${target.size}"
        }
        matrix.`val`.copyInto(target, offset)
    }
}
