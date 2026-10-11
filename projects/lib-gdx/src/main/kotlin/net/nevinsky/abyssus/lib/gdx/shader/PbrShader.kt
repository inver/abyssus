/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import net.nevinsky.abyssus.lib.gdx.model.PBRTextureAttribute

/**
 * Metallic-roughness shader. It reads base color, normal and emissive from the same attributes as the
 * [DefaultShader] (a PBR material keeps both) and adds the metallic and roughness factors and the combined
 * metallic-roughness and occlusion textures.
 */
class PbrShader(config: ShaderConfig, renderable: Renderable?) : DefaultShader(withDefaults(config), renderable!!) {
    /** True when this program was made for an environment fading to a second one (with the second cube fetch). */
    private val fading = EnvironmentLightAttribute.isFading(renderable?.environment)

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
            shader.set(inputID, shader.context!!.textureBinder.bind(sky.irradiance))
        }
        registerUniformLocal("u_envSpecular", EnvironmentLightAttribute.Type) { shader, inputID, _, attributes ->
            val sky = attributes!!.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute
            shader.set(inputID, shader.context!!.textureBinder.bind(sky.specular))
        }
        registerUniformLocal("u_envMaxLod", EnvironmentLightAttribute.Type) { shader, inputID, _, attributes ->
            val sky = attributes!!.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute
            shader.set(inputID, (sky.levels - 1).toFloat())
        }
        // the second environment of a cross-fade; only programs compiled with environmentFadeFlag have these uniforms
        registerUniformLocal("u_envIrradianceTo", EnvironmentLightAttribute.Type) { shader, inputID, _, attributes ->
            val to = (attributes!!.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute).fadeTo ?: return@registerUniformLocal
            shader.set(inputID, shader.context!!.textureBinder.bind(to.irradiance))
        }
        registerUniformLocal("u_envSpecularTo", EnvironmentLightAttribute.Type) { shader, inputID, _, attributes ->
            val to = (attributes!!.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute).fadeTo ?: return@registerUniformLocal
            shader.set(inputID, shader.context!!.textureBinder.bind(to.specular))
        }
        registerUniformLocal("u_envMaxLodTo", EnvironmentLightAttribute.Type) { shader, inputID, _, attributes ->
            val to = (attributes!!.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute).fadeTo ?: return@registerUniformLocal
            shader.set(inputID, (to.levels - 1).toFloat())
        }
        registerUniformLocal("u_envFade", EnvironmentLightAttribute.Type) { shader, inputID, _, attributes ->
            shader.set(inputID, (attributes!!.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute).weight)
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

    /** Only the program compiled for the same fading state: the cross-fade changes the program, not just a uniform. */
    override fun canRender(instance: Renderable?): Boolean =
        super.canRender(instance) && EnvironmentLightAttribute.isFading(instance?.environment) == fading

    override fun preprocessShaderContents(renderable: Renderable) {
        super.preprocessShaderContents(renderable)

        val defines = pbrDefines(renderable.material?.getMask() ?: 0, renderable.environment)
        vertexShader = defines + vertexShader
        fragmentShader = defines + fragmentShader
    }

    companion object {
        /**
         * The `#define` lines this shader adds for a material of [mask] in [environment]; `environmentLightFlag`
         * only when the environment has an [EnvironmentLightAttribute], and `environmentFadeFlag` when that fades to a second one.
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
                if (EnvironmentLightAttribute.isFading(environment)) sb.append("#define environmentFadeFlag\n")
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
