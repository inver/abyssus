/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.math.Matrix4

/**
 * A map of how much of one directional light reaches each world position, sampled by [DefaultShader] and [PbrShader]
 * for that light only. [texture] holds the visibility (0 to 1) in its red channel; [worldToMap] takes a world position
 * to map coordinates (x and y in 0 to 1 cover the map); [lightIndex] is the light's index among the shader's
 * directional lights (the order [com.badlogic.gdx.graphics.g3d.Environment] lists them in). Positions beyond the map,
 * and its outer [edgeFade] (a share of the map's width), fade to [outside], so a caller can carry a coarser factor
 * there. The visibility multiplies the light's atlas shadow, if any, and never touches another light or the ambient.
 * It names no source; clouds, foliage or anything else can fill the texture.
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
        require(lightIndex >= 0) { "A directional light index is not negative" }
        require(edgeFade > 0f && edgeFade <= 0.5f) { "The edge fade is a share of the map, above 0 and at most 0.5" }
        require(outside in 0f..1f) { "The visibility outside the map is 0 to 1" }
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
        return compareValuesBy(this, o, { it.texture.textureObjectHandle }, { it.lightIndex }, { it.edgeFade }, { it.outside })
    }

    companion object {
        const val Alias = "directionalVisibility"

        @JvmField
        val Type: Long = register(Alias)
    }
}
