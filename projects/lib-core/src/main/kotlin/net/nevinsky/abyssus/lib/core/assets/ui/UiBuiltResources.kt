/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.assets.ui

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import net.nevinsky.abyssus.lib.core.ui.render.UiRenderResources
import net.nevinsky.abyssus.lib.gdx.loader.Pixmaps

/**
 * GL objects built from [UiPreparedResources]. [build] and [dispose] need a current GL context and are called on the
 * owner's GL thread; the owner (one editor session or one game UI) disposes exactly once. A resource that failed to
 * prepare is simply absent, so the renderer falls back to its placeholder or the default font.
 */
class UiBuiltResources private constructor(
    private val textures: Map<String, Texture>,
    private val fonts: Map<String, BitmapFont>,
) {
    fun texture(id: String): Texture? = textures[id]
    fun font(id: String): BitmapFont? = fonts[id]

    /** These resources as the renderer sees them, with [defaultFont] standing in for a missing or unnamed font. */
    fun forRenderer(defaultFont: BitmapFont): UiRenderResources = object : UiRenderResources {
        override val defaultFont = defaultFont
        override fun texture(id: String) = this@UiBuiltResources.texture(id)
        override fun font(id: String) = this@UiBuiltResources.font(id)
    }

    fun dispose() {
        fonts.values.forEach(BitmapFont::dispose)
        textures.values.forEach(Texture::dispose)
    }

    class Builder {
        /** Builds what [prepared] holds; on a GL failure everything built so far is disposed before rethrowing. */
        fun build(prepared: UiPreparedResources): UiBuiltResources {
            val textures = LinkedHashMap<String, Texture>()
            val fonts = LinkedHashMap<String, BitmapFont>()
            try {
                for (resource in prepared.resources.values) when (resource) {
                    is UiPreparedResource.Image -> textures[resource.id] = texture(resource.file)
                    is UiPreparedResource.Font -> fonts[resource.id] = BitmapFont(FileHandle(resource.fnt), false)
                        .also { font -> font.regions.forEach { it.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) } }
                    is UiPreparedResource.Failed -> Unit
                }
            } catch (e: Throwable) {
                fonts.values.forEach(BitmapFont::dispose)
                textures.values.forEach(Texture::dispose)
                throw e
            }
            return UiBuiltResources(textures, fonts)
        }

        private fun texture(file: java.io.File): Texture {
            val pixmap = Pixmaps.load(FileHandle(file))
            return try {
                Texture(pixmap).also { it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
            } finally {
                pixmap.dispose()
            }
        }
    }
}

/**
 * The bundled default font (libGDX's own `lsans-15`), shared by every UI of one host through explicit leases: the first
 * [acquire] builds it and the last [release] disposes it. GL thread only, with `Gdx.files` installed (both hosts do).
 */
class UiBundledFont {
    private var font: BitmapFont? = null
    private var leases = 0

    fun acquire(): BitmapFont {
        leases++
        return font ?: BitmapFont().also { font = it }
    }

    fun release() {
        check(leases > 0) { "bundled UI font released more often than acquired" }
        if (--leases == 0) { font?.dispose(); font = null }
    }

    val activeLeases: Int get() = leases
}
