/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.core.loader

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.core.model.Model
import net.nevinsky.abyssus.core.model.ModelData
import net.nevinsky.abyssus.lib.assets.assimp.AssimpFlags
import net.nevinsky.abyssus.lib.assets.assimp.AssimpModelDataLoader

/**
 * Loads a model file through Assimp. Trimmed copy of Mundus' `AssimpModelLoader` (no exporter, no import preview).
 *
 *
 * [loadData] needs no OpenGL context and may run on any thread; [build] creates the GPU resources and
 * must run with the GL context current.
 */
class AssimpModelLoader {
    private val dataLoader = AssimpModelDataLoader()

    /** Parses the file. Embedded textures are extracted into `embedded` next to the model, as Mundus does.  */
    fun loadData(file: FileHandle): ModelData {
        return dataLoader.load(file.name(), file, AssimpFlags.DEFAULT, file.parent().child("embedded"))
    }

    fun build(data: ModelData, file: FileHandle): Model = Model(data, ParentBasedTextureProvider(file))

    /**
     * Decodes the images the model's materials use. Needs no OpenGL context and may run on any thread; an image that
     * cannot be decoded is left out and reported again when the model is built. The caller owns the pixmaps.
     */
    fun decodeTextures(data: ModelData, file: FileHandle): MutableMap<String, Pixmap> {
        val result = HashMap<String, Pixmap>()
        for (material in data.materials) {
            for (texture in material.textures ?: continue) {
                val name = texture.fileName
                if (name == null || result.containsKey(name)) {
                    continue
                }
                val source = if (name.startsWith("/")) FileHandle(name) else file.parent().child(name)
                try {
                    result[name] = Pixmaps.load(source)
                } catch (e: RuntimeException) {
                    // built later through the fallback, which fails the model the same way as before
                }
            }
        }
        return result
    }

    /**
     * Builds the model from textures that are already uploaded (see [PreloadedTextureProvider.upload]); GL
     * context required. Textures the model did not take stay in `uploaded` for the caller to dispose.
     */
    fun build(data: ModelData, file: FileHandle, uploaded: MutableMap<String, Texture>): Model =
        Model(data, PreloadedTextureProvider(uploaded, ParentBasedTextureProvider(file)))
}
