/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.DocumentTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
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
    private val edits: UiDocumentEdits = UiDocumentEdits(),
    private val decoder: UiCollectionDecoder = UiCollectionDecoder(),
    private val editor: DocumentTextEditor = DocumentTextEditor(),
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
        var verdict: UiTransform = UiTransform.Unchanged
        val outcome = editor.edit(text, DocumentKind.ASSET) { root ->
            verdict = transform(root, op)
            verdict is UiTransform.Changed
        }
        (verdict as? UiTransform.Rejected)?.let { return UiTextResult.Rejected(it.error, it.subject, it.issues) }
        return when (outcome) {
            is TextEditOutcome.Edited -> UiTextResult.Edited(text, outcome.text, (verdict as UiTransform.Changed).result)
            TextEditOutcome.Unchanged -> UiTextResult.Unchanged
            is TextEditOutcome.Refused -> UiTextResult.Refused(outcome.cause)
        }
    }
}

/** What [UiTextEditor.transform] made of a tree. */
sealed interface UiTransform {
    data class Changed(val result: UiEditResult) : UiTransform

    data object Unchanged : UiTransform

    data class Rejected(val error: UiEditError, val subject: String? = null, val issues: List<UiIssue> = emptyList()) : UiTransform
}
