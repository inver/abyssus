/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

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
 *
 * An optional second environment, [fadeTo], cross-fades in: the shaders light with the mix of this environment and
 * [fadeTo] by [weight] (0 is this one, 1 is [fadeTo]). [PbrShader] compiles a second cube fetch only for an attribute
 * with [fadeTo], and [DefaultShader] blends the six axis colors on the CPU, so an attribute without it costs nothing.
 */
class EnvironmentLightAttribute(
    val specular: GLTexture,
    val irradiance: GLTexture,
    val levels: Int,
    val ambient: FloatArray,
    val fadeTo: EnvironmentLightAttribute? = null,
    weight: Float = 0f,
) : Attribute(Type) {
    /** How far [fadeTo] has replaced this environment, 0 to 1; 0 without [fadeTo]. */
    val weight: Float = if (fadeTo == null) 0f else weight.coerceIn(0f, 1f)

    init {
        require(ambient.size == 18) { "ambient needs 6 RGB colors" }
        require(fadeTo == null || fadeTo.fadeTo == null) { "only one environment can be faded to" }
    }

    /** Writes the six axis colors this attribute lights with, [fadeTo] mixed in by [weight], to [out]. */
    fun blendAmbientInto(out: FloatArray): FloatArray {
        val to = fadeTo?.ambient
        for (i in 0 until 18) out[i] = if (to == null) ambient[i] else ambient[i] + (to[i] - ambient[i]) * weight
        return out
    }

    override fun copy(): Attribute =
        EnvironmentLightAttribute(specular, irradiance, levels, ambient.copyOf(), fadeTo?.copy() as EnvironmentLightAttribute?, weight)

    override fun hashCode(): Int {
        var result = 31 * super.hashCode() + specular.textureObjectHandle
        result = 31 * result + irradiance.textureObjectHandle
        result = 31 * result + levels
        result = 31 * result + ambient.contentHashCode()
        result = 31 * result + (fadeTo?.hashCode() ?: 0)
        return 31 * result + weight.hashCode()
    }

    override fun equals(other: Any?): Boolean =
        other is EnvironmentLightAttribute && other.specular === specular && other.irradiance === irradiance &&
            other.levels == levels && other.ambient.contentEquals(ambient) && other.fadeTo == fadeTo &&
            other.weight == weight

    override fun compareTo(other: Attribute): Int {
        if (type != other.type) return type.compareTo(other.type)
        val o = other as EnvironmentLightAttribute
        return compareValuesBy(
            this, o, { it.specular.textureObjectHandle }, { it.irradiance.textureObjectHandle }, { it.levels },
            { it.fadeTo?.specular?.textureObjectHandle ?: 0 }, { it.fadeTo?.irradiance?.textureObjectHandle ?: 0 },
            { it.weight },
        )
    }

    companion object {
        const val Alias = "environmentLight"

        @JvmField
        val Type: Long = register(Alias)

        fun has(attributes: com.badlogic.gdx.graphics.g3d.Attributes?): Boolean = attributes?.has(Type) == true

        /** True when [attributes] has an environment light that fades to a second environment. */
        fun isFading(attributes: com.badlogic.gdx.graphics.g3d.Attributes?): Boolean =
            (attributes?.get(Type) as EnvironmentLightAttribute?)?.fadeTo != null
    }
}
