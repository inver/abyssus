/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.gdx.Renderable

/**
 * Returns [Shader] instances for a [Renderable] on request. Also responsible for disposing of any created
 * [Shader] instances on a call to [Disposable.dispose].
 *
 * @author badlogic
 */
interface ShaderProvider : Disposable {
    /**
     * Method returns default shader
     *
     * @param renderable the renderable for shader initialization
     * @return default shader instance
     */
    fun get(renderable: Renderable?): Shader? {
        return get(DEFAULT_SHADER_KEY, renderable)
    }

    /**
     * Returns a [Shader] for the given [String]. The RenderInstance may already contain a Shader, in
     * which case the provider may decide to return that.
     *
     * @param key        the key of shader
     * @param renderable the renderable for shader initialization
     * @return the Shader to be used for the RenderInstance
     */
    fun get(key: String?, renderable: Renderable?): Shader?

    companion object {
        /**
         * The key for default shader, which should be bundle with 3d editor
         */
        const val DEFAULT_SHADER_KEY: String = "defaultShader"
    }
}
