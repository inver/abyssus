/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.foliage

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMaskFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageRead
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageSettingsReader
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.assetfiles.sha256Hex
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.io.File

/** What the foliage section knows about the asset it shows: ready to edit, or why it cannot be. */
sealed interface FoliageSource {

    /**
     * A foliage asset whose `meta.json` is a supported native document and whose terrain and masks are readable.
     * Everything is read in one step off the EDT, so a preview and an Apply work from one consistent view of the files.
     */
    class Ready(
        val folderName: String,
        /** `meta.json` as the editors show it (unsaved text included): the text every settings diff is made from. */
        val metaText: String,
        /** The MODEL asset folders of the project: the only ones a layer may name, and the ones the panel offers. */
        val modelAssets: Set<String>,
        /** The stored settings, with the problems they already have. */
        val read: FoliageRead,
        val terrain: TerrainData,
        /** Each layer's mask as its file holds it; a full mask for a layer whose file is missing. */
        val masks: Map<Int, ByteArray>,
        /** SHA-256 of `foliage.data`, or null when the asset has none. */
        val dataSha256: String?,
        /** True when no readable bake was made from these settings, this terrain and these masks. */
        val stale: Boolean,
        /** The fingerprint of the settings, the terrain and the masks as they were read. */
        val fingerprint: ByteArray,
    ) : FoliageSource {
        val settings: FoliageMeta get() = read.settings

        /** Whether [other] describes the same files: another one means the preview must be made again. */
        fun sameAs(other: Ready): Boolean = metaText == other.metaText && dataSha256 == other.dataSha256 &&
            stale == other.stale && modelAssets == other.modelAssets &&
            fingerprint.contentEquals(other.fingerprint)
    }

    /** The asset cannot be edited; [reason] is shown in place of the section. */
    data class Unusable(val reason: String) : FoliageSource
}

/**
 * Reads the foliage asset [folderName] of the project in [projectDir] from the `meta.json` text [metaText] and its
 * already parsed [additional] block: the settings and their problems, the terrain it stands on, the layer masks and
 * the state of the bake. Reads files, so off the EDT; an asset whose terrain or masks cannot be read is
 * [FoliageSource.Unusable] with the reason, because neither the preview nor a write could work from it.
 */
fun readFoliageSource(
    folderName: String,
    projectDir: File,
    json: JsonProcessor,
    metaText: String,
    additional: JsonNode?,
): FoliageSource {
    val modelAssets = foliageModelAssets(projectDir, json)
    val read = FoliageSettingsReader(EditorBundle).read(additional, modelAssets)
    val settings = read.settings
    val terrain = foliageTerrainData(projectDir, json, settings.terrain)
        ?: return FoliageSource.Unusable(AbyssusBundle.message("foliageTerrainUnreadable", settings.terrain))
    val files = FileLoader(projectDir)
    val maskFile = FoliageMaskFile(files)
    val masks = runCatchingKeepingCancellation {
        settings.layers.associate { it.id to maskFile.read(folderName, it.id, settings.maskResolution) }
    }.getOrElse { return FoliageSource.Unusable(maskReason(it)) }

    val fingerprint = FoliageFingerprint().of(settings, terrain, masks)
    val bytes = files.findAssetFile(folderName, settings.dataFileName())
        ?.let { file -> runCatchingKeepingCancellation { file.readBytes() }.getOrNull() }
    val bake = bytes?.let { FoliageDataFile().read(it) }
    val stale = bake == null || !bake.fingerprint.contentEquals(fingerprint)
    return FoliageSource.Ready(
        folderName, metaText, modelAssets, read, terrain, masks,
        bytes?.let(::sha256Hex), stale, fingerprint,
    )
}

private fun maskReason(failure: Throwable) =
    AbyssusBundle.message("foliageMaskUnreadable", failure.documentDisplayMessage())

/** The heights of the terrain [name], or null when the project cannot read them; the scatter needs them. */
internal fun foliageTerrainData(projectDir: File, json: JsonProcessor, name: String): TerrainData? =
    runCatchingKeepingCancellation {
        val files = FileLoader(projectDir)
        TerrainLoader(files, AssetMetaLoader(json, files)).prepare(name)?.staged?.data
    }.getOrNull()

/** The MODEL asset folders of the project in [projectDir], by name; the only assets a foliage layer may scatter. */
internal fun foliageModelAssets(projectDir: File, json: JsonProcessor): Set<String> {
    val files = FileLoader(projectDir)
    val metas = AssetMetaLoader(json, files)
    return File(projectDir, ASSETS_DIR).listFiles { f -> f.isDirectory }.orEmpty()
        .mapNotNull { folder ->
            runCatchingKeepingCancellation {
                folder.name.takeIf { metas.loadBaseMeta(it)?.type == MetaType.MODEL }
            }.getOrNull()
        }
        .toSet()
}
