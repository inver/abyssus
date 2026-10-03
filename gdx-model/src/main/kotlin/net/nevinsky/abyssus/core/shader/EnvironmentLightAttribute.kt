/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
