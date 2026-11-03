/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import net.nevinsky.abyssus.lib.core.editor.terrain.TERRAIN_RECIPE_FILE
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SCULPT_BASE_FILE
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SCULPT_DELTA_FILE
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SCULPT_MANIFEST_FILE
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptFileSet
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileStore
import net.nevinsky.abyssus.plugin.assetfiles.AssetTransaction
import net.nevinsky.abyssus.plugin.assetfiles.FileChange
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot

/**
 * Turns the planned content of a terrain folder's height files ([SculptFileSet]) into one [AssetTransaction] against
 * what the files hold in [store] now: only files whose content differs become a [FileChange], a file that should not exist
 * is deleted and one that is new is created. Regenerate, a committed stroke, Reset and Forget all go through this, so
 * they share the engine's checks, rollback and undo. Null when nothing would change.
 *
 * [expected] are files the operation depends on but does not write (the `meta.json` it was planned from).
 */
class SculptTransactions(private val store: AssetFileStore) {
    fun plan(
        name: String,
        folderName: String,
        dataName: String,
        set: SculptFileSet,
        expected: Map<String, FileSnapshot> = emptyMap(),
    ): AssetTransaction? {
        val dir = "$ASSETS_DIR/$folderName"
        val wanted = listOf(
            "$dir/$dataName" to set.terrain,
            "$dir/$TERRAIN_RECIPE_FILE" to set.recipe,
            "$dir/$SCULPT_MANIFEST_FILE" to set.manifest,
            "$dir/$SCULPT_DELTA_FILE" to set.delta,
            "$dir/$SCULPT_BASE_FILE" to set.base,
        )
        val changes = wanted.mapNotNull { (path, bytes) ->
            val before = store.read(path)
            val after = bytes?.let { FileSnapshot.Bytes(it) } ?: FileSnapshot.Absent
            if (before == after) null else FileChange(path, before, after)
        }
        return if (changes.isEmpty()) null else AssetTransaction(name, changes, expectedFiles = expected)
    }
}
