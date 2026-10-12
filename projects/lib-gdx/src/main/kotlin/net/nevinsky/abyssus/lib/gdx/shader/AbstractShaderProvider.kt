/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import net.nevinsky.abyssus.lib.gdx.Renderable
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractShaderProvider : ShaderProvider {
    protected val shaderCache: MutableMap<String, ShaderHolder> = ConcurrentHashMap()

    override fun get(key: String?, renderable: Renderable?): Shader? {
        var res: Shader?
        if (renderable != null) {
            res = renderable.shader
            if (res != null && res.canRender(renderable)) {
                return res
            }
        }

        res = getShaderFromCache(shaderCache, key, renderable)
        return res
    }

    protected fun getShaderFromCache(
        cacheMap: MutableMap<String, ShaderHolder>,
        key: String?,
        renderable: Renderable?
    ): Shader? {
        val holderByKey = cacheMap[key!!] ?: createHolder(key)?.also { cacheMap[key] = it }

        if (holderByKey == null) {
            return null
        }
        var res = holderByKey.getForRenderable(renderable)
        if (res != null) {
            return res
        }

        res = createShader(holderByKey, renderable)
        if (res != null && res.canRender(renderable)) {
            res.init(renderable!!)
            holderByKey.addShader(res)
            return res
        }
        return null
    }

    protected fun createHolder(key: String?): ShaderHolder? = key?.let(::ShaderHolder)

    protected abstract fun createShader(holder: ShaderHolder, renderable: Renderable?): Shader?

    override fun dispose() {
        shaderCache.values.forEach(ShaderHolder::dispose)
    }
}
