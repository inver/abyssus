/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.document

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation

/** What [DocumentTextEditor.edit] made of a document's text. */
sealed interface TextEditOutcome {
    /** The edited document, printed in the style of the original (indented when it spans lines, else on one line). */
    data class Edited(val text: String) : TextEditOutcome

    /** The mutation declined, or what it did prints as the original text. */
    data object Unchanged : TextEditOutcome

    /** The text cannot be read as a supported native document, or the edit would leave it unsupported; [cause] says why. */
    data class Refused(val cause: Throwable) : TextEditOutcome
}

/**
 * The text transform behind every native document write: parse with [SceneJson] (key order and number text kept),
 * admit the document, let the mutation edit the tree, admit it again, and print it in the original's style. The
 * plugin's `editSceneJson` wraps it in one undoable command; a caller without the IDE uses it directly.
 */
class DocumentTextEditor(private val format: AbyssusDocumentFormat = AbyssusDocumentFormat()) {
    fun edit(text: String, kind: DocumentKind, mutate: (JsonNode) -> Boolean): TextEditOutcome {
        val root = runCatchingKeepingCancellation { SceneJson().parse(text).also { format.requireSupported(it, kind) } }
            .getOrElse { return TextEditOutcome.Refused(it) }
        if (!mutate(root)) return TextEditOutcome.Unchanged
        format.validate(root, kind)?.let { return TextEditOutcome.Refused(UnsupportedDocumentFormat(kind, it)) }
        val edited = SceneJson().inStyleOf(text, root)
        return if (edited == text) TextEditOutcome.Unchanged else TextEditOutcome.Edited(edited)
    }
}
