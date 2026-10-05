/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.dto

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import net.nevinsky.abyssus.assets.META_FILE
import net.nevinsky.abyssus.filetype.SceneJson

/**
 * Reads asset folders' `meta.json` through the virtual file system into the one [AssetMetaReader], so the Abyssus tree,
 * the Properties panel and the skybox chooser agree on a folder's type. Both throw when the file is not a JSON object.
 */
class MetaFiles(private val reader: AssetMetaReader) {
    /** The `meta.json` of [folder] as saved on disk; null when the folder has none. */
    fun saved(folder: VirtualFile): MetaDocument? = folder.findChild(META_FILE)?.let { reader.read(it.readText()) }

    /**
     * The `meta.json` of [folder] as the editor shows it (unsaved text first), parsed with [SceneJson] so the numbers
     * keep their text for display; null when the folder has none.
     */
    fun inEditor(folder: VirtualFile): MetaDocument? =
        folder.findChild(META_FILE)?.let { reader.read(SceneJson.parseObject(textOf(it))) }
}
