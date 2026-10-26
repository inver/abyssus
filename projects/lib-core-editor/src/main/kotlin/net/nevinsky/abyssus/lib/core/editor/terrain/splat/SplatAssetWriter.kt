/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_MAP
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaDocument
import net.nevinsky.abyssus.lib.core.editor.document.DocumentTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.editor.document.TextureAssetAdditional
import net.nevinsky.abyssus.lib.core.editor.terrain.uniqueAssetUuid
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import java.io.File
import java.util.UUID

/** The file name of a splat map inside its texture asset folder. */
const val SPLAT_FILE_NAME: String = "splat.png"

/**
 * A new splat texture asset ready to be written, with the terrain `meta.json` that links it: the asset's [folder] under
 * the project's `assets`, its fresh [uuid], the compact [metaText] of the texture, the [imageBytes] of `splat.png` and
 * the [terrainMetaText] to store in place of the terrain's old one.
 */
class NewSplatFiles(
    val folder: String,
    val uuid: String,
    val metaText: String,
    val imageBytes: ByteArray,
    val terrainMetaText: String,
)

/** Why a splat asset cannot be planned. */
enum class SplatPlanProblem { RESOLUTION, TERRAIN_META }

/** What [SplatAssetWriter] made of a request. */
sealed interface SplatPlan {
    class Planned(val files: NewSplatFiles) : SplatPlan
    class Refused(val problem: SplatPlanProblem) : SplatPlan
}

/**
 * Plans the files of Create splat map (a blank image) and Make unique copy (the shared map's image): a texture asset
 * folder `texture_<terrain>_splat` (`_2`, `_3` while taken) with a native compact `meta.json` of `type: "TEXTURE"` and
 * `additional.file`, and `splat.png`, plus the terrain's `meta.json` with `splatMap` set through [DocumentTextEditor],
 * so its formatting, key order, number text and unknown fields are kept. Nothing is written here; the caller stages the
 * files in one `AssetTransaction`.
 */
class SplatAssetWriter(
    private val json: JsonProcessor,
    private val codec: SplatImageCodec,
    private val clock: () -> Long = System::currentTimeMillis,
    private val randomUuid: () -> UUID = { UUID.randomUUID() },
) {
    /** A new, blank map of [resolution] pixels for the terrain asset folder [terrainFolder] whose `meta.json` text is [terrainMetaText]. */
    fun create(projectDir: File, terrainFolder: String, terrainMetaText: String, resolution: Int): SplatPlan {
        if (resolution !in SPLAT_MIN_SIDE..SPLAT_MAX_SIDE) return SplatPlan.Refused(SplatPlanProblem.RESOLUTION)
        return plan(projectDir, terrainFolder, terrainMetaText, SplatPixels(resolution, ByteArray(resolution * resolution * 4)))
    }

    /** A new texture asset holding a copy of [source] for the same terrain. */
    fun uniqueCopy(projectDir: File, terrainFolder: String, terrainMetaText: String, source: SplatPixels): SplatPlan =
        plan(projectDir, terrainFolder, terrainMetaText, source)

    /** [terrainMetaText] with `additional.splatMap` set to [uuid]; null when the text is not a supported native terrain `meta.json`. */
    fun link(terrainMetaText: String, uuid: String): String? {
        val outcome = DocumentTextEditor().edit(terrainMetaText, DocumentKind.ASSET) { root ->
            val additional = root.get("additional") as? ObjectNode ?: return@edit false
            additional.put(SPLAT_MAP, uuid)
            true
        }
        return when (outcome) {
            is TextEditOutcome.Edited -> outcome.text
            TextEditOutcome.Unchanged -> terrainMetaText
            is TextEditOutcome.Refused -> null
        }
    }

    private fun plan(projectDir: File, terrainFolder: String, terrainMetaText: String, pixels: SplatPixels): SplatPlan {
        val assetsDir = File(projectDir, ASSETS_DIR)
        val uuid = uniqueAssetUuid(json, assetsDir, randomUuid)
        val linked = link(terrainMetaText, uuid.toString()) ?: return SplatPlan.Refused(SplatPlanProblem.TERRAIN_META)
        val meta = SceneJson().compact(
            AssetMetaDocument(
                lastModified = clock(), uuid = uuid.toString(), type = MetaType.TEXTURE,
                additional = TextureAssetAdditional(SPLAT_FILE_NAME),
            ),
        )
        return SplatPlan.Planned(NewSplatFiles(freeFolder(assetsDir, terrainFolder), uuid.toString(), meta, codec.encode(pixels), linked))
    }

    /** `texture_<terrain>_splat`, or the same with `_2`, `_3` while a folder of that name exists (compared ignoring case). */
    private fun freeFolder(assetsDir: File, terrainFolder: String): String {
        val taken = assetsDir.list().orEmpty().map { it.lowercase() }.toSet()
        val base = "texture_${terrainFolder}_splat"
        var name = base
        var n = 2
        while (name.lowercase() in taken) name = "${base}_${n++}"
        return name
    }
}
