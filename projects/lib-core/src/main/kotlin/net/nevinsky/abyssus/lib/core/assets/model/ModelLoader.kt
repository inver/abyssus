/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.RaySnapshotStore
import net.nevinsky.abyssus.lib.core.assets.loading.TextureUploadQueue
import net.nevinsky.abyssus.lib.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.core.loader.PreloadedTextureProvider
import net.nevinsky.abyssus.lib.core.model.Model
import net.nevinsky.abyssus.lib.core.model.ModelData
import kotlin.coroutines.cancellation.CancellationException


/**
 * Model assets through Assimp: parsed and their images decoded off the GL thread, textures uploaded one per frame. With
 * [decodeTextures] false only the geometry is read (no image is decoded, so no libGDX natives are needed): for a reader
 * of the prepared model's data, never for building one.
 */
class ModelLoader(
    private val metaLoader: AssetMetaLoader,
    private val assimp: AssimpModelLoader,
    private val fileLoader: FileLoader,
    private val raySnapshots: RaySnapshotStore<RayModelSnapshot, RayModelSource>? = null,
    private val decodeTextures: Boolean = true,
) : AssetLoader<PreparedModel, Model> {
    override fun loadPrepared(meta: AssetMeta<Any>): PreparedModel? {
        val capture = raySnapshots?.preparation(meta.name)
        val handle = FileHandle(fileLoader.loadAssetFile(meta.name, meta.typedAdditional<ModelMeta>().file))
        val data = assimp.loadData(handle)
        val images = if (decodeTextures) assimp.decodeTextures(data, handle) else mutableMapOf()
        val prepared = PreparedModel(data, handle, images)
        try {
            capture?.offer(RayModelSource(data, images))
            return prepared
        } catch (cancelled: CancellationException) {
            prepared.dispose()
            throw cancelled
        }
    }

    override fun prepare(name: String): PreparedModel? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        return loadPrepared(meta)
    }

    override fun upload(prepared: PreparedModel) = prepared.uploadNext()

    override fun build(prepared: PreparedModel, assets: BuiltAssets): Model =
        assimp.build(prepared.data, prepared.file, prepared.textures).also { prepared.dispose() }

    override fun discard(prepared: PreparedModel) = prepared.dispose()
}

/** A parsed model waiting for its GL resources; [file] is where its textures are resolved from. */
class PreparedModel(val data: ModelData, val file: FileHandle, pixmaps: MutableMap<String, Pixmap>) {
    private val uploads = TextureUploadQueue(pixmaps) { _, pixmap -> PreloadedTextureProvider.upload(pixmap) }

    /** The textures uploaded so far, by the name the model's materials use. */
    val textures: MutableMap<String, Texture> get() = uploads.textures

    /** Uploads one more texture; true when every texture is on the GPU. This is the slow part of building a model. */
    fun uploadNext(): Boolean = uploads.uploadNext()

    /** Releases whatever the model did not take. Safe to call more than once. */
    fun dispose() = uploads.dispose()
}
