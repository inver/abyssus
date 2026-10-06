/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.language

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class GltfFileType private constructor() : LanguageFileType(GltfLanguage) {
    override fun getName(): String {
        return "Gltf File"
    }

    override fun getDescription(): String {
        return "Gltf file"
    }

    override fun getDefaultExtension(): String {
        return "gltf"
    }

    override fun getIcon(): Icon? {
        return GltfIcons.FILE
    }

    companion object {
        @JvmField
        val INSTANCE: GltfFileType = GltfFileType()
    }
}
