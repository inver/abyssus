/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assetfiles.FileSnapshot
import net.nevinsky.abyssus.assets.ASSETS_DIR
import net.nevinsky.abyssus.assets.META_FILE
import net.nevinsky.abyssus.sceneview.obj
import net.nevinsky.abyssus.sceneview.text
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.assets.terrain.MAX_TERRAIN_RESOLUTION
import net.nevinsky.abyssus.terrain.generation.MIN_TERRAIN_RESOLUTION
import net.nevinsky.abyssus.terrain.generation.RecipeStatus
import net.nevinsky.abyssus.terrain.generation.SourceSnapshot
import net.nevinsky.abyssus.terrain.generation.TERRAIN_RECIPE_FILE
import net.nevinsky.abyssus.terrain.generation.TerrainRecipeCodec
import net.nevinsky.abyssus.properties.AssetReferenceChoices
import net.nevinsky.abyssus.filetype.documentDisplayMessage
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What the terrain section knows about the terrain asset it shows: usable for regeneration, or why not. */
sealed interface TerrainSource {
    /** A terrain whose heights are a valid square grid. Paths are relative to the project folder, `/` separated. */
    class Ready(
        val folderName: String,
        val size: Int,
        val resolution: Int,
        val heights: FileSnapshot.Bytes,
        val recipeBytes: FileSnapshot,
        val recipe: RecipeStatus,
        val source: SourceSnapshot,
        val metaBytes: FileSnapshot.Bytes,
    ) : TerrainSource {
        val dataPath: String get() = "$ASSETS_DIR/$folderName/$dataName"
        val recipePath: String get() = "$ASSETS_DIR/$folderName/$TERRAIN_RECIPE_FILE"
        val metaPath: String get() = "$ASSETS_DIR/$folderName/$META_FILE"

        /** The terrain data file's name inside the folder, as `meta.json` names it. */
        var dataName: String = "terrain.data"
            internal set
    }

    /** Regeneration is not possible; [reason] is shown in place of the controls. */
    data class Unusable(val reason: String) : TerrainSource
}

/**
 * Reads the terrain in [folder] (an asset folder on disk) from `meta.json` text [metaText] (the editors' text, saved or not)
 * parsed as [meta]: size, the height file and the recipe beside it. Reads files, so off the EDT.
 */
fun readTerrainSource(folder: File, metaText: String, meta: JsonNode, choices: AssetReferenceChoices, recipes: TerrainRecipeCodec): TerrainSource {
    net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat().validate(meta, net.nevinsky.abyssus.assets.format.DocumentKind.ASSET)?.let {
        return TerrainSource.Unusable(net.nevinsky.abyssus.assets.format.UnsupportedDocumentFormat(net.nevinsky.abyssus.assets.format.DocumentKind.ASSET, it).documentDisplayMessage())
    }
    val additional = meta.obj("additional") ?: return unusable("terrainNoAdditional")
    val size = additional.get("size")?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue()?.takeIf { it > 0 }
        ?: additional.get("size")?.takeIf { it.isNumber && it.doubleValue() == it.intValue().toDouble() && it.intValue() > 0 }?.intValue()
        ?: return unusable("terrainBadSize")
    val dataName = additional.text("terrainFile")?.takeIf { it.isNotBlank() } ?: return unusable("terrainNoDataFile")
    val dataFile = choices.inside(folder, dataName)?.takeIf { it.isFile } ?: return unusable("terrainDataMissing", dataName)
    val heights = runCatchingKeepingCancellation { dataFile.readBytes() }.getOrElse { return unusable("terrainDataUnreadable", dataName) }
    val count = heights.size / Float.SIZE_BYTES
    val resolution = sqrt(count.toDouble()).roundToInt()
    if (heights.size % Float.SIZE_BYTES != 0 || resolution * resolution != count) return unusable("terrainDataNotSquare", dataName)
    if (resolution !in MIN_TERRAIN_RESOLUTION..MAX_TERRAIN_RESOLUTION) return unusable("terrainDataResolution", resolution.toString(), MAX_TERRAIN_RESOLUTION.toString())

    val recipeFile = File(folder, TERRAIN_RECIPE_FILE)
    val recipeBytes = if (recipeFile.isFile) runCatchingKeepingCancellation { recipeFile.readBytes() }.getOrNull() else null
    val recipeText = recipeBytes?.toString(Charsets.UTF_8)
    val status = recipes.status(recipeText, size, resolution, heights)
    val snapshot = SourceSnapshot(metaText, sha256Hex(heights), recipeText, folderExists = true)
    return TerrainSource.Ready(
        folder.name, size, resolution, FileSnapshot.Bytes(heights),
        recipeBytes?.let { FileSnapshot.Bytes(it) } ?: FileSnapshot.Absent,
        status, snapshot, FileSnapshot.Bytes(metaText.toByteArray()),
    ).also { it.dataName = dataName }
}

private fun unusable(key: String, vararg args: String) = TerrainSource.Unusable(AbyssusBundle.message(key, *args))
