package net.nevinsky.abyssus.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.MetaType

/**
 * One loader for every kind of asset of a project: it reads an asset's `meta.json` and hands the asset to the loader
 * registered for its `type` in [loaders]. A single [AssetStorage] over it owns the built assets of all kinds, so an
 * asset can depend on another by name (a terrain on its textures) without any loader calling another. Assets of a type
 * with no loader (or no meta) prepare to nothing and fail like any unreadable asset.
 */
class CompositeAssetLoader(
    private val metaLoader: AssetMetaLoader,
    private val loaders: Map<MetaType, AssetLoader<*, *>>,
) : AssetLoader<PreparedAsset, Disposable> {
    override fun loadPrepared(meta: AssetMeta<Any>): PreparedAsset? {
        val loader = loaders[meta.type] ?: return null
        return loader.prepared(meta)
    }

    override fun prepare(name: String): PreparedAsset? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        return loadPrepared(meta)
    }

    override fun upload(prepared: PreparedAsset): Boolean = prepared.upload()

    override fun dependencies(prepared: PreparedAsset): Set<String> = prepared.dependencies()

    override fun build(prepared: PreparedAsset, assets: BuiltAssets): Disposable = prepared.build(assets)

    override fun discard(prepared: PreparedAsset) = prepared.discard()

    @Suppress("UNCHECKED_CAST")
    private fun AssetLoader<*, *>.prepared(meta: AssetMeta<Any>): PreparedAsset? {
        val loader = this as AssetLoader<Any, Disposable>
        return loader.loadPrepared(meta)?.let { PreparedAsset(loader, it) }
    }
}

/**
 * An asset prepared by one of a [CompositeAssetLoader]'s loaders, with the loader that finishes it. [value] is what that
 * loader prepared (a `PreparedModel`, a `PreparedTerrain`), for a reader that needs the CPU data and no GL; release it
 * with [CompositeAssetLoader.discard] when it is not built.
 */
class PreparedAsset internal constructor(private val loader: AssetLoader<Any, Disposable>, val value: Any) {
    internal fun upload() = loader.upload(value)
    internal fun dependencies() = loader.dependencies(value)
    internal fun build(assets: BuiltAssets) = loader.build(value, assets)
    internal fun discard() = loader.discard(value)
}
