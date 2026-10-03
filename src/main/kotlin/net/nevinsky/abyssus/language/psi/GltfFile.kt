/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.language.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import net.nevinsky.abyssus.language.GltfFileType
import net.nevinsky.abyssus.language.GltfLanguage

class GltfFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, GltfLanguage) {
    override fun getFileType(): FileType {
        return GltfFileType.Companion.INSTANCE
    }

    override fun toString(): String {
        return "Gltf File"
    }
}
