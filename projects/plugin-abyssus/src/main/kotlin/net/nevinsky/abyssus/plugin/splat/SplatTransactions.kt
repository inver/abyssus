/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import net.nevinsky.abyssus.lib.core.editor.terrain.splat.NewSplatFiles
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileStore
import net.nevinsky.abyssus.plugin.assetfiles.AssetTransaction
import net.nevinsky.abyssus.plugin.assetfiles.FileChange
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot

/**
 * Turns what a splat operation plans into one [AssetTransaction], so a stroke, Create splat map and Make unique copy
 * share the engine's checks, rollback and undo. A stroke keeps whole before/after PNG bytes: a mask patch cannot patch a
 * compressed image.
 */
class SplatTransactions(private val store: AssetFileStore, private val projectHasAssetsDir: () -> Boolean = { true }) {
    /**
     * One stroke: the image [imageName] in the texture asset folder [splatFolder] goes from [before] (the bytes the
     * stroke began with) to [after]; null when they are equal. [expected] are files the stroke depends on but does not
     * write (the texture's and the terrain's `meta.json`), checked as they are in [store] now.
     */
    fun stroke(
        name: String,
        splatFolder: String,
        imageName: String,
        before: ByteArray,
        after: ByteArray,
        expected: List<String>
    ): AssetTransaction? {
        if (before.contentEquals(after)) return null
        val path = "$ASSETS_DIR/$splatFolder/$imageName"
        return AssetTransaction(
            name,
            listOf(FileChange(path, FileSnapshot.Bytes(before), FileSnapshot.Bytes(after))),
            expectedFiles = expected.associateWith { store.read(it) },
        )
    }

    /**
     * Create splat map or Make unique copy: a new texture asset folder with its `meta.json` and image, and the terrain's
     * `meta.json` going from [terrainMetaBefore] to the linked text. Undo removes the folder and restores the link.
     * [terrainFolder] names the terrain asset whose `meta.json` changes.
     */
    fun create(
        name: String,
        terrainFolder: String,
        files: NewSplatFiles,
        terrainMetaBefore: ByteArray,
        imageName: String
    ): AssetTransaction {
        val base = "$ASSETS_DIR/${files.folder}"
        val terrainMeta = "$ASSETS_DIR/$terrainFolder/$META_FILE"
        return AssetTransaction(
            name,
            changes = listOf(
                FileChange("$base/$META_FILE", FileSnapshot.Absent, FileSnapshot.Bytes(files.metaText.toByteArray())),
                FileChange("$base/$imageName", FileSnapshot.Absent, FileSnapshot.Bytes(files.imageBytes)),
                FileChange(
                    terrainMeta,
                    FileSnapshot.Bytes(terrainMetaBefore),
                    FileSnapshot.Bytes(files.terrainMetaText.toByteArray())
                ),
            ),
            createdDirs = (if (projectHasAssetsDir()) emptyList() else listOf(ASSETS_DIR)) + base,
        )
    }
}
