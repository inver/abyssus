/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.model

import net.nevinsky.abyssus.assets.loading.AssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.loader.PreloadedTextureProvider
import net.nevinsky.abyssus.core.model.Model
import net.nevinsky.abyssus.core.model.ModelData
import net.nevinsky.abyssus.assets.files.AssetFiles


/** Model assets through Assimp: parsed and their images decoded off the GL thread, textures uploaded one per frame. */
class ModelLoader(private val assimp: AssimpModelLoader) : AssetLoader<PreparedModel, Model> {
    override fun prepare(files: AssetFiles, name: String): PreparedModel? {
        val handle = FileHandle(files.model(name) ?: return null)
        val data = assimp.loadData(handle)
        return PreparedModel(data, handle, assimp.decodeTextures(data, handle))
    }

    override fun upload(prepared: PreparedModel) = prepared.uploadNext()

    override fun build(prepared: PreparedModel): Model =
        assimp.build(prepared.data, prepared.file, prepared.textures).also { prepared.dispose() }

    override fun discard(prepared: PreparedModel) = prepared.dispose()
}

/** A parsed model waiting for its GL resources; [file] is where its textures are resolved from. */
class PreparedModel(val data: ModelData, val file: FileHandle, private val pixmaps: MutableMap<String, Pixmap>) {
    /** The textures uploaded so far, by the name the model's materials use. */
    val textures = HashMap<String, Texture>()

    /** Uploads one more texture; true when every texture is on the GPU. This is the slow part of building a model. */
    fun uploadNext(): Boolean {
        val name = pixmaps.keys.firstOrNull() ?: return true
        textures[name] = PreloadedTextureProvider.upload(pixmaps.remove(name)!!)
        return pixmaps.isEmpty()
    }

    /** Releases whatever the model did not take. Safe to call more than once. */
    fun dispose() {
        pixmaps.values.forEach(Pixmap::dispose)
        pixmaps.clear()
        textures.values.forEach(Texture::dispose)
        textures.clear()
    }
}