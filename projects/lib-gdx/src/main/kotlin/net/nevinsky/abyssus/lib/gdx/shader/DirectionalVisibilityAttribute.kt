/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.math.Matrix4

/**
 * A map of how much of one directional light reaches each place, for [DefaultShader] and [PbrShader]: the red channel of
 * [texture] (0 dark to 1 lit) sampled at the fragment's world position transformed by [worldToMap] (x and y give the
 * texture coordinates; a position outside 0 to 1 is outside the map). Only the directional light number [lightIndex] of
 * the environment (in the order its lights are added) is multiplied by it, together with that light's own shadow;
 * every other light is untouched. Within [edgeFade] (a share of the map's width) of its edge the map fades into
 * [outside], the visibility used beyond it. It names no source (clouds, say), so the library stays reusable.
 */
class DirectionalVisibilityAttribute(
    val texture: GLTexture,
    val lightIndex: Int,
    worldToMap: Matrix4,
    val edgeFade: Float = 0.1f,
    val outside: Float = 1f,
) : Attribute(Type) {
    val worldToMap: Matrix4 = Matrix4(worldToMap)

    init {
        require(lightIndex >= 0) { "lightIndex must not be negative" }
        require(edgeFade > 0f && edgeFade <= 0.5f) { "edgeFade must be in (0, 0.5]" }
    }

    override fun copy(): Attribute = DirectionalVisibilityAttribute(texture, lightIndex, worldToMap, edgeFade, outside)

    override fun hashCode(): Int {
        var result = 31 * super.hashCode() + texture.textureObjectHandle
        result = 31 * result + lightIndex
        result = 31 * result + worldToMap.`val`.contentHashCode()
        result = 31 * result + edgeFade.hashCode()
        return 31 * result + outside.hashCode()
    }

    override fun equals(other: Any?): Boolean =
        other is DirectionalVisibilityAttribute && other.texture === texture && other.lightIndex == lightIndex &&
            other.worldToMap.`val`.contentEquals(worldToMap.`val`) && other.edgeFade == edgeFade && other.outside == outside

    override fun compareTo(other: Attribute): Int {
        if (type != other.type) return type.compareTo(other.type)
        val o = other as DirectionalVisibilityAttribute
        return compareValuesBy(this, o, { it.texture.textureObjectHandle }, { it.lightIndex }, { it.outside }, { it.edgeFade })
    }

    companion object {
        const val Alias = "directionalVisibility"

        @JvmField
        val Type: Long = register(Alias)
    }
}
