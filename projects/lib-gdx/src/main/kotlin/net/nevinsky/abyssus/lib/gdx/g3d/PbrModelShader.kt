/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import net.nevinsky.abyssus.lib.gdx.model.PBRTextureAttribute
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShaderSources

/**
 * Metallic-roughness variant of [ModelShader]. It reads base color, normal and emissive from the same attributes as
 * the default shader (a PBR material keeps both) and adds the metallic and roughness factors, the combined
 * metallic-roughness and occlusion textures and the image-based light of an [EnvironmentLightAttribute].
 */
class PbrModelShader(
    renderable: Renderable,
    config: DefaultShader.Config
) : ModelShader(
    renderable, config,
    prefix = modelPrefix(renderable, config) + pbrDefines(renderable.material?.mask ?: 0L, renderable.environment),
    fragmentSource = config.fragmentShader ?: ShaderSources.read(ShaderSources.PBR_FRAGMENT)
) {
    /** True when this program was made for an environment fading to a second one (with the second cube fetch). */
    private val fading = EnvironmentLightAttribute.isFading(renderable.environment)

    init {
        registerLocal("u_metallicFactor", PBRFloatAttribute.Metallic) { shader, id, _, attributes ->
            shader.set(id, (attributes.get(PBRFloatAttribute.Metallic) as FloatAttribute).value)
        }
        registerLocal("u_roughnessFactor", PBRFloatAttribute.Roughness) { shader, id, _, attributes ->
            shader.set(id, (attributes.get(PBRFloatAttribute.Roughness) as FloatAttribute).value)
        }
        registerTexture("u_metallicRoughnessTexture", PBRTextureAttribute.MetallicRoughnessTexture)
        registerUvTransform("u_metallicRoughnessUVTransform", PBRTextureAttribute.MetallicRoughnessTexture)
        registerTexture("u_occlusionTexture", PBRTextureAttribute.OcclusionTexture)
        registerUvTransform("u_occlusionUVTransform", PBRTextureAttribute.OcclusionTexture)

        registerLocal("u_envIrradiance", EnvironmentLightAttribute.Type) { shader, id, _, attributes ->
            shader.set(id, shader.context.textureBinder.bind(sky(attributes).irradiance))
        }
        registerLocal("u_envSpecular", EnvironmentLightAttribute.Type) { shader, id, _, attributes ->
            shader.set(id, shader.context.textureBinder.bind(sky(attributes).specular))
        }
        registerLocal("u_envMaxLod", EnvironmentLightAttribute.Type) { shader, id, _, attributes ->
            shader.set(id, (sky(attributes).levels - 1).toFloat())
        }
        // the second environment of a cross-fade; only programs compiled with environmentFadeFlag have these uniforms
        registerLocal("u_envIrradianceTo", EnvironmentLightAttribute.Type) { shader, id, _, attributes ->
            sky(attributes).fadeTo?.let { shader.set(id, shader.context.textureBinder.bind(it.irradiance)) }
        }
        registerLocal("u_envSpecularTo", EnvironmentLightAttribute.Type) { shader, id, _, attributes ->
            sky(attributes).fadeTo?.let { shader.set(id, shader.context.textureBinder.bind(it.specular)) }
        }
        registerLocal("u_envMaxLodTo", EnvironmentLightAttribute.Type) { shader, id, _, attributes ->
            sky(attributes).fadeTo?.let { shader.set(id, (it.levels - 1).toFloat()) }
        }
        registerLocal("u_envFade", EnvironmentLightAttribute.Type) { shader, id, _, attributes ->
            shader.set(id, sky(attributes).weight)
        }
    }

    private fun sky(attributes: Attributes) = attributes.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute

    private fun registerTexture(alias: String, attribute: Long) {
        registerLocal(alias, attribute) { shader, id, _, attributes ->
            shader.set(id, shader.context.textureBinder.bind((attributes.get(attribute) as TextureAttribute).textureDescription))
        }
    }

    private fun registerUvTransform(alias: String, attribute: Long) {
        registerLocal(alias, attribute) { shader, id, _, attributes ->
            val texture = attributes.get(attribute) as TextureAttribute
            shader.set(id, texture.offsetU, texture.offsetV, texture.scaleU, texture.scaleV)
        }
    }

    /** Only the program compiled for the same fading state: the cross-fade changes the program, not just a uniform. */
    override fun canRender(renderable: Renderable): Boolean =
        super.canRender(renderable) && EnvironmentLightAttribute.isFading(renderable.environment) == fading

    companion object {
        /**
         * The `#define` lines this shader adds for a material of [mask] in [environment]; `environmentLightFlag`
         * only when the environment has an [EnvironmentLightAttribute], and `environmentFadeFlag` when that fades to
         * a second one.
         */
        fun pbrDefines(mask: Long, environment: Attributes?): String {
            val sb = StringBuilder()
            fun has(flag: Long) = (mask and flag) == flag
            if (has(PBRFloatAttribute.Metallic)) {
                sb.append("#define metallicFactorFlag\n")
            }
            if (has(PBRFloatAttribute.Roughness)) {
                sb.append("#define roughnessFactorFlag\n")
            }
            if (has(PBRTextureAttribute.MetallicRoughnessTexture)) {
                sb.append("#define metallicRoughnessTextureFlag\n")
            }
            if (has(PBRTextureAttribute.OcclusionTexture)) {
                sb.append("#define occlusionTextureFlag\n")
            }
            if (EnvironmentLightAttribute.has(environment)) {
                sb.append("#define environmentLightFlag\n")
                if (EnvironmentLightAttribute.isFading(environment)) {
                    sb.append("#define environmentFadeFlag\n")
                }
            }
            return sb.toString()
        }

        /** `true` if [renderable] has a PBR material (metallic or roughness set), so this shader is the right one. */
        fun isPbr(renderable: Renderable): Boolean {
            val mask = renderable.material?.mask ?: return false
            return (mask and (PBRFloatAttribute.Metallic or PBRFloatAttribute.Roughness)) != 0L
        }
    }
}
