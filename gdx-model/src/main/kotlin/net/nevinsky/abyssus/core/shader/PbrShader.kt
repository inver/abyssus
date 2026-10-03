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

import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import net.nevinsky.abyssus.core.Renderable
import net.nevinsky.abyssus.core.model.PBRFloatAttribute
import net.nevinsky.abyssus.core.model.PBRTextureAttribute

/**
 * Metallic-roughness shader. It reads base color, normal and emissive from the same attributes as the
 * [DefaultShader] (a PBR material keeps both) and adds the metallic and roughness factors and the combined
 * metallic-roughness and occlusion textures.
 */
class PbrShader(config: ShaderConfig, renderable: Renderable?) : DefaultShader(withDefaults(config), renderable!!) {
    protected var u_metallicFactor: Int
    protected var u_roughnessFactor: Int
    protected var u_metallicRoughnessTexture: Int
    protected var u_metallicRoughnessUVTransform: Int
    protected var u_occlusionTexture: Int
    protected var u_occlusionUVTransform: Int

    /**
     * @param config the vertex shader defaults to the default vertex shader, the fragment shader to the PBR one
     */
    init {
        u_metallicFactor = registerUniformLocal(
            "u_metallicFactor", PBRFloatAttribute.Metallic,
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    (combinedAttributes!!.get(PBRFloatAttribute.Metallic) as FloatAttribute).value
                )
            })
        u_roughnessFactor = registerUniformLocal(
            "u_roughnessFactor", PBRFloatAttribute.Roughness,
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    (combinedAttributes!!.get(PBRFloatAttribute.Roughness) as FloatAttribute).value
                )
            })

        u_metallicRoughnessTexture = registerTexture(
            "u_metallicRoughnessTexture",
            PBRTextureAttribute.MetallicRoughnessTexture
        )
        u_metallicRoughnessUVTransform = registerUvTransform(
            "u_metallicRoughnessUVTransform",
            PBRTextureAttribute.MetallicRoughnessTexture
        )
        u_occlusionTexture = registerTexture("u_occlusionTexture", PBRTextureAttribute.OcclusionTexture)
        u_occlusionUVTransform = registerUvTransform("u_occlusionUVTransform", PBRTextureAttribute.OcclusionTexture)
        registerUniformLocal("u_envIrradiance", EnvironmentLightAttribute.Type) { shader, inputID, _, attributes ->
            val sky = attributes!!.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute
            shader!!.set(inputID, shader.context!!.textureBinder.bind(sky.irradiance))
        }
        registerUniformLocal("u_envSpecular", EnvironmentLightAttribute.Type) { shader, inputID, _, attributes ->
            val sky = attributes!!.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute
            shader!!.set(inputID, shader.context!!.textureBinder.bind(sky.specular))
        }
        registerUniformLocal("u_envMaxLod", EnvironmentLightAttribute.Type) { shader, inputID, _, attributes ->
            val sky = attributes!!.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute
            shader!!.set(inputID, (sky.levels - 1).toFloat())
        }
    }

    private fun registerTexture(alias: String?, attribute: Long): Int {
        return registerUniformLocal(
            alias, attribute,
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    shader.context!!.textureBinder.bind(
                        (combinedAttributes!!.get(attribute) as TextureAttribute).textureDescription
                    )
                )
            })
    }

    private fun registerUvTransform(alias: String?, attribute: Long): Int {
        return registerUniformLocal(
            alias,
            attribute,
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                val ta = combinedAttributes!!.get(attribute) as TextureAttribute
                shader!!.set(inputID, ta.offsetU, ta.offsetV, ta.scaleU, ta.scaleV)
            })
    }

    override fun preprocessShaderContents(renderable: Renderable) {
        super.preprocessShaderContents(renderable)

        val defines = pbrDefines(renderable.material?.getMask() ?: 0, renderable.environment)
        vertexShader = defines + vertexShader
        fragmentShader = defines + fragmentShader
    }

    companion object {
        /**
         * The `#define` lines this shader adds for a material of [mask] in [environment]; `environmentLightFlag`
         * only when the environment has an [EnvironmentLightAttribute].
         */
        fun pbrDefines(mask: Long, environment: Attributes?): String {
            val sb = StringBuilder()
            if ((mask and PBRFloatAttribute.Metallic) == PBRFloatAttribute.Metallic) {
                sb.append("#define metallicFactorFlag\n")
            }
            if ((mask and PBRFloatAttribute.Roughness) == PBRFloatAttribute.Roughness) {
                sb.append("#define roughnessFactorFlag\n")
            }
            if ((mask and PBRTextureAttribute.MetallicRoughnessTexture) == PBRTextureAttribute.MetallicRoughnessTexture) {
                sb.append("#define metallicRoughnessTextureFlag\n")
            }
            if ((mask and PBRTextureAttribute.OcclusionTexture) == PBRTextureAttribute.OcclusionTexture) {
                sb.append("#define occlusionTextureFlag\n")
            }
            if (EnvironmentLightAttribute.has(environment)) {
                sb.append("#define environmentLightFlag\n")
            }
            return sb.toString()
        }

        /**
         * @return `true` if the renderable has a PBR material (metallic or roughness set), so this shader is the
         * right one
         */
        fun isPbr(renderable: Renderable): Boolean {
            if (renderable.material == null) {
                return false
            }
            val mask = renderable.material!!.getMask()
            return (mask and (PBRFloatAttribute.Metallic or PBRFloatAttribute.Roughness)) != 0L
        }

        private fun withDefaults(config: ShaderConfig): ShaderConfig {
            val copy = config.copy()
            if (copy.vertexShader == null) {
                copy.vertexShader = ShaderSources.read(ShaderSources.DEFAULT_VERTEX)
            }
            if (copy.fragmentShader == null) {
                copy.fragmentShader = ShaderSources.read(ShaderSources.PBR_FRAGMENT)
            }
            return copy
        }
    }
}
