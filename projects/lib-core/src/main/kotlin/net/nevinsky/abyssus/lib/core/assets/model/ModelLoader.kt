/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.lib.core.assets.loading.*
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.decodeModelKey
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.loader.PreloadedTextureProvider
import com.badlogic.gdx.graphics.g3d.Model
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import kotlin.coroutines.cancellation.CancellationException

/**
 * Model assets through Assimp: parsed and their images decoded off the GL thread, textures uploaded one per frame. With
 * [decodeTextures] false only the geometry is read (no image is decoded, so no libGDX natives are needed): for a reader
 * of the prepared model's data, never for building one.
 */
class ModelLoader(
    metaLoader: AssetMetaLoader,
    private val assimp: AssimpModelLoader,
    private val fileLoader: FileLoader,
    private val preparation: ModelPreparationTap? = null,
    private val decodeTextures: Boolean = true,
    private val log: Logger = NOPLogger.NOP_LOGGER,
) : StagedMetaAssetLoader<PreparedModel, Model>(metaLoader) {
    override fun read(meta: AssetMeta<Any>): PreparedModel {
        // A variant is named by its encoded key (see AssetStorage); the files are the base asset's.
        val key = decodeModelKey(meta.name)
        val started = System.nanoTime()
        val capture = preparation?.begin(meta.name)
        val handle = FileHandle(fileLoader.loadAssetFile(key.name, meta.typedAdditional<ModelMeta>().file))
        val data = assimp.loadData(handle)
        // Before the images are decoded, so only the kept materials' textures are.
        key.meshId?.let { selectMesh(data, key.name, it) }
        val images = if (decodeTextures) assimp.decodeTextures(data, handle) else mutableMapOf()
        val prepared = PreparedModel(data, handle, images)
        try {
            capture?.offer(data, images)
            if (key.meshId != null) {
                log.atDebug().log { "Read model '${key.name}' mesh '${key.meshId}' in ${(System.nanoTime() - started) / 1_000_000} ms" }
            }
            return prepared
        } catch (cancelled: CancellationException) {
            prepared.dispose()
            throw cancelled
        }
    }

    override fun upload(staged: PreparedModel) = staged.uploadNext()

    override fun build(staged: PreparedModel, assets: BuiltAssets): Model =
        assimp.buildIntModel(staged.data, staged.file, staged.textures).also { staged.dispose() }


    override fun discardStaged(staged: PreparedModel) = staged.dispose()
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
