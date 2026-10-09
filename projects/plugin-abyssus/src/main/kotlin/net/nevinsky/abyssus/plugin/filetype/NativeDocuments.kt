/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.filetype

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.gdx.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.gdx.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.gdx.io.AbyssusProjectLayout.Companion.PROJECT_EXTENSION
import net.nevinsky.abyssus.lib.gdx.io.AbyssusProjectLayout.Companion.SCENE_EXTENSION

internal fun documentKind(file: VirtualFile): DocumentKind? = when {
    file.extension == SCENE_EXTENSION -> DocumentKind.SCENE
    file.extension == PROJECT_EXTENSION -> DocumentKind.PROJECT
    file.name == META_FILE -> DocumentKind.ASSET
    else -> null
}
