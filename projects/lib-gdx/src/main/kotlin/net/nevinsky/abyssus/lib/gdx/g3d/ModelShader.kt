/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute
import com.badlogic.gdx.graphics.g3d.environment.AmbientCubemap
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.shader.ClipPlaneAttribute
import net.nevinsky.abyssus.lib.gdx.shader.DirectionalVisibilityAttribute
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransformAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShaderSources
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings

/**
 * This module's uber shader on the stock [DefaultShader]: the same GLSL as before (per-pixel lighting, shadow atlas,
 * directional visibility, world clip plane, instancing) loaded through the stock constructor. What the stock shader
 * lacks is added through its hooks:
 *
 * - the prefix is the stock `createPrefix` plus [extraDefines] (`instancedFlag`, `clipPlaneFlag`, `shadowAtlasFlag`,
 *   `directionalVisibilityFlag`);
 * - `canRender` also requires the same instancing as the program was built for;
 * - `bindLights` also binds the shadow atlas and the directional visibility map, `render` the clip plane;
 * - `u_ambientCubemap` is seeded from the sky's six irradiance colors when the environment has one.
 *
 * Use [ModelShaderProvider] to get the right variant (plain, instanced, PBR) for a renderable.
 */
open class ModelShader(
    renderable: Renderable,
    config: Config,
    prefix: String = modelPrefix(renderable, config),
    vertexSource: String = config.vertexShader ?: ShaderSources.read(ShaderSources.DEFAULT_VERTEX),
    fragmentSource: String = config.fragmentShader ?: ShaderSources.readAssembled(ShaderSources.DEFAULT_FRAGMENT)
) : DefaultShader(renderable, config, prefix, vertexSource, fragmentSource) {
    /** `true` for the `instancedFlag` variant: the world matrix comes from the mesh's instance data. */
    val instanced: Boolean = renderable.meshPart.mesh.isInstanced

    private val atlasBindings = ShadowAtlasBindings()

    init {
        if (renderable.environment != null) {
            // the stock setter starts from the ambient light only; this one starts from the sky when there is one
            register(Inputs.ambientCube, AmbientCubemapSetter(config.numDirectionalLights, config.numPointLights))
        }
    }

    /** Registers a per-renderable uniform with the validator `alias` + [overallMask]; [set] runs for each draw. */
    protected fun registerLocal(
        alias: String,
        overallMask: Long = 0,
        set: (shader: BaseShader, inputId: Int, renderable: Renderable?, attributes: Attributes) -> Unit
    ): Int = register(Uniform(alias, overallMask), object : LocalSetter() {
        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes?) =
            set(shader, inputID, renderable, combinedAttributes!!)
    })

    override fun canRender(renderable: Renderable): Boolean =
        instanced == renderable.meshPart.mesh.isInstanced && super.canRender(renderable)

    override fun render(renderable: Renderable, combinedAttributes: Attributes) {
        bindClipPlane(combinedAttributes)
        bindOutputTransform(combinedAttributes)
        super.render(renderable, combinedAttributes)
    }

    /** The world clip plane of the pass, when it set one; the program was compiled with the test only in that case. */
    private fun bindClipPlane(attributes: Attributes) {
        val plane = attributes.get(ClipPlaneAttribute.Type) as? ClipPlaneAttribute ?: return
        val location = program.fetchUniformLocation("u_clipPlane", false)
        if (location >= 0) {
            program.setUniformf(location, plane.normalX, plane.normalY, plane.normalZ, plane.offset)
        }
    }

    /** The exposure of the pass's output transform, when it set one; the program was compiled with the transform only in that case. */
    private fun bindOutputTransform(attributes: Attributes) {
        val output = attributes.get(OutputTransformAttribute.Type) as? OutputTransformAttribute ?: return
        val location = program.fetchUniformLocation("u_exposure", false)
        if (location >= 0) {
            program.setUniformf(location, output.transform.exposure)
        }
    }

    override fun bindLights(renderable: Renderable, attributes: Attributes) {
        super.bindLights(renderable, attributes)
        bindShadowAtlas(attributes)
        bindDirectionalVisibility(attributes)
    }

    private fun bindShadowAtlas(attributes: Attributes) {
        val atlas = attributes.get(ShadowAtlasAttribute.Type) as? ShadowAtlasAttribute ?: return
        val dirs = lights<DirectionalLightsAttribute, DirectionalLight>(attributes, DirectionalLightsAttribute.Type) { it.lights }
        val points = lights<PointLightsAttribute, PointLight>(attributes, PointLightsAttribute.Type) { it.lights }
        val spots = lights<SpotLightsAttribute, SpotLight>(attributes, SpotLightsAttribute.Type) { it.lights }
        val unit = context.textureBinder.bind(atlas.atlas)
        atlasBindings.bind(
            program, atlas, dirs.map(atlas::recordFor), points.map(atlas::recordFor), spots.map(atlas::recordFor), unit
        )
    }

    private inline fun <reified A : com.badlogic.gdx.graphics.g3d.Attribute, L> lights(
        attributes: Attributes, type: Long, of: (A) -> Iterable<L>
    ): List<L> = (attributes.get(type) as? A)?.let { of(it).toList() } ?: emptyList()

    private fun bindDirectionalVisibility(attributes: Attributes) {
        val visibility = attributes.get(DirectionalVisibilityAttribute.Type) as? DirectionalVisibilityAttribute ?: return
        val unit = context.textureBinder.bind(visibility.texture)
        fun location(name: String) = program.fetchUniformLocation(name, false)
        location("u_dirVisMap").takeIf { it >= 0 }?.let { program.setUniformi(it, unit) }
        location("u_dirVisWorldToMap").takeIf { it >= 0 }?.let { program.setUniformMatrix(it, visibility.worldToMap) }
        location("u_dirVisParams").takeIf { it >= 0 }?.let { program.setUniformf(it, visibility.edgeFade, visibility.outside) }
        location("u_dirVisIndex").takeIf { it >= 0 }?.let { program.setUniformi(it, visibility.lightIndex) }
    }

    /** The ambient cube: the sky's six colors (or the ambient light) plus the lights beyond the shader's own. */
    internal class AmbientCubemapSetter(private val dirLightsOffset: Int, private val pointLightsOffset: Int) : LocalSetter() {
        private val cache = AmbientCubemap()
        private val position = Vector3()

        override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes?) {
            if (renderable!!.environment == null) {
                shader.program.setUniform3fv(shader.loc(inputID), ONES, 0, ONES.size)
                return
            }
            renderable.worldTransform.getTranslation(position)
            setAmbientBase(cache, combinedAttributes!!)
            (combinedAttributes.get(DirectionalLightsAttribute.Type) as? DirectionalLightsAttribute)?.let { attribute ->
                for (i in dirLightsOffset until attribute.lights.size) {
                    cache.add(attribute.lights[i].color, attribute.lights[i].direction)
                }
            }
            (combinedAttributes.get(PointLightsAttribute.Type) as? PointLightsAttribute)?.let { attribute ->
                for (i in pointLightsOffset until attribute.lights.size) {
                    val light = attribute.lights[i]
                    cache.add(light.color, light.position, position, light.intensity)
                }
            }
            cache.clamp()
            shader.program.setUniform3fv(shader.loc(inputID), cache.data, 0, cache.data.size)
        }

        companion object {
            private val blended = FloatArray(18)
            private val ONES = FloatArray(18) { 1f }

            /**
             * Starts [cubemap] from the sky's six irradiance colors when [attributes] has an [EnvironmentLightAttribute]
             * (it replaces the ambient color), else from the ambient color when there is one; otherwise leaves it as it
             * is. Lights beyond the shader's count are added after this.
             */
            fun setAmbientBase(cubemap: AmbientCubemap, attributes: Attributes) {
                val sky = attributes.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute?
                if (sky != null) {
                    cubemap.set(sky.blendAmbientInto(blended))
                } else if (attributes.has(ColorAttribute.AmbientLight)) {
                    cubemap.set((attributes.get(ColorAttribute.AmbientLight) as ColorAttribute).color)
                }
            }
        }
    }
}

/** The prefix of the stock shader: the stock `#define`s of [renderable] plus [extraDefines]. */
fun modelPrefix(renderable: Renderable, config: DefaultShader.Config): String {
    val attributes = Attributes()
    renderable.environment?.let { attributes.set(it) }
    renderable.material?.let { attributes.set(it) }
    return DefaultShader.createPrefix(renderable, config) + extraDefines(
        attributes, renderable.meshPart.mesh.vertexAttributes.mask, renderable.environment != null,
        renderable.meshPart.mesh.isInstanced
    )
}

/**
 * The `#define` lines this module's GLSL needs beyond the stock prefix. [attributes] is the environment and the
 * material combined, [vertexMask] the usage mask of the mesh. The lighting-only flags follow the stock rule: an
 * environment and normals (or tangent and binormal).
 */
fun extraDefines(attributes: Attributes, vertexMask: Long, hasEnvironment: Boolean, instanced: Boolean): String {
    val sb = StringBuilder()
    if (attributes.has(ClipPlaneAttribute.Type)) {
        sb.append("#define clipPlaneFlag\n")
    }
    if (attributes.has(OutputTransformAttribute.Type)) {
        sb.append("#define outputTransformFlag\n")
    }
    val normal = VertexAttributes.Usage.Normal.toLong()
    val tangentAndBinormal = (VertexAttributes.Usage.Tangent or VertexAttributes.Usage.BiNormal).toLong()
    val lights = (vertexMask and normal) == normal || (vertexMask and tangentAndBinormal) == tangentAndBinormal
    if (hasEnvironment && lights) {
        if (attributes.has(ShadowAtlasAttribute.Type)) {
            sb.append("#define shadowAtlasFlag\n")
        }
        if (attributes.has(DirectionalVisibilityAttribute.Type)) {
            sb.append("#define directionalVisibilityFlag\n")
        }
    }
    if (instanced) {
        sb.append("#define instancedFlag\n")
    }
    return sb.toString()
}
