/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.filetype

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.core.assets.displayMessage
import net.nevinsky.abyssus.editor.document.DocumentKind
import net.nevinsky.abyssus.editor.document.UnsupportedDocumentFormat
import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.PROJECT_EXTENSION
import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.SCENE_EXTENSION

internal fun documentKind(file: VirtualFile): DocumentKind? = when {
    file.extension == SCENE_EXTENSION -> DocumentKind.SCENE
    file.extension == PROJECT_EXTENSION -> DocumentKind.PROJECT
    file.name == META_FILE -> DocumentKind.ASSET
    else -> null
}
