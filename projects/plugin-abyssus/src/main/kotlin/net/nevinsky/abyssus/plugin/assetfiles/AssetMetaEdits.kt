/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.assetfiles

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.lib.core.editor.meta.EditError
import net.nevinsky.abyssus.lib.core.editor.meta.EditOutcome
import net.nevinsky.abyssus.lib.core.editor.meta.AssetFieldValue
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.plugin.filetype.editTextDocument
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.editor.meta.AssetMetaEditor

/** What came of one asset property edit. Only [Changed] wrote anything. */
sealed interface AssetEditResult {
    data object Changed : AssetEditResult

    /** The value already is the effective value: nothing was written. */
    data object Unchanged : AssetEditResult

    data class Rejected(val error: EditError) : AssetEditResult

    /** The stored value is no longer the one the editor showed; [actual] is what the file holds now. */
    data class Conflict(val actual: AssetFieldValue) : AssetEditResult

    /** The `meta.json` is missing or is not a JSON object: nothing was written. */
    data object Unreadable : AssetEditResult
}

/**
 * Commits one editable `additional` property of an asset's `meta.json` through [editSceneDocument], so the edit is one
 * undoable command on the document that keeps the file's formatting and number text. The rules are the core
 * `AssetMetaEditor`'s; the document text (saved or not) is what is compared with the value the editor was filled from.
 */
object AssetMetaEdits {
    fun update(project: Project, assetFolder: VirtualFile, key: String, expected: AssetFieldValue, value: AssetFieldValue, editor: AssetMetaEditor): AssetEditResult {
        val file = assetFolder.takeIf { it.isValid }?.findChild(META_FILE) ?: return AssetEditResult.Unreadable
        var result: AssetEditResult = AssetEditResult.Unreadable
        editTextDocument(project, file, AbyssusBundle.message("commandEditAssetMeta")) { text ->
            val edit = editor.editText(text, key, expected, value) ?: return@editTextDocument TextEditOutcome.Unchanged
            result = when (val outcome = edit.outcome) {
                EditOutcome.Changed -> AssetEditResult.Changed
                EditOutcome.NoChange -> AssetEditResult.Unchanged
                is EditOutcome.Rejected -> AssetEditResult.Rejected(outcome.error)
                is EditOutcome.Conflict -> AssetEditResult.Conflict(outcome.actual)
            }
            edit.text?.let { TextEditOutcome.Edited(it) } ?: TextEditOutcome.Unchanged
        }
        return result
    }
}
