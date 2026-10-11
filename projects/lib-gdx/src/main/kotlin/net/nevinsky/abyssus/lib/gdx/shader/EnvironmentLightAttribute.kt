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
 * While one environment is replaced by another, [next] holds the incoming one (with the same [levels]) and [blend] how far
 * (0 to 1) the light is from this one to it; the shaders mix the two. Without [next], or at blend 0, nothing is mixed.
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
    val next: Next? = null,
    blend: Float = 0f,
) : Attribute(Type) {
    /** The environment being faded in: its cubes and six axis colors. */
    class Next(val specular: GLTexture, val irradiance: GLTexture, val ambient: FloatArray) {
        init {
            require(ambient.size == 18) { "ambient needs 6 RGB colors" }
        }
    }

    /** How far the light is from this environment (0) to [next] (1); 0 without a next one. */
    val blend: Float = if (next == null) 0f else blend.coerceIn(0f, 1f)

    init {
        require(ambient.size == 18) { "ambient needs 6 RGB colors" }
    }

    /** The six axis colors of the light now: [ambient] mixed toward the next environment's by [blend]. */
    val blendedAmbient: FloatArray by lazy(LazyThreadSafetyMode.NONE) {
        val to = next?.ambient
        if (to == null || blend <= 0f) ambient else FloatArray(18) { ambient[it] + (to[it] - ambient[it]) * blend }
    }

    override fun copy(): Attribute = EnvironmentLightAttribute(
        specular, irradiance, levels, ambient.copyOf(), next?.let { Next(it.specular, it.irradiance, it.ambient.copyOf()) }, blend
    )

    override fun hashCode(): Int {
        var result = 31 * super.hashCode() + specular.textureObjectHandle
        result = 31 * result + irradiance.textureObjectHandle
        result = 31 * result + levels
        result = 31 * result + ambient.contentHashCode()
        result = 31 * result + (next?.irradiance?.textureObjectHandle ?: 0)
        return 31 * result + blend.hashCode()
    }

    override fun equals(other: Any?): Boolean =
        other is EnvironmentLightAttribute && other.specular === specular && other.irradiance === irradiance &&
            other.levels == levels && other.ambient.contentEquals(ambient) && other.blend == blend &&
            other.next?.specular === next?.specular && other.next?.irradiance === next?.irradiance &&
            (other.next?.ambient ?: EMPTY).contentEquals(next?.ambient ?: EMPTY)

    override fun compareTo(other: Attribute): Int {
        if (type != other.type) return type.compareTo(other.type)
        val o = other as EnvironmentLightAttribute
        return compareValuesBy(
            this, o, { it.specular.textureObjectHandle }, { it.irradiance.textureObjectHandle }, { it.levels },
            { it.next?.irradiance?.textureObjectHandle ?: 0 }, { it.blend }
        )
    }

    companion object {
        const val Alias = "environmentLight"

        @JvmField
        val Type: Long = register(Alias)

        private val EMPTY = FloatArray(0)

        fun has(attributes: com.badlogic.gdx.graphics.g3d.Attributes?): Boolean = attributes?.has(Type) == true
    }
}
