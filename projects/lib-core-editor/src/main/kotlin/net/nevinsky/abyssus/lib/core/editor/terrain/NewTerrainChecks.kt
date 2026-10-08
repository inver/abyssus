/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.util.text
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** Why a folder name for a new terrain is refused; the dialog shows a localized reason for each. */
enum class FolderNameError { BLANK, DOT, SEPARATOR, INVALID_CHARACTER, TRAILING, RESERVED, OUTSIDE, EXISTS }

/** Why a new terrain's size or resolution is refused. */
enum class GeometryError { SIZE, RESOLUTION }

private val RESERVED = Regex("(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?$")
private const val INVALID_CHARACTERS = "<>:\"|?*"

/** The reason [name] cannot be the folder of a new asset in [assetsDir], or null. Reads the folder; call off or on the UI thread. */
fun checkFolderName(assetsDir: File, name: String): FolderNameError? {
    val n = name.trim()
    return when {
        n.isEmpty() -> FolderNameError.BLANK
        n == "." || n == ".." -> FolderNameError.DOT
        n.contains('/') || n.contains('\\') -> FolderNameError.SEPARATOR
        n.any { it.code < 0x20 || it in INVALID_CHARACTERS } -> FolderNameError.INVALID_CHARACTER
        n.endsWith('.') || n.endsWith(' ') -> FolderNameError.TRAILING
        RESERVED.matches(n) -> FolderNameError.RESERVED
        exists(assetsDir, n) -> FolderNameError.EXISTS
        escapes(assetsDir, n) -> FolderNameError.OUTSIDE
        else -> null
    }
}

private fun escapes(assetsDir: File, name: String): Boolean {
    if (!assetsDir.exists()) return false
    val root = runCatchingKeepingCancellation { assetsDir.canonicalFile }.getOrNull() ?: return true
    val parent =
        runCatchingKeepingCancellation { File(assetsDir, name).canonicalFile.parentFile }.getOrNull() ?: return true
    return parent != root
}

/** True for a folder, file or link of that name, in any letter case (a case-insensitive file system would collide with it). */
private fun exists(assetsDir: File, name: String): Boolean {
    val target = File(assetsDir, name)
    if (target.exists() || Files.isSymbolicLink(target.toPath())) return true
    return assetsDir.list()?.any { it.equals(name, ignoreCase = true) } == true
}

fun checkGeometry(size: Int?, resolution: Int?): GeometryError? = when {
    size == null || size <= 0 -> GeometryError.SIZE
    resolution == null || resolution !in MIN_TERRAIN_RESOLUTION..255 -> GeometryError.RESOLUTION
    else -> null
}

/** A random `uuid` (from [random]) that no asset folder in [assetsDir] already uses. Reads every `meta.json`. */
fun uniqueAssetUuid(json: JsonProcessor, assetsDir: File, random: () -> UUID = { UUID.randomUUID() }): UUID {
    val used = assetsDir.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
        runCatchingKeepingCancellation {
            File(dir, META_FILE).takeIf { it.isFile }?.let {
                AssetMetaReader(json).read(it.readText()).json.text("uuid")
            }
        }.getOrNull()
    }.toSet()
    var uuid = random()
    while (uuid.toString() in used) uuid = random()
    return uuid
}
