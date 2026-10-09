package net.nevinsky.abyssus.lib.gdx.assets.texture

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.assets.AssetMeta
import net.nevinsky.abyssus.lib.gdx.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.Prepared
import net.nevinsky.abyssus.lib.gdx.loader.Pixmaps

/**
 * `TEXTURE` and `PIXMAP_TEXTURE` assets: the image is decoded off the GL thread, then uploaded as a mipmapped,
 * repeating texture. Other loaders that need only the decoded image (a terrain's splat layers) take it with
 * [PreparedTexture.release].
 */
class TextureLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
) : AssetLoader<Unit, PreparedTexture, Texture> {
    /** The decoded image of [meta]; null when [meta] is not a texture asset. Throws when the image cannot be read. */
    override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, PreparedTexture>? {
        if (meta.type != MetaType.TEXTURE && meta.type != MetaType.PIXMAP_TEXTURE) {
            return null
        }
        val file = fileLoader.loadAssetFile(meta.name, meta.typedAdditional<TextureMeta>().file)
        return Prepared(PreparedTexture(Pixmaps.load(FileHandle(file))))
    }

    override fun prepare(name: String): Prepared<Unit, PreparedTexture>? =
        metaLoader.loadBaseMeta(name)?.let(::loadPrepared)

    override fun build(staged: PreparedTexture, assets: BuiltAssets): Texture {
        val pixmap = staged.release()
        return try {
            Texture(pixmap, true).also {
                it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                it.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
            }
        } finally {
            pixmap.dispose()
        }
    }

    override fun discard(model: Unit) = Unit

    override fun discardStaged(staged: PreparedTexture) = staged.dispose()
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
