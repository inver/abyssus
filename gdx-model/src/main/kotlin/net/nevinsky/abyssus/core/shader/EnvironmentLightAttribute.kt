/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.shader

import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.g3d.Attribute

/**
 * Image based light for an [com.badlogic.gdx.graphics.g3d.Environment]: a diffuse [irradiance] cube, a [specular] cube
 * with [levels] mips (level n prefiltered for GGX roughness n / (levels - 1)), and the irradiance in the six axis
 * directions as [ambient] (+X, -X, +Y, -Y, +Z, -Z, three floats each).
 *
 * Set it in place of [com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.AmbientLight]: [DefaultShader] then takes
 * its ambient cubemap from [ambient], and [PbrShader] compiles with `environmentLightFlag` and samples both cubes. An
 * environment without it renders exactly as before.
 */
class EnvironmentLightAttribute(
    val specular: GLTexture,
    val irradiance: GLTexture,
    val levels: Int,
    val ambient: FloatArray,
) : Attribute(Type) {
    init {
        require(ambient.size == 18) { "ambient needs 6 RGB colors" }
    }

    override fun copy(): Attribute = EnvironmentLightAttribute(specular, irradiance, levels, ambient.copyOf())

    override fun hashCode(): Int {
        var result = 31 * super.hashCode() + specular.textureObjectHandle
        result = 31 * result + irradiance.textureObjectHandle
        result = 31 * result + levels
        return 31 * result + ambient.contentHashCode()
    }

    override fun equals(other: Any?): Boolean =
        other is EnvironmentLightAttribute && other.specular === specular && other.irradiance === irradiance &&
            other.levels == levels && other.ambient.contentEquals(ambient)

    override fun compareTo(other: Attribute): Int {
        if (type != other.type) return type.compareTo(other.type)
        val o = other as EnvironmentLightAttribute
        return compareValuesBy(this, o, { it.specular.textureObjectHandle }, { it.irradiance.textureObjectHandle }, { it.levels })
    }

    companion object {
        const val Alias = "environmentLight"

        @JvmField
        val Type: Long = register(Alias)

        fun has(attributes: com.badlogic.gdx.graphics.g3d.Attributes?): Boolean = attributes?.has(Type) == true
    }
}
