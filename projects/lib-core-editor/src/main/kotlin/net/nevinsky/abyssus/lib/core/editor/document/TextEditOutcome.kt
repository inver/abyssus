/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

/** What a text editor made of a document's text. */
sealed interface TextEditOutcome {
    /** The edited document's text. */
    data class Edited(val text: String) : TextEditOutcome

    /** The mutation declined, or it changed nothing. */
    data object Unchanged : TextEditOutcome

    /** The text cannot be read as a supported native document, or the edit cannot be applied; [cause] says why. */
    data class Refused(val cause: Throwable) : TextEditOutcome
}
