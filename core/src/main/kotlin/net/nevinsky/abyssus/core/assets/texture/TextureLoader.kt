package net.nevinsky.abyssus.core.assets.texture

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.core.loader.Pixmaps

/**
 * `TEXTURE` and `PIXMAP_TEXTURE` assets: the image is decoded off the GL thread, then uploaded as a mipmapped,
 * repeating texture. Other loaders that need only the decoded image (a terrain's splat layers) take it with
 * [PreparedTexture.release].
 */
class TextureLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
) : AssetLoader<PreparedTexture, Texture> {
    /** The decoded image of [meta]; null when [meta] is not a texture asset. Throws when the image cannot be read. */
    override fun loadPrepared(meta: AssetMeta<Any>): PreparedTexture? {
        if (meta.type != MetaType.TEXTURE && meta.type != MetaType.PIXMAP_TEXTURE) {
            return null
        }
        val file = fileLoader.loadAssetFile(meta.name, meta.typedAdditional<TextureMeta>().file)
        return PreparedTexture(Pixmaps.load(FileHandle(file)))
    }

    override fun prepare(name: String): PreparedTexture? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        return loadPrepared(meta)
    }

    override fun build(prepared: PreparedTexture, assets: BuiltAssets): Texture {
        val pixmap = prepared.release()
        return try {
            Texture(pixmap, true).also {
                it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                it.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
            }
        } finally {
            pixmap.dispose()
        }
    }

    override fun discard(prepared: PreparedTexture) = prepared.dispose()
}

/** A decoded texture image. Whoever takes it with [release] owns the [Pixmap]; otherwise [dispose] frees it. */
class PreparedTexture(pixmap: Pixmap) {
    private var pixmap: Pixmap? = pixmap

    /** The image, now owned by the caller; fails when it was already released or disposed. */
    fun release(): Pixmap = checkNotNull(pixmap) { "texture image already released" }.also { pixmap = null }

    /** Frees the image unless it was released. Safe to call more than once. */
    fun dispose() {
        pixmap?.dispose()
        pixmap = null
    }
}
