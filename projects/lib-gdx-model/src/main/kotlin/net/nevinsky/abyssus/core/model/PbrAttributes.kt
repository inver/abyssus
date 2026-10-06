/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor

/*
 * The metallic-roughness material attributes the model and PBR shader use. They mirror gdx-gltf's attributes of the
 * same names (same aliases, so the type bits match), without depending on it. The base color, normal and emissive
 * textures share their aliases with libGDX's diffuse, normal and emissive textures, as in gdx-gltf.
 */

class PBRColorAttribute(type: Long, color: Color) : ColorAttribute(type, color) {
    override fun copy(): Attribute = PBRColorAttribute(type, color)

    companion object {
        @JvmField
        val BaseColorFactor: Long = register("BaseColorFactor")

        init {
            Mask = Mask or BaseColorFactor
        }

        fun createBaseColorFactor(color: Color) = PBRColorAttribute(BaseColorFactor, color)
    }
}

class PBRFloatAttribute(type: Long, value: Float) : FloatAttribute(type, value) {
    override fun copy(): Attribute = PBRFloatAttribute(type, value)

    companion object {
        @JvmField
        val Metallic: Long = register("Metallic")

        @JvmField
        val Roughness: Long = register("Roughness")

        fun createMetallic(value: Float) = PBRFloatAttribute(Metallic, value)

        fun createRoughness(value: Float) = PBRFloatAttribute(Roughness, value)
    }
}

class PBRTextureAttribute : TextureAttribute {
    constructor(type: Long, descriptor: TextureDescriptor<Texture>) : super(type, descriptor)
    constructor(other: PBRTextureAttribute) : super(other)

    override fun copy(): Attribute = PBRTextureAttribute(this)

    companion object {
        @JvmField
        val BaseColorTexture: Long = register("diffuseTexture")

        @JvmField
        val EmissiveTexture: Long = register("emissiveTexture")

        @JvmField
        val NormalTexture: Long = register("normalTexture")

        @JvmField
        val MetallicRoughnessTexture: Long = register("MetallicRoughnessSampler")

        @JvmField
        val OcclusionTexture: Long = register("OcclusionSampler")

        init {
            Mask = Mask or MetallicRoughnessTexture or OcclusionTexture or BaseColorTexture or NormalTexture or EmissiveTexture
        }
    }
}
