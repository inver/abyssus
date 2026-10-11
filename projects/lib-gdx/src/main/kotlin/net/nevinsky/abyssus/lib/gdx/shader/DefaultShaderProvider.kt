/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.files.FileHandle
import net.nevinsky.abyssus.lib.gdx.Renderable

class DefaultShaderProvider @JvmOverloads constructor(config: ShaderConfig? = null) :
    AbstractShaderProvider() {
    val config: ShaderConfig

    init {
        this.config = if (config == null) ShaderConfig() else config
    }

    constructor(vertexShader: String?, fragmentShader: String?) : this(ShaderConfig(vertexShader, fragmentShader))

    constructor(vertexShader: FileHandle, fragmentShader: FileHandle) : this(
        vertexShader.readString(),
        fragmentShader.readString()
    )

    override fun get(key: String?, renderable: Renderable?): Shader {
        var res = super.get(key, renderable)
        if (res != null) {
            return res
        }

        res = shaderCache.get(ShaderProvider.Companion.DEFAULT_SHADER_KEY)!!.getForRenderable(renderable)
        if (res != null) {
            return res
        }

        throw RuntimeException("Could not find shader for renderable. Even default shader doesn't accept it.")
    }

    override fun createShader(holder: ShaderHolder, renderable: Renderable?): Shader? {
        if (ShaderProvider.Companion.DEFAULT_SHADER_KEY != holder.key) {
            return null
        }
        // an instanced mesh is drawn by the instancedFlag variant, which reads the world matrix from its instance data
        val instanced = renderable!!.meshPart.mesh!!.isInstanced
        if (PbrShader.Companion.isPbr(renderable)) {
            // the configured fragment shader is meant for the default shader: PBR uses its own
            val pbrConfig = config.copy()
            pbrConfig.fragmentShader = null
            return PbrShader(pbrConfig, renderable, instanced)
        }
        return DefaultShader(config, renderable, instanced)
    }
}
