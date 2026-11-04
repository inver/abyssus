/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiIssue
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder

sealed interface UiTextResult {
    /** [after] is the whole new text; [before] is the exact text an undo restores. */
    data class Edited(val before: String, val after: String, val result: UiEditResult) : UiTextResult

    data object Unchanged : UiTextResult

    /**
     * Nothing was written. [issues] lists the UI admission reasons when the document, or the edit's result, is not
     * valid UI.
     */
    data class Rejected(val error: UiEditError, val subject: String? = null, val issues: List<UiIssue> = emptyList()) :
        UiTextResult

    /** The text is not a supported native document at all. */
    data class Refused(val cause: Throwable) : UiTextResult
}

/**
 * One UI edit as a text transform: the document must be an admitted UI asset before the edit and again after it, the
 * edit is refused when the text changed since [apply]'s `expectedText` (a gesture's start), and the document is bound
 * to [UiMetaDoc], edited and printed by `JsonProcessor`. Members the model does not name are kept; number spelling is
 * not. The plugin wraps it in one undoable command.
 */
class UiTextEditor(
    private val json: JsonProcessor,
    private val edits: UiDocumentEdits = UiDocumentEdits(json),
    private val decoder: UiCollectionDecoder = UiCollectionDecoder(),
) {
    fun apply(text: String, expectedText: String? = null, op: UiTreeEdit): UiTextResult {
        if (expectedText != null && expectedText != text) {
            return UiTextResult.Rejected(UiEditError.STALE)
        }
        val admitted = runCatchingKeepingCancellation {
            decoder.decode(text, json)
        }
            .getOrElse {
                return UiTextResult.Refused(it)
            }
        (admitted as? UiAdmission.Refused)?.let {
            return UiTextResult.Rejected(UiEditError.NOT_EDITABLE, issues = it.issues)
        }
        val doc = runCatchingKeepingCancellation {
            json.parse(text, UiMetaDoc::class.java)
        }
            .getOrElse {
                return UiTextResult.Refused(it)
            }
        val result = op(edits, doc)
        if (result is UiEditResult.Rejected) {
            return UiTextResult.Rejected(result.error, result.subject)
        }
        if (result == UiEditResult.Unchanged) {
            return UiTextResult.Unchanged
        }
        val printed = json.pretty(doc)
        val after = runCatchingKeepingCancellation {
            decoder.decode(printed, json)
        }
            .getOrElse {
                return UiTextResult.Refused(it)
            }
        (after as? UiAdmission.Refused)?.let {
            return UiTextResult.Rejected(UiEditError.INVALID_RESULT, issues = it.issues)
        }
        return if (printed == text) {
            UiTextResult.Unchanged
        } else {
            UiTextResult.Edited(text, printed, result)
        }
    }
}
