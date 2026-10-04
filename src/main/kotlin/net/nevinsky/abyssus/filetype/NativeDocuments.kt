/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.filetype

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assets.displayMessage
import net.nevinsky.abyssus.assets.format.DocumentKind
import net.nevinsky.abyssus.assets.format.UnsupportedDocumentFormat

internal fun documentKind(file: VirtualFile): DocumentKind? = when {
    file.extension == "scene" -> DocumentKind.SCENE
    file.extension == "abss" -> DocumentKind.PROJECT
    file.name == "meta.json" -> DocumentKind.ASSET
    else -> null
}

fun Throwable.documentDisplayMessage(): String = when (this) {
    is UnsupportedDocumentFormat -> AbyssusBundle.message("unsupportedFormat.${reason.problem.name}", reason.path)
    else -> displayMessage()
}
