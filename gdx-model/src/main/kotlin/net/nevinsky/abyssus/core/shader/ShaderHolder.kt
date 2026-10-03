/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.shader

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.Renderable
import java.util.function.Consumer

class ShaderHolder(val key: String) : Disposable {
    protected val shaders: MutableList<Shader> = ArrayList<Shader>()

    fun getForRenderable(renderable: Renderable?): Shader? {
        for (shader in shaders) {
            if (shader.canRender(renderable)) {
                return shader
            }
        }
        return null
    }

    fun addShader(shader: Shader) {
        shaders.add(shader)
    }

    override fun dispose() {
        shaders.forEach(Consumer { obj: Shader? -> obj!!.dispose() })
        shaders.clear()
    }
}
