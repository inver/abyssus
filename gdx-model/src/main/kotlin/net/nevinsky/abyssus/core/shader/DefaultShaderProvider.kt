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

import com.badlogic.gdx.files.FileHandle
import net.nevinsky.abyssus.core.Renderable

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
        if (PbrShader.Companion.isPbr(renderable!!)) {
            // the configured fragment shader is meant for the default shader: PBR uses its own
            val pbrConfig = config.copy()
            pbrConfig.fragmentShader = null
            return PbrShader(pbrConfig, renderable)
        }
        return DefaultShader(config, renderable)
    }
}
