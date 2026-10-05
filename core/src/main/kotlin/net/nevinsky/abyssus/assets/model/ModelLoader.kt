/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.model

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.assets.loading.TextureUploadQueue
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.loader.PreloadedTextureProvider
import net.nevinsky.abyssus.core.model.Model
import net.nevinsky.abyssus.core.model.ModelData
import kotlin.coroutines.cancellation.CancellationException


/** Model assets through Assimp: parsed and their images decoded off the GL thread, textures uploaded one per frame. */
class ModelLoader(
    private val assimp: AssimpModelLoader,
    private val raySnapshots: RayModelSnapshots? = null,
) : AssetLoader<PreparedModel, Model> {
    override fun prepare(files: AssetFiles, name: String): PreparedModel? {
        val capture = raySnapshots?.preparation(files, name)
        val handle = FileHandle(files.model(name) ?: return null)
        val data = assimp.loadData(handle)
        val images = assimp.decodeTextures(data, handle)
        val prepared = PreparedModel(data, handle, images)
        try {
            capture?.offer(data, images)
            return prepared
        } catch (cancelled: CancellationException) {
            prepared.dispose()
            throw cancelled
        }
    }

    override fun upload(prepared: PreparedModel) = prepared.uploadNext()

    override fun build(prepared: PreparedModel): Model =
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
