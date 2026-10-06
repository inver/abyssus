/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.shader

import com.badlogic.gdx.graphics.GL20
class ShaderConfig {
    /**
     * The uber vertex shader to use, null to use the default vertex shader.
     */
    var vertexShader: String? = null

    /**
     * The uber fragment shader to use, null to use the default fragment shader.
     */
    var fragmentShader: String? = null

    /**
     * The number of directional lights to use
     */
    var numDirectionalLights = 2

    /**
     * The number of point lights to use
     */
    var numPointLights = 5

    /**
     * The number of spotlights to use
     */
    var numSpotLights = 0

    /**
     * The number of bones to use
     */
    var numBones = 12

    /**
     *
     */
    var ignoreUnimplemented = true

    /**
     * Set to 0 to disable culling
     */
    var defaultCullFace = GL20.GL_BACK

    /**
     * Set to 0 to disable depth test
     */
    var defaultDepthFunc = GL20.GL_LEQUAL

    constructor()

    constructor(vertexShader: String?, fragmentShader: String?) {
        this.vertexShader = vertexShader
        this.fragmentShader = fragmentShader
    }

    /**
     * @return a copy of this config
     */
    fun copy(): ShaderConfig {
        val res = ShaderConfig(vertexShader, fragmentShader)
        res.numDirectionalLights = numDirectionalLights
        res.numPointLights = numPointLights
        res.numSpotLights = numSpotLights
        res.numBones = numBones
        res.ignoreUnimplemented = ignoreUnimplemented
        res.defaultCullFace = defaultCullFace
        res.defaultDepthFunc = defaultDepthFunc
        return res
    }
}