/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_MAP
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.editor.meta.AssetReferenceChoices
import net.nevinsky.abyssus.lib.core.format.UnsupportedDocumentFormat
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.util.obj
import net.nevinsky.abyssus.lib.core.util.text
import java.io.File

/** Why a terrain's splat map cannot be painted; [IMAGE] carries the codec's [SplatImageProblem] in the state. */
enum class SplatProblem { TERRAIN_META, TEXTURE_META, IMAGE_MISSING, IMAGE }

/** What a terrain's splat map looks like on disk. */
sealed interface SplatState {
    /** No map to paint: `splatMap` is null, or [unresolved] names no readable texture asset. Create splat map is offered. */
    class None(val unresolved: String? = null) : SplatState

    /**
     * The map can be painted. [folder] is the texture asset folder, [file] its image, [pixels] the decoded weights, and
     * [users] the folder names of the other terrain assets whose `splatMap` is the same texture. [fileBytes] are the
     * bytes of [file] the pixels were decoded from, which a commit's undo restores, and [length] / [modified] what the
     * file looked like then, to tell cheaply that it has changed since.
     */
    class Ready(
        val uuid: String,
        val folder: File,
        val file: File,
        val pixels: SplatPixels,
        val users: List<String>,
        val fileBytes: ByteArray = ByteArray(0),
        val length: Long = fileBytes.size.toLong(),
        val modified: Long = 0L,
    ) : SplatState {
        /** Whether [file] still has the length and modification time it had when it was read. */
        fun isCurrent(): Boolean = file.length() == length && file.lastModified() == modified
    }

    /** The map cannot be painted; [detail] adds what the reader found (the sides of the image, say). */
    class Unusable(val problem: SplatProblem, val image: SplatImageProblem? = null, val detail: String = "") : SplatState
}

/**
 * Reads a terrain's [SplatState] from its files: the terrain `meta.json` is validated as native first, `splatMap` is
 * resolved by `uuid` to a texture asset, the image is decoded strictly, and the other terrains using it are listed.
 * Plain [File] access, so off the EDT.
 */
class SplatStateReader(private val json: JsonProcessor, private val codec: SplatImageCodec) {
    private val metas = AssetMetaReader(json)
    private val choices = AssetReferenceChoices(json)

    /**
     * The state for the terrain asset in [terrainFolder] under [assetsDir]. [terrainMetaText] is the terrain's
     * `meta.json` as the editors hold it (unsaved text included); null reads the file.
     */
    fun read(assetsDir: File, terrainFolder: File, terrainMetaText: String? = null): SplatState {
        val terrain = (terrainMetaText?.let(::parseMeta) ?: readMeta(File(terrainFolder, META_FILE)))
            ?: return SplatState.Unusable(SplatProblem.TERRAIN_META)
        val uuid = terrain.json.obj("additional")?.text(SPLAT_MAP)?.takeIf { it.isNotBlank() } ?: return SplatState.None()
        val folder = textureFolder(assetsDir, uuid) ?: return SplatState.None(uuid)
        val meta = readMeta(File(folder, META_FILE)) ?: return SplatState.Unusable(SplatProblem.TEXTURE_META)
        val name = meta.json.obj("additional")?.text("file")
        val file = name?.let { choices.inside(folder, it) }?.takeIf { it.isFile && it.canRead() }
            ?: return SplatState.Unusable(SplatProblem.IMAGE_MISSING, detail = name.orEmpty())
        val length = file.length()
        val modified = file.lastModified()
        val bytes = try {
            file.readBytes()
        } catch (e: java.io.IOException) {
            return SplatState.Unusable(SplatProblem.IMAGE_MISSING, detail = file.name)
        }
        val pixels = try {
            codec.decode(file.name, bytes)
        } catch (e: SplatImageException) {
            return SplatState.Unusable(SplatProblem.IMAGE, e.problem, e.detail)
        }
        return SplatState.Ready(uuid, folder, file, pixels, otherUsers(assetsDir, terrainFolder, uuid), bytes, length, modified)
    }

    /** The folder of the texture asset with [uuid], or null when no readable one has it. */
    fun textureFolder(assetsDir: File, uuid: String): File? =
        assetFolders(assetsDir).firstOrNull { dir ->
            readMeta(File(dir, META_FILE)).let {
                it != null && (it.type == MetaType.TEXTURE || it.type == MetaType.PIXMAP_TEXTURE) && it.json.text("uuid") == uuid
            }
        }

    private fun otherUsers(assetsDir: File, terrainFolder: File, uuid: String): List<String> =
        assetFolders(assetsDir).filter { it.name != terrainFolder.name }.filter { dir ->
            readMeta(File(dir, META_FILE))?.takeIf { it.type == MetaType.TERRAIN }
                ?.json?.obj("additional")?.text(SPLAT_MAP) == uuid
        }.map { it.name }

    private fun assetFolders(assetsDir: File): List<File> =
        assetsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()

    private class Meta(val type: MetaType, val json: JsonNode)

    /** The native `meta.json` in [file], or null when it is missing, not JSON or not a supported native document. */
    private fun readMeta(file: File): Meta? {
        if (!file.isFile) return null
        return try {
            parseMeta(file.readText())
        } catch (e: java.io.IOException) {
            null
        }
    }

    private fun parseMeta(text: String): Meta? {
        return try {
            val doc = metas.read(text)
            Meta(doc.type, doc.json)
        } catch (e: UnsupportedDocumentFormat) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: java.io.IOException) {
            null
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            null
        }
    }
}
