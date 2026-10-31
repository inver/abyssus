/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser

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
class DocumentTextEditor(
    private val jsonProcessor: JsonProcessor,
    private val sceneParser: SceneParser,
) {
    fun edit(text: String, kind: DocumentKind, mutate: (Any) -> Boolean): TextEditOutcome {
        val doc: Any
        if (kind == DocumentKind.SCENE) {
            doc = runCatchingKeepingCancellation { sceneParser.parse(text) }
        } else {
            throw NotImplementedError("Unknown kind: $kind")
        }

        if (!mutate(doc)) {
            return TextEditOutcome.Unchanged
        }

        val edited = jsonProcessor.toString(doc)
        return if (edited == text) {
            TextEditOutcome.Unchanged
        } else TextEditOutcome.Edited(edited)
    }
}
