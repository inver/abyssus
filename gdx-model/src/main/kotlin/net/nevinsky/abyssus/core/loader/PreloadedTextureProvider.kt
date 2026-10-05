/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.loader

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider

/**
 * Hands out textures that were uploaded earlier (one per frame, so a big model does not stall the render loop). Names
 * it has no texture for go to `fallback`, which decodes and uploads on the calling thread. A texture handed
 * out belongs to the caller; the rest stay in the map for the owner to dispose.
 */
class PreloadedTextureProvider(
    private val textures: MutableMap<String, Texture>,
    private val fallback: TextureProvider
) : TextureProvider {
    override fun load(fileName: String?): Texture? {
        val texture = textures.remove(fileName)
        return texture ?: fallback.load(fileName)
    }

    companion object {
        /** Uploads a decoded image the way the model textures are set up. The pixmap is disposed.  */
        fun upload(pixmap: Pixmap): Texture {
            try {
                val result = Texture(pixmap, false)
                result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                result.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
                return result
            } finally {
                pixmap.dispose()
            }
        }
    }
}
