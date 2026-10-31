/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.meta

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.parseUuidOrNull
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.editor.document.MetaDocument
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.util.obj
import net.nevinsky.abyssus.lib.core.util.text
import java.io.File

/**
 * The editor's view of a project's asset folders as a repository: which folders there are, what each `meta.json` says,
 * and which folder a `uuid` names. It exists beside the runtime's `AssetIndex` because the editor must read text that
 * is not on disk yet; [readText] supplies each `meta.json` (an unsaved buffer, say; null when there is none). A `uuid`
 * is compared as a UUID when it parses, so an upper-case spelling resolves as it does in the game. Plain [File]
 * access, so off the EDT.
 */
class AssetCatalog(
    json: JsonProcessor,
    private val readText: (File) -> String? = { it.takeIf(File::isFile)?.readText() },
) {
    private val reader = AssetMetaReader(json)

    /** The asset folders under [assetsDir], by name. */
    fun folders(assetsDir: File): List<File> =
        assetsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()

    /** The native `meta.json` of [folder], or null when it is missing, unreadable, not JSON or not a native document. */
    fun meta(folder: File): MetaDocument? = runCatchingKeepingCancellation {
        readText(File(folder, META_FILE))?.let(reader::read)
    }.getOrNull()

    /** The asset folder whose meta has [uuid] and one of [types] (any type when empty); the first by name wins. */
    fun folderOf(assetsDir: File, uuid: String, types: Set<MetaType> = emptySet()): File? =
        folders(assetsDir).firstOrNull { dir ->
            meta(dir)?.takeIf { types.isEmpty() || it.type in types }?.json?.text("uuid")?.let { same(it, uuid) } == true
        }

    /** The names of the asset folders other than [except] whose [type] meta holds [uuid] in its `additional.[field]`. */
    fun users(assetsDir: File, type: MetaType, field: String, uuid: String, except: String? = null): List<String> =
        folders(assetsDir).filter { it.name != except }.filter { dir ->
            meta(dir)?.takeIf { it.type == type }?.json?.obj("additional")?.text(field)?.let { same(it, uuid) } == true
        }.map { it.name }

    /** Every `uuid` an asset meta under [assetsDir] declares, as written. */
    fun uuids(assetsDir: File): Set<String> = folders(assetsDir).mapNotNull { meta(it)?.json?.text("uuid") }.toSet()

    private fun same(a: String, b: String): Boolean {
        val left = parseUuidOrNull(a)
        val right = parseUuidOrNull(b)
        return if (left != null && right != null) left == right else a == b
    }
}
