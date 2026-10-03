/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
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
