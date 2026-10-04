/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.files

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.assets.META_FILE
import net.nevinsky.abyssus.assets.SPLAT_FIELDS
import net.nevinsky.abyssus.assets.ASSETS_DIR
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.json.float
import net.nevinsky.abyssus.assets.json.obj
import net.nevinsky.abyssus.assets.json.text
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** The files of a terrain asset: [data] is the height data, [splat] the splat textures present (by `meta.json` field). */
data class TerrainFiles(val data: File, val size: Int, val uv: Float, val splat: Map<String, File>)

/** Reads a `meta.json` file as text; null when there is none. The default reads the file from disk. */
fun interface MetaTextSource {
    fun read(metaFile: File): String?
}

/** The default [MetaTextSource]: the file's content on disk. */
class DiskMetaText : MetaTextSource {
    override fun read(metaFile: File): String? = metaFile.takeIf { it.isFile }?.readText()
}

/**
 * Finds the files an asset folder under `<projectDir>/assets` names in its `meta.json`, parsing with [json]. Pure file
 * access, so it can run on any thread; every lookup returns null for a missing folder, unreadable `meta.json` or
 * missing file.
 *
 * An instance is a snapshot: the `uuid` index is read once, on first use, and never updated. When assets may have
 * been added, removed or renamed, make a new instance ([refreshed]). Metadata is read through [metaText], so a caller
 * can substitute text that is not on disk yet (unsaved editor content) with an immutable snapshot.
 */
class AssetFiles(projectDir: File, private val json: JsonProcessor, private val metaText: MetaTextSource = DiskMetaText()) {
    val projectDir: File = projectDir.absoluteFile

    private val assetsDir = File(this.projectDir, ASSETS_DIR)

    /** Asset folders by the `uuid` in their `meta.json` (the first folder by name wins); read once, on first use. */
    private val foldersByUuid: Map<String, File> by lazy {
        val dirs = assetsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
        buildMap { for (dir in dirs) meta(dir)?.text("uuid")?.let { putIfAbsent(it, dir) } }
    }

    private fun folder(assetName: String): File? =
        // an asset name is a folder name: refuse anything that could leave the assets folder
        if (assetName.isEmpty() || assetName.contains('/') || assetName.contains('\\') || assetName == ".." || assetName == ".") null
        else File(assetsDir, assetName).takeIf { it.isDirectory }

    private val metaReader = AssetMetaReader(json)

    /** A new snapshot of the same project, reading the assets again (a changed `uuid` index, added or removed folders). */
    fun refreshed(metaText: MetaTextSource = this.metaText): AssetFiles = AssetFiles(projectDir, json, metaText)

    /** A folder's `meta.json` as last read: the file stamp (disk source) and the text it was parsed from. */
    private class CachedMeta(val stamp: Pair<Long, Long>?, val text: String, val document: MetaDocument?)

    private val metas = ConcurrentHashMap<File, CachedMeta>()

    /**
     * The parsed `meta.json` of [folder], or null when it is missing or unreadable. One read serves every lookup: from
     * disk it is read again when the file changes, from another source when the text differs.
     */
    private fun metaDocument(folder: File): MetaDocument? {
        val file = File(folder, META_FILE)
        val stamp = if (metaText is DiskMetaText && file.isFile) file.lastModified() to file.length() else null
        val cached = metas[folder]
        if (stamp != null && cached?.stamp == stamp) return cached.document
        val text = runCatchingKeepingCancellation { metaText.read(file) }.getOrNull() ?: return null
        if (cached != null && cached.text == text) return cached.document
        val document = runCatchingKeepingCancellation { metaReader.read(text) }.getOrNull()
        metas[folder] = CachedMeta(stamp, text, document)
        return document
    }

    private fun meta(folder: File): JsonNode? = metaDocument(folder)?.json

    private fun additional(folder: File) = meta(folder)?.obj("additional")

    /** The file [name] inside [folder], or null when it is blank, missing or outside the folder. */
    fun file(folder: File, name: String?): File? =
        name?.takeIf { it.isNotBlank() }?.let { File(folder, it) }
            ?.takeIf { it.isFile && it.canonicalPath.startsWith(folder.canonicalPath) }

    fun model(assetName: String): File? {
        val dir = folder(assetName) ?: return null
        return file(dir, additional(dir)?.text("file"))
    }

    fun terrain(assetName: String): TerrainFiles? {
        val dir = folder(assetName) ?: return null
        val a = additional(dir) ?: return null
        val data = file(dir, a.text("terrainFile")) ?: return null
        val size = a.float("size")?.toInt()?.takeIf { it > 0 } ?: return null
        val uv = a.float("uv") ?: 1f
        return TerrainFiles(data, size, uv, splatTextures(a))
    }

    /** `splat*` fields hold the `uuid` of a texture asset; each is resolved to that asset's image file when it exists. */
    private fun splatTextures(additional: JsonNode): Map<String, File> =
        SPLAT_FIELDS.mapNotNull { field ->
            val dir = additional.text(field)?.let(foldersByUuid::get) ?: return@mapNotNull null
            file(dir, additional(dir)?.text("file"))?.let { field to it }
        }.toMap()

    private fun <T, M : MetaBase<T>> loadMeta(clazz: Class<M>, folder: File): M? = metaDocument(folder)?.typed(clazz)

    /** The `type` of [name]'s `meta.json`; null when there is no readable one. */
    fun metaType(name: String): MetaType? = folder(name)?.let(::metaDocument)?.type

    fun loadFile(assetName: String, fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        val dir = folder(assetName) ?: return null
        return file(dir, fileName)
    }

    fun <T, M : MetaBase<T>> loadAsset(clazz: Class<M>, name: String): Asset<T>? {
        val dir = folder(name) ?: return null
        val meta = loadMeta(clazz, dir) ?: return null
        return Asset(name, meta, dir)
    }
}
