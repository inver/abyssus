/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import net.nevinsky.abyssus.lib.gdx.editor.terrain.FolderNameError
import net.nevinsky.abyssus.lib.gdx.editor.terrain.GeometryError
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainAssetWriter
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.lib.gdx.editor.terrain.checkFolderName
import net.nevinsky.abyssus.lib.gdx.editor.terrain.sha256Hex
import net.nevinsky.abyssus.lib.gdx.editor.terrain.uniqueAssetUuid
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.assetfiles.AssetReferenceGuard
import net.nevinsky.abyssus.plugin.assetfiles.AssetTransaction
import net.nevinsky.abyssus.plugin.assetfiles.FileChange
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.editor.meta.TERRAIN_DATA_FILE
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TERRAIN_RECIPE_FILE
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainPreview
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainRecipe
import net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainRecipeCodec
import java.io.File
import java.util.*

fun FolderNameError.message(): String = AbyssusBundle.message("newTerrainNameError.$name")

fun GeometryError.message(): String = AbyssusBundle.message("newTerrainGeometryError.$name")

/** A new terrain ready to be written: its [name] under the project's `assets`, fresh [uuid] and the staged files. */
class NewTerrain(val name: String, val uuid: String, val transaction: AssetTransaction)

/**
 * Stages the files of a new terrain asset from a finished [net.nevinsky.abyssus.lib.gdx.editor.terrain.TerrainPreview]: `meta.json` in native layout with a fresh
 * `uuid`, the big-endian heights, and the Abyssus recipe. Nothing is written here; no scene or project file is touched.
 */
class NewTerrainFactory(
    private val json: JsonProcessor,
    private val writer: TerrainAssetWriter,
    private val encoder: TerrainHeightEncoder,
    private val recipes: TerrainRecipeCodec,
    private val clock: () -> Long = System::currentTimeMillis,
    private val randomUuid: () -> UUID = { UUID.randomUUID() },
) {
    /** The staged terrain, or null when the folder name is refused (re-checked here: the folder may have appeared since). */
    fun stage(projectDir: File, rawName: String, preview: TerrainPreview): NewTerrain? {
        val name = rawName.trim()
        val assetsDir = File(projectDir, ASSETS_DIR)
        if (checkFolderName(assetsDir, name) != null) return null
        val uuid = uniqueAssetUuid(json, assetsDir, randomUuid)
        val files = writer.create(uuid, clock(), preview.size, preview.heights)
        val recipe = TerrainRecipe(preview.settings, preview.size, preview.resolution, sha256Hex(files.heightBytes))
        val base = "$ASSETS_DIR/$name"
        val transaction = AssetTransaction(
            AbyssusBundle.message("commandNewTerrain"),
            changes = listOf(
                FileChange("$base/$META_FILE", FileSnapshot.Absent, FileSnapshot.Bytes(files.metaText.toByteArray())),
                FileChange("$base/$TERRAIN_DATA_FILE", FileSnapshot.Absent, FileSnapshot.Bytes(files.heightBytes)),
                FileChange(
                    "$base/${TERRAIN_RECIPE_FILE}",
                    FileSnapshot.Absent,
                    FileSnapshot.Bytes(recipes.encode(recipe).toByteArray())
                ),
            ),
            createdDirs = (if (assetsDir.isDirectory) emptyList() else listOf(ASSETS_DIR)) + base,
            guard = AssetReferenceGuard(projectDir).let { guard -> { guard.blocker(name, uuid.toString()) } },
        )
        return NewTerrain(name, uuid.toString(), transaction)
    }

}
