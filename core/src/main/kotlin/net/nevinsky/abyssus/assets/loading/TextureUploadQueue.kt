/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.loading

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture

/**
 * Decoded images waiting for the GPU, uploaded one per [uploadNext] (the slow part of building a model or a terrain).
 * [makeTexture] turns one pixmap into a texture and takes over the pixmap: it must dispose it. [dispose] releases the
 * pixmaps not yet uploaded and the textures uploaded so far, and may be called more than once.
 */
class TextureUploadQueue(
    pixmaps: Map<String, Pixmap>,
    private val makeTexture: (String, Pixmap) -> Texture,
) {
    private val pixmaps = LinkedHashMap(pixmaps)

    /** The textures uploaded so far, by name. */
    val textures = HashMap<String, Texture>()

    /** Uploads one more image; true when every image is on the GPU. */
    fun uploadNext(): Boolean {
        val name = pixmaps.keys.firstOrNull() ?: return true
        textures[name] = makeTexture(name, pixmaps.remove(name)!!)
        return pixmaps.isEmpty()
    }

    fun dispose() {
        pixmaps.values.forEach(Pixmap::dispose)
        pixmaps.clear()
        textures.values.forEach(Texture::dispose)
        textures.clear()
    }
}
