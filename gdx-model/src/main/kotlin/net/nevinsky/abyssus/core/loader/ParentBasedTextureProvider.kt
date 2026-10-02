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

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider

/** Resolves texture names relative to the folder of the model file. Copied from Mundus (see the package README).  */
class ParentBasedTextureProvider(private val mainFile: FileHandle) : TextureProvider {
    override fun load(fileName: String): Texture {
        val file = if (fileName.startsWith("/"))
            Gdx.files.internal(fileName)
        else
            Gdx.files.internal(mainFile.parent().child(fileName).path())
        val result = Texture(file, false)
        result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        result.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        return result
    }
}
