/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.EditError
import net.nevinsky.abyssus.EditOutcome
import net.nevinsky.abyssus.FieldValue
import net.nevinsky.abyssus.filetype.editSceneJson
import net.nevinsky.abyssus.core.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.AssetMetaEditor

/** What came of one asset property edit. Only [Changed] wrote anything. */
sealed interface AssetEditResult {
    data object Changed : AssetEditResult

    /** The value already is the effective value: nothing was written. */
    data object Unchanged : AssetEditResult

    data class Rejected(val error: EditError) : AssetEditResult

    /** The stored value is no longer the one the editor showed; [actual] is what the file holds now. */
    data class Conflict(val actual: FieldValue) : AssetEditResult

    /** The `meta.json` is missing or is not a JSON object: nothing was written. */
    data object Unreadable : AssetEditResult
}

/**
 * Commits one editable `additional` property of an asset's `meta.json` through [editSceneJson], so the edit is one
 * undoable command on the document that keeps the file's formatting and number text. The rules are the core
 * `AssetMetaEditor`'s; the document text (saved or not) is what is compared with the value the editor was filled from.
 */
object AssetMetaEdits {
    fun update(project: Project, assetFolder: VirtualFile, key: String, expected: FieldValue, value: FieldValue, editor: AssetMetaEditor): AssetEditResult {
        val file = assetFolder.takeIf { it.isValid }?.findChild(META_FILE) ?: return AssetEditResult.Unreadable
        var result: AssetEditResult = AssetEditResult.Unreadable
        editSceneJson(project, file, AbyssusBundle.message("commandEditAssetMeta")) { root ->
            result = when (val outcome = editor.edit(root, key, expected, value)) {
                EditOutcome.Changed -> AssetEditResult.Changed
                EditOutcome.NoChange -> AssetEditResult.Unchanged
                is EditOutcome.Rejected -> AssetEditResult.Rejected(outcome.error)
                is EditOutcome.Conflict -> AssetEditResult.Conflict(outcome.actual)
            }
            result == AssetEditResult.Changed
        }
        return result
    }
}
