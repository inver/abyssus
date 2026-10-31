/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiIssue
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder

sealed interface UiTextResult {
    /** [after] is the whole new text; [before] is the exact text an undo restores. */
    data class Edited(val before: String, val after: String, val result: UiEditResult) : UiTextResult

    data object Unchanged : UiTextResult

    /** Nothing was written. [issues] lists the UI admission reasons when the document, or the edit's result, is not valid UI. */
    data class Rejected(val error: UiEditError, val subject: String? = null, val issues: List<UiIssue> = emptyList()) : UiTextResult

    /** The text is not a supported native document at all. */
    data class Refused(val cause: Throwable) : UiTextResult
}

/**
 * One UI edit as a text transform: the document must be an admitted UI asset before the edit and again after it, the
 * edit is refused when the text changed since [apply]'s `expectedText` (a gesture's start), and the printed result keeps
 * the original's style and everything the edit did not touch. The plugin wraps it in one undoable command.
 */
class UiTextEditor(
    private val json: JsonProcessor,
    private val edits: UiDocumentEdits = UiDocumentEdits(),
    private val decoder: UiCollectionDecoder = UiCollectionDecoder(),
) {
    /**
     * Runs [op] on the parsed `meta.json` [root] with the admission checks of [apply]: the tree must be an admitted UI asset
     * before and after the edit. For callers that already hold the tree inside their own document write (the plugin's
     * `editSceneJson`); [UiTransform.Changed] means the tree may be written.
     */
    fun transform(root: JsonNode, op: (UiDocumentEdits, JsonNode) -> UiEditResult): UiTransform {
        (decoder.decode(root) as? UiAdmission.Refused)?.let {
            return UiTransform.Rejected(UiEditError.NOT_EDITABLE, issues = it.issues)
        }
        return when (val result = op(edits, root)) {
            is UiEditResult.Rejected -> UiTransform.Rejected(result.error, result.subject)
            UiEditResult.Unchanged -> UiTransform.Unchanged
            else -> (decoder.decode(root) as? UiAdmission.Refused)?.let {
                UiTransform.Rejected(UiEditError.INVALID_RESULT, issues = it.issues)
            } ?: UiTransform.Changed(result)
        }
    }

    fun apply(text: String, expectedText: String? = null, op: (UiDocumentEdits, JsonNode) -> UiEditResult): UiTextResult {
        if (expectedText != null && expectedText != text) return UiTextResult.Rejected(UiEditError.STALE)
        // UI documents are still edited as a tree: there is no typed UI DTO to edit (gate G6).
        val root = runCatchingKeepingCancellation { json.readObject(text) }.getOrElse { return UiTextResult.Refused(it) }
        return when (val verdict = transform(root, op)) {
            is UiTransform.Rejected -> UiTextResult.Rejected(verdict.error, verdict.subject, verdict.issues)
            UiTransform.Unchanged -> UiTextResult.Unchanged
            is UiTransform.Changed -> {
                val printed = json.pretty(root)
                if (printed == text) UiTextResult.Unchanged else UiTextResult.Edited(text, printed, verdict.result)
            }
        }
    }
}

/** What [UiTextEditor.transform] made of a tree. */
sealed interface UiTransform {
    data class Changed(val result: UiEditResult) : UiTransform

    data object Unchanged : UiTransform

    data class Rejected(val error: UiEditError, val subject: String? = null, val issues: List<UiIssue> = emptyList()) : UiTransform
}
