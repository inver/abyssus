/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain

import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assetfiles.AssetReferenceGuard
import net.nevinsky.abyssus.assetfiles.AssetTransaction
import net.nevinsky.abyssus.assetfiles.FileChange
import net.nevinsky.abyssus.assetfiles.FileSnapshot
import net.nevinsky.abyssus.assets.ASSETS_DIR
import net.nevinsky.abyssus.assets.META_FILE
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.json.text
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.assets.terrain.generation.MIN_TERRAIN_RESOLUTION
import net.nevinsky.abyssus.assets.terrain.generation.TERRAIN_DATA_FILE
import net.nevinsky.abyssus.assets.terrain.generation.TERRAIN_RECIPE_FILE
import net.nevinsky.abyssus.assets.terrain.generation.TerrainAssetWriter
import net.nevinsky.abyssus.assets.terrain.generation.TerrainPreview
import net.nevinsky.abyssus.assets.terrain.generation.TerrainRecipe
import net.nevinsky.abyssus.assets.terrain.generation.TerrainRecipeCodec
import net.nevinsky.abyssus.assets.terrain.generation.TerrainHeightEncoder
import net.nevinsky.abyssus.assets.terrain.generation.sha256Hex
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
    val root = runCatching { assetsDir.canonicalFile }.getOrNull() ?: return true
    val parent = runCatching { File(assetsDir, name).canonicalFile.parentFile }.getOrNull() ?: return true
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

fun FolderNameError.message(): String = AbyssusBundle.message("newTerrainNameError.$name")

fun GeometryError.message(): String = AbyssusBundle.message("newTerrainGeometryError.$name")

/** A new terrain ready to be written: its [name] under the project's `assets`, fresh [uuid] and the staged files. */
class NewTerrain(val name: String, val uuid: String, val transaction: AssetTransaction)

/**
 * Stages the files of a new terrain asset from a finished [TerrainPreview]: `meta.json` in Mundus's layout with a fresh
 * `uuid`, the big-endian heights, and the Abyssus recipe. Nothing is written here; no scene or project file is touched.
 */
class NewTerrainFactory(
    private val json: JsonProcessor,
    private val writer: TerrainAssetWriter,
    private val encoder: TerrainHeightEncoder,
    private val recipes: TerrainRecipeCodec,
    private val clock: () -> Long = System::currentTimeMillis,
    private val randomUuid: () -> String = { UUID.randomUUID().toString() },
) {
    /** The staged terrain, or null when the folder name is refused (re-checked here: the folder may have appeared since). */
    fun stage(projectDir: File, rawName: String, preview: TerrainPreview): NewTerrain? {
        val name = rawName.trim()
        val assetsDir = File(projectDir, ASSETS_DIR)
        if (checkFolderName(assetsDir, name) != null) return null
        val uuid = uniqueUuid(assetsDir)
        val files = writer.create(uuid, clock(), preview.size, preview.heights)
        val recipe = TerrainRecipe(preview.settings, preview.size, preview.resolution, sha256Hex(files.heightBytes))
        val base = "$ASSETS_DIR/$name"
        val transaction = AssetTransaction(
            AbyssusBundle.message("commandNewTerrain"),
            changes = listOf(
                FileChange("$base/$META_FILE", FileSnapshot.Absent, FileSnapshot.Bytes(files.metaText.toByteArray())),
                FileChange("$base/$TERRAIN_DATA_FILE", FileSnapshot.Absent, FileSnapshot.Bytes(files.heightBytes)),
                FileChange("$base/$TERRAIN_RECIPE_FILE", FileSnapshot.Absent, FileSnapshot.Bytes(recipes.encode(recipe).toByteArray())),
            ),
            createdDirs = (if (assetsDir.isDirectory) emptyList() else listOf(ASSETS_DIR)) + base,
            guard = AssetReferenceGuard(projectDir).let { guard -> { guard.blocker(name, uuid) } },
        )
        return NewTerrain(name, uuid, transaction)
    }

    /** A random `uuid` no asset folder of the project already uses. */
    private fun uniqueUuid(assetsDir: File): String {
        val used = assetsDir.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
            runCatchingKeepingCancellation { File(dir, META_FILE).takeIf { it.isFile }?.let { json.readObject(it.readText()).text("uuid") } }.getOrNull()
        }.toSet()
        var uuid = randomUuid()
        while (uuid in used) uuid = randomUuid()
        return uuid
    }
}
