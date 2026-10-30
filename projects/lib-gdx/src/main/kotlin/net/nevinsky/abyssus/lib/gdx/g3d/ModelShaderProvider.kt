/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider

/**
 * The shader provider of a stock `ModelBatch` for this module's models: which shader a renderable gets (plain, PBR,
 * instanced) follows from its material and mesh, as stock `DefaultShaderProvider.createShader` does. The shader
 * [config] is the stock one; the bone count is raised per renderable (see [fitBones]) and the bone weights are
 * always the 8 the vertex shader supports.
 */
class ModelShaderProvider(private val config: DefaultShader.Config = DefaultShader.Config()) : BaseShaderProvider() {
    override fun createShader(renderable: Renderable): Shader {
        val fitted = fitBones(config, renderable.bones?.size ?: 0)
        if (PbrModelShader.isPbr(renderable)) {
            // the configured fragment shader is meant for the default shader: PBR uses its own
            fitted.fragmentShader = null
            return PbrModelShader(renderable, fitted)
        }
        return ModelShader(renderable, fitted)
    }

    /** How many shader programs this provider has built. */
    val shaderCount: Int get() = shaders.size

    companion object {
        private const val BONES_STEP = 8
        private const val BONE_WEIGHTS = 8

        /**
         * A copy of [config] for a renderable with [bones] bones. The bone array of the shader has a fixed size and a
         * skinned model often has more bones than the default, so the size is raised to the bone count of the
         * renderable (in steps, so similar models share a shader).
         */
        fun fitBones(config: DefaultShader.Config, bones: Int): DefaultShader.Config {
            val fitted = DefaultShader.Config(config.vertexShader, config.fragmentShader)
            fitted.numDirectionalLights = config.numDirectionalLights
            fitted.numPointLights = config.numPointLights
            fitted.numSpotLights = config.numSpotLights
            fitted.numBones = if (bones <= config.numBones) config.numBones else (bones + BONES_STEP - 1) / BONES_STEP * BONES_STEP
            fitted.numBoneWeights = maxOf(config.numBoneWeights, BONE_WEIGHTS)
            fitted.ignoreUnimplemented = config.ignoreUnimplemented
            fitted.defaultCullFace = config.defaultCullFace
            fitted.defaultDepthFunc = config.defaultDepthFunc
            return fitted
        }
    }
}
