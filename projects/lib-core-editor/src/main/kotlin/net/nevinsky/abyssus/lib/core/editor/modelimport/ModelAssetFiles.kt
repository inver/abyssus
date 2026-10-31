/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.modelimport

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.model.ModelMeta
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import java.util.*

/**
 * The parts every staged native MODEL asset folder shares, whatever imported it: its validated `meta.json` naming
 * [MODEL_FILE], its `source.json`, and texture paths that stay distinct on case-insensitive filesystems. The importers
 * only produce the geometry, the textures and their own source record.
 */
class ModelAssetFiles(private val jsonProcessor: JsonProcessor) {
    /** The native `meta.json` of a MODEL asset. */
    fun metaBytes(uuid: UUID, lastModified: Long): ByteArray {
        val meta = AssetMeta(
            lastModified = lastModified,
            uuid = uuid,
            type = MetaType.MODEL,
            additional = ModelMeta(file = MODEL_FILE),
        )
        return jsonProcessor.toString(meta).toByteArray()
    }

    /** The pretty-printed `source.json` for an import [record]. */
    fun sourceBytes(record: Any): ByteArray = jsonProcessor.toString(record).toByteArray()
}

/**
 * `textures/<name>` for a texture file called [name], or `textures/<stem>-<n>.<extension>` when [taken] already
 * holds that path ignoring case, so two textures never overwrite one another on macOS or Windows.
 */
fun uniqueTexturePath(taken: Collection<String>, name: String): String {
    var candidate = "$TEXTURES_DIR/$name"
    var n = 2
    val stem = name.substringBeforeLast('.')
    val extension = name.substringAfterLast('.', "")
    while (taken.any { it.equals(candidate, ignoreCase = true) }) {
        candidate = "$TEXTURES_DIR/$stem-${n++}" + if (extension.isEmpty()) "" else ".$extension"
    }
    return candidate
}
