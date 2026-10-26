/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_LAYERS
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.editor.meta.AssetReferenceChoices
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.util.obj
import net.nevinsky.abyssus.lib.core.util.text
import java.io.File

/**
 * One of a terrain's five layer slots: [slot] 0 is Base and 1 to 4 are the splat map's R, G, B and A, [field] is the
 * `additional` key that holds it (`splatBase`, `splatR`...), [uuid] the stored texture reference (null when empty). A
 * reference that names no readable texture asset is [resolved] false and keeps its [uuid] as the [name]; a resolved one
 * has the texture asset's folder name as [name] and its [image] file for a thumbnail.
 */
class SplatLayer(val slot: Int, val field: String, val uuid: String?, val name: String?, val resolved: Boolean, val image: File?)

/** Reads the five layer slots of a terrain from its `meta.json` text and the project's texture assets. Plain [File] access. */
class SplatLayers(private val json: JsonProcessor) {
    private val metas = AssetMetaReader(json)
    private val choices = AssetReferenceChoices(json)

    /** The slots in order Base, R, G, B, A; empty when [terrainMetaText] is not a supported native document. */
    fun read(assetsDir: File, terrainMetaText: String): List<SplatLayer> {
        val additional = try {
            metas.read(terrainMetaText).json.obj("additional")
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            null
        } ?: return emptyList()
        val textures = assetsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
        return SPLAT_LAYERS.mapIndexed { slot, field ->
            val uuid = additional.text(field)?.takeIf { it.isNotBlank() }
            val folder = uuid?.let { id -> textures.firstOrNull { textureUuid(it) == id } }
            when {
                uuid == null -> SplatLayer(slot, field, null, null, true, null)
                folder == null -> SplatLayer(slot, field, uuid, uuid, false, null)
                else -> SplatLayer(slot, field, uuid, folder.name, true, imageOf(folder))
            }
        }
    }

    /** The texture assets that can be chosen for a slot currently holding [current], as [AssetReferenceChoices.textures]. */
    fun choices(assetsDir: File, current: String?) = choices.textures(assetsDir, current)

    private fun textureUuid(folder: File): String? {
        val file = File(folder, META_FILE).takeIf { it.isFile } ?: return null
        val meta = try {
            metas.read(file.readText())
        } catch (e: IllegalArgumentException) {
            return null
        } catch (e: java.io.IOException) {
            return null
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            return null
        }
        return meta.json.text("uuid")?.takeIf { choices.isImage(folder, meta.json.obj("additional")?.text("file")) }
    }

    private fun imageOf(folder: File): File? {
        val file = try {
            metas.read(File(folder, META_FILE).readText()).json.obj("additional")?.text("file")
        } catch (e: Exception) {
            return null
        }
        return file?.let { choices.inside(folder, it) }
    }
}
