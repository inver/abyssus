/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.g3d.Attribute

/**
 * A world-space clip plane for a pass: [DefaultShader] and [PbrShader] discard every fragment whose world position `p`
 * has `dot(normal, p) + offset < 0`. It is an environment attribute that a pass sets while it draws and removes after,
 * so a scene that never sets it renders exactly as before (the shaders compile the clip test only when the attribute is
 * present). Done in the fragment shader, not with platform clip planes, which the GL 3.2 core canvas does not offer.
 */
class ClipPlaneAttribute(val normalX: Float, val normalY: Float, val normalZ: Float, val offset: Float) : Attribute(Type) {
    /** The plane as `(a, b, c, d)` of `a x + b y + c z + d >= 0` (the side that stays). */
    constructor(plane: FloatArray) : this(plane[0], plane[1], plane[2], plane[3]) {
        require(plane.size == 4) { "a plane is four numbers" }
    }

    override fun copy(): Attribute = ClipPlaneAttribute(normalX, normalY, normalZ, offset)

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + normalX.hashCode()
        result = 31 * result + normalY.hashCode()
        result = 31 * result + normalZ.hashCode()
        return 31 * result + offset.hashCode()
    }

    override fun equals(other: Any?): Boolean = other is ClipPlaneAttribute &&
        other.normalX == normalX && other.normalY == normalY && other.normalZ == normalZ && other.offset == offset

    override fun compareTo(other: Attribute): Int {
        if (type != other.type) return type.compareTo(other.type)
        val o = other as ClipPlaneAttribute
        return compareValuesBy(this, o, { it.normalX }, { it.normalY }, { it.normalZ }, { it.offset })
    }

    companion object {
        const val Alias = "clipPlane"

        @JvmField
        val Type: Long = register(Alias)
    }
}
