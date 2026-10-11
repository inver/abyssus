/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.META_VERSION_DEFAULT
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.foliageChunkSize
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.editor.terrain.FolderNameError
import net.nevinsky.abyssus.lib.core.editor.terrain.checkFolderName
import net.nevinsky.abyssus.lib.core.editor.terrain.uniqueAssetUuid
import java.io.File
import java.util.UUID

/** Why a foliage asset cannot be created; the dialog shows a localized reason for each. */
sealed interface FoliageCreateError {
    /** The folder name, with the shared folder-name reason (a name already taken included). */
    data class FolderName(val reason: FolderNameError) : FoliageCreateError

    /** The mask resolution is outside [FOLIAGE_MASK_RESOLUTION_MIN] through [FOLIAGE_MASK_RESOLUTION_MAX]. */
    data object MaskResolution : FoliageCreateError
}

/** A new foliage asset ready to be written: its [folder] under the project's `assets`, a fresh [uuid] and the staged files. */
class NewFoliageFiles(val folder: String, val uuid: String, val metaText: String, val dataBytes: ByteArray)

/**
 * Plans new foliage assets: one compact line of `format` and `formatVersion` first, then `version`, `lastModified`,
 * `uuid`, `type` and `additional` (`terrain`, `maskResolution`, no layers), plus the empty bake whose fingerprint
 * matches those inputs, so a fresh asset is not stale. Nothing is written here; the caller stages the files in an
 * `AssetTransaction`.
 */
class FoliageAssetWriter(
    private val json: JsonProcessor,
    private val dataFile: FoliageDataFile = FoliageDataFile(),
    private val fingerprint: FoliageFingerprint = FoliageFingerprint(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val randomUuid: () -> UUID = { UUID.randomUUID() },
) {
    /** Why nothing may be written for [rawName] and [maskResolution], or null when they may. Reads the assets folder. */
    fun check(projectDir: File, rawName: String, maskResolution: Int): FoliageCreateError? {
        checkFolderName(File(projectDir, ASSETS_DIR), rawName)?.let { return FoliageCreateError.FolderName(it) }
        if (maskResolution !in FOLIAGE_MASK_RESOLUTION_MIN..FOLIAGE_MASK_RESOLUTION_MAX) {
            return FoliageCreateError.MaskResolution
        }
        return null
    }

    /**
     * The staged foliage for [terrainFolder], or null when [check] refuses it (re-checked here: the folder may have
     * appeared since). [terrain] is the chosen terrain's heights, which the empty bake's fingerprint covers.
     */
    fun plan(projectDir: File, terrainFolder: String, rawName: String, maskResolution: Int, terrain: TerrainData): NewFoliageFiles? {
        val name = rawName.trim()
        if (check(projectDir, name, maskResolution) != null) return null
        val uuid = uniqueAssetUuid(json, File(projectDir, ASSETS_DIR), randomUuid)
        val settings = FoliageMeta(terrain = terrainFolder, maskResolution = maskResolution)
        val empty = dataFile.empty(fingerprint.of(settings, terrain, emptyMap()), foliageChunkSize(terrain.size))
        return NewFoliageFiles(name, uuid.toString(), metaText(terrainFolder, maskResolution, uuid, clock()), dataFile.write(empty))
    }

    private fun metaText(terrainFolder: String, maskResolution: Int, uuid: UUID, lastModified: Long): String {
        val nodes = JsonNodeFactory.instance
        val additional = nodes.objectNode().apply {
            put(FOLIAGE_TERRAIN, terrainFolder)
            put(FOLIAGE_MASK_RESOLUTION, maskResolution)
        }
        val root = nodes.objectNode().apply {
            put("format", "abyssus")
            put("formatVersion", 1)
            put("version", META_VERSION_DEFAULT)
            put("lastModified", lastModified)
            put("uuid", uuid.toString())
            put("type", MetaType.FOLIAGE.name)
            set<ObjectNode>("additional", additional)
        }
        return json.toString(root)
    }
}
