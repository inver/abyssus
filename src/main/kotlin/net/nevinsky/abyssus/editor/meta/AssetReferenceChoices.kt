/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.meta

import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.runtime.obj
import net.nevinsky.abyssus.runtime.text
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.dto.AssetMetaReader
import java.io.File

/**
 * One entry of a texture or image chooser: [value] is what is stored in the file (a texture asset's `uuid`, a face's
 * relative path, null for none), [label] what the user reads, [resolved] false for a stored value that names nothing
 * usable. An unresolved entry is only ever the current value; it is shown, never offered as a new choice.
 */
data class AssetChoice(val value: String?, val label: String, val resolved: Boolean = true)

/**
 * The values a terrain's texture fields and a cube skybox's face fields can take, found from the files on disk. Plain
 * [File] access with no platform state, so it is testable and may run on any thread.
 */
class AssetReferenceChoices(private val json: JsonProcessor) {
    /**
     * `None`, then every readable texture asset under [assetsDir] with a `uuid`, by folder name. The stored [current]
     * `uuid` is kept as a first-class, unresolved entry when it is not among them.
     */
    fun textures(assetsDir: File, current: String?): List<AssetChoice> {
        val usable = assetsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
            .mapNotNull { dir -> textureUuid(dir)?.let { AssetChoice(it, dir.name) } }
        val none = AssetChoice(null, "")
        val unresolved = current?.takeIf { c -> usable.none { it.value == c } }?.let { AssetChoice(it, it, resolved = false) }
        return listOfNotNull(none, unresolved) + usable
    }

    /** The `uuid` of the texture asset in [folder], or null when it is not a texture, has no `uuid` or no readable image. */
    private fun textureUuid(folder: File): String? {
        val meta = runCatchingKeepingCancellation {
            File(folder, META_FILE).takeIf { it.isFile }?.let { AssetMetaReader(json).read(it.readText()).json }
        }.getOrNull() ?: return null
        val type = meta.text("type")
        if (type != MetaType.TEXTURE.name && type != MetaType.PIXMAP_TEXTURE.name) return null
        val uuid = meta.text("uuid")?.takeIf { it.isNotBlank() } ?: return null
        return uuid.takeIf { isImage(folder, meta.obj("additional")?.text("file")) }
    }

    /**
     * `None`, then every readable image inside the asset [folder] (below it, not above), by relative path with `/`
     * separators. The stored [current] name is kept as an unresolved entry when it is not among them.
     */
    fun faces(folder: File, current: String?): List<AssetChoice> {
        val meta = runCatchingKeepingCancellation { File(folder, META_FILE).takeIf { it.isFile }?.let { AssetMetaReader(
            json
        ).read(it.readText()) } }.getOrNull()
        if (meta == null) return listOfNotNull(AssetChoice(null, ""), current?.let { AssetChoice(it, it, resolved = false) })
        val found = folder.walkTopDown().maxDepth(MAX_FACE_DEPTH).filter { it.isFile }
            .map { it.relativeTo(folder).invariantSeparatorsPath }
            .filter { isImage(folder, it) }
            .sorted().map { AssetChoice(it, it) }.toList()
        val unresolved = current?.takeIf { c -> found.none { it.value == c } }?.let { AssetChoice(it, it, resolved = false) }
        return listOfNotNull(AssetChoice(null, ""), unresolved) + found
    }

    /** Whether [name] is a readable image file of a type the loaders decode, inside [folder] after resolving links. */
    fun isImage(folder: File, name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (name.substringAfterLast('.', "").lowercase() !in IMAGE_EXTENSIONS) return false
        val file = inside(folder, name) ?: return false
        return file.isFile && file.canRead()
    }

    /** The file [name] under [folder] when its canonical path stays strictly inside the canonical [folder], else null. */
    fun inside(folder: File, name: String): File? {
        val root = runCatchingKeepingCancellation { folder.canonicalFile.toPath() }.getOrNull() ?: return null
        val file = runCatchingKeepingCancellation { File(folder, name).canonicalFile }.getOrNull() ?: return null
        // Path.startsWith compares whole path elements, so `skybox2` is not inside `skybox`
        return file.takeIf { it.toPath() != root && it.toPath().startsWith(root) }
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "bmp", "gif")
        const val MAX_FACE_DEPTH = 3
    }
}
